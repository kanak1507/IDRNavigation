package com.example.idrnavigation

/**
 * Stores one complete snapshot of the phone's
 * navigation sensor data.
 *
 * Eventually this structure will be passed to
 * our Dead Reckoning / ML engine.
 */
data class SensorData(

    // ---------------------------------------------------------
    // TIMESTAMP
    // ---------------------------------------------------------

    // Time at which this sensor measurement was recorded.
    // Nanoseconds are provided by Android's sensor system.
    val timestamp: Long,


    // ---------------------------------------------------------
    // ACCELEROMETER
    // ---------------------------------------------------------

    // Acceleration along the phone's X axis.
    val accelerometerX: Float,

    // Acceleration along the phone's Y axis.
    val accelerometerY: Float,

    // Acceleration along the phone's Z axis.
    val accelerometerZ: Float,


    // ---------------------------------------------------------
    // GYROSCOPE
    // ---------------------------------------------------------

    // Rotation around the phone's X axis.
    val gyroscopeX: Float,

    // Rotation around the phone's Y axis.
    val gyroscopeY: Float,

    // Rotation around the phone's Z axis.
    val gyroscopeZ: Float,


    // ---------------------------------------------------------
    // GNSS / GPS
    // ---------------------------------------------------------

    // Current latitude.
    val latitude: Double,

    // Current longitude.
    val longitude: Double,

    // Current speed reported by GNSS.
    // We store this in meters per second.
    val gpsSpeed: Float,

    // Estimated horizontal positioning accuracy in meters.
    val gpsAccuracy: Float,


    // ---------------------------------------------------------
    // GNSS STATUS
    // ---------------------------------------------------------

    // True when a valid GNSS location is available.
    val gpsAvailable: Boolean
)