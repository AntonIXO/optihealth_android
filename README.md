# Personal Informatics Health Sync (PIHS) Android App

## Overview

PIHS is an Android application designed to help users track and consolidate their personal health and app usage data. It interfaces with Android's Health Connect to gather various health metrics and uses Android's UsageStatsManager to track application usage. This data is then periodically synced to a Supabase backend for storage and potential further analysis.

## Key Features

*   **Health Data Synchronization:**
    *   Reads various health metrics from Health Connect (e.g., weight, exercise, sleep, heart rate, steps).
    *   Uploads this data to a Supabase backend.
    *   Syncs occur periodically (daily).
    *   Handles data push failures gracefully, continuing to sync other available data.
*   **App Usage Tracking:**
    *   Tracks daily usage duration for configured applications.
    *   Uploads this data to Supabase.
    *   Syncs occur periodically (daily).
*   **Background Sync:**
    *   Utilizes Android WorkManager to schedule and run data synchronization tasks efficiently in the background.

## Tech Stack & Dependencies

*   **Kotlin:** Primary programming language.
*   **Android SDK:** Core Android development framework.
*   **Health Connect:** For accessing health and wellness data.
*   **UsageStatsManager:** For accessing app usage statistics.
*   **Supabase:** Backend-as-a-Service for data storage (PostgreSQL, Auth).
    *   `supabase-kt` client library for interactions.
*   **Hilt:** For dependency injection.
*   **WorkManager:** For managing background tasks.
*   **Coroutines:** For asynchronous programming.

## Development Setup

### Prerequisites

*   Android Studio (latest stable version recommended).
*   An Android device or emulator with API level 28+ (for Health Connect, target device needs appropriate support).
*   A Supabase project.

### Configuration

1.  **Supabase Credentials:**
    *   You need to configure your Supabase URL and public anonymous key. Typically, these are placed in the `local.properties` file in the root of your Android project. Add the following lines to your `local.properties` (create the file if it doesn't exist):
        ```properties
        SUPABASE_URL=YOUR_SUPABASE_URL
        SUPABASE_ANON_KEY=YOUR_SUPABASE_ANON_KEY
        ```
    *   These properties are then usually accessed in your `build.gradle.kts` file and passed as build configuration fields or resource values. The current project injects `SupabaseClient` via Hilt (see `SupabaseModule.kt`), which would need to be configured to use these values.
    *   **Note:** `local.properties` is usually included in `.gitignore` to prevent committing sensitive keys.

2.  **Health Connect Setup:**
    *   On your test device/emulator:
        *   Install the Health Connect APK. (Can be downloaded from sources like APKMirror or the Play Store if available on the emulator image).
        *   Open Health Connect and grant permissions to this app (PIHS) once it's installed and requests them.

3.  **App Usage Stats Permission:**
    *   After installing PIHS, you'll need to manually grant the "Usage access" permission. The app should guide you to the relevant settings page if the permission is missing. This can typically be found under `Settings -> Security -> Usage access` or similar, depending on the Android version and OEM.

### Building and Running

1.  **Clone the repository:**
    ```bash
    git clone <repository-url>
    cd <project-directory>
    ```
2.  **Open in Android Studio:**
    *   Open Android Studio and select "Open an Existing Project."
    *   Navigate to the cloned project directory and open it.
3.  **Sync Gradle:**
    *   Let Android Studio sync the Gradle files. This will download all necessary dependencies.
4.  **Set up `local.properties`:**
    *   Create/update `local.properties` with your Supabase credentials as described above.
5.  **Run the app:**
    *   Select a target device/emulator.
    *   Click the "Run" button (green play icon) in Android Studio.

## Backend Schema (Brief)

The Supabase backend is expected to have at least the following tables:

*   `metric_sources`: Stores information about data sources (e.g., Health Connect, App Usage).
    *   `id`, `user_id`, `source_identifier`, `source_name`, `last_synced_at`, etc.
*   `data_points`: Stores time-series metric data.
    *   `user_id`, `metric_source_id`, `timestamp`, `metric_name`, `value_numeric`, `unit`, `value_json`, etc.
*   `events`: Stores event-based data like workouts or meals.
    *   `user_id`, `event_name`, `start_timestamp`, `end_timestamp`, `duration_minutes`, `properties`, etc.

Refer to the data class definitions in the `.kt` files (e.g., `PihsDataPoint.kt`, `HealthRepository.kt` for `MetricSource`) for more details on expected fields.
