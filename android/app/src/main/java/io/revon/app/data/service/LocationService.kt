package io.revon.app.data.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GPSData(
    val latitude: Double = 23.9738,
    val longitude: Double = 120.9820,
    val speedKmh: Float = 0f,
    val bearing: Float = 0f,
    val accuracyMeters: Float = 999f,
    val accumulatedDistanceMeters: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val sensorAccelG: Float = 0f
)

class LocationService : Service(), SensorEventListener {

    private val binder = LocalBinder()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var previousLocation: Location? = null
    private var totalDistance = 0f

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var smoothedSensorG = 0.0f

    private var wakeLock: PowerManager.WakeLock? = null

    private val _gpsData = MutableStateFlow(GPSData())
    val gpsData: StateFlow<GPSData> = _gpsData.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        // 1. 初始化 PowerManager WakeLock，確保關閉螢幕或切換至後台時 CPU 仍保持常亮運作
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "REVON:TelemetryServiceWakeLock"
        )?.apply {
            setReferenceCounted(false)
        }

        // 2. 初始化硬體 Accelerometer 感測器
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                var speedKmh = 0f
                if (loc.hasSpeed() && loc.speed > 0) {
                    speedKmh = loc.speed * 3.6f
                } else if (previousLocation != null) {
                    val dt = (loc.time - previousLocation!!.time) / 1000f
                    if (dt > 0) {
                        val dist = loc.distanceTo(previousLocation!!)
                        speedKmh = (dist / dt) * 3.6f
                    }
                }

                previousLocation?.let { prev ->
                    val delta = loc.distanceTo(prev)
                    if (delta > 0.5f) {
                        totalDistance += delta
                    }
                }
                previousLocation = loc

                _gpsData.value = GPSData(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    speedKmh = speedKmh,
                    bearing = loc.bearing,
                    accuracyMeters = loc.accuracy,
                    accumulatedDistanceMeters = totalDistance,
                    timestamp = loc.time,
                    sensorAccelG = smoothedSensorG
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        acquireWakeLock()
        startForegroundServiceNotification()
        startLocationUpdates()
        return START_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld != true) {
            try {
                wakeLock?.acquire(10 * 60 * 60 * 1000L) // 最長保持 10 小時
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            try {
                wakeLock?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val gVal = (Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat() / 9.80665f) - 1.0f
            val clampedG = gVal.coerceIn(-3.0f, 3.0f)
            smoothedSensorG = smoothedSensorG + 0.25f * (clampedG - smoothedSensorG)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 400L
        ).apply {
            setMinUpdateIntervalMillis(200L)
            setWaitForAccurateLocation(false)
        }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun startForegroundServiceNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🏁 REVON 賽道高精度計時與感測中")
            .setContentText("即使關閉螢幕或返回桌面，GPS 與 telemetry 感測器仍保持常亮運作")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "REVON Touge Drive Telemetry Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun stopUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager?.unregisterListener(this)
        releaseWakeLock()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    companion object {
        private const val CHANNEL_ID = "touge_gps_channel"
        private const val NOTIFICATION_ID = 8844
    }
}
