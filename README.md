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

## Code Structure

This project follows a modern Android architecture, leveraging MVVM-like patterns with Jetpack Compose for the UI and Hilt for dependency injection.

### Main Source Code Directories (`app/src/main/java/org/devpins/pihs/`)

*   **Root Package (`org.devpins.pihs`)**:
    *   `MainActivity.kt`: The single entry point for the application's UI. It hosts Jetpack Compose UI content.
    *   `PIHSApplication.kt`: The application class, used for Hilt setup and other application-level initializations.

*   **`ui/`**: Contains all Jetpack Compose UI elements.
    *   `ui/theme/`: Defines the application's theme, including colors, typography, and shapes.
    *   Composable functions for different screens and UI components are typically organized in sub-packages within `ui/`.

*   **`health/`**: Manages interactions with Android's Health Connect API.
    *   `HealthConnectManager.kt`: Responsible for checking Health Connect availability, managing permissions, and providing an interface to read health data.
    *   `HealthRepository.kt`: Acts as a data layer for health metrics, fetching data via `HealthConnectManager` and preparing it for use by ViewModels or services.
    *   `HealthDataTransformer.kt`: (If it exists or is implied by functionality) Transforms raw data from Health Connect into the application's data models.

*   **`stats/`**: Handles the collection of application usage statistics.
    *   `UsageStatsManager.kt`: Interacts with Android's `UsageStatsManager` to query app usage data.
    *   It works in conjunction with `UsageDataSyncWorker` (in `background/`) to periodically fetch and sync this data.

*   **`location/`**: Manages location tracking functionalities.
    *   `LocationManager.kt`: Provides an interface to access device location, handling permissions and location updates.
    *   `LocationTrackingService.kt`: A foreground service that continuously tracks location when active.
    *   `LocationTrackingCard.kt`: (Likely a UI component in `ui/` related to displaying location tracking status or data, but mentioned here for functional grouping).

*   **`background/`**: Contains `WorkManager` workers for performing background tasks.
    *   `HealthDataSyncWorker.kt`: Periodically fetches health data using `HealthRepository` and syncs it to the backend.
    *   `UsageDataSyncWorker.kt`: Periodically fetches app usage data using `UsageStatsManager` (from the `stats/` package) and syncs it to the backend.

*   **`di/`**: Holds Hilt dependency injection modules.
    *   `SupabaseModule.kt`: Provides the `SupabaseClient` instance.
    *   `HealthConnectModule.kt`: Provides dependencies related to Health Connect, such as `HealthConnectClient`.
    *   Other modules provide other dependencies like `Context`, `DataStore`, etc.

### Navigation

The application uses a single-Activity architecture. Navigation between different screens or states is managed within Jetpack Compose using Composable functions and state management (e.g., `NavController` with Compose Navigation, or custom state hoisting).

### Data Flow

1.  **Data Fetching**:
    *   Managers (e.g., `HealthConnectManager`, `UsageStatsManager`, `LocationManager`) are responsible for interacting with Android APIs to retrieve raw data.
    *   Repositories (e.g., `HealthRepository`) abstract the data sources, providing a clean API for ViewModels or background workers to access data. They might fetch data from managers or local storage.

2.  **Data Storage (Local)**:
    *   While not explicitly detailed for all data types, preferences or temporary data might be stored using Jetpack DataStore or SharedPreferences.
    *   Health and usage data is primarily fetched on demand or for syncing, rather than being extensively cached locally long-term, though Repositories might implement caching strategies.

3.  **Data Syncing**:
    *   `WorkManager` is used to schedule periodic background tasks (Workers in `background/`).
    *   These workers use Repositories/Managers to fetch the latest data and then use the `SupabaseClient` (provided by Hilt via `di/SupabaseModule.kt`) to upload the data to the Supabase backend.
    *   Data transformation into the backend schema often occurs within the Repositories or the Workers before syncing.

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
