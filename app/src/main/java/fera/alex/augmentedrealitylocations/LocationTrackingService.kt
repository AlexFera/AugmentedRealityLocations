package fera.alex.augmentedrealitylocations

import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.util.Log

class LocationTrackingService : Service(), LocationListener {

    companion object {
        const val TAG = "LocationTrackingService"

        const val INTERVAL = 1000.toLong() // In milliseconds
        const val DISTANCE = 10.toFloat() // In meters
    }

    private var locationManager: LocationManager? = null
    private lateinit var localBroadcastManager: LocalBroadcastManager

    override fun onLocationChanged(location: Location?) {
        val intent = Intent("location-changed")
        intent.putExtra("location", location)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }

    override fun onProviderEnabled(provider: String?) {
    }

    override fun onProviderDisabled(provider: String?) {
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onCreate() {
        localBroadcastManager = LocalBroadcastManager.getInstance(this)
        if (locationManager == null) {
            locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        }

        try {
            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, INTERVAL, DISTANCE, this)
        } catch (e: SecurityException) {
            Log.e(TAG, "Fail to request location update", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Network provider does not exist", e)
        }

        try {
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL, DISTANCE, this)
        } catch (e: SecurityException) {
            Log.e(TAG, "Fail to request location update", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "GPS provider does not exist", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (locationManager != null)
            try {
                locationManager?.removeUpdates(this)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove location listeners")
            }
    }
}