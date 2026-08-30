package org.airshare.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airshare.app.data.model.CloneCategory
import org.airshare.app.data.model.CloneCategoryType
import org.airshare.app.data.model.MediaCategory
import org.airshare.app.data.model.TransferFile
import java.io.File
import java.util.UUID

class FileRepository(private val context: Context) {

    suspend fun getApps(): List<TransferFile> = withContext(Dispatchers.IO) {
        val appList = mutableListOf<TransferFile>()
        val pm = context.packageManager
        val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }

        for (pkg in installedPackages) {
            val appInfo = pkg.applicationInfo ?: continue
            // Filter system apps unless updated
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !isUpdatedSystem && pkg.packageName != context.packageName) {
                continue
            }

            val sourceDir = appInfo.sourceDir ?: continue
            val apkFile = File(sourceDir)
            if (apkFile.exists() && apkFile.length() > 0) {
                val appLabel = pm.getApplicationLabel(appInfo).toString()
                appList.add(
                    TransferFile(
                        id = "app_${pkg.packageName}",
                        name = "$appLabel.apk",
                        size = apkFile.length(),
                        mimeType = "application/vnd.android.package-archive",
                        category = MediaCategory.APPS,
                        localFilePath = apkFile.absolutePath,
                        uriString = Uri.fromFile(apkFile).toString()
                    )
                )
            }
        }
        appList.sortedBy { it.name.lowercase() }
    }

    suspend fun getPhotos(): List<TransferFile> = withContext(Dispatchers.IO) {
        val items = mutableListOf<TransferFile>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATA
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        queryMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder) { cursor ->
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: "Photo_$id.jpg"
            val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
            val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)) ?: "image/jpeg"
            val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
            val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

            if (size > 0) {
                items.add(
                    TransferFile(
                        id = "photo_$id",
                        name = name,
                        size = size,
                        mimeType = mimeType,
                        category = MediaCategory.PHOTOS,
                        localFilePath = path,
                        uriString = contentUri.toString()
                    )
                )
            }
        }
        items
    }

    suspend fun getVideos(): List<TransferFile> = withContext(Dispatchers.IO) {
        val items = mutableListOf<TransferFile>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATA
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        queryMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder) { cursor ->
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) ?: "Video_$id.mp4"
            val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))
            val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)) ?: "video/mp4"
            val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))
            val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

            if (size > 0) {
                items.add(
                    TransferFile(
                        id = "video_$id",
                        name = name,
                        size = size,
                        mimeType = mimeType,
                        category = MediaCategory.VIDEOS,
                        localFilePath = path,
                        uriString = contentUri.toString()
                    )
                )
            }
        }
        items
    }

    suspend fun getMusic(): List<TransferFile> = withContext(Dispatchers.IO) {
        val items = mutableListOf<TransferFile>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA
        )
        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        queryMediaStore(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder) { cursor ->
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)) ?: "Audio_$id.mp3"
            val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE))
            val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)) ?: "audio/mpeg"
            val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
            val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

            if (size > 0) {
                items.add(
                    TransferFile(
                        id = "music_$id",
                        name = name,
                        size = size,
                        mimeType = mimeType,
                        category = MediaCategory.MUSIC,
                        localFilePath = path,
                        uriString = contentUri.toString()
                    )
                )
            }
        }
        items
    }

    suspend fun getDocuments(): List<TransferFile> = withContext(Dispatchers.IO) {
        val items = mutableListOf<TransferFile>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATA
        )
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} IN (?, ?, ?, ?, ?, ?, ?)"
        val selectionArgs = arrayOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "text/plain"
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        queryMediaStore(MediaStore.Files.getContentUri("external"), projection, selection, selectionArgs, sortOrder) { cursor ->
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)) ?: "Document_$id"
            val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE))
            val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)) ?: "application/octet-stream"
            val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))
            val contentUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)

            if (size > 0) {
                items.add(
                    TransferFile(
                        id = "doc_$id",
                        name = name,
                        size = size,
                        mimeType = mimeType,
                        category = MediaCategory.DOCS,
                        localFilePath = path,
                        uriString = contentUri.toString()
                    )
                )
            }
        }
        items
    }

    suspend fun getCloneCategories(): List<CloneCategory> = withContext(Dispatchers.IO) {
        val photos = getPhotos()
        val videos = getVideos()
        val music = getMusic()
        val apps = getApps()
        val docs = getDocuments()

        listOf(
            CloneCategory(
                type = CloneCategoryType.PHOTOS,
                title = "Photos & Gallery",
                count = photos.size,
                totalBytes = photos.sumOf { it.size },
                isSelected = true
            ),
            CloneCategory(
                type = CloneCategoryType.VIDEOS,
                title = "Videos",
                count = videos.size,
                totalBytes = videos.sumOf { it.size },
                isSelected = true
            ),
            CloneCategory(
                type = CloneCategoryType.MUSIC,
                title = "Music & Audio",
                count = music.size,
                totalBytes = music.sumOf { it.size },
                isSelected = true
            ),
            CloneCategory(
                type = CloneCategoryType.APPS,
                title = "Installed Apps",
                count = apps.size,
                totalBytes = apps.sumOf { it.size },
                isSelected = true
            ),
            CloneCategory(
                type = CloneCategoryType.DOCUMENTS,
                title = "Documents & Files",
                count = docs.size,
                totalBytes = docs.sumOf { it.size },
                isSelected = true
            )
        )
    }

    private inline fun queryMediaStore(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        block: (Cursor) -> Unit
    ) {
        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext()) {
                    block(cursor)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
