package org.devpins.pihs.settings

object SettingsKeys {
    const val SETTINGS_PREFS = "AppSettings"
    const val KEY_ENABLE_USAGE = "enable_usage_tracking"
    const val KEY_ENABLE_LOCATION = "enable_location_tracking"
    const val KEY_ENABLE_BACKGROUND_SYNC = "enable_background_sync"
    const val KEY_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"

    const val KEY_LAST_HEALTH_SYNC_AT = "last_health_sync_at" // epoch millis
    const val KEY_LAST_USAGE_SYNC_AT = "last_usage_sync_at"   // epoch millis

    // Wear OS Companion sync keys
    const val KEY_WEAR_LAST_TEMP_VALUE = "wear_last_temp_value_c"
    const val KEY_WEAR_LAST_TEMP_RECEIVED_AT = "wear_last_temp_received_at"
    const val KEY_WEAR_LAST_UPLOAD_AT = "wear_last_upload_at"
    const val KEY_WEAR_LAST_UPLOAD_ERROR = "wear_last_upload_error"

    // Neiry Headband BCI
    const val KEY_ENABLE_NEIRY = "enable_neiry"
}
