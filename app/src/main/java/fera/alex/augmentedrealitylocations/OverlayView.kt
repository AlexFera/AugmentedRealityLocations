package fera.alex.augmentedrealitylocations

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.graphics.Paint.Align
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.view.View
import com.wonderkiln.camerakit.CameraView
import com.google.maps.GeoApiContext
import com.google.maps.PlacesApi
import com.google.maps.model.LatLng
import com.google.maps.model.PlaceType

class OverlayView(context: Context, cameraView: CameraView) : View(context), SensorEventListener {
    private var accelerometerData = "Accelerometer Data"
    private var compassData = "Compass Data"
    private var gyroData = "Gyro Data"
    private var cameraView: CameraView
    private var lastGravityData = FloatArray(size = 9)
    private var lastGeomagneticData = FloatArray(size = 9)
    private lateinit var nearbyLocations: List<NearbyLocation>
    private lateinit var lastLocation: Location
    private lateinit var testLocation: Location

    init {
        registerSensorChanges(context)
        this.cameraView = cameraView
    }

    fun onLocationChanged(location: Location) {
        lastLocation = location

        val context = GeoApiContext.Builder().apiKey("AIzaSyAon5czn9QT7u_Odl_lq0C0MINyQBwQrek").build()
        val response = PlacesApi.nearbySearchQuery(context, LatLng(lastLocation.latitude, lastLocation.longitude))
                .radius(1000)
                .type(PlaceType.BUS_STATION, PlaceType.GAS_STATION, PlaceType.MUSEUM, PlaceType.HOSPITAL,
                        PlaceType.FIRE_STATION, PlaceType.HEALTH, PlaceType.LIBRARY, PlaceType.LODGING,
                        PlaceType.UNIVERSITY, PlaceType.TRAIN_STATION, PlaceType.SUBWAY_STATION, PlaceType.RESTAURANT)
                .awaitIgnoreError()

        for (result in response.results) {
            val nearbyLocation = NearbyLocation(result.name, result.geometry.location.lat, result.geometry.location.lng)
        }

        testLocation = Location("manual")
        testLocation.latitude = response.results[0].geometry.location.lat
        testLocation.longitude = response.results[0].geometry.location.lng
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        val contentPaint = Paint(ANTI_ALIAS_FLAG)
        contentPaint.textAlign = Align.CENTER
        contentPaint.textSize = 20F
        contentPaint.isAntiAlias = true
        contentPaint.color = Color.RED

        val verticalFOV = cameraView.cameraProperties?.verticalViewingAngle
        val horizontalFOV = cameraView.cameraProperties?.horizontalViewingAngle

        if (::lastLocation.isInitialized && horizontalFOV != null && verticalFOV != null && ::testLocation.isInitialized) {
            val currentBearing = lastLocation.bearingTo(testLocation)
            val orientation = SensorUtilities.computeDeviceOrientation(lastGravityData, lastGeomagneticData)
            // use roll for screen rotation
            canvas?.rotate((0.0f - Math.toDegrees(orientation[2].toDouble())).toFloat())
            // Translate, but normalize for the FOV of the camera -- basically, pixels per degree, times degrees == pixels
            val dx = (canvas?.width!! / horizontalFOV * (Math.toDegrees(orientation[0].toDouble()) - currentBearing))
            val dy = (canvas.height / verticalFOV!! * Math.toDegrees(orientation[1].toDouble()))

            // wait to translate the dx so the horizon doesn't get pushed off
            canvas.translate(0.0f, (0.0f - dy).toFloat())

            // make our line big enough to draw regardless of rotation and translation
            canvas.drawLine(0f - canvas.height, (canvas.height / 2).toFloat(), (canvas.width + canvas.height).toFloat(), (canvas.height / 2).toFloat(), contentPaint)

            // now translate the dx
            canvas.translate((0.0f - dx).toFloat(), 0.0f)

            // draw our point -- we've rotated and translated this to the right spot already
            canvas.drawCircle((canvas.width / 2).toFloat(), (canvas.height / 2).toFloat(), 8.0f, contentPaint)

            canvas.drawText("Guvernul României", (canvas.width / 2).toFloat(), (canvas.height / 2).toFloat(), contentPaint)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            val msg = StringBuilder(event.sensor.name).append(" ")
            for (value in event.values) {
                msg.append("[").append(value).append("]")
            }

            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    accelerometerData = msg.toString()
                    lastGravityData = SensorUtilities.filterSensors(event.values, lastGravityData)
                }
                Sensor.TYPE_GYROSCOPE -> {
                    gyroData = msg.toString()
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    compassData = msg.toString()
                    lastGeomagneticData = SensorUtilities.filterSensors(event.values, lastGeomagneticData)
                }
            }

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (location != null) {
                    lastLocation = location
                }
            } catch (e: SecurityException) {
            }

            this.invalidate()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    private fun registerSensorChanges(context: Context) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometerSensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val compassSensor = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val gyroSensor = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        sensors.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensors.registerListener(this, compassSensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensors.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }
}