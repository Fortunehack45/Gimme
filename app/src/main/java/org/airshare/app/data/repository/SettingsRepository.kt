package org.airshare.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import org.airshare.app.ui.theme.ThemePreset
import java.io.File

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("airshare_settings_prefs", Context.MODE_PRIVATE)

    var deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, null) ?: generateDefaultDeviceName()
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value.trim()).apply()

    var themePresetId: String
        get() = prefs.getString(KEY_THEME_PRESET, ThemePreset.RED.id) ?: ThemePreset.RED.id
        set(value) = prefs.edit().putString(KEY_THEME_PRESET, value).apply()

    var isEncryptionEnforced: Boolean
        get() = prefs.getBoolean(KEY_ENCRYPTION_ENFORCED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENCRYPTION_ENFORCED, value).apply()

    var isPinConfirmationRequired: Boolean
        get() = prefs.getBoolean(KEY_PIN_REQUIRED, false)
        set(value) = prefs.edit().putBoolean(KEY_PIN_REQUIRED, value).apply()

    var sessionPinCode: String
        get() = prefs.getString(KEY_PIN_CODE, "1234") ?: "1234"
        set(value) = prefs.edit().putString(KEY_PIN_CODE, value).apply()

    var saveDirectoryPath: String
        get() = prefs.getString(KEY_SAVE_DIR, null) ?: getDefaultDownloadDirectory().absolutePath
        set(value) = prefs.edit().putString(KEY_SAVE_DIR, value).apply()

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    var autoClearHistoryDays: Int
        get() = prefs.getInt(KEY_AUTO_CLEAR_DAYS, 0) // 0 = never
        set(value) = prefs.edit().putInt(KEY_AUTO_CLEAR_DAYS, value).apply()

    private fun generateDefaultDeviceName(): String {
        val model = Build.MODEL ?: "Android"
        val manufacturer = Build.MANUFACTURER ?: ""
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model".trim()
        }
    }

    fun getDefaultDownloadDirectory(): File {
        val airShareDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "AirShare"
        )
        if (!airShareDir.exists()) {
            airShareDir.mkdirs()
        }
        return airShareDir
    }

    companion object {
        private const val KEY_DEVICE_NAME = "pref_device_name"
        private const val KEY_THEME_PRESET = "pref_theme_preset"
        private const val KEY_ENCRYPTION_ENFORCED = "pref_encryption_enforced"
        private const val KEY_PIN_REQUIRED = "pref_pin_required"
        private const val KEY_PIN_CODE = "pref_pin_code"
        private const val KEY_SAVE_DIR = "pref_save_directory"
        private const val KEY_ONBOARDING_DONE = "pref_onboarding_done"
        private const val KEY_AUTO_CLEAR_DAYS = "pref_auto_clear_days"
    }
}
