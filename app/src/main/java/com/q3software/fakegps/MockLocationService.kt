package com.q3software.fakegps

import android.app.*
import android.content.Intent
import android.location.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class MockLocationService : Service() {

    private val binder = LocalBinder()
    var isRunning = false
        private set

    private var mockLat = 0.0
    private var mockLng = 0.0
    private var timerMins = 0

    private lateinit var locationManager: LocationManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "fakegps_channel"
        const val NOTIF_ID = 1
        val PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )
    }

    inner class LocalBinder : Binder() {
        fun getService(): MockLocationService = this@MockLocationService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mockLat = intent?.getDoubleExtra("lat", 0.0) ?: 0.0
        mockLng = intent?.getDoubleExtra("lng", 0.0) ?: 0.0
        timerMins = intent?.getIntExtra("timer_mins", 0) ?: 0

        startForeground(NOTIF_ID, buildNotification())
        startMocking()

        return START_STICKY
    }

    private fun startMocking() {
        isRunning = true

        // Registrar proveedores de prueba
        PROVIDERS.forEach { provider ->
            try {
                locationManager.addTestProvider(
                    provider,
                    false, false, false, false,
                    true, true, true,
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
                locationManager.setTestProviderEnabled(provider, true)
            } catch (e: Exception) {
                Log.w("FakeGPS", "No se pudo añadir proveedor $provider: ${e.message}")
            }
        }

        // Enviar ubicaciones falsas cada 500ms
        scope.launch {
            val startTime = System.currentTimeMillis()
            val limitMs = if (timerMins > 0) timerMins * 60 * 1000L else Long.MAX_VALUE

            while (isActive && isRunning) {
                if (System.currentTimeMillis() - startTime >= limitMs) {
                    stopSelf()
                    break
                }
                pushLocation()
                delay(500)
            }
        }
    }

    private fun pushLocation() {
        PROVIDERS.forEach { provider ->
            try {
                val loc = Location(provider).apply {
                    latitude = mockLat
                    longitude = mockLng
                    altitude = 0.0
                    accuracy = 1.0f
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        verticalAccuracyMeters = 1.0f
                    }
                }
                locationManager.setTestProviderLocation(provider, loc)
            } catch (e: Exception) {
                Log.e("FakeGPS", "Error enviando ubicación para $provider: ${e.message}")
            }
        }
    }

    private fun stopMocking() {
        isRunning = false
        scope.coroutineContext.cancelChildren()
        PROVIDERS.forEach { provider ->
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) {
                Log.w("FakeGPS", "Error removiendo proveedor $provider: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        stopMocking()
        scope.cancel()
        super.onDestroy()
    }

    // --- Notificación persistente ---
    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MockLocationService::class.java).apply { action = "STOP" },
            PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle("FakeGPS Activo")
            .setContentText("📍 %.5f, %.5f".format(mockLat, mockLng))
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_stop, "Detener", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FakeGPS Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servicio de ubicación simulada"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
