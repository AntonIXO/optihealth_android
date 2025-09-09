package org.devpins.pihs.ui.activity

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.devpins.pihs.ui.viewmodel.CsvImportViewModel
import org.devpins.pihs.ui.viewmodel.ImportState

@AndroidEntryPoint
class CsvImportActivity : AppCompatActivity() {

    private val viewModel: CsvImportViewModel by viewModels()
    // We'll hold a direct reference to the TextView to update its text.
    private lateinit var statusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Create the UI programmatically instead of inflating from XML
        createProgrammaticLayout()

        // 2. Observe the state from the ViewModel
        observeImportState()

        // 3. Handle the incoming share intent (same as before)
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                viewModel.processCsvUri(uri)
            } ?: run {
                Toast.makeText(this, "No file provided", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            // This activity shouldn't be launched directly
            finish()
        }
    }

    private fun createProgrammaticLayout() {
        // Create a root container
        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Set a semi-transparent background color
            setBackgroundColor(Color.parseColor("#80000000"))
        }

        // Create the ProgressBar
        val progressBar = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        // Create the status TextView and assign it to our class property
        statusTextView = TextView(this).apply {
            text = "Importing data..."
            setTextColor(Color.WHITE)
            textSize = 16f
            // Set layout params to position it below the progress bar
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            params.topMargin = 120 // Adjust this value to control spacing
            layoutParams = params
        }

        // Add views to the root layout
        rootLayout.addView(progressBar)
        rootLayout.addView(statusTextView)

        // Set the programmatically created layout as the content view
        setContentView(rootLayout)
    }

    private fun observeImportState() {
        lifecycleScope.launch {
            viewModel.importState.collectLatest { state ->
                when (state) {
                    is ImportState.Loading -> statusTextView.text = "Importing data..."
                    is ImportState.Success -> {
                        Toast.makeText(
                            this@CsvImportActivity,
                            "Successfully imported ${state.insertedCount} data points.",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                    is ImportState.Error -> {
                        Toast.makeText(
                            this@CsvImportActivity,
                            "Import Failed: ${state.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                    is ImportState.Idle -> { /* Initial state, do nothing */ }
                }
            }
        }
    }
}