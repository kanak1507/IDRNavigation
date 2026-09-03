package com.example.idrnavigation

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorCollector: SensorCollector
    private lateinit var speedPredictor: SpeedPredictor
    private lateinit var idrEngine: IdrEngine

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager

    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private val accelValues = FloatArray(3)
    private val gyroValues = FloatArray(3)

    private var latestLocation by mutableStateOf<Location?>(null)

    private var gpsAvailable by mutableStateOf(false)

    private var simulateGnssOutage by mutableStateOf(false)

    private var latestIdrResult by mutableStateOf(
        IdrResult(
            latitude = 0.0,
            longitude = 0.0,
            gpsAvailable = false,
            speed = 0.0f,
            heading = 0.0
        )
    )

    private var lastGpsUpdateTime = 0L

    // ============================================================
    // CSV RECORDING
    // ============================================================

    private var isRecording by mutableStateOf(false)

    private var recordedSampleCount by mutableStateOf(0)

    private val csvRows = mutableListOf<String>()

    private var csvToExport = ""

    private var lastRecordTimeNs = 0L

    private val recordIntervalNs = 100_000_000L // 100 ms = 10 Hz

    private lateinit var exportCsvLauncher: ActivityResultLauncher<String>

    // ============================================================
    // LOCATION CALLBACK
    // ============================================================

    private lateinit var locationCallback: LocationCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --------------------------------------------------------
        // Sensor collector
        // --------------------------------------------------------

        sensorCollector = SensorCollector(this)

        // --------------------------------------------------------
        // ML model
        // --------------------------------------------------------

        speedPredictor = SpeedPredictor(assets)

        idrEngine = IdrEngine(speedPredictor)

        // --------------------------------------------------------
        // Location
        // --------------------------------------------------------

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        // --------------------------------------------------------
        // Sensors
        // --------------------------------------------------------

        sensorManager =
            getSystemService(SENSOR_SERVICE) as SensorManager

        accelerometer =
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        gyroscope =
            sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // --------------------------------------------------------
        // CSV export launcher
        // --------------------------------------------------------

        exportCsvLauncher =
            registerForActivityResult(
                ActivityResultContracts.CreateDocument("text/csv")
            ) { uri ->

                if (uri != null) {

                    try {

                        contentResolver.openOutputStream(uri)?.use { output ->

                            output.write(
                                csvToExport.toByteArray(Charsets.UTF_8)
                            )
                        }

                        Log.d(
                            "IDR_CSV",
                            "CSV exported successfully"
                        )

                    } catch (e: Exception) {

                        Log.e(
                            "IDR_CSV",
                            "CSV export failed",
                            e
                        )
                    }
                }
            }

        // --------------------------------------------------------
        // Location callback
        // --------------------------------------------------------

        locationCallback =
            object : LocationCallback() {

                override fun onLocationResult(
                    result: LocationResult
                ) {

                    val location =
                        result.lastLocation ?: return

                    latestLocation = location

                    gpsAvailable = true

                    lastGpsUpdateTime =
                        System.currentTimeMillis()
                }
            }

        // --------------------------------------------------------
        // Start sensors/location
        // --------------------------------------------------------

        sensorCollector.start()

        registerSensors()

        requestLocationPermission()

        // --------------------------------------------------------
        // UI
        // --------------------------------------------------------

        setContent {

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {

                IdrDashboard()
            }
        }
    }

    // ============================================================
    // SENSOR SETUP
    // ============================================================

    private fun registerSensors() {

        accelerometer?.let {

            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }

        gyroscope?.let {

            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    // ============================================================
    // LOCATION PERMISSION
    // ============================================================

    private fun requestLocationPermission() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                100
            )

        } else {

            startLocationUpdates()
        }
    }

    // ============================================================
    // LOCATION UPDATES
    // ============================================================

    private fun startLocationUpdates() {

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val locationRequest =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000L
            )
                .setMinUpdateIntervalMillis(500L)
                .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    // ============================================================
    // SENSOR CALLBACK
    // ============================================================

    override fun onSensorChanged(event: SensorEvent?) {

        if (event == null) return

        when (event.sensor.type) {

            Sensor.TYPE_ACCELEROMETER -> {

                accelValues[0] = event.values[0]
                accelValues[1] = event.values[1]
                accelValues[2] = event.values[2]

                processIdr()
            }

            Sensor.TYPE_GYROSCOPE -> {

                gyroValues[0] = event.values[0]
                gyroValues[1] = event.values[1]
                gyroValues[2] = event.values[2]

                processIdr()
            }
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
        // Not required for prototype
    }

    // ============================================================
    // IDR PROCESSING
    // ============================================================

    private fun processIdr() {

        // Check REAL GPS status first
        updateGpsAvailability()

        val realGpsAvailable = gpsAvailable

        // Only simulate outage when button is ON
        val effectiveGpsAvailable =
            realGpsAvailable && !simulateGnssOutage

        val location = latestLocation

        val sensorData = SensorData(

            timestamp = System.nanoTime(),

            accelerometerX = accelValues[0],
            accelerometerY = accelValues[1],
            accelerometerZ = accelValues[2],

            gyroscopeX = gyroValues[0],
            gyroscopeY = gyroValues[1],
            gyroscopeZ = gyroValues[2],

            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0,

            gpsSpeed = location?.speed ?: 0f,

            gpsAccuracy = location?.accuracy ?: 0f,

            gpsAvailable = effectiveGpsAvailable
        )

        try {

            val result = idrEngine.process(sensorData)

            latestIdrResult = result

            Log.d(
                "IDR_RESULT",
                "REAL_GPS=$realGpsAvailable | " +
                        "SIMULATED_OUTAGE=$simulateGnssOutage | " +
                        "EFFECTIVE_GPS=$effectiveGpsAvailable | " +
                        "Speed=${result.speed}"
            )

            recordSample(
                sensorData,
                result
            )

        } catch (e: Exception) {

            Log.e(
                "IDR_RESULT",
                "IDR processing failed",
                e
            )
        }
    }

    // ============================================================
    // GPS AVAILABILITY
    // ============================================================

    private fun updateGpsAvailability() {

        if (lastGpsUpdateTime == 0L) {

            gpsAvailable = false

            return
        }

        val elapsed =
            System.currentTimeMillis() -
                    lastGpsUpdateTime

        gpsAvailable =
            elapsed < 6000
    }

    // ============================================================
    // CSV RECORDING
    // ============================================================

    private fun startRecording() {

        csvRows.clear()

        csvRows.add(
            "timestamp," +
                    "gps_available," +
                    "gps_latitude," +
                    "gps_longitude," +
                    "idr_latitude," +
                    "idr_longitude," +
                    "ml_speed_mps," +
                    "heading_deg," +
                    "acc_x," +
                    "acc_y," +
                    "acc_z," +
                    "gyro_x," +
                    "gyro_y," +
                    "gyro_z"
        )

        recordedSampleCount = 0

        lastRecordTimeNs = 0L

        isRecording = true

        Log.d(
            "IDR_CSV",
            "Recording started"
        )
    }

    private fun stopRecording() {

        isRecording = false

        Log.d(
            "IDR_CSV",
            "Recording stopped. Samples=$recordedSampleCount"
        )
    }

    private fun recordSample(
        sensorData: SensorData,
        result: IdrResult
    ) {

        if (!isRecording) return

        val now = System.nanoTime()

        // Record at approximately 10 Hz.
        if (
            lastRecordTimeNs != 0L &&
            now - lastRecordTimeNs < recordIntervalNs
        ) {
            return
        }

        lastRecordTimeNs = now

        val gpsLat =
            if (latestLocation != null)
                latestLocation!!.latitude
            else
                0.0

        val gpsLon =
            if (latestLocation != null)
                latestLocation!!.longitude
            else
                0.0

        val row =
            "${sensorData.timestamp}," +
                    "${result.gpsAvailable}," +
                    "${gpsLat}," +
                    "${gpsLon}," +
                    "${result.latitude}," +
                    "${result.longitude}," +
                    "${result.speed}," +
                    "${result.heading}," +
                    "${sensorData.accelerometerX}," +
                    "${sensorData.accelerometerY}," +
                    "${sensorData.accelerometerZ}," +
                    "${sensorData.gyroscopeX}," +
                    "${sensorData.gyroscopeY}," +
                    "${sensorData.gyroscopeZ}"

        csvRows.add(row)

        recordedSampleCount = csvRows.size - 1
    }

    // ============================================================
    // EXPORT CSV
    // ============================================================

    private fun exportCsv() {

        if (csvRows.size <= 1) {
            Log.d(
                "IDR_CSV",
                "No data available to export"
            )
            return
        }

        csvToExport =
            csvRows.joinToString("\n")

        val timestamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.getDefault()
            ).format(Date())

        val filename =
            "IDR_recording_$timestamp.csv"

        exportCsvLauncher.launch(filename)
    }

    // ============================================================
    // COMPOSE UI
    // ============================================================

    @androidx.compose.runtime.Composable
    private fun IdrDashboard() {

        val gps = latestLocation

        Column(

            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(16.dp),

            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            // ----------------------------------------------------
            // HEADER
            // ----------------------------------------------------

            Text(
                text = "IDR NAVIGATION",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    "AI-ML Intelligent Dead Reckoning System",
                fontSize = 14.sp
            )

            // ----------------------------------------------------
            // STATUS
            // ----------------------------------------------------

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (latestIdrResult.gpsAvailable)
                                Color(0xFFE8F5E9)
                            else
                                Color(0xFFFFEBEE)
                    )
            ) {

                Column(
                    modifier =
                        Modifier.padding(16.dp)
                ) {

                    Text(
                        text =
                            if (latestIdrResult.gpsAvailable)
                                "🟢 GNSS AVAILABLE"
                            else
                                "🔴 GNSS OUTAGE",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    Text(
                        text =
                            if (latestIdrResult.gpsAvailable)
                                "📡 GPS navigation active"
                            else
                                "🤖 DEAD RECKONING ACTIVE",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ----------------------------------------------------
            // SPEED
            // ----------------------------------------------------

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(16.dp)
                ) {

                    Text(
                        text = "ML PREDICTED SPEED",
                        fontSize = 13.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )

                    Text(
                        text =
                            String.format(
                                Locale.US,
                                "%.2f m/s",
                                latestIdrResult.speed
                            ),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            String.format(
                                Locale.US,
                                "%.2f km/h",
                                latestIdrResult.speed * 3.6f
                            ),
                        fontSize = 16.sp
                    )
                }
            }

            // ----------------------------------------------------
            // HEADING
            // ----------------------------------------------------

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),

                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Column {

                        Text(
                            text = "HEADING"
                        )

                        Text(
                            text =
                                String.format(
                                    Locale.US,
                                    "%.1f°",
                                    latestIdrResult.heading
                                ),
                            fontSize = 24.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    Column {

                        Text(
                            text = "DR STATUS"
                        )

                        Text(
                            text =
                                if (
                                    !latestIdrResult.gpsAvailable
                                )
                                    "ACTIVE"
                                else
                                    "STANDBY",

                            fontSize = 20.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }

            // ----------------------------------------------------
            // GPS POSITION
            // ----------------------------------------------------

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(16.dp)
                ) {

                    Text(
                        text = "📡 GPS POSITION",
                        fontSize = 16.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    if (gps != null) {

                        Text(
                            text =
                                String.format(
                                    Locale.US,
                                    "Latitude: %.7f",
                                    gps.latitude
                                )
                        )

                        Text(
                            text =
                                String.format(
                                    Locale.US,
                                    "Longitude: %.7f",
                                    gps.longitude
                                )
                        )

                    } else {

                        Text(
                            text =
                                "Waiting for GPS..."
                        )
                    }
                }
            }

            // ----------------------------------------------------
            // IDR POSITION
            // ----------------------------------------------------

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(16.dp)
                ) {

                    Text(
                        text = "🤖 IDR ESTIMATED POSITION",
                        fontSize = 16.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    Text(
                        text =
                            String.format(
                                Locale.US,
                                "Latitude: %.7f",
                                latestIdrResult.latitude
                            )
                    )

                    Text(
                        text =
                            String.format(
                                Locale.US,
                                "Longitude: %.7f",
                                latestIdrResult.longitude
                            )
                    )
                }
            }

            // ----------------------------------------------------
            // SENSOR VALUES
            // ----------------------------------------------------

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(16.dp)
                ) {

                    Text(
                        text = "📱 IMU SENSOR DATA",
                        fontSize = 16.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            String.format(
                                Locale.US,
                                "Accelerometer: %.2f, %.2f, %.2f m/s²",
                                accelValues[0],
                                accelValues[1],
                                accelValues[2]
                            )
                    )

                    Text(
                        text =
                            String.format(
                                Locale.US,
                                "Gyroscope: %.3f, %.3f, %.3f rad/s",
                                gyroValues[0],
                                gyroValues[1],
                                gyroValues[2]
                            )
                    )
                }
            }

            // ----------------------------------------------------
            // RECORDING STATUS
            // ----------------------------------------------------

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(16.dp)
                ) {

                    Text(
                        text =
                            if (isRecording)
                                "🔴 RECORDING"
                            else
                                "⏹ RECORDING STOPPED",

                        fontSize = 17.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )

                    Text(
                        text =
                            "Samples: $recordedSampleCount"
                    )
                }
            }

            // ----------------------------------------------------
            // GNSS OUTAGE BUTTON
            // ----------------------------------------------------

            Button(

                onClick = {

                    if (!simulateGnssOutage) {

                        if (latestLocation != null) {

                            simulateGnssOutage = true

                            Log.d(
                                "IDR_TEST",
                                "GNSS outage simulation ON"
                            )
                        }

                    } else {

                        simulateGnssOutage = false

                        Log.d(
                            "IDR_TEST",
                            "GNSS outage simulation OFF"
                        )
                    }
                },

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        if (simulateGnssOutage)
                            "📡 RESTORE GNSS"
                        else
                            "🚨 SIMULATE GNSS OUTAGE"
                )
            }

            // ----------------------------------------------------
            // RECORD BUTTON
            // ----------------------------------------------------

            Button(

                onClick = {

                    if (isRecording) {

                        stopRecording()

                    } else {

                        startRecording()
                    }
                },

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        if (isRecording)
                            "⏹ STOP RECORDING"
                        else
                            "⏺ START RECORDING"
                )
            }

            // ----------------------------------------------------
            // EXPORT BUTTON
            // ----------------------------------------------------

            OutlinedButton(

                onClick = {
                    exportCsv()
                },

                enabled =
                    recordedSampleCount > 0,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text = "💾 EXPORT CSV"
                )
            }

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            Text(
                text =
                    "Demo flow: Get GPS fix → Start Recording → Simulate GNSS Outage → Observe IDR position → Restore GNSS → Stop Recording → Export CSV",

                fontSize = 12.sp
            )
        }
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    override fun onDestroy() {

        super.onDestroy()

        sensorManager.unregisterListener(this)

        try {
            fusedLocationClient.removeLocationUpdates(
                locationCallback
            )
        } catch (e: Exception) {
            Log.e(
                "IDR",
                "Failed to stop location updates",
                e
            )
        }

        try {
            sensorCollector.stop()
        } catch (e: Exception) {
            Log.e(
                "IDR",
                "Failed to stop sensor collector",
                e
            )
        }

        try {
            idrEngine.close()
        } catch (e: Exception) {
            Log.e(
                "IDR",
                "Failed to close IDR engine",
                e
            )
        }
    }
}