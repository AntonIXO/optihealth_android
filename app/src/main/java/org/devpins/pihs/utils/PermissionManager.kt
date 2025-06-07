package org.devpins.pihs.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionManager {

    private const val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 101

    fun requestRecordAudioPermission(activity: Activity, callback: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            callback(true)
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_REQUEST_CODE
            )
            // The actual result will be handled in the Activity's onRequestPermissionsResult callback
            // For now, we'll assume the caller will handle this by invoking a method here later
            // or by checking the permission status again after the request.
            // This placeholder will be improved later. For now, returning false.
            // A more robust solution would involve a listener or a way to await the dialog result.
            callback(false) // Placeholder: Ideally, this callback is invoked after user interaction.
        }
    }

    // This function would be called from onRequestPermissionsResult in the Activity
    fun handleRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        callback: (Boolean) -> Unit
    ) {
        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                callback(true)
            } else {
                callback(false)
            }
        }
    }
}
