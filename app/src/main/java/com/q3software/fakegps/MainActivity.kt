package com.q3software.fakegps

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.q3software.fakegps.databinding.ActivityMainBinding
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mockService: MockLocationService? = null
    private var isBound = false
    private var selectedPoint: GeoPoint? = null
    private var currentMarker: Marker? = null
    private val prefs by lazy { getSharedPreferences("fakegps_prefs", Context.MODE_PRIVATE) }

    companion object {
        const val PERM_REQUEST = 100
    }

    // --- Conexión con el servicio ---
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as MockLocationService.LocalBinder
            mockService = b.getService()
            isBound = true
            updateUI()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            mockService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar OSMDroid
        Configuration.getInstance().load(this, prefs)
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMap()
        setupUI()
        checkPermissions()
        loadFavorites()
    }

    private fun setupMap() {
        binding.map.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(40.4168, -3.7038)) // Madrid por defecto
        }

        // Tap en el mapa para seleccionar punto
        val receiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                selectPoint(p)
                return true
            }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }
        binding.map.overlays.add(MapEventsOverlay(receiver))
    }

    private fun setupUI() {
        // Botón activar/desactivar
        binding.btnToggle.setOnClickListener {
            if (mockService?.isRunning == true) {
                stopMock()
            } else {
                startMock()
            }
        }

        // Botón guardar favorito
        binding.btnSaveFavorite.setOnClickListener {
            saveFavorite()
        }

        // Búsqueda por coordenadas
        binding.etCoords.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                parseAndGoToCoords(binding.etCoords.text.toString())
                true
            } else false
        }
        // Botón para ir a las coordenadas
        binding.btnGoCoords.setOnClickListener {
            parseAndGoToCoords(binding.etCoords.text.toString())
        }

        // Timer
        binding.sliderTimer.addOnChangeListener { _, value, _ ->
            val mins = value.toInt()
            binding.tvTimer.text = if (mins == 0) "Sin límite de tiempo" else "Desactivar en $mins min"
        }

        updateUI()
    }

    private fun selectPoint(point: GeoPoint) {
        selectedPoint = point

        // Actualizar marcador
        currentMarker?.let { binding.map.overlays.remove(it) }
        currentMarker = Marker(binding.map).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Ubicación simulada"
        }
        binding.map.overlays.add(currentMarker)
        binding.map.invalidate()

        // Mostrar coordenadas
        binding.tvSelectedCoords.text = "%.5f, %.5f".format(point.latitude, point.longitude)
        binding.btnToggle.isEnabled = true
        binding.btnSaveFavorite.isEnabled = true
    }

    private fun startMock() {
        val point = selectedPoint ?: run {
            Toast.makeText(this, "Selecciona un punto en el mapa", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isMockLocationEnabled()) {
            showMockLocationDialog()
            return
        }

        val timerMins = binding.sliderTimer.value.toInt()

        val intent = Intent(this, MockLocationService::class.java).apply {
            putExtra("lat", point.latitude)
            putExtra("lng", point.longitude)
            putExtra("timer_mins", timerMins)
        }
        ContextCompat.startForegroundService(this, intent)

        if (!isBound) {
            bindService(Intent(this, MockLocationService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        }

        updateUI(running = true)
        Toast.makeText(this, "📍 Ubicación simulada activada", Toast.LENGTH_SHORT).show()
    }

    private fun stopMock() {
        stopService(Intent(this, MockLocationService::class.java))
        updateUI(running = false)
        Toast.makeText(this, "Ubicación simulada desactivada", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI(running: Boolean = mockService?.isRunning ?: false) {
        if (running) {
            binding.btnToggle.text = "⏹ Desactivar Mock"
            binding.btnToggle.setBackgroundColor(getColor(R.color.red_stop))
            binding.tvStatus.text = "🟢 Mock ACTIVO"
            binding.tvStatus.setTextColor(getColor(R.color.green_active))
        } else {
            binding.btnToggle.text = "▶ Activar Mock"
            binding.btnToggle.setBackgroundColor(getColor(R.color.blue_primary))
            binding.tvStatus.text = "⚪ Mock INACTIVO"
            binding.tvStatus.setTextColor(getColor(R.color.gray_inactive))
            binding.btnToggle.isEnabled = selectedPoint != null
        }
    }

    // --- Favoritos ---
    private fun saveFavorite() {
        val point = selectedPoint ?: return
        val name = binding.etFavoriteName.text.toString().ifBlank {
            "Favorito ${System.currentTimeMillis() % 1000}"
        }
        val key = "fav_${System.currentTimeMillis()}"
        prefs.edit().putString(key, "$name|${point.latitude}|${point.longitude}").apply()
        loadFavorites()
        Toast.makeText(this, "Favorito guardado: $name", Toast.LENGTH_SHORT).show()
    }

    private fun loadFavorites() {
        binding.chipGroupFavorites.removeAllViews()
        prefs.all.filter { it.key.startsWith("fav_") }.forEach { (key, value) ->
            val parts = (value as String).split("|")
            if (parts.size == 3) {
                val chip = Chip(this).apply {
                    text = parts[0]
                    isCloseIconVisible = true
                    setOnClickListener {
                        val p = GeoPoint(parts[1].toDouble(), parts[2].toDouble())
                        selectPoint(p)
                        binding.map.controller.animateTo(p)
                    }
                    setOnCloseIconClickListener {
                        prefs.edit().remove(key).apply()
                        loadFavorites()
                    }
                }
                binding.chipGroupFavorites.addView(chip)
            }
        }
    }

    // --- Coordenadas manuales ---
    private fun parseAndGoToCoords(input: String) {
        try {
            val parts = input.split(",").map { it.trim() }
            if (parts.size == 2) {
                val lat = parts[0].toDouble()
                val lng = parts[1].toDouble()
                val point = GeoPoint(lat, lng)
                selectPoint(point)
                binding.map.controller.animateTo(point)
            } else {
                Toast.makeText(this, "Formato: latitud, longitud", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Coordenadas inválidas", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Checks ---
    private fun isMockLocationEnabled(): Boolean {
        return try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
                lm.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false,
                true, true, true, 0, 5
            )
            lm.removeTestProvider(LocationManager.GPS_PROVIDER)
            true
        } catch (se: SecurityException) {
            false
        } catch (e: Exception) {
            true
        }
    }

    private fun showMockLocationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Activar ubicación simulada")
            .setMessage("Ve a Ajustes → Opciones de desarrollador → Seleccionar aplicación de ubicación simulada → elige FakeGPS")
            .setPositiveButton("Abrir ajustes") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun checkPermissions() {
        val perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERM_REQUEST)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
        if (!isBound) {
            bindService(Intent(this, MockLocationService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
