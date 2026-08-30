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
            val url = URL("http://$hostIp:$port/api/handshake")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
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
            val message = json.optString("message", "")

            if (accepted && hostPubKeyBase64.isNotBlank()) {
                val hostPubKeyBytes = CryptoEngine.decodePublicKey(hostPubKeyBase64)
                sessionKey = CryptoEngine.deriveSharedSessionKey(clientKeyPair.private, hostPubKeyBytes)
            }

            HandshakeResponse(
                accepted = accepted,
                hostDeviceName = hostName,
                hostPublicKeyBase64 = hostPubKeyBase64,
                message = message
            )
        }

    suspend fun fetchManifest(hostIp: String, port: Int = 8080): List<TransferFile> =
        withContext(Dispatchers.IO) {
            val url = URL("http://$hostIp:$port/api/manifest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val filesArray = json.getJSONArray("files")
            val result = mutableListOf<TransferFile>()

            for (i in 0 until filesArray.length()) {
                val item = filesArray.getJSONObject(i)
                val categoryName = item.optString("category", "FILES")
                val category = runCatching { MediaCategory.valueOf(categoryName) }.getOrDefault(MediaCategory.FILES)
                val size = item.getLong("size")
                val chunkSize = 512 * 1024
                val chunkCount = ((size + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)

                result.add(
                    TransferFile(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        size = size,
                        mimeType = item.optString("mimeType", "application/octet-stream"),
                        category = category,
                        checksumSha256 = item.optString("checksumSha256", ""),
                        chunkCount = chunkCount,
                        status = TransferStatus.PENDING
                    )
                )
            }
            result
        }

    suspend fun downloadFiles(
        hostIp: String,
        port: Int = 8080,
        files: List<TransferFile>,
        targetDirectory: File,
        onSessionProgress: (speedMBs: Double, etaSec: Long, totalProgress: Int) -> Unit,
        onFileFinished: (TransferFile, File) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        isPaused = false
        isCanceled = false

        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs()
        }

        var totalSessionBytes = files.sumOf { it.size }
        var sessionTransferredBytes = 0L
        var lastSpeedCalcTime = System.currentTimeMillis()
        var bytesSinceLastSpeedCalc = 0L
        var currentSpeedMBs = 0.0

        for (file in files) {
            if (isCanceled) break

            val destFile = File(targetDirectory, file.name)
            val chunkSize = 512 * 1024
            val totalChunks = ((file.size + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)

            // Resumable transfer support: detect existing valid size
            var startChunk = 0
            if (destFile.exists()) {
                val existingLen = destFile.length()
                if (existingLen < file.size) {
                    startChunk = (existingLen / chunkSize).toInt()
                    file.transferredBytes = (startChunk.toLong() * chunkSize).coerceAtMost(file.size)
                    sessionTransferredBytes += file.transferredBytes
                } else if (existingLen == file.size) {
                    // File already completely downloaded!
                    file.transferredBytes = file.size
                    file.status = TransferStatus.COMPLETED
                    sessionTransferredBytes += file.size
                    onFileFinished(file, destFile)
                    continue
                }
            }

            file.status = TransferStatus.TRANSFERRING
            val raf = RandomAccessFile(destFile, "rw")

            try {
                for (chunkIdx in startChunk until totalChunks) {
                    while (isPaused && !isCanceled) {
                        delay(200)
                    }
                    if (isCanceled) break

                    val chunkBytes = fetchChunkWithRetry(hostIp, port, file.id, chunkIdx)
                    val decryptedBytes = if (sessionKey != null) {
                        CryptoEngine.decryptChunk(chunkBytes, sessionKey!!)
                    } else {
                        chunkBytes
                    }

                    val seekOffset = chunkIdx.toLong() * chunkSize
                    raf.seek(seekOffset)
                    raf.write(decryptedBytes)

                    file.transferredBytes += decryptedBytes.size
                    sessionTransferredBytes += decryptedBytes.size
                    bytesSinceLastSpeedCalc += decryptedBytes.size

                    // Update real-time metrics every 400ms
                    val now = System.currentTimeMillis()
                    val timeDelta = now - lastSpeedCalcTime
                    if (timeDelta >= 400) {
                        val speedBytesSec = (bytesSinceLastSpeedCalc.toDouble() / (timeDelta.toDouble() / 1000.0))
                        currentSpeedMBs = speedBytesSec / (1024.0 * 1024.0)
                        val remainingBytes = (totalSessionBytes - sessionTransferredBytes).coerceAtLeast(0L)
                        val etaSec = if (speedBytesSec > 0) (remainingBytes / speedBytesSec).toLong() else 0L
                        val totalProgress = if (totalSessionBytes > 0) ((sessionTransferredBytes.toDouble() / totalSessionBytes) * 100).toInt() else 0

                        onSessionProgress(currentSpeedMBs, etaSec, totalProgress)
                        reportProgressToHost(hostIp, port, totalProgress, sessionTransferredBytes, speedBytesSec)

                        lastSpeedCalcTime = now
                        bytesSinceLastSpeedCalc = 0L
                    }
                }

                if (!isCanceled) {
                    file.status = TransferStatus.COMPLETED
                    onFileFinished(file, destFile)
                }
            } catch (e: Exception) {
                file.status = TransferStatus.FAILED
                file.errorMessage = e.message
                e.printStackTrace()
            } finally {
                raf.close()
            }
        }

        !isCanceled
    }

    private suspend fun fetchChunkWithRetry(hostIp: String, port: Int, fileId: String, chunkIndex: Int, maxRetries: Int = 3): ByteArray {
        var lastError: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val url = URL("http://$hostIp:$port/api/chunk?fileId=$fileId&chunkIndex=$chunkIndex")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 10000
                    setRequestProperty("x-device-id", clientDeviceId)
                }

                if (conn.responseCode == 200) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    return bytes
                } else {
                    throw IOException("HTTP error ${conn.responseCode}")
                }
            } catch (e: Exception) {
                lastError = e
                delay(300L * attempt)
            }
        }
        throw lastError ?: IOException("Failed to fetch chunk $chunkIndex")
    }

    private fun reportProgressToHost(hostIp: String, port: Int, progressPercent: Int, transferredBytes: Long, speed: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://$hostIp:$port/api/client/progress")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 3000
                    readTimeout = 3000
                    setRequestProperty("Content-Type", "application/json")
                }

                val body = JSONObject().apply {
                    put("deviceId", clientDeviceId)
                    put("progressPercent", progressPercent)
                    put("transferredBytes", transferredBytes)
                    put("speedBytesPerSec", speed)
                }

                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                conn.responseCode
            } catch (e: Exception) {
                // Ignore transient heartbeat errors
            }
        }
    }
}
