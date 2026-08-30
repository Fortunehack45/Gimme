package org.airshare.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.airshare.app.data.local.AppDatabase
import org.airshare.app.data.repository.SettingsRepository
import org.airshare.app.data.repository.TransferRepository
import org.airshare.app.ui.theme.ThemeManager

class AirShareApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var transferRepository: TransferRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        transferRepository = TransferRepository(database.transferDao())
        settingsRepository = SettingsRepository(this)

        ThemeManager.init(settingsRepository.themePresetId)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val transferChannel = NotificationChannel(
                CHANNEL_TRANSFERS,
                "AirShare Active Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time speed, progress, and actions for file transfers"
                setShowBadge(false)
            }

            val systemChannel = NotificationChannel(
                CHANNEL_SYSTEM,
                "AirShare Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for completed transfers or incoming peer connection requests"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(transferChannel)
            notificationManager?.createNotificationChannel(systemChannel)
        }
    }

    companion object {
        const val CHANNEL_TRANSFERS = "airshare_channel_transfers"
        const val CHANNEL_SYSTEM = "airshare_channel_system"

        lateinit var instance: AirShareApplication
            private set
    }
}
