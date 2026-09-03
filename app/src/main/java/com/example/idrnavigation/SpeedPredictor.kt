package com.example.idrnavigation

import android.content.res.AssetManager
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/**
 * Runs the trained RAW IMU speed prediction model.
 *
 * Model input:
 * [batch, 6, 10]
 *
 * Channel order:
 * 0 = Accelerometer X
 * 1 = Accelerometer Y
 * 2 = Accelerometer Z
 * 3 = Gyroscope X
 * 4 = Gyroscope Y
 * 5 = Gyroscope Z
 *
 * The model was trained using normalized sensor values,
 * so Android must apply the SAME normalization before inference.
 */
class SpeedPredictor(
    private val assetManager: AssetManager
) {

    private val ortEnvironment =
        OrtEnvironment.getEnvironment()

    private val ortSession: OrtSession

    // --------------------------------------------------------
    // Normalization values from normalization_raw_imu.json
    // --------------------------------------------------------

    private val mean = floatArrayOf(
        0.072388f,
        0.077817f,
        9.839184f,
        0.002656f,
        -0.005501f,
        0.002687f
    )

    private val std = floatArrayOf(
        1.092317f,
        1.104160f,
        0.531866f,
        0.098370f,
        0.145236f,
        0.053265f
    )

    init {

        // ----------------------------------------------------
        // Load ONNX model from Android assets.
        // ----------------------------------------------------

        val modelBytes =
            assetManager
                .open("speed_predictor_raw_imu.onnx")
                .use {
                    it.readBytes()
                }

        ortSession =
            ortEnvironment.createSession(
                modelBytes
            )

        android.util.Log.d(
            "IDR_ML",
            "Speed predictor model loaded."
        )

        android.util.Log.d(
            "IDR_ML",
            "Input name: ${ortSession.inputNames.first()}"
        )

        android.util.Log.d(
            "IDR_ML",
            "Output name: ${ortSession.outputNames.first()}"
        )
    }

    /**
     * Predict speed from a 6 × 10 IMU window.
     *
     * inputData[channel][sample]
     *
     * Channel order:
     * Acc X
     * Acc Y
     * Acc Z
     * Gyro X
     * Gyro Y
     * Gyro Z
     */
    fun predict(
        inputData: Array<FloatArray>
    ): Float {

        // ----------------------------------------------------
        // Validate input dimensions.
        // ----------------------------------------------------

        require(inputData.size == 6) {
            "Expected 6 IMU channels."
        }

        for (channel in inputData) {

            require(channel.size == 10) {
                "Each IMU channel must contain 10 samples."
            }
        }

        // ----------------------------------------------------
        // Create normalized input.
        //
        // Formula:
        //
        // normalized = (value - mean) / std
        // ----------------------------------------------------

        val normalizedInput =
            Array(6) { channel ->

                FloatArray(10) { sample ->

                    (
                            inputData[channel][sample]
                                    - mean[channel]
                            ) / std[channel]
                }
            }

        // ----------------------------------------------------
        // ONNX expects:
        //
        // [batch, channels, samples]
        //
        // Therefore:
        //
        // Array(1) { normalizedInput }
        //
        // gives:
        //
        // [1, 6, 10]
        // ----------------------------------------------------

        val inputTensor =
            OnnxTensor.createTensor(
                ortEnvironment,
                arrayOf(normalizedInput)
            )

        // ----------------------------------------------------
        // Run inference.
        // ----------------------------------------------------

        val results =
            ortSession.run(
                mapOf(
                    "imu_input" to inputTensor
                )
            )

        // ----------------------------------------------------
        // Extract predicted speed.
        //
        // Output shape:
        // [1, 1]
        // ----------------------------------------------------

        val output =
            results[0].value as Array<*>

        val predictedSpeed =
            (output[0] as FloatArray)[0]

        // ----------------------------------------------------
        // Release ONNX resources.
        // ----------------------------------------------------

        inputTensor.close()
        results.close()

        return predictedSpeed
    }

    /**
     * Releases the ONNX session when the activity is destroyed.
     */
    fun close() {

        ortSession.close()
        ortEnvironment.close()
    }
}