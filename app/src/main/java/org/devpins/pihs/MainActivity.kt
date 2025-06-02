package org.devpins.pihs

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.devpins.pihs.ui.theme.PIHSTheme
import javax.inject.Inject

// Hardcoded Supabase credentials
private const val SUPABASE_URL = "https://gdvghuytkemlslumyeov.supabase.co"
private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdkdmdodXl0a2VtbHNsdW15ZW92Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDg4NzM0NTAsImV4cCI6MjA2NDQ0OTQ1MH0.HcdUWxm2Rbypxk8gIXrLkLvOxQg6BDpMF72eE4DXxQA"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var supabaseClient: SupabaseClient

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Note: Deeplink handling will be implemented according to the Supabase documentation
        // This is a placeholder for now

        enableEdgeToEdge()
        setContent {
            val scope = rememberCoroutineScope()
            PIHSTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SignInScreen(
                        modifier = Modifier.padding(innerPadding),
                        onGoogleSignInClick = { 
                            scope.launch {
                                signInWithGoogle()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Note: Deeplink handling will be implemented according to the Supabase documentation
        // This is a placeholder for now
    }

    private suspend fun signInWithGoogle() {
        try {
            // Launch Google sign-in flow
            supabaseClient.auth.signInWith(Google)
            Log.d("LOGIN", "Google sign-in initiated")
        } catch (e: Exception) {
            Log.e("LOGIN", "Google Sign-In error: ${e.message}")
        }
    }
}

@Composable
fun SignInScreen(modifier: Modifier = Modifier, onGoogleSignInClick: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Welcome to PIHS")

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Supabase URL:")
        Text(text = SUPABASE_URL, modifier = Modifier.padding(horizontal = 16.dp))

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "Supabase Anon Key:")
        Text(text = SUPABASE_ANON_KEY, modifier = Modifier.padding(horizontal = 16.dp))

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onGoogleSignInClick) {
            Text(text = "Sign in with Google")
        }
    }
}
