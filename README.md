# OptiHealth Android Application

## Overview

**OptiHealth** is a comprehensive personal health data platform that empowers individuals to collect, store, visualize, and analyze a wide array of health and wellness data. This Android application is a key component of the OptiHealth ecosystem, serving as an automated data synchronization hub that transforms siloed data from wearables, sensors, and usage patterns into unified, actionable insights.

**Core Mission**: Transform raw data from wearables, apps, and manual logs into a unified, secure database, then leverage modern data analysis and AI to uncover personalized, actionable insights.

**Key Differentiator**: OptiHealth uniquely combines mental health analysis using LLMs and NLP, raw biometric data from wearables, supplements tracking for global research, and environmental context tracking.

## System Architecture

The OptiHealth ecosystem consists of three main components:

1. **Web Application (Next.js)** - Primary user interface for data visualization, manual logging, and AI-powered insights assistant
2. **Android Application (Kotlin)** - This app: automated background sync, health data collection, and supplement tracking
3. **Supabase Backend** - PostgreSQL database with TimescaleDB, pgvector for semantic search, Edge Functions for data ingestion

### Data Flow

```
Android Sensors & APIs → Android App → Compression (zstd) → Supabase Edge Functions → PostgreSQL Database
Health Connect ────────┘               ↓
Usage Stats ────────────────────────────┘
Location (GPS) ─────────────────────────┘
WearOS Companion ────────────────────────┘
```

## Key Features

### ✅ Implemented Features

#### 1. Health Data Synchronization
- **Health Connect Integration**: Reads various health metrics including weight, exercise sessions, sleep stages, heart rate, HRV, steps, and more
- **Automatic Background Sync**: Uses WorkManager to schedule periodic syncs (configurable: 15 min to 24 hours)
- **Compression Pipeline**: Data is compressed using Zstandard (zstd) before upload to reduce bandwidth and improve efficiency
- **Duplicate Prevention**: Database-level UNIQUE constraints with `ON CONFLICT DO NOTHING` prevent duplicate records
- **Last Sync Tracking**: Tracks the last successful sync timestamp to avoid re-fetching old data

#### 2. Supplement Tracking System
A complete supplement management system implementing a "Chapter 15 ontology" architecture:

- **4-Tier Data Model**:
  - **Substances**: Abstract parent concepts (e.g., "Magnesium")
  - **Compounds**: Specific chemical forms (e.g., "Magnesium L-Threonate")
  - **Vendors**: Manufacturers with trust scores
  - **Products**: The actual "bottle" with dosage information

- **User Features**:
  - **Cabinet View**: Displays products you've previously logged (derived from log history)
  - **Quick-Log Interface**: 3-tap supplement logging from cabinet
  - **Add Product Wizard**: 3-step guided product creation
  - **Auto-Calculated Dosage**: Database trigger automatically converts units to normalized mg

- **Global Product Database**: Products are shared across all users; your "cabinet" is derived from your personal log history

#### 3. Location Tracking (Low-Power)
Battery-efficient background location polling for automatic "Anchor" discovery (Home, Work, etc.):

- **WorkManager-Based**: No persistent foreground service notification required
- **Low-Power Priority**: Uses `PRIORITY_BALANCED_POWER_ACCURACY` (~100m accuracy) via WiFi/cell towers
- **Periodic Polling**: ~30-minute intervals (inexact for battery optimization)
- **Smart Constraints**: Only runs when battery is not low and device has network
- **Persistent Across Reboots**: LocationBootReceiver re-enqueues work after device restart
- **GeoJSON Format**: Uploads location as PostGIS geography(Point) to database

### 🚧 Hidden in Settings

The following features are implemented but not fully functional and are hidden behind settings toggles:

#### 1. Neiry Headband Integration (Beta)
- **Status**: Early integration with Neiry BCI headband
- **Functionality**: Reads heart rate and other biometrics from the headband
- **Access**: Enable via "Enable Neiry Headband (beta)" in Settings
- **Current State**: Basic connection and data reading implemented; full pipeline in development

#### 2. Test Data Upload
- **Status**: Development/debugging tool
- **Functionality**: Upload sample health data or empty payloads to test compression and Edge Function ingestion
- **Access**: Enable via "Show Test Upload Card" in Settings
- **Purpose**: Validates zstd compression and data ingestion pipeline

#### 3. App Usage Tracking
- **UsageStatsManager Integration**: Tracks daily usage duration for all apps
- **Background Sync**: Automatic periodic upload to Supabase
- **Privacy-First**: Data is user-owned and stored in their secure database

#### 4. WearOS Companion App
A companion app for Wear OS devices to enable on-demand biometric capture:

- **Architecture**: Satellite to main Android app (no direct backend communication)
- **Data Transfer**: Uses Wearable Data Layer API to send data to phone
- **Sensors Supported**: Heart rate, body temperature, and other available WearOS sensors
- **Integration**: Data flows through the main app's existing compression and ingestion pipeline

## Advanced Storage Architecture

OptiHealth uses a sophisticated hybrid PostgreSQL architecture designed for high-performance health data analytics.

### Core Principles

1. **Normalized & Tidy**: "Hub-and-spoke" model with canonical `metric_definitions` table ensures data consistency
2. **Time-Series Optimized**: TimescaleDB hypertables for efficient querying of millions of data points
3. **Pre-Aggregated for Speed**: Multi-level summary tables (`daily_summaries`, `time_aggregated_summaries`) power responsive dashboards
4. **AI-Ready with Vector Search**: pgvector extension stores text embeddings for semantic search and correlation analysis

### Key Technical Enhancements

#### pgvector for Semantic Analysis
- **Use Case**: Journal entries converted to vector embeddings enable semantic queries like "Show me sleep quality on days I felt anxious"
- **Implementation**: `events` table has a `vector` column; pgvector extension enables similarity searches
- **Benefit**: Correlate subjective experiences with objective biometric data

#### GIN Indexing for JSONB
- **Use Case**: Flexible storage of complex data structures (e.g., sleep stages, supplement properties)
- **Implementation**: `value_json` and `tags` columns use GIN indexes for fast key/value searches
- **Benefit**: Query nested JSON without performance penalties

#### Storing Time-Series Sequences
Example: Sleep stages stored as JSON array in a single `data_points` row:

```json
[
  {
    "stage": "awake",
    "startTimestamp": "2024-10-23T22:05:00Z",
    "endTimestamp": "2024-10-23T22:15:00Z",
    "durationSeconds": 600
  },
  {
    "stage": "light",
    "startTimestamp": "2024-10-23T22:15:00Z",
    "endTimestamp": "2024-10-23T23:05:00Z",
    "durationSeconds": 3000
  }
]
```

**Benefits**:
- Atomicity: Single database row per sleep session
- Performance: Leverage PostgreSQL JSONB functions with GIN indexes
- Simplicity: Avoids complex junction tables

## Tech Stack & Dependencies

### Core Technologies
- **Kotlin**: Primary programming language
- **Android SDK**: Core Android development framework (API 34+)
- **Jetpack Compose**: Modern declarative UI framework
- **Hilt**: Dependency injection framework

### Data Collection
- **Health Connect**: Access to health and fitness data from wearables
- **UsageStatsManager**: App usage statistics
- **Fused Location Provider**: Low-power location tracking
- **Wearable Data Layer API**: Communication with WearOS companion app

### Backend Integration
- **Supabase**: Backend-as-a-Service
  - `supabase-kt` client library
  - Edge Functions for serverless data ingestion
  - Row-Level Security (RLS) for user data isolation
- **Zstandard (zstd)**: High-performance compression for data uploads

### Background Processing
- **WorkManager**: Reliable background task scheduling
- **Coroutines**: Asynchronous programming and concurrency
- **Flow**: Reactive data streams

## Code Structure

### Main Source Code Directories (`app/src/main/java/org/devpins/pihs/`)

#### Core Application
- **`MainActivity.kt`**: Single-Activity architecture entry point with Jetpack Compose
- **`PIHSApplication.kt`**: Application class for Hilt setup and initialization

#### Data Layer (`data/`)
- **`model/`**: Data classes for domain models (Supplement, Product, HealthDataPoint, etc.)
- **`repository/`**: Repository pattern for data access (SupplementRepository, HealthRepository)
- **`local/`**: Local data storage and preferences
- **`remote/`**: Remote API clients and data sources

#### Feature Modules

##### Health (`health/`)
- **`HealthConnectManager.kt`**: Health Connect API integration, permission management
- **`HealthRepository.kt`**: Data layer for health metrics, fetching and transformation
- **`HealthDataTransformer.kt`**: Transforms Health Connect records into app data models

##### Supplements (`data/model/supplement/`)
- **Models**: Substance, Compound, Vendor, Product, SupplementLog
- **Repository**: Global product database with user-specific cabinet (derived from logs)
- **ViewModels**: SupplementViewModel, CabinetViewModel, AddProductViewModel
- **UI Screens**: SupplementDashboardScreen, CabinetScreen, AddProductWizardScreen

##### Location (`location/`)
- **`LocationManager.kt`**: Lifecycle management for location polling
- **`LocationPollingWorker.kt`**: WorkManager worker for periodic location capture
- **`LocationBootReceiver.kt`**: Re-enables polling after device reboot
- **`LocationTrackingCard.kt`**: UI component for location tracking controls

##### App Usage (`stats/`)
- **`UsageStatsManager.kt`**: Interfaces with Android's UsageStatsManager API
- **Integration**: Works with `UsageDataSyncWorker` for periodic sync

##### WearOS Integration (`wear/`)
- **`WearDataLayerListenerService.kt`**: Receives sensor data from WearOS companion app

##### Neiry Integration (`neiry/`)
- **`NeiryManager.kt`**: (Beta) Integration with Neiry BCI headband

#### Background Tasks (`background/`)
- **`HealthDataSyncWorker.kt`**: Periodic health data sync from Health Connect
- **`UsageDataSyncWorker.kt`**: Periodic app usage data sync
- **`BackgroundSyncController.kt`**: Centralized control for enabling/disabling background sync

#### Dependency Injection (`di/`)
- **`SupabaseModule.kt`**: Provides SupabaseClient singleton
- **`HealthConnectModule.kt`**: Provides HealthConnectClient
- **Other modules**: Context, DataStore, repositories

#### UI (`ui/`)
- **`screens/`**: Composable screens (HomeScreen, SettingsScreen, ManualDataEntryScreen, etc.)
- **`components/`**: Reusable UI components (HealthConnectCard, SupplementComponents, etc.)
- **`viewmodel/`**: ViewModels for each screen
- **`navigation/`**: Navigation graph and route definitions
- **`theme/`**: Material Design 3 theme configuration

### Navigation

Single-Activity architecture with Jetpack Compose Navigation:
- **Bottom Navigation**: Home, Add Data, Supplements, Settings
- **Deep Links**: Support for navigation to specific screens (e.g., Cabinet, Add Product)

## Data Ingestion Pipeline

OptiHealth uses a high-performance data ingestion pipeline optimized for mobile networks:

1. **Chunking**: Health data collected in batches (100-500 records)
2. **Compression**: JSON serialized and compressed with Zstandard (zstd), reducing payload size by ~70-90%
3. **Edge Function Upload**: Compressed binary POSTed to Supabase Edge Function
4. **Server-Side Decompression**: Edge Function decompresses zstd payload
5. **Bulk Insert**: Edge Function calls `bulk_insert_data_points` PostgreSQL RPC function
6. **Duplicate Handling**: UNIQUE constraints + `ON CONFLICT DO NOTHING` prevent duplicates at database level

**Benefits**:
- Reduced data transfer costs
- Faster uploads on slow connections
- Transactional integrity
- Database-level deduplication

## Metric Definitions

OptiHealth tracks a comprehensive set of health metrics, organized by category:

### Trainings 🏃‍♂️
- `activity_steps`, `workout_duration`, `workout_distance`, `workout_calories_burned`, `workout_type`

### Vitals & Heart ❤️
- `hr_resting`, `hr`, `hrv_rmssd`, `body_temperature`, `blood_oxygen_spo2`, `respiratory_rate`

### Sleep 😴
- `sleep_duration_total`, `sleep_duration_deep`, `sleep_duration_light`, `sleep_duration_rem`, `sleep_duration_awake`
- `sleep_score`, `sleep_stages` (JSON array with detailed stage sequences)

### Mental Health & Mood 😊
- `mental_health_log` (free-text journaling with vector embeddings for semantic search)

### Environment 🌳
- `environment_uv_index`, `environment_noise_level`, `environment_air_quality`, `environment_pressure`, `environment_location` (PostGIS geography)

### Nutrition 🥗
- `nutrition_calories`, `nutrition_sugar`, `nutrition_glucose_blood`

### Supplements 💊
- Tracked via dedicated supplement system (see Supplement Tracking System section)

## Settings & Configuration

All features can be toggled in the Settings screen:

### User-Accessible Settings
- **Enable Background Sync**: Master toggle for all background workers
- **Sync Interval**: Choose from 15 min, 30 min, 1 hour, 6 hours, 12 hours, or 24 hours
- **Enable App Usage Tracking**: Toggle app usage statistics collection
- **Enable Location Tracking**: Toggle background location polling

### Developer/Beta Settings
- **Enable Neiry Headband (beta)**: Show Neiry integration card on Home screen
- **Show Test Upload Card**: Show zstd compression test card for debugging

### Sync Status Display
- Shows last sync timestamps for health and usage data
- Displays current sync configuration
- Refresh button to update status

### WearOS Companion Status
- Connection status with paired Wear OS device
- Last received sensor data and timestamps
- Upload status and error logs

## Setup & Installation

### Prerequisites
1. Android device with Android 8.0 (API 26) or higher
2. Health Connect app installed (for health data sync)
3. Supabase project with OptiHealth schema deployed

### Configuration
1. Update `local.properties` with your Supabase credentials:
   ```
   supabase.url=YOUR_SUPABASE_URL
   supabase.key=YOUR_SUPABASE_ANON_KEY
   ```
2. Build and install the app
3. Log in with Google account (linked to your Supabase project)
4. Grant Health Connect permissions when prompted
5. Enable background sync and configure sync interval in Settings

### WearOS Companion Setup
1. Install companion app on Wear OS device
2. Ensure phone and watch are paired
3. Companion app will automatically sync with main Android app

---

**Built with ❤️ for personal health empowerment**
