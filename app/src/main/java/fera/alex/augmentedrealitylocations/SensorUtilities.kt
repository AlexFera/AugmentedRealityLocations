package fera.alex.augmentedrealitylocations

import android.hardware.SensorManager

object SensorUtilities {

    private const val LOW_PASS_FILTER_CONSTANT = 0.25f

    fun filterSensors(input: FloatArray, current: FloatArray?): FloatArray {
        var output = FloatArray(3)

        if (current == null) {
            output = input
        } else {
            for (i in input.indices) {
                output[i] = current[i] + LOW_PASS_FILTER_CONSTANT * (input[i] - current[i])
            }
        }

        return output
    }

    fun computeDeviceOrientation(accelerometerReading: FloatArray, magnetometerReading: FloatArray): FloatArray {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)

        // Remap the coordinates with the camera pointing along the Y axis.
        // This way, portrait and landscape orientation return the same azimuth to magnetic north.
        val cameraRotationMatrix = FloatArray(9)
        SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, cameraRotationMatrix)

        val orientationAngles = FloatArray(3)
        SensorManager.getOrientation(cameraRotationMatrix, orientationAngles)

        // Return a float array containing [azimuth, pitch, roll]
        return orientationAngles
    }
}