package org.devpins.pihs.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import javax.inject.Singleton

// Hardcoded Supabase credentials - same as in MainActivity
private const val SUPABASE_URL = "https://gdvghuytkemlslumyeov.supabase.co"
private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdkdmdodXl0a2VtbHNsdW15ZW92Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDg4NzM0NTAsImV4cCI6MjA2NDQ0OTQ1MH0.HcdUWxm2Rbypxk8gIXrLkLvOxQg6BDpMF72eE4DXxQA"

@InstallIn(SingletonComponent::class)
@Module
object SupabaseModule {

    // Supabase URL provider
    @Provides
    @Singleton
    fun provideSupabaseUrl(): String {
        return SUPABASE_URL
    }

    // Supabase Anon Key provider
    @Provides
    @Singleton
    fun provideSupabaseAnonKey(): String {
        return SUPABASE_ANON_KEY
    }

    // Supabase Client provider
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = "pihs"
                host = "gdvghuytkemlslumyeov.supabase.co/auth/v1/callback"
            }
            install(Postgrest)
            install(Realtime)
        }
    }

    // Supabase Auth provider
    @Provides
    @Singleton
    fun provideSupabaseAuth(client: SupabaseClient): Auth {
        return client.auth
    }

    // Supabase Postgrest provider
    @Provides
    @Singleton
    fun provideSupabasePostgrest(client: SupabaseClient): Postgrest {
        return client.postgrest
    }

    // Supabase Realtime provider
    @Provides
    @Singleton
    fun provideSupabaseRealtime(client: SupabaseClient): Realtime {
        return client.realtime
    }
}
