package org.airshare.app.engine.network.client

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.airshare.app.data.model.*
import org.airshare.app.engine.crypto.CryptoEngine
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPair
import javax.crypto.SecretKey

class AirShareClient(
    private val context: Context,
    private val clientDeviceId: String,
    private val clientDeviceName: String
) {

    private val clientKeyPair: KeyPair = CryptoEngine.generateEphemeralKeyPair()
    private var sessionKey: SecretKey? = null

    private val _transferStateFlow = MutableStateFlow<TransferSession?>(null)
    val transferStateFlow: StateFlow<TransferSession?> = _transferStateFlow.asStateFlow()

    @Volatile
    private var isPaused = false

    @Volatile
    private var isCanceled = false

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    fun cancel() {
        isCanceled = true
    }

    suspend fun performHandshake(hostIp: String, port: Int = 8080, pinCode: String = ""): HandshakeResponse =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("http://$hostIp:$port/api/handshake")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("Content-Type", "application/json")
                }

                val body = JSONObject().apply {
                    put("deviceId", clientDeviceId)
                    put("deviceName", clientDeviceName)
                    put("publicKeyBase64", CryptoEngine.encodePublicKey(clientKeyPair.public))
                    put("pinCode", pinCode)
                    put("appVersion", "1.0.0")
                }

                conn.outputStream.use { os ->
                    os.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                val responseString = if (responseCode in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "{}"
                }

                val json = JSONObject(responseString)
                val accepted = json.optBoolean("accepted", false)
                val hostName = json.optString("hostDeviceName", "Host Device")
                val hostPubKeyBase64 = json.optString("hostPublicKeyBase64", "")
                val message = json.optString("message", if (accepted) "Connected" else "Connection rejected")

                if (accepted && hostPubKeyBase64.isNotBlank()) {
                    try {
                        val hostPubKeyBytes = CryptoEngine.decodePublicKey(hostPubKeyBase64)
                        sessionKey = CryptoEngine.deriveSharedSessionKey(clientKeyPair.private, hostPubKeyBytes)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                HandshakeResponse(
                    accepted = accepted,
                    hostDeviceName = hostName,
                    hostPublicKeyBase64 = hostPubKeyBase64,
                    message = message
                )
            } catch (e: Exception) {
                HandshakeResponse(
                    accepted = false,
                    hostDeviceName = "",
                    hostPublicKeyBase64 = "",
                    message = e.message ?: "Connection error"
                )
            }
        }

    suspend fun fetchManifest(hostIp: String, port: Int = 8080): List<TransferFile> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("http://$hostIp:$port/api/manifest")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                }

                if (conn.responseCode != 200) return@withContext emptyList()

                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val array = json.optJSONArray("files") ?: return@withContext emptyList()

                val list = mutableListOf<TransferFile>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val catName = obj.optString("category", MediaCategory.FILES.name)
                    val cat = runCatching { MediaCategory.valueOf(catName) }.getOrDefault(MediaCategory.FILES)

                    list.add(
                        TransferFile(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            size = obj.getLong("size"),
                            mimeType = obj.optString("mimeType", "application/octet-stream"),
                            category = cat,
                            checksumSha256 = obj.optString("checksumSha256", ""),
                            chunkCount = obj.optInt("chunkCount", 1),
                            status = TransferStatus.PENDING
                        )
                    )
                }
                list
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    suspend fun downloadFiles(
        hostIp: String,
        port: Int,
        files: List<TransferFile>,
        targetDirectory: File,
        onSessionProgress: (speedMBs: Double, etaSeconds: Long, totalProgressPercent: Int) -> Unit,
        onFileFinished: (TransferFile, File) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs()
        }

        var totalTransferredBytes = 0L
        val totalSessionBytes = files.sumOf { it.size }
        var sessionStartTime = System.currentTimeMillis()
        var lastCalcTime = sessionStartTime
        var lastCalcBytes = 0L
        var currentSpeedMBs = 0.0

        for (file in files) {
            if (isCanceled) break

            val destinationFile = File(targetDirectory, file.name)
            file.status = TransferStatus.TRANSFERRING

            val chunkSize = 512 * 1024L
            val totalChunks = ((file.size + chunkSize - 1) / chunkSize).toInt()
            var existingBytes = 0L

            if (destinationFile.exists()) {
                existingBytes = destinationFile.length()
                if (existingBytes > file.size) {
                    destinationFile.delete()
                    existingBytes = 0L
                }
            }

            val startChunkIndex = (existingBytes / chunkSize).toInt()
            totalTransferredBytes += existingBytes
            file.transferredBytes = existingBytes

            val raf = try {
                RandomAccessFile(destinationFile, "rw")
            } catch (e: Exception) {
                e.printStackTrace()
                file.status = TransferStatus.FAILED
                file.errorMessage = e.message
                continue
            }

            raf.use { output ->
                output.seek(startChunkIndex * chunkSize)

                for (chunkIndex in startChunkIndex until totalChunks) {
                    while (isPaused && !isCanceled) {
                        delay(200)
                    }
                    if (isCanceled) break

                    var chunkSuccess = false
                    var retryCount = 0

                    while (!chunkSuccess && retryCount < 4 && !isCanceled) {
                        try {
                            val chunkData = fetchChunk(hostIp, port, file.id, chunkIndex)
                            if (chunkData != null) {
                                output.write(chunkData)
                                file.transferredBytes += chunkData.size
                                totalTransferredBytes += chunkData.size
                                chunkSuccess = true

                                val now = System.currentTimeMillis()
                                val deltaSec = (now - lastCalcTime) / 1000.0
                                if (deltaSec >= 0.4) {
                                    val deltaBytes = totalTransferredBytes - lastCalcBytes
                                    currentSpeedMBs = (deltaBytes / (1024.0 * 1024.0)) / deltaSec
                                    lastCalcTime = now
                                    lastCalcBytes = totalTransferredBytes

                                    val remainingBytes = (totalSessionBytes - totalTransferredBytes).coerceAtLeast(0)
                                    val etaSec = if (currentSpeedMBs > 0.05) (remainingBytes / (currentSpeedMBs * 1024 * 1024)).toLong() else 0L
                                    val totalProgress = if (totalSessionBytes > 0) ((totalTransferredBytes.toDouble() / totalSessionBytes) * 100).toInt().coerceIn(0, 100) else 0

                                    onSessionProgress(currentSpeedMBs, etaSec, totalProgress)
                                }
                            } else {
                                retryCount++
                                delay(300)
                            }
                        } catch (e: Exception) {
                            retryCount++
                            delay(300)
                        }
                    }

                    if (!chunkSuccess) {
                        file.status = TransferStatus.FAILED
                        file.errorMessage = "Failed downloading chunk $chunkIndex"
                        break
                    }
                }
            }

            if (file.transferredBytes >= file.size) {
                file.status = TransferStatus.COMPLETED
                onFileFinished(file, destinationFile)
            } else if (file.status != TransferStatus.FAILED) {
                file.status = TransferStatus.PAUSED
            }
        }

        !isCanceled && files.all { it.status == TransferStatus.COMPLETED }
    }

    private fun fetchChunk(hostIp: String, port: Int, fileId: String, chunkIndex: Int): ByteArray? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$hostIp:$port/api/chunk?fileId=$fileId&chunkIndex=$chunkIndex")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 10000
                setRequestProperty("X-Device-Id", clientDeviceId)
            }

            if (conn.responseCode != 200) return null

            val isEncrypted = conn.getHeaderField("X-Encrypted") == "true"
            val rawBytes = conn.inputStream.use { it.readBytes() }

            return if (isEncrypted && sessionKey != null) {
                CryptoEngine.decryptChunk(rawBytes, sessionKey!!)
            } else {
                rawBytes
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            conn?.disconnect()
        }
    }
}
