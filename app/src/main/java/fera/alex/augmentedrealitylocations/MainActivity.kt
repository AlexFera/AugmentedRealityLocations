package fera.alex.augmentedrealitylocations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AppCompatActivity
import android.widget.FrameLayout
import com.wonderkiln.camerakit.CameraView

class MainActivity : AppCompatActivity() {

    private lateinit var cameraView: CameraView
    private lateinit var overlayView: OverlayView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val permissions = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        ActivityCompat.requestPermissions(this, permissions, 0)

        val intent = Intent(this.applicationContext, LocationTrackingService::class.java)
        this.applicationContext.startService(intent)

        cameraView = findViewById(R.id.cameraView)

        val mainFrame = findViewById<FrameLayout>(R.id.main_frame)
        overlayView = OverlayView(this.applicationContext, cameraView)
        mainFrame.addView(overlayView)
    }

    override fun onResume() {
        super.onResume()
        cameraView.start()
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, IntentFilter("location-changed"))
    }

    override fun onPause() {
        cameraView.stop()
        super.onPause()
    }

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra("location") as Location
            overlayView.onLocationChanged(location)
        }
    }
}
