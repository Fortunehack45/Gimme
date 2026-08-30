package org.airshare.app.data.local

import androidx.room.TypeConverter
import org.airshare.app.data.model.MediaCategory
import org.airshare.app.data.model.TransferStatus

class Converters {
    @TypeConverter
    fun fromCategory(category: MediaCategory): String = category.name

    @TypeConverter
    fun toCategory(value: String): MediaCategory = runCatching { MediaCategory.valueOf(value) }.getOrDefault(MediaCategory.FILES)

    @TypeConverter
    fun fromStatus(status: TransferStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): TransferStatus = runCatching { TransferStatus.valueOf(value) }.getOrDefault(TransferStatus.PENDING)
}
