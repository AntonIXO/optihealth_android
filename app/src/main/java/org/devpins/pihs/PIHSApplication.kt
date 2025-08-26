package org.devpins.pihs

import android.app.Application
import android.util.Log
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import org.devpins.pihs.background.BackgroundSyncController
import org.devpins.pihs.settings.SettingsKeys
import javax.inject.Inject

/**
 * Custom [Application] class for PIHS.
 * It implements [Configuration.Provider] to provide a custom [WorkManager] configuration,
 * enabling Hilt integration for workers.
 * It also schedules daily background synchronization tasks for health and usage data.
 */
@HiltAndroidApp
class PIHSApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val TAG = "PIHSApplication"

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.DEBUG) // Increase WorkManager logging for diagnostics
            .build()

    override fun onCreate() {
        super.onCreate()
        // Since WorkManager's default initializer is disabled in the Manifest, initialize manually
        try {
            WorkManager.initialize(this, workManagerConfiguration)
            Log.i(TAG, "WorkManager initialized manually with HiltWorkerFactory")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "WorkManager already initialized: ${e.message}")
        }

        Log.d(TAG, "Application onCreate - Applying background sync settings")
        val prefs = getSharedPreferences(SettingsKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(SettingsKeys.KEY_ENABLE_BACKGROUND_SYNC, true)
        Log.i(TAG, "Background sync enabled setting: $enabled")
        if (enabled) {
            BackgroundSyncController.schedulePeriodicSyncWorkers(this)
        } else {
            BackgroundSyncController.disable(this)
        }
    }
}
