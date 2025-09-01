package org.devpins.pihs.neiry

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.gelo.capsule.CapsuleNative
import com.neurosdk2.neuro.Headband
import com.neurosdk2.neuro.Scanner
import com.neurosdk2.neuro.Sensor
import com.neurosdk2.neuro.interfaces.FPGDataReceived
import com.neurosdk2.neuro.types.FPGData
import com.neurosdk2.neuro.types.HeadphonesResistData
import com.neurosdk2.neuro.interfaces.HeadbandResistDataReceived
import com.neurosdk2.neuro.types.HeadbandResistData
import com.neurosdk2.neuro.types.SensorFamily

object NeiryManager {

    private var scanner: Scanner? = null
    private var sensor: Sensor? = null

    private var prevSample: Double = 0.0
    private var lastSample: Double = 0.0
    private var lastPeakAt: Long = 0L

    fun initAndConnect(activity: Activity) {
        try {
            CapsuleNative.initCapsule()
        } catch (t: Throwable) {
            Log.e("Neiry", "Failed to init CapsuleNative", t)
        }

        try {
            if (!CapsuleNative.hasPermissions(activity)) {
                val requested = CapsuleNative.requestPermissions(activity)
                Toast.makeText(activity, if (requested) "Requesting Bluetooth permissions" else "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (t: Throwable) {
            Log.w("Neiry", "Capsule permissions check failed: ${t.message}")
        }

        if (scanner == null) {
            scanner = Scanner(SensorFamily.SensorLEHeadband)
        }

        val sc = scanner ?: return
        try {
            sc.start()
        } catch (t: Throwable) {
            Log.e("Neiry", "Scanner start failed", t)
        }

        // Try to find device after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            val sensors = try { sc.sensors } catch (t: Throwable) { emptyList() }
            if (sensors.isNullOrEmpty()) {
                Log.w("Neiry", "No Neiry headband sensors found")
                Toast.makeText(activity, "No Neiry headband found", Toast.LENGTH_SHORT).show()
                return@postDelayed
            }
            val info = sensors.first()
            try {
                val created = sc.createSensor(info)
                sensor = created
                if (created is Headband) {
                    Log.d("Neiry", "Found Neiry headband: ${info.name}")
                    Log.d("Neiry", "Family: ${info.sensFamily}")
                    Log.d("Neiry", "Sampling frequency: ${created.samplingFrequencyFPG}")
                    Log.d("Neiry", "Battery power: ${created.battPower}")
                    Log.d("Neiry", "Firmware version: ${created.gain}")
                    Log.d("Neiry", "Pairing required: ${info.pairingRequired}")
                    Log.d("Neiry", "Battery level: ${created.state}")
                    setupHeadbandCallbacks(created)
                }
                created.connect()
                Toast.makeText(activity, "Connecting to ${info.name}", Toast.LENGTH_SHORT).show()
                Log.d("Neiry", "Connecting to ${info.name}")
            } catch (t: Throwable) {
                Log.e("Neiry", "Failed to create/connect sensor", t)
            }
        }, 1500)
    }

    private fun setupHeadbandCallbacks(headband: Headband) {
        // Log FPG data and naive HR estimate
        headband.fpgDataReceived = FPGDataReceived { arr: Array<FPGData> ->
            arr.forEach { data ->
                val ir = data.irAmplitude
                val now = System.currentTimeMillis()
                if (prevSample < lastSample && lastSample > ir && lastSample > 0.1) {
                    if (lastPeakAt > 0) {
                        val dt = now - lastPeakAt
                        if (dt in 300..2000) {
                            val bpm = (60000.0 / dt.toDouble()).toInt()
                            Log.d("Neiry", "HeartRate ~ ${bpm} bpm (approx) — IR=${"%.3f".format(lastSample)}")
                        }
                    }
                    lastPeakAt = now
                }
                prevSample = lastSample
                lastSample = ir
            }
        }
        // Log electrode/headband resistance when available
        try {
            headband.headbandResistDataReceived = HeadbandResistDataReceived { resist: HeadbandResistData ->
                Log.d(
                    "Neiry",
                    "Resistance O1=${"%.1f".format(resist.o1)}kΩ, O2=${"%.1f".format(resist.o2)}kΩ, T3=${"%.1f".format(resist.t3)}kΩ, T4=${"%.1f".format(resist.t4)}kΩ"
                )
            }
        } catch (t: Throwable) {
            Log.w("Neiry", "Failed to set resistance callback: ${t.message}")
        }
    }

    fun disconnect() {
        try {
            sensor?.disconnect()
            scanner?.stop()
        } catch (_: Throwable) {}
        sensor = null
    }
}
