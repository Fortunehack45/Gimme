package org.airshare.app.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.airshare.app.data.model.CloneCategory
import org.airshare.app.data.model.CloneCategoryType
import org.airshare.app.data.model.MediaCategory
import org.airshare.app.data.model.TransferFile
import java.io.File

class FileRepository(private val context: Context) {

    suspend fun getFilesForCategory(category: MediaCategory): List<TransferFile> = withContext(Dispatchers.IO) {
        when (category) {
            MediaCategory.APPS -> getInstalledApps()
            MediaCategory.PHOTOS -> getPhotos()
            MediaCategory.VIDEOS -> getVideos()
            MediaCategory.MUSIC -> getMusic()
            MediaCategory.DOCS, MediaCategory.FILES -> getDocuments()
        }
    }

    suspend fun getPhotos(): List<TransferFile> = withContext(Dispatchers.IO) {
        queryMediaStore(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaCategory.PHOTOS,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.DATA)
        )
    }

    suspend fun getVideos(): List<TransferFile> = withContext(Dispatchers.IO) {
        queryMediaStore(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaCategory.VIDEOS,
            arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.MIME_TYPE, MediaStore.Video.Media.DATA)
        )
    }

    suspend fun getMusic(): List<TransferFile> = withContext(Dispatchers.IO) {
        queryMediaStore(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaCategory.MUSIC,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.DATA)
        )
    }

    suspend fun getDocuments(): List<TransferFile> = withContext(Dispatchers.IO) {
        queryDocumentFiles()
    }

    suspend fun getInstalledApps(): List<TransferFile> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val appsList = mutableListOf<TransferFile>()

        try {
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                pm.getInstalledPackages(0)
            }

            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue

                if (pkg.packageName == context.packageName) continue

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val launchIntent = pm.getLaunchIntentForPackage(pkg.packageName)

                if (!isSystem || isUpdatedSystem || launchIntent != null) {
                    val apkFile = File(appInfo.sourceDir)
                    if (apkFile.exists() && apkFile.length() > 0) {
                        val appLabel = pm.getApplicationLabel(appInfo).toString()
                        val versionName = pkg.versionName ?: "1.0"
                        val fileName = "${appLabel.replace(Regex("[^a-zA-Z0-9._-]"), "_")}_v${versionName}.apk"

                        appsList.add(
                            TransferFile(
                                id = "app_${pkg.packageName}",
                                name = fileName,
                                size = apkFile.length(),
                                mimeType = "application/vnd.android.package-archive",
                                category = MediaCategory.APPS,
                                localFilePath = apkFile.absolutePath,
                                packageName = pkg.packageName
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        appsList.sortedBy { it.name.lowercase() }
    }

    private fun queryMediaStore(
        contentUri: Uri,
        category: MediaCategory,
        projection: Array<String>
    ): List<TransferFile> {
        val files = mutableListOf<TransferFile>()
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        val cursor: Cursor? = context.contentResolver.query(
            contentUri,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(projection[0])
            val nameCol = it.getColumnIndexOrThrow(projection[1])
            val sizeCol = it.getColumnIndexOrThrow(projection[2])
            val mimeCol = it.getColumnIndexOrThrow(projection[3])
            val dataCol = if (projection.size > 4) it.getColumnIndex(projection[4]) else -1

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val name = it.getString(nameCol) ?: "Unknown"
                val size = it.getLong(sizeCol)
                val mime = it.getString(mimeCol) ?: "application/octet-stream"
                val path = if (dataCol != -1) it.getString(dataCol) else null

                if (size > 0) {
                    files.add(
                        TransferFile(
                            id = "${category.name}_$id",
                            name = name,
                            size = size,
                            mimeType = mime,
                            category = category,
                            localFilePath = path
                        )
                    )
                }
            }
        }
        return files
    }

    private fun queryDocumentFiles(): List<TransferFile> {
        val files = mutableListOf<TransferFile>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATA
        )

        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} IN (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        val selectionArgs = arrayOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "application/zip"
        )

        val cursor = context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val mimeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dataCol = it.getColumnIndex(MediaStore.Files.FileColumns.DATA)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val name = it.getString(nameCol) ?: "Document"
                val size = it.getLong(sizeCol)
                val mime = it.getString(mimeCol) ?: "application/octet-stream"
                val path = if (dataCol != -1) it.getString(dataCol) else null

                if (size > 0) {
                    files.add(
                        TransferFile(
                            id = "doc_$id",
                            name = name,
                            size = size,
                            mimeType = mime,
                            category = MediaCategory.DOCS,
                            localFilePath = path
                        )
                    )
                }
            }
        }
        return files
    }

    suspend fun getCloneCategories(): List<CloneCategory> = withContext(Dispatchers.IO) {
        val photos = getPhotos()
        val videos = getVideos()
        val music = getMusic()
        val apps = getInstalledApps()
        val docs = getDocuments()

        listOf(
            CloneCategory(CloneCategoryType.PHOTOS, "Photos", photos.size, photos.sumOf { it.size }),
            CloneCategory(CloneCategoryType.VIDEOS, "Videos", videos.size, videos.sumOf { it.size }),
            CloneCategory(CloneCategoryType.APPS, "Installed Apps", apps.size, apps.sumOf { it.size }),
            CloneCategory(CloneCategoryType.MUSIC, "Music & Audio", music.size, music.sumOf { it.size }),
            CloneCategory(CloneCategoryType.DOCUMENTS, "Documents & Files", docs.size, docs.sumOf { it.size })
        )
    }
}
