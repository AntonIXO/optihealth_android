package org.devpins.companion.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import org.devpins.companion.presentation.theme.PIHSTheme
import java.text.DecimalFormat

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var tempSensor: Sensor? = null
    private var ppgSensor: Sensor? = null // Using Heart Rate as a proxy for PPG

    // State variables to hold sensor data for the UI
    private val _temperature = mutableStateOf("-.--°C")
    private val _ppg = mutableStateOf("PPG: --")
    private val _onBody = mutableStateOf(true) // Assume on-body by default

    // Sensor names based on your logs and smali analysis
    private val MOBVOI_TEMP_SENSOR_NAME = "mobvoi_temperature"
    // Standard heart rate sensor, often the source of PPG-derived data
    private val HEART_RATE_SENSOR_NAME = "psp_hr"
    // On-body sensor from the smali analysis to check if the watch is worn
    private val ON_BODY_SENSOR_NAME = "on_body_detect"

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Find sensors by name, similar to the smali code logic
        findSensors()

        setContent {
            WearApp(
                temperature = _temperature,
                ppg = _ppg,
                isOnBody = _onBody
            )
        }
    }

    private fun findSensors() {
        val deviceSensors: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)
        Log.d("SensorList", "--- Available Sensors ---")
        deviceSensors.forEach {
            Log.d("SensorList", "Found sensor: ${it.name} | Type: ${it.stringType}")
            // Check for the custom Mobvoi temperature sensor by its unique name
            if (it.name.contains(MOBVOI_TEMP_SENSOR_NAME, ignoreCase = true)) {
                tempSensor = it
                Log.i("SensorInit", "Mobvoi Temperature Sensor found!")
            }
            // Find a heart rate sensor to get PPG-related data
            if (it.type == Sensor.TYPE_HEART_RATE) {
                ppgSensor = it
                Log.i("SensorInit", "Heart Rate (PPG) Sensor found!")
            }
        }
        if (tempSensor == null) Log.e("SensorInit", "Custom Temperature Sensor NOT FOUND.")
        if (ppgSensor == null) Log.e("SensorInit", "Heart Rate (PPG) Sensor NOT FOUND.")
    }

    override fun onResume() {
        super.onResume()
        // Register listeners for the sensors when the app is resumed
        tempSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        ppgSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister listener to save power when the app is paused
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Can be ignored for this use case
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        // Check which sensor triggered the event
        when (event.sensor.type) {
            // Case for the Heart Rate sensor (PPG)
            Sensor.TYPE_HEART_RATE -> {
                val ppgValue = event.values[0]
                if(ppgValue > 0) {
                    _ppg.value = "HR: ${ppgValue.toInt()} bpm"
                }
            }
        }

        // Handle custom sensor by name since it has no standard type
        if (event.sensor.name.contains(MOBVOI_TEMP_SENSOR_NAME, ignoreCase = true)) {
            // As per the smali analysis, the temperature value is at index 3
            if (event.values.size > 3) {
                val tempValue = event.values[3]
                val decimalFormat = DecimalFormat("0.00")
                _temperature.value = "${decimalFormat.format(tempValue)}°C"
            }
        }
    }
}

@Composable
fun WearApp(temperature: State<String>, ppg: State<String>, isOnBody: State<Boolean>) {
    PIHSTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            SensorReadings(
                temperature = temperature.value,
                ppg = ppg.value,
                isOnBody = isOnBody.value
            )
        }
    }
}

@Composable
fun SensorReadings(temperature: String, ppg: String, isOnBody: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isOnBody) {
            Text(
                text = temperature,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = Color(0xFFFFA500), // Orange color for temperature
                style = MaterialTheme.typography.title1
            )
            Text(
                text = ppg,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = Color(0xFFC71585), // Pinkish color for PPG/HR
                style = MaterialTheme.typography.title2
            )
        } else {
            Text(
                text = "Place watch on wrist",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.primary,
                style = MaterialTheme.typography.body1
            )
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    val temp = mutableStateOf("36.67°C")
    val ppg = mutableStateOf("HR: 75 bpm")
    val onBody = mutableStateOf(true)
    WearApp(temperature = temp, ppg = ppg, isOnBody = onBody)
}
