package com.example.idrnavigation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * SensorCollector
 *
 * Responsible for collecting raw data from the phone's
 * accelerometer and gyroscope.
 *
 * Later, GNSS data will also be combined with this data
 * and passed into our IDR engine.
 */
class SensorCollector(
    context: Context
) : SensorEventListener {

    // Android's manager for accessing hardware sensors.
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE)
                as SensorManager


    // ---------------------------------------------------------
    // SENSOR REFERENCES
    // ---------------------------------------------------------

    private val accelerometer =
        sensorManager.getDefaultSensor(
            Sensor.TYPE_ACCELEROMETER
        )

    private val gyroscope =
        sensorManager.getDefaultSensor(
            Sensor.TYPE_GYROSCOPE
        )


    // ---------------------------------------------------------
    // CURRENT SENSOR VALUES
    // ---------------------------------------------------------

    var accelerometerX = 0f
        private set

    var accelerometerY = 0f
        private set

    var accelerometerZ = 0f
        private set


    var gyroscopeX = 0f
        private set

    var gyroscopeY = 0f
        private set

    var gyroscopeZ = 0f
        private set


    // Timestamp of the latest sensor event.
    var timestamp = 0L
        private set


    // ---------------------------------------------------------
    // START COLLECTING
    // ---------------------------------------------------------

    fun start() {

        // Start accelerometer updates.
        accelerometer?.let {

            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }


        // Start gyroscope updates.
        gyroscope?.let {

            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }


    // ---------------------------------------------------------
    // STOP COLLECTING
    // ---------------------------------------------------------

    fun stop() {

        // Stop receiving sensor events.
        sensorManager.unregisterListener(this)
    }


    // ---------------------------------------------------------
    // SENSOR CALLBACK
    // ---------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent?) {

        if (event == null) return


        // Save timestamp.
        timestamp = event.timestamp


        when (event.sensor.type) {

            // -------------------------------------------------
            // ACCELEROMETER
            // -------------------------------------------------

            Sensor.TYPE_ACCELEROMETER -> {

                accelerometerX = event.values[0]
                accelerometerY = event.values[1]
                accelerometerZ = event.values[2]
            }


            // -------------------------------------------------
            // GYROSCOPE
            // -------------------------------------------------

            Sensor.TYPE_GYROSCOPE -> {

                gyroscopeX = event.values[0]
                gyroscopeY = event.values[1]
                gyroscopeZ = event.values[2]
            }
        }
    }


    // ---------------------------------------------------------
    // SENSOR ACCURACY
    // ---------------------------------------------------------

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
        // We don't need this information yet.
    }


    // ---------------------------------------------------------
    // CREATE SENSOR DATA
    // ---------------------------------------------------------

    /**
     * Creates a SensorData object containing the latest
     * accelerometer and gyroscope measurements.
     *
     * GNSS values will be added later.
     */
    fun getSensorData(): SensorData {

        return SensorData(

            timestamp = timestamp,

            accelerometerX = accelerometerX,
            accelerometerY = accelerometerY,
            accelerometerZ = accelerometerZ,

            gyroscopeX = gyroscopeX,
            gyroscopeY = gyroscopeY,
            gyroscopeZ = gyroscopeZ,

            // GNSS isn't connected to this class yet.
            latitude = 0.0,
            longitude = 0.0,

            gpsSpeed = 0f,
            gpsAccuracy = 0f,

            gpsAvailable = false
        )
    }
}