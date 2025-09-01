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
        ignoreUnknownKeys = true // Robust against API changes
        prettyPrint = false // Minimize payload size
        // Important for ingestion: omit nulls so Edge Function/SQL don't see explicit nulls for optional fields
        explicitNulls = false
        // Avoid sending default values that aren't needed by the server schema
        encodeDefaults = false
        isLenient = true
    }
}
