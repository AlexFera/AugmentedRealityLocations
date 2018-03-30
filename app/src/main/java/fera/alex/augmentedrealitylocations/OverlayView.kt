package fera.alex.augmentedrealitylocations

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.util.Log
import android.view.View
import com.wonderkiln.camerakit.CameraView
import com.google.maps.GeoApiContext
import com.google.maps.PlacesApi
import com.google.maps.model.LatLng
import com.google.maps.model.PlaceType
import android.graphics.RectF


class OverlayView(context: Context, cameraView: CameraView) : View(context), SensorEventListener {
    private var cameraView: CameraView
    private var lastGravityData = FloatArray(size = 9)
    private var lastGeomagneticData = FloatArray(size = 9)
    private var viewPortsCalculated = false
    private var verticalFOV: Float = 0.0f
    private var horizontalFOV: Float = 0.0f
    private var viewportHeight = 0.0f
    private var viewportWidth = 0.0f
    private var nearbyPlaces = mutableListOf<NearbyPlace>()
    private var arePaintsConfigured = false
    private var textPaint: Paint
    private var outlinePaint: Paint
    private var bubblePaint: Paint
    private var currentAzimuth = 0.0f
    private var currentPitch = 0.0f
    private var bubble: RectF
    private lateinit var lastLocation: Location

    init {
        this.registerSensorChanges(context)
        this.cameraView = cameraView
        this.textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        this.outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        this.bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        this.textPaint.textAlign = Paint.Align.LEFT
        this.textPaint.textSize = 20f
        this.textPaint.color = Color.BLACK
        this.outlinePaint.style = Paint.Style.STROKE
        this.outlinePaint.strokeWidth = 2f
        this.bubblePaint.style = Paint.Style.FILL
        this.arePaintsConfigured = true
        this.bubble = RectF()
    }

    fun onLocationChanged(location: Location) {
        lastLocation = location

        val context = GeoApiContext.Builder().apiKey("AIzaSyAon5czn9QT7u_Odl_lq0C0MINyQBwQrek").build()
        val response = PlacesApi.nearbySearchQuery(context, LatLng(lastLocation.latitude, lastLocation.longitude))
                .radius(1000)
                .type(PlaceType.MUSEUM, PlaceType.HOSPITAL,
                        PlaceType.HEALTH, PlaceType.LIBRARY, PlaceType.LODGING,
                        PlaceType.UNIVERSITY, PlaceType.TRAIN_STATION, PlaceType.SUBWAY_STATION, PlaceType.RESTAURANT)
                .awaitIgnoreError()

        nearbyPlaces = mutableListOf()
        for (result in response.results) {
            val nearbyLocation = NearbyPlace(result.name, result.geometry.location.lat, result.geometry.location.lng, 0f, 0f, 0)
            nearbyPlaces.add(nearbyLocation)
        }
    }

    /*
    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        val verticalFOV = cameraView.cameraProperties?.verticalViewingAngle
        val horizontalFOV = cameraView.cameraProperties?.horizontalViewingAngle

        if (::lastLocation.isInitialized && horizontalFOV != null && verticalFOV != null && !nearbyPlaces.isEmpty()) {
            val location = Location("manual")
            location.longitude = nearbyPlaces[0].longitude
            location.latitude = nearbyPlaces[0].latitude

            val currentBearing = lastLocation.bearingTo(location)
            val orientation = SensorUtilities.computeDeviceOrientation(lastGravityData, lastGeomagneticData)
            // use roll for screen rotation
            canvas?.rotate((0.0f - Math.toDegrees(orientation[2].toDouble())).toFloat())
            // Translate, but normalize for the FOV of the camera -- basically, pixels per degree, times degrees == pixels
            val dx = (canvas?.width!! / horizontalFOV * (Math.toDegrees(orientation[0].toDouble()) - currentBearing))
            val dy = (canvas.height / verticalFOV * Math.toDegrees(orientation[1].toDouble()))

            // wait to translate the dx so the horizon doesn't get pushed off
            canvas.translate(0.0f, (0.0f - dy).toFloat())

            // make our line big enough to draw regardless of rotation and translation
            canvas.drawLine(0f - canvas.height, (canvas.height / 2).toFloat(), (canvas.width + canvas.height).toFloat(), (canvas.height / 2).toFloat(), contentPaint)

            // now translate the dx
            canvas.translate((0.0f - dx).toFloat(), 0.0f)

            // draw our point -- we've rotated and translated this to the right spot already
            canvas.drawCircle((canvas.width / 2).toFloat(), (canvas.height / 2).toFloat(), 8.0f, contentPaint)

            val distanceTo = lastLocation.distanceTo(location)
            canvas.drawText(nearbyPlaces[0].name + " " + distanceTo.roundToInt() + " metri" , (canvas.width / 2).toFloat(), (canvas.height / 2).toFloat(), contentPaint)
        }
    }
    */

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (nearbyPlaces.isEmpty()){
            return
        }

        this.calculateViewPorts(canvas)
        if (!this.viewPortsCalculated) {
            return
        }

        if (nearbyPlaces.isNotEmpty()){
            // Center of view
            val x = canvas!!.width / 2f
            val y = canvas.height / 2f

            val dy = this.currentPitch * this.viewportHeight
            // Iterate backwards to draw more distant places first
            for (nearbyPlace in nearbyPlaces.asReversed()){
                val degreesToTarget = this.currentAzimuth - getBearingToPlace(nearbyPlace)
                val dx = this.viewportWidth * degreesToTarget
                nearbyPlace.iconX = x - dx
                nearbyPlace.iconY = y - dy
                nearbyPlace.distanceToPlace = this.getDistanceToPlace(nearbyPlace)

                var angleToTarget = degreesToTarget
                if (degreesToTarget < 0) {
                    angleToTarget = 360 + degreesToTarget
                }
                if (angleToTarget >= 0 && angleToTarget < 90) {
                    drawInQuadrant(canvas, 1, nearbyPlace)
                } else if (angleToTarget >= 90 && angleToTarget < 180) {
                    drawInQuadrant(canvas, 2, nearbyPlace)
                } else if (angleToTarget >= 180 && angleToTarget < 270) {
                    drawInQuadrant(canvas, 3, nearbyPlace)
                } else {
                    drawInQuadrant(canvas, 4, nearbyPlace)
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    lastGravityData = SensorUtilities.filterSensors(event.values, lastGravityData)
                }
                Sensor.TYPE_GYROSCOPE -> {
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    lastGeomagneticData = SensorUtilities.filterSensors(event.values, lastGeomagneticData)
                }
            }
            val orientation = SensorUtilities.computeDeviceOrientation(lastGravityData, lastGeomagneticData)

            // Convert azimuth relative to magnetic north from radians to degrees
            this.currentAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (this.currentAzimuth < 0) {
                this.currentAzimuth += 360f
            }

            // Convert pitch and roll from radians to degrees
            this.currentPitch = Math.toDegrees(orientation[1].toDouble()).toFloat()

            // Update the OverlayDisplayView to redraw when sensor data changes,
            // redrawing only when the camera is not pointing straight up or down
            if ( this.currentPitch <= 75 &&  this.currentPitch >= -75) {
                this.invalidate()
            }
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

    private fun calculateViewPorts(canvas: Canvas?) {
        if (this.cameraView.cameraProperties != null) {
            this.verticalFOV = this.cameraView.cameraProperties!!.verticalViewingAngle
            this.horizontalFOV = this.cameraView.cameraProperties!!.horizontalViewingAngle
            if (!this.viewPortsCalculated && this.verticalFOV > 0 && this.horizontalFOV > 0) {
                if (canvas != null) {
                    this.viewportHeight = canvas.height / verticalFOV
                    this.viewportWidth = canvas.width / horizontalFOV
                    this.viewPortsCalculated = true
                }
            }
        }
    }

    private fun getBearingToPlace(nearbyPlace: NearbyPlace): Float {
        val location = Location("manual")
        location.longitude = nearbyPlace.longitude
        location.latitude = nearbyPlace.latitude

        return lastLocation.bearingTo(location)
    }

    private fun getDistanceToPlace(nearbyPlace: NearbyPlace): Int {
        val location = Location("manual")
        location.longitude = nearbyPlace.longitude
        location.latitude = nearbyPlace.latitude

        return lastLocation.distanceTo(location).toInt()
    }

    private fun drawInQuadrant(canvas: Canvas, quadrantId: Int, nearbyPlace: NearbyPlace){
        Log.i("test", "quadrantId: $quadrantId ${nearbyPlace.name}")

        nearbyPlace.iconY += this.nearbyPlaces.indexOf(nearbyPlace) * 40f
        val canvasRightMargin = 5f
        val iconHeight = 48f
        val iconWidth = 48f
        val textX = nearbyPlace.iconX + iconWidth
        val nameY = nearbyPlace.iconY + iconHeight / 2.4f
        val distanceY = nameY + iconHeight / 2.1f

        val left = nearbyPlace.iconX
        val top = nearbyPlace.iconY
        val bottom = top + iconHeight
        val right = left + iconWidth + this.textPaint.measureText(nearbyPlace.name) + canvasRightMargin
        this.bubble.set(left, top, right, bottom)
        val bubbleColor = Color.RED
        this.textPaint.alpha = 148
        this.bubblePaint.color = bubbleColor
        this.bubblePaint.alpha = 98
        this.outlinePaint.color = bubbleColor
        this.outlinePaint.alpha = 32

        val cornerRadius = 5f
        canvas.drawRoundRect(this.bubble, cornerRadius, cornerRadius, this.bubblePaint)
        canvas.drawRoundRect(this.bubble, cornerRadius, cornerRadius, this.bubblePaint)
        canvas.drawText(nearbyPlace.name, textX, nameY, this.textPaint)
        canvas.drawText(nearbyPlace.distanceToPlace.toString() + " metri", textX, distanceY, this.textPaint)
    }
}
