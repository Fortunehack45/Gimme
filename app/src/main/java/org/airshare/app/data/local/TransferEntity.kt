package org.airshare.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.airshare.app.data.model.MediaCategory
import org.airshare.app.data.model.TransferStatus

@Entity(tableName = "transfer_history")
data class TransferEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val category: MediaCategory,
    val isIncoming: Boolean, // true = received, false = sent
    val peerName: String,
    val localFilePath: String?,
    val status: TransferStatus,
    val timestamp: Long = System.currentTimeMillis()
)
