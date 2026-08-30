package org.airshare.app.engine.network.server

import android.content.Context
import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.airshare.app.data.model.*
import org.airshare.app.engine.crypto.CryptoEngine
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.KeyPair
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

class AirShareServer(
    private val context: Context,
    private val hostDeviceName: String,
    private val isPinRequired: Boolean = false,
    private val expectedPin: String = "",
    port: Int = 8080
) : NanoHTTPD(port) {

    // Ephemeral KeyPair for session
    val serverKeyPair: KeyPair = CryptoEngine.generateEphemeralKeyPair()

    // Active session file list
    val sessionFiles = ConcurrentHashMap<String, TransferFile>()

    // Connected peer clients mapped by client IP/DeviceId -> SessionKey
    val peerSessionKeys = ConcurrentHashMap<String, SecretKey>()
    val connectedPeers = ConcurrentHashMap<String, DevicePeer>()

    private val _liveConnectedPeersFlow = MutableStateFlow<List<DevicePeer>>(emptyList())
    val liveConnectedPeersFlow: StateFlow<List<DevicePeer>> = _liveConnectedPeersFlow.asStateFlow()

    private val _totalTransferredBytes = MutableStateFlow(0L)
    val totalTransferredBytes: StateFlow<Long> = _totalTransferredBytes.asStateFlow()

    var onUploadCompleted: ((File, String) -> Unit)? = null

    fun setFiles(files: List<TransferFile>) {
        sessionFiles.clear()
        files.forEach { file ->
            sessionFiles[file.id] = file
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        try {
            when {
                // Handshake & Key Exchange
                uri == "/api/handshake" && method == Method.POST -> return handleHandshake(session)

                // Manifest of files
                uri == "/api/manifest" && method == Method.GET -> return handleManifest()

                // Streaming Chunk
                uri == "/api/chunk" && method == Method.GET -> return handleChunkDownload(session)

                // Client reporting its progress
                uri == "/api/client/progress" && method == Method.POST -> return handleClientProgress(session)

                // Web Connect / Browser Download
                uri == "/api/web/files" && method == Method.GET -> return handleWebFilesList()
                uri == "/api/web/download" && method == Method.GET -> return handleWebFileDownload(session)
                uri == "/api/web/upload" && method == Method.POST -> return handleWebFileUpload(session)

                // Server Status
                uri == "/api/status" -> return handleStatus()

                // Web UI Assets
                else -> return serveWebStaticAsset(uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                JSONObject().put("error", e.message ?: "Unknown error").toString()
            )
        }
    }

    private fun handleHandshake(session: IHTTPSession): Response {
        val map = HashMap<String, String>()
        session.parseBody(map)
        val postData = map["postData"] ?: ""
        val json = JSONObject(postData)

        val clientDeviceId = json.optString("deviceId", "client_${System.currentTimeMillis()}")
        val clientDeviceName = json.optString("deviceName", "Unknown Peer")
        val clientPublicKeyBase64 = json.optString("publicKeyBase64", "")
        val providedPin = json.optString("pinCode", "")

        if (isPinRequired && expectedPin.isNotBlank() && providedPin != expectedPin) {
            val resp = JSONObject().apply {
                put("accepted", false)
                put("message", "Invalid PIN code")
            }
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", resp.toString())
        }

        // Derive AES session key if public key was supplied
        if (clientPublicKeyBase64.isNotBlank()) {
            val clientPubKeyBytes = CryptoEngine.decodePublicKey(clientPublicKeyBase64)
            val sessionKey = CryptoEngine.deriveSharedSessionKey(serverKeyPair.private, clientPubKeyBytes)
            peerSessionKeys[clientDeviceId] = sessionKey
        }

        val peer = DevicePeer(
            id = clientDeviceId,
            name = clientDeviceName,
            ipAddress = session.remoteIpAddress ?: "unknown",
            state = PeerConnectionState.CONNECTED,
            publicKeyBase64 = clientPublicKeyBase64
        )
        connectedPeers[clientDeviceId] = peer
        _liveConnectedPeersFlow.value = connectedPeers.values.toList()

        val resp = JSONObject().apply {
            put("accepted", true)
            put("hostDeviceName", hostDeviceName)
            put("hostPublicKeyBase64", CryptoEngine.encodePublicKey(serverKeyPair.public))
            put("message", "Connected to host")
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", resp.toString())
    }

    private fun handleManifest(): Response {
        val array = JSONArray()
        sessionFiles.values.forEach { file ->
            val obj = JSONObject().apply {
                put("id", file.id)
                put("name", file.name)
                put("size", file.size)
                put("mimeType", file.mimeType)
                put("category", file.category.name)
                put("checksumSha256", file.checksumSha256)
                put("chunkCount", file.chunkCount)
            }
            array.put(obj)
        }
        val result = JSONObject().apply {
            put("hostName", hostDeviceName)
            put("totalFiles", sessionFiles.size)
            put("files", array)
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
    }

    private fun handleChunkDownload(session: IHTTPSession): Response {
        val params = session.parameters
        val fileId = params["fileId"]?.firstOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing fileId")
        val chunkIndex = params["chunkIndex"]?.firstOrNull()?.toIntOrNull() ?: 0
        val clientDeviceId = session.headers["x-device-id"] ?: session.remoteIpAddress ?: ""

        val file = sessionFiles[fileId] ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found")

        val chunkSize = 512 * 1024 // 512 KB chunks for high throughput and fast resume
        val startOffset = chunkIndex.toLong() * chunkSize
        if (startOffset >= file.size) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Chunk out of range")
        }
        val lengthToRead = minOf(chunkSize.toLong(), file.size - startOffset).toInt()

        val rawBytes = readRawFileChunk(file, startOffset, lengthToRead)

        val sessionKey = peerSessionKeys[clientDeviceId]
        val payload = if (sessionKey != null) {
            CryptoEngine.encryptChunk(rawBytes, sessionKey)
        } else {
            rawBytes
        }

        _totalTransferredBytes.value += rawBytes.size

        val responseStream = ByteArrayInputStream(payload)
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/octet-stream",
            responseStream,
            payload.size.toLong()
        ).apply {
            addHeader("X-Chunk-Index", chunkIndex.toString())
            addHeader("X-Encrypted", if (sessionKey != null) "true" else "false")
        }
    }

    private fun handleClientProgress(session: IHTTPSession): Response {
        val map = HashMap<String, String>()
        session.parseBody(map)
        val postData = map["postData"] ?: ""
        val json = JSONObject(postData)

        val deviceId = json.optString("deviceId")
        val progress = json.optInt("progressPercent", 0)
        val transferred = json.optLong("transferredBytes", 0L)
        val speed = json.optDouble("speedBytesPerSec", 0.0)

        connectedPeers[deviceId]?.let { peer ->
            peer.progressPercent = progress
            peer.transferredBytes = transferred
            peer.speedBytesPerSec = speed
            if (progress >= 100) {
                peer.state = PeerConnectionState.COMPLETED
            }
            _liveConnectedPeersFlow.value = connectedPeers.values.toList()
        }

        return newFixedLengthResponse(Response.Status.OK, "application/json", JSONObject().put("status", "ok").toString())
    }

    private fun handleWebFilesList(): Response {
        val array = JSONArray()
        sessionFiles.values.forEach { file ->
            val obj = JSONObject().apply {
                put("id", file.id)
                put("name", file.name)
                put("size", file.size)
                put("formattedSize", file.formattedSize)
                put("mimeType", file.mimeType)
                put("category", file.category.name)
            }
            array.put(obj)
        }
        val result = JSONObject().apply {
            put("hostName", hostDeviceName)
            put("files", array)
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", result.toString())
    }

    private fun handleWebFileDownload(session: IHTTPSession): Response {
        val fileId = session.parameters["id"]?.firstOrNull() ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing id")
        val file = sessionFiles[fileId] ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found")

        val inputStream: InputStream = if (file.localFilePath != null) {
            FileInputStream(File(file.localFilePath))
        } else if (file.uriString != null) {
            context.contentResolver.openInputStream(Uri.parse(file.uriString)) ?: FileInputStream(File(""))
        } else {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Cannot open stream")
        }

        return newFixedLengthResponse(
            Response.Status.OK,
            file.mimeType,
            inputStream,
            file.size
        ).apply {
            addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
        }
    }

    private fun handleWebFileUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)

        val saveDir = File(context.getExternalFilesDir(null), "WebUploads").apply { mkdirs() }
        var uploadedCount = 0

        files.forEach { (key, tempPath) ->
            if (key.startsWith("file") || key == "content") {
                val tempFile = File(tempPath)
                val destFile = File(saveDir, "Upload_${System.currentTimeMillis()}_${tempFile.name}")
                tempFile.copyTo(destFile, overwrite = true)
                uploadedCount++
                onUploadCompleted?.invoke(destFile, destFile.name)
            }
        }

        val resp = JSONObject().apply {
            put("success", true)
            put("uploadedFiles", uploadedCount)
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", resp.toString())
    }

    private fun handleStatus(): Response {
        val json = JSONObject().apply {
            put("hostDeviceName", hostDeviceName)
            put("activeFilesCount", sessionFiles.size)
            put("connectedPeersCount", connectedPeers.size)
            put("isPinRequired", isPinRequired)
            put("version", "1.0.0")
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
    }

    private fun serveWebStaticAsset(uri: String): Response {
        val assetPath = when (uri) {
            "/", "/index.html" -> "web/index.html"
            "/style.css" -> "web/style.css"
            "/app.js" -> "web/app.js"
            else -> "web/index.html"
        }

        val mimeType = when {
            assetPath.endsWith(".html") -> "text/html"
            assetPath.endsWith(".css") -> "text/css"
            assetPath.endsWith(".js") -> "application/javascript"
            else -> "text/plain"
        }

        return try {
            val stream = context.assets.open(assetPath)
            newChunkedResponse(Response.Status.OK, mimeType, stream)
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Asset not found")
        }
    }

    private fun readRawFileChunk(file: TransferFile, startOffset: Long, length: Int): ByteArray {
        val buffer = ByteArray(length)
        if (file.localFilePath != null) {
            RandomAccessFile(File(file.localFilePath), "r").use { raf ->
                raf.seek(startOffset)
                raf.readFully(buffer)
            }
        } else if (file.uriString != null) {
            context.contentResolver.openInputStream(Uri.parse(file.uriString))?.use { input ->
                var skipped = 0L
                while (skipped < startOffset) {
                    val s = input.skip(startOffset - skipped)
                    if (s <= 0) break
                    skipped += s
                }
                var bytesRead = 0
                while (bytesRead < length) {
                    val count = input.read(buffer, bytesRead, length - bytesRead)
                    if (count == -1) break
                    bytesRead += count
                }
            }
        }
        return buffer
    }
}
