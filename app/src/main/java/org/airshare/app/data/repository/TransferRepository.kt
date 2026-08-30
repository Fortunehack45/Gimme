package org.airshare.app.data.repository

import kotlinx.coroutines.flow.Flow
import org.airshare.app.data.local.TransferDao
import org.airshare.app.data.local.TransferEntity
import org.airshare.app.data.model.TransferFile
import org.airshare.app.data.model.TransferStatus

class TransferRepository(private val transferDao: TransferDao) {

    val allTransfers: Flow<List<TransferEntity>> = transferDao.getAllTransfers()

    fun getTransfersByDirection(isIncoming: Boolean): Flow<List<TransferEntity>> =
        transferDao.getTransfersByDirection(isIncoming)

    suspend fun recordTransfer(
        sessionId: String,
        file: TransferFile,
        isIncoming: Boolean,
        peerName: String,
        status: TransferStatus = TransferStatus.COMPLETED
    ): Long {
        val entity = TransferEntity(
            sessionId = sessionId,
            fileName = file.name,
            fileSize = file.size,
            mimeType = file.mimeType,
            category = file.category,
            isIncoming = isIncoming,
            peerName = peerName,
            localFilePath = file.localFilePath,
            status = status,
            timestamp = System.currentTimeMillis()
        )
        return transferDao.insertTransfer(entity)
    }

    suspend fun recordBatchTransfers(
        sessionId: String,
        files: List<TransferFile>,
        isIncoming: Boolean,
        peerName: String
    ) {
        val entities = files.map { file ->
            TransferEntity(
                sessionId = sessionId,
                fileName = file.name,
                fileSize = file.size,
                mimeType = file.mimeType,
                category = file.category,
                isIncoming = isIncoming,
                peerName = peerName,
                localFilePath = file.localFilePath,
                status = file.status,
                timestamp = System.currentTimeMillis()
            )
        }
        transferDao.insertTransfers(entities)
    }

    suspend fun deleteTransfer(entity: TransferEntity) {
        transferDao.deleteTransfer(entity)
    }

    suspend fun deleteById(id: Long) {
        transferDao.deleteById(id)
    }

    suspend fun clearHistory() {
        transferDao.clearAll()
    }
}
