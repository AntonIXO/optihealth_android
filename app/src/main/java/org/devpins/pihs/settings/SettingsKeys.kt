package org.devpins.pihs.settings

object SettingsKeys {
    const val SETTINGS_PREFS = "AppSettings"
    const val KEY_ENABLE_USAGE = "enable_usage_tracking"
    const val KEY_ENABLE_LOCATION = "enable_location_tracking"
    const val KEY_ENABLE_BACKGROUND_SYNC = "enable_background_sync"
    const val KEY_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"

    const val KEY_LAST_HEALTH_SYNC_AT = "last_health_sync_at" // epoch millis
    const val KEY_LAST_USAGE_SYNC_AT = "last_usage_sync_at"   // epoch millis

    // Neiry Headband BCI
    const val KEY_ENABLE_NEIRY = "enable_neiry"
}
