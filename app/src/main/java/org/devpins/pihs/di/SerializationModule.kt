package org.devpins.pihs.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SerializationModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true // Good practice for robustness against API changes
        prettyPrint = false // Not needed for network transmission, saves bandwidth
        encodeDefaults = true // Ensure all fields are present in JSON, even if default
        isLenient = true // Be lenient with JSON format if necessary
    }
}
