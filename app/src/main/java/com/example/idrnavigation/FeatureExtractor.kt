package com.example.idrnavigation

import kotlin.math.sqrt

/**
 * FeatureExtractor
 *
 * Converts raw phone IMU measurements into the
 * features expected by our speed prediction model.
 *
 * Model features:
 *
 * 1. LINEAR_ACC_X
 * 2. LINEAR_ACC_Y
 * 3. LINEAR_ACC_Z
 * 4. ACC_MAG
 * 5. ACC_MAG_SMOOTH
 * 6. GYRO_MAG
 * 7. WORLD_ACC_X
 * 8. WORLD_ACC_Y
 */
class FeatureExtractor {

    /*
     * Previous smoothed acceleration magnitude.
     *
     * We use this to create a simple rolling-style
     * smoothing value for the prototype.
     */
    private var previousAccMagnitude = 0f

    /**
     * Extracts the 8 features required by the ML model.
     */
    fun extract(
        accelerometerX: Float,
        accelerometerY: Float,
        accelerometerZ: Float,
        gyroscopeX: Float,
        gyroscopeY: Float,
        gyroscopeZ: Float
    ): FloatArray {

        /*
         * ------------------------------------------------
         * STEP 1: Estimate gravity
         * ------------------------------------------------
         *
         * The accelerometer measures:
         *
         *     gravity + movement
         *
         * For now we use a simple gravity estimate.
         *
         * Later we will replace this with Android's
         * rotation/orientation information for better
         * gravity compensation.
         */
        val gravityX = 0f
        val gravityY = 0f
        val gravityZ = 9.81f

        /*
         * Remove gravity from raw acceleration.
         */
        val linearAccX =
            accelerometerX - gravityX

        val linearAccY =
            accelerometerY - gravityY

        val linearAccZ =
            accelerometerZ - gravityZ

        /*
         * ------------------------------------------------
         * STEP 2: Acceleration magnitude
         * ------------------------------------------------
         *
         * Magnitude:
         *
         * sqrt(x² + y² + z²)
         */
        val accMagnitude =
            sqrt(
                linearAccX * linearAccX +
                        linearAccY * linearAccY +
                        linearAccZ * linearAccZ
            )

        /*
         * ------------------------------------------------
         * STEP 3: Smooth acceleration magnitude
         * ------------------------------------------------
         *
         * Simple exponential smoothing.
         *
         * This reduces sudden sensor noise.
         */
        val smoothingFactor = 0.2f

        val accMagnitudeSmooth =
            smoothingFactor * accMagnitude +
                    (1f - smoothingFactor) * previousAccMagnitude

        previousAccMagnitude = accMagnitudeSmooth

        /*
         * ------------------------------------------------
         * STEP 4: Gyroscope magnitude
         * ------------------------------------------------
         */
        val gyroMagnitude =
            sqrt(
                gyroscopeX * gyroscopeX +
                        gyroscopeY * gyroscopeY +
                        gyroscopeZ * gyroscopeZ
            )

        /*
         * ------------------------------------------------
         * STEP 5: World-frame acceleration
         * ------------------------------------------------
         *
         * For the first Android prototype, we use the
         * phone-frame X/Y acceleration directly.
         *
         * IMPORTANT:
         * This is temporary.
         *
         * Later we will use the phone orientation/
         * rotation matrix to transform acceleration
         * into the navigation/world coordinate frame.
         */
        val worldAccX = linearAccX
        val worldAccY = linearAccY

        /*
         * ------------------------------------------------
         * STEP 6: Build model input
         * ------------------------------------------------
         *
         * The order MUST remain exactly the same as
         * the model's training feature order.
         */
        return floatArrayOf(
            linearAccX,
            linearAccY,
            linearAccZ,
            accMagnitude,
            accMagnitudeSmooth,
            gyroMagnitude,
            worldAccX,
            worldAccY
        )
    }
}