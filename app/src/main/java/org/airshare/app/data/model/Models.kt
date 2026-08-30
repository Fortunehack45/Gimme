package org.airshare.app.data.model

import android.net.Uri
import java.io.Serializable

enum class MediaCategory {
    APPS,
    PHOTOS,
    VIDEOS,
    MUSIC,
    DOCS,
    FILES
}

enum class TransferStatus {
    PENDING,
    CONNECTING,
    TRANSFERRING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED
}

enum class SessionType {
    SEND_ONE_TO_ONE,
    RECEIVE_ONE_TO_ONE,
    GROUP_BROADCAST,
    WEB_CONNECT,
    PHONE_CLONE
}

enum class PeerConnectionState {
    DISCOVERED,
    PAIRING,
    CONNECTED,
    TRANSFERRING,
    COMPLETED,
    REJECTED,
    DISCONNECTED
}

data class TransferFile(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val category: MediaCategory,
    val uriString: String? = null,
    val localFilePath: String? = null,
    var checksumSha256: String = "",
    var chunkCount: Int = 1,
    var transferredBytes: Long = 0L,
    var status: TransferStatus = TransferStatus.PENDING,
    var errorMessage: String? = null
) : Serializable {
    val progressPercent: Int
        get() = if (size > 0) ((transferredBytes.toDouble() / size) * 100).toInt().coerceIn(0, 100) else 0

    val formattedSize: String
        get() = formatByteSize(size)

    companion object {
        fun formatByteSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
            val pre = "KMGTPE"[exp - 1]
            return "%.1f %sB".format(bytes / Math.pow(1024.0, exp.toDouble()), pre)
        }
    }
}

data class DevicePeer(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int = 8080,
    val isHost: Boolean = false,
    var state: PeerConnectionState = PeerConnectionState.DISCOVERED,
    var progressPercent: Int = 0,
    var transferredBytes: Long = 0L,
    var speedBytesPerSec: Double = 0.0,
    var publicKeyBase64: String = "",
    val avatarColorHex: String = "#6366F1"
) : Serializable

data class ChunkMeta(
    val fileId: String,
    val chunkIndex: Int,
    val startOffset: Long,
    val endOffset: Long,
    val chunkSize: Int,
    val isLastChunk: Boolean = false
) : Serializable

data class TransferSession(
    val sessionId: String,
    val type: SessionType,
    val isHost: Boolean,
    val pinCode: String = "",
    val files: MutableList<TransferFile> = mutableListOf(),
    val peers: MutableList<DevicePeer> = mutableListOf(),
    var status: TransferStatus = TransferStatus.PENDING,
    var speedBytesPerSec: Double = 0.0,
    var etaSeconds: Long = 0L
) : Serializable {
    val totalBytes: Long
        get() = files.sumOf { it.size }

    val transferredBytes: Long
        get() = files.sumOf { it.transferredBytes }

    val progressPercent: Int
        get() {
            val total = totalBytes
            return if (total > 0) ((transferredBytes.toDouble() / total) * 100).toInt().coerceIn(0, 100) else 0
        }
}

enum class CloneCategoryType {
    PHOTOS,
    VIDEOS,
    MUSIC,
    APPS,
    DOCUMENTS,
    CONTACTS
}

data class CloneCategory(
    val type: CloneCategoryType,
    val title: String,
    val count: Int,
    val totalBytes: Long,
    var isSelected: Boolean = true
) {
    val formattedSize: String
        get() = TransferFile.formatByteSize(totalBytes)
}

data class HandshakeRequest(
    val deviceId: String,
    val deviceName: String,
    val publicKeyBase64: String,
    val appVersion: String = "1.0.0",
    val pinCode: String? = null
) : Serializable

data class HandshakeResponse(
    val accepted: Boolean,
    val hostDeviceName: String,
    val hostPublicKeyBase64: String,
    val message: String? = null
) : Serializable

data class ManifestPayload(
    val sessionId: String,
    val hostName: String,
    val files: List<TransferFileItemPayload>
) : Serializable

data class TransferFileItemPayload(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val category: String,
    val checksumSha256: String,
    val chunkCount: Int
) : Serializable
