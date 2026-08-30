package org.airshare.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {

    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfer_history WHERE isIncoming = :incoming ORDER BY timestamp DESC")
    fun getTransfersByDirection(incoming: Boolean): Flow<List<TransferEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: TransferEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfers(transfers: List<TransferEntity>)

    @Update
    suspend fun updateTransfer(transfer: TransferEntity)

    @Delete
    suspend fun deleteTransfer(transfer: TransferEntity)

    @Query("DELETE FROM transfer_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transfer_history WHERE timestamp < :olderThanTimestamp")
    suspend fun deleteOlderThan(olderThanTimestamp: Long)

    @Query("DELETE FROM transfer_history")
    suspend fun clearAll()
}
