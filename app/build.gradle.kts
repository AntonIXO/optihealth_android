plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "org.devpins.pihs"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.devpins.pihs"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Check if you have this parameter enabled.
            // Add if missing and rebuild your app to device.
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildToolsVersion = "36.0.0"
    packaging {
        resources {
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.identity.jvm)
    implementation(libs.androidx.appcompat)

    // Supabase - using the correct package names
    val supabaseVersion = "3.2.5" // Consolidate version
    implementation(platform("io.github.jan-tennert.supabase:bom:$supabaseVersion"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")

    // Credential Manager for Google Sign-In
    implementation(libs.androidx.credentials)
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Ktor
    val ktorVersion = "3.3.1" // Updated Ktor version
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion") // Or ktor-client-android
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion") // For content negotiation
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion") // For JSON serialization with Ktor

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0")

    // Hilt for Dependency Injection
    implementation("com.google.dagger:hilt-android:2.56.2") // Check for latest Hilt version
    ksp("com.google.dagger:hilt-android-compiler:2.56.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0") // Check for latest version

    // Hilt AndroidX Integration (for WorkManager, ViewModel, etc.)
    implementation(libs.androidx.hilt.work) // Use the latest version
    ksp(libs.androidx.hilt.compiler) // Use the latest version

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.3") // Use the latest version
    
    // Guava for WorkManager ListenableFuture
    implementation("com.google.guava:guava:33.5.0-android")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")

    // Google Play Services Location for Fused Location Provider
    implementation("com.google.android.gms:play-services-location:21.3.0")
    
    // Coroutines for Google Play Services (for .await() extension)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    implementation("com.github.luben:zstd-jni:1.5.7-6@aar")
    testImplementation("com.github.luben:zstd-jni:1.5.7-6")

    // Neiry
    implementation(files("libs/capsuleService-embedded-release.aar"))
    implementation(files("libs/devicedriver-aar-release.aar"))
//    implementation("com.github.BrainbitLLC:neurosdk2:1.0.6.34")
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.10.0")

    // WearOS
    implementation("com.google.android.gms:play-services-wearable:19.0.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
