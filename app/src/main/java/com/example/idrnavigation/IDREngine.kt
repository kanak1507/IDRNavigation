package com.example.idrnavigation

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class IdrEngine(
    private val speedPredictor: SpeedPredictor
) {

    // Stores the latest 10 sensor samples
    private val imuWindow = ArrayDeque<SensorData>()

    private val WINDOW_SIZE = 10

    // Navigation state
    private var initialized = false
    private var lastTimestamp = 0L

    private var currentLatitude = 0.0
    private var currentLongitude = 0.0

    private var currentSpeed = 0.0f

    // Heading in radians
    // 0 = North
    // 90 = East
    private var currentHeading = 0.0
    private var gyroHeadingInitialized = false
    // Previous GPS position
    private var previousGpsLatitude: Double? = null
    private var previousGpsLongitude: Double? = null


    fun process(sensorData: SensorData): IdrResult {

        // --------------------------------------------------
        // FIRST SAMPLE
        // --------------------------------------------------

        if (!initialized) {

            if (sensorData.gpsAvailable) {

                currentLatitude = sensorData.latitude
                currentLongitude = sensorData.longitude

                previousGpsLatitude = sensorData.latitude
                previousGpsLongitude = sensorData.longitude
            }

            lastTimestamp = sensorData.timestamp

            initialized = true

            addToImuWindow(sensorData)

            return IdrResult(
                latitude = currentLatitude,
                longitude = currentLongitude,
                gpsAvailable = sensorData.gpsAvailable,
                speed = 0.0f,
                heading = Math.toDegrees(currentHeading)
            )
        }


        // --------------------------------------------------
        // CALCULATE TIME DIFFERENCE
        // --------------------------------------------------

        var dt =
            (sensorData.timestamp - lastTimestamp) /
                    1_000_000_000.0

        // Protect against invalid timestamps
        if (dt <= 0.0 || dt > 1.0) {
            dt = 0.01
        }

        lastTimestamp = sensorData.timestamp


        // --------------------------------------------------
        // ADD SENSOR SAMPLE
        // --------------------------------------------------

        addToImuWindow(sensorData)


        // --------------------------------------------------
        // UPDATE HEADING USING GPS
        // --------------------------------------------------

        // First use GPS course when GPS is available
        if (sensorData.gpsAvailable) {
            updateHeadingFromGps(sensorData)
        }

// Always integrate gyroscope for relative heading
        updateHeadingFromGyro(sensorData, dt)


        // --------------------------------------------------
        // ML SPEED PREDICTION
        // --------------------------------------------------

        if (imuWindow.size >= WINDOW_SIZE) {

            try {

                val inputData =
                    createModelInput()

                val predictedSpeed =
                    speedPredictor.predict(inputData)

                val newSpeed =
                    predictedSpeed.coerceIn(0.0f, 20.0f)

// Smooth ML output
                currentSpeed =
                    0.85f * currentSpeed +
                            0.15f * newSpeed

                android.util.Log.d(
                    "IDR_ML",
                    "Predicted speed = $currentSpeed m/s"
                )

            } catch (e: Exception) {

                android.util.Log.e(
                    "IDR_ML",
                    "ML prediction failed",
                    e
                )
            }

        } else {

            // Before 10 samples are available,
            // use GPS speed if available.

            if (sensorData.gpsAvailable) {

                currentSpeed =
                    sensorData.gpsSpeed.coerceAtLeast(0.0f)
            }
        }


        // --------------------------------------------------
        // GPS AVAILABLE
        // --------------------------------------------------

        if (sensorData.gpsAvailable) {

            /*
             * GPS is currently available.
             *
             * For now we use GPS as the position reference.
             */

            currentLatitude =
                sensorData.latitude

            currentLongitude =
                sensorData.longitude
        }


        // --------------------------------------------------
        // GPS UNAVAILABLE
        // --------------------------------------------------

        else {

            /*
             * GNSS outage.
             *
             * Use:
             *
             * distance = speed × time
             */

            val distance =
                currentSpeed * dt

            deadReckon(distance)
        }


        // --------------------------------------------------
        // RETURN RESULT
        // --------------------------------------------------

        return IdrResult(
            latitude = currentLatitude,
            longitude = currentLongitude,
            gpsAvailable = sensorData.gpsAvailable,
            speed = currentSpeed,
            heading = Math.toDegrees(currentHeading)
        )
    }


    // ======================================================
    // ADD SAMPLE TO IMU WINDOW
    // ======================================================

    private fun addToImuWindow(
        sensorData: SensorData
    ) {

        imuWindow.addLast(sensorData)

        if (imuWindow.size > WINDOW_SIZE) {
            imuWindow.removeFirst()
        }
    }


    // ======================================================
    // CREATE ML INPUT
    // ======================================================

    private fun createModelInput(): Array<FloatArray> {

        /*
         * Model expects:
         *
         * [6][10]
         *
         * 0 = Accelerometer X
         * 1 = Accelerometer Y
         * 2 = Accelerometer Z
         * 3 = Gyroscope X
         * 4 = Gyroscope Y
         * 5 = Gyroscope Z
         */

        val input =
            Array(6) {
                FloatArray(WINDOW_SIZE)
            }

        imuWindow
            .toList()
            .forEachIndexed { index, data ->

                input[0][index] =
                    data.accelerometerX

                input[1][index] =
                    data.accelerometerY

                input[2][index] =
                    data.accelerometerZ

                input[3][index] =
                    data.gyroscopeX

                input[4][index] =
                    data.gyroscopeY

                input[5][index] =
                    data.gyroscopeZ
            }

        return input
    }


    // ======================================================
    // GPS HEADING
    // ======================================================

    private fun updateHeadingFromGps(
        sensorData: SensorData
    ) {

        val previousLat =
            previousGpsLatitude

        val previousLon =
            previousGpsLongitude

        if (
            previousLat != null &&
            previousLon != null &&
            sensorData.gpsSpeed > 0.5f
        ) {

            currentHeading =
                calculateBearing(
                    previousLat,
                    previousLon,
                    sensorData.latitude,
                    sensorData.longitude
                )
        }

        previousGpsLatitude =
            sensorData.latitude

        previousGpsLongitude =
            sensorData.longitude
    }
// ======================================================
// GYROSCOPE HEADING
// ======================================================

    private fun updateHeadingFromGyro(
        sensorData: SensorData,
        dt: Double
    ) {

        /*
         * Android gyroscope values are in rad/s.
         *
         * For a prototype we assume:
         *
         * gyro Z = rotation around vertical axis.
         *
         * Integrating angular velocity:
         *
         * heading = heading + angularVelocity × dt
         */

        val gyroZ = sensorData.gyroscopeZ.toDouble()

        // Ignore extremely small gyro noise.
        val deadband = 0.015

        if (kotlin.math.abs(gyroZ) < deadband) {
            return
        }

        currentHeading += gyroZ * dt

        // Keep heading between 0 and 2π.
        currentHeading =
            (currentHeading + 2.0 * Math.PI) %
                    (2.0 * Math.PI)

        android.util.Log.d(
            "IDR_HEADING",
            "GyroZ=$gyroZ | " +
                    "Heading=${Math.toDegrees(currentHeading)}°"
        )
    }

    // ======================================================
    // DEAD RECKONING
    // ======================================================

    private fun deadReckon(
        distance: Double
    ) {

        val earthRadius =
            6_378_137.0

        // North movement
        val northDistance =
            distance * cos(currentHeading)

        // East movement
        val eastDistance =
            distance * sin(currentHeading)


        // Convert meters → latitude
        val deltaLatitude =
            northDistance / earthRadius

        // Convert meters → longitude
        val deltaLongitude =
            eastDistance /
                    (
                            earthRadius *
                                    cos(
                                        Math.toRadians(
                                            currentLatitude
                                        )
                                    )
                            )

        currentLatitude +=
            Math.toDegrees(deltaLatitude)

        currentLongitude +=
            Math.toDegrees(deltaLongitude)

        android.util.Log.d(
            "IDR_DR",
            "Dead reckoning: " +
                    "lat=$currentLatitude, " +
                    "lon=$currentLongitude, " +
                    "speed=$currentSpeed"
        )
    }


    // ======================================================
    // CALCULATE GPS BEARING
    // ======================================================

    private fun calculateBearing(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {

        val latitude1 =
            Math.toRadians(lat1)

        val latitude2 =
            Math.toRadians(lat2)

        val deltaLongitude =
            Math.toRadians(
                lon2 - lon1
            )

        val y =
            sin(deltaLongitude) *
                    cos(latitude2)

        val x =
            cos(latitude1) *
                    sin(latitude2) -
                    sin(latitude1) *
                    cos(latitude2) *
                    cos(deltaLongitude)

        return (
                atan2(y, x) + 2.0 * Math.PI
                ) % (2.0 * Math.PI)
    }


    // ======================================================
    // CLEANUP
    // ======================================================

    fun close() {
        speedPredictor.close()
    }
}


// ==========================================================
// IDR RESULT
// ==========================================================

data class IdrResult(

    val latitude: Double,

    val longitude: Double,

    val gpsAvailable: Boolean,

// Phone heading in degrees.
// 0 = North
// 90 = East
// 180 = South
// 270 = West
    val heading: Double,

    val speed: Float,


)