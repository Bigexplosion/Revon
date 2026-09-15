package com.example.qstart

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import com.google.android.gms.location.*
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Locale
import kotlin.math.sqrt

class CircuitFragment : Fragment(), SensorEventListener {

    data class TrackInfo(
        val name: String, 
        val points: List<GeoPoint>, 
        val startPoint: GeoPoint, 
        val endPoint: GeoPoint,
        val diagonalMeters: Double = 0.0,
        val isLoop: Boolean = false, // 新增：是否為環狀賽道
        val pitPolygon: List<GeoPoint>? = null,
        val pitEntrance: GeoPoint? = null,
        val pitEntranceRadius: Double = 15.0,
        val pitExit: GeoPoint? = null,
        val pitExitRadius: Double = 15.0
    )

    private var map: MapView? = null
    private lateinit var speedText: TextView
    private lateinit var timerText: TextView
    private var gpsAccuracyText: TextView? = null
    private lateinit var btnFollowMe: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var btnStopTiming: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var btnManualMode: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager
    private lateinit var trackManager: TrackManager
    private lateinit var sensorManager: SensorManager
    private var gravitySensor: Sensor? = null
    private var accelSensor: Sensor? = null
    
    private var isTiming = false
    private var startTime = 0L
    private var lastFinishTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var lastUserPoint: GeoPoint? = null
    private var userLocationMarker: Marker? = null
    private var predictedMarker: Marker? = null
    private var isFollowMode = true 
    private var isPinching = false
    private var lastZoomTime = 0L
    private var isFirstLocationFix = true // 新增：紀錄是否為進入後的第一次定位
    private var isManualMode = false 
    private val currentTrackPoints = mutableListOf<TrackPoint>()
    private val sessionLaps = mutableListOf<TrackManager.SessionLap>() // 修改：儲存帶有完賽狀態的圈數
    private val dynamicOverlays = mutableListOf<Polyline>() 
    private var lastAutoSaveTime = 0L 
    private var isInPit = false
    private var btnResetLean: com.google.android.material.floatingactionbutton.FloatingActionButton? = null

    // 近期 GPS 速度樣本（用於校正們測判斷，降低喪變誤觸）
    private val recentSpeeds = ArrayDeque<Float>(5)

    // --- 起點終點偵錯相關 ---
    private val debugBuffer = mutableListOf<JSONObject>()
    private var isInsideDebugZone = false
    private var closestDebugDist = Double.MAX_VALUE
    private var lastDebugPoint: GeoPoint? = null

    // G-force & Lean logic
    private var currentRunDistance = 0.0
    private val sensorAlpha = 0.08f 
    private var filteredG = floatArrayOf(0f, 0f, 0f)
    private var lastRawAccel = floatArrayOf(0f, 0f, 0f)
    private var lastGravity = floatArrayOf(0f, 0f, 9.8f) // 姿態補償關鍵：重力基準

    private var currentGForce = 0.0
    private var currentAccelG = 0.0
    private var currentBrakingG = 0.0
    private var currentRightW = doubleArrayOf(1.0, 0.0, 0.0) // 用於儲存當下車身橫向軸向量
    private var currentLeanAngle = 0.0
    private var currentLatG = 0.0 // 新增：側向 G 力

    // Sensor Fusion 變數
    private var gyroSensor: Sensor? = null
    private var lastGyroTime = 0L
    private var dynamicPitch = 0.0
    private var lastGpsSpeedKmh = 0.0
    private var lastGpsTime = 0L
    private var fusedLongG = 0.0
    private var lastGpsAccel = 0.0
    private var currentImuG = 0.0

    private var currentSlopeBiasG = 0.0
    private var allTracks = mutableListOf<TrackInfo>()
    private var activeTrack: TrackInfo? = null
    
    // 逆向偵測相關
    private var lastTrackIndex = -1
    private var wrongWayStartTime = 0L

    // 動態速域：紀錄本次 Session 的速度極值
    private var sessionSpeedMin = Double.MAX_VALUE
    private var sessionSpeedMax = 0.0

    // SharedPreferences 監聽器：當 SettingsFragment 清除校正資料時，同步更新按鈕顏色
    private val calibPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "gravity_x" || key == "gravity_y" || key == "gravity_z") {
            if (isAdded) handler.post { updateCalibBtnColor() }
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            if (!isAdded || map == null) return
            locationResult.lastLocation?.let {
                // 添加近期速度樣本
                recentSpeeds.addLast(it.speed)
                if (recentSpeeds.size > 5) recentSpeeds.removeFirst()
                handleNewLocation(it)
            }
        }
    }

    private val nativeListener = LocationListener {
        recentSpeeds.addLast(it.speed)
        if (recentSpeeds.size > 5) recentSpeeds.removeFirst()
        handleNewLocation(it)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        org.osmdroid.config.Configuration.getInstance().load(requireContext(), requireContext().getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        org.osmdroid.config.Configuration.getInstance().userAgentValue = requireContext().packageName
        return inflater.inflate(R.layout.fragment_circuit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // --- 核心修正：避免 Fragment 重建 View 時產生殘留問題 ---
        isFirstLocationFix = true 
        isFollowMode = true 
        userLocationMarker = null
        predictedMarker = null
        activePathOverlay = null
        dynamicOverlays.clear()
        
        // 檢查是否需要顯示校正教學
        checkFirstRunCalibration()

        val mapView = view.findViewById<MapView>(R.id.map)
        map = mapView
        speedText = view.findViewById(R.id.speedText)
        timerText = view.findViewById(R.id.timerText)
        gpsAccuracyText = view.findViewById(R.id.gpsAccuracyText)
        btnFollowMe = view.findViewById(R.id.btnFollowMe)
        btnStopTiming = view.findViewById(R.id.btnStopTiming)
        btnManualMode = view.findViewById(R.id.btnManualMode)
        
        btnResetLean = view.findViewById(R.id.btnResetLean)
        btnResetLean?.setOnClickListener {
            startCalibration()
        }

        btnManualMode.setOnClickListener {
            isManualMode = !isManualMode
            if (isManualMode) {
                btnManualMode.supportImageTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.race_accent_cyan))
                Toast.makeText(requireContext(), "已開啟手動模式", Toast.LENGTH_SHORT).show()
                btnStopTiming.visibility = View.VISIBLE
                btnStopTiming.setImageResource(android.R.drawable.ic_media_play)
            } else {
                btnManualMode.supportImageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#8E8E93"))
                Toast.makeText(requireContext(), "已回歸自動模式", Toast.LENGTH_SHORT).show()
                if (!isTiming) btnStopTiming.visibility = View.GONE
                btnStopTiming.setImageResource(android.R.drawable.ic_media_pause)
            }
        }

        btnStopTiming.setOnClickListener {
            if (isTiming) {
                if (isManualMode) {
                    finishRun()
                } else {
                    abortRun("使用者手動停止紀錄")
                }
            } else if (isManualMode) {
                startNewRun()
                btnStopTiming.setImageResource(android.R.drawable.ic_media_pause)
            }
        }
        
        loadAllTracks()
        
        GpsConfig.loadGravityBaseline(requireContext())
        updateCalibBtnColor()
        trackManager = TrackManager(requireContext())
        checkForRecovery()
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val rawAccel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val navView = activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
        if (navView?.selectedItemId != R.id.nav_circuit) {
            navView?.setOnItemSelectedListener(null)
            navView?.selectedItemId = R.id.nav_circuit
            navView?.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_drag -> { (activity as? MainActivity)?.showFragment("drag"); true }
                    R.id.nav_circuit -> { (activity as? MainActivity)?.showFragment("circuit"); true }
                    R.id.nav_history -> { (activity as? MainActivity)?.showFragment("history"); true }
                    R.id.nav_tools -> { (activity as? MainActivity)?.showFragment("settings"); true }
                    else -> false
                }
            }
        }
        setupMapView(mapView)
        requestLocationUpdates()
        startTimerUpdate()
        
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        // 核心修正：即便有線性加速，也必須註冊原始加速計以供偵錯日誌紀錄
        rawAccel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    private fun checkFirstRunCalibration() {
        if (GpsConfig.gravityBaseline == null) {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_calibration_guide, null)
            val builder = AlertDialog.Builder(requireContext())
            builder.setView(dialogView)
            val dialog = builder.create()
            
            dialogView.findViewById<View>(R.id.btnStartCalibrationGuide).setOnClickListener {
                dialog.dismiss()
                val settingsFrag = SettingsFragment().apply {
                    arguments = Bundle().apply {
                        putBoolean("highlight_calibration", true)
                    }
                }
                val navView = activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
                navView?.setOnItemSelectedListener(null)
                navView?.selectedItemId = R.id.nav_tools
                navView?.setOnItemSelectedListener { item ->
                    when (item.itemId) {
                        R.id.nav_drag -> { (activity as? MainActivity)?.showFragment("drag"); true }
                        R.id.nav_circuit -> { (activity as? MainActivity)?.showFragment("circuit"); true }
                        R.id.nav_history -> { (activity as? MainActivity)?.showFragment("history"); true }
                        R.id.nav_tools -> { (activity as? MainActivity)?.showFragment("settings"); true }
                        else -> false
                    }
                }
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, settingsFrag)
                    .addToBackStack(null)
                    .commit()
            }
            dialog.show()
        }
    }

    private var isCalibrating = false
    private var calibrationSamples = mutableListOf<FloatArray>()
    private var calibrationStartTime = 0L

    private fun startCalibration() {
        // 取最近最多 3 個樣本的平均速度（降低喪變誤觸）
        val samples = recentSpeeds.takeLast(3)
        val avgSpeed = if (samples.isNotEmpty()) samples.average().toFloat() else (lastLocationObject?.speed ?: 0f)
        if (avgSpeed > 0.5f) {
            Toast.makeText(requireContext(), "請確保車輛是停止狀態並且車身直立", Toast.LENGTH_LONG).show()
            return
        }

        isCalibrating = true
        calibrationSamples.clear()
        calibrationStartTime = System.currentTimeMillis()
        Toast.makeText(requireContext(), "正在校正姿態 (請保持靜止 2 秒)...", Toast.LENGTH_SHORT).show()
    }

    private fun updateCalibBtnColor() {
        val btn = btnResetLean ?: return
        val baseline = GpsConfig.gravityBaseline
        val tint = when {
            baseline == null       -> android.content.res.ColorStateList.valueOf(Color.parseColor("#FF5252")) // 從未校正 - 紅
            !GpsConfig.isBaselineFresh -> android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD600")) // 前次紀錄 - 黃
            else                   -> android.content.res.ColorStateList.valueOf(Color.WHITE) // 本次校正完成 - 白
        }
        btn.backgroundTintList = tint
    }

    private fun loadAllTracks() {
        allTracks.clear()
        try {
            // 1. 載入 Assets 內建賽道
            val files = requireContext().assets.list("") ?: return
            for (fileName in files) {
                if (fileName.endsWith(".json")) {
                    if (fileName == "freeform_resolutions.json" || fileName == "pit_lane_geojson_example.json") continue
                    loadTrackFile(fileName, fileName.substringBeforeLast("."), isAsset = true)
                }
            }
            // 2. 載入本地下載/更新的雲端賽道 (覆蓋內建)
            val tracksDir = File(requireContext().filesDir, "tracks")
            if (tracksDir.exists()) {
                val localFiles = tracksDir.listFiles { _, name -> name.endsWith(".json") } ?: arrayOf()
                for (file in localFiles) {
                    loadTrackFile(file.absolutePath, file.name.substringBeforeLast("."), isAsset = false)
                }
            }
        } catch (e: Exception) {}
    }

    private fun loadTrackFile(sourcePath: String, trackName: String, isAsset: Boolean) {
        try {
            val jsonString = if (isAsset) {
                requireContext().assets.open(sourcePath).bufferedReader().use { it.readText() }
            } else {
                File(sourcePath).readText()
            }
            val points = mutableListOf<GeoPoint>()
            var pitPoly: List<GeoPoint>? = null
            var pitEntrancePt: GeoPoint? = null
            var pitEntranceRad = 15.0
            var pitExitPt: GeoPoint? = null
            var pitExitRad = 15.0

            if (jsonString.trim().startsWith("{")) {
                val geoJson = JSONObject(jsonString)
                val features = geoJson.optJSONArray("features")
                if (features != null) {
                    for (f in 0 until features.length()) {
                        val feature = features.getJSONObject(f)
                        val geometry = feature.getJSONObject("geometry")
                        val properties = feature.optJSONObject("properties") ?: JSONObject()
                        val typeProp = properties.optString("type", "")
                        val geomType = geometry.getString("type")

                        if (geomType == "LineString") {
                            val coords = geometry.getJSONArray("coordinates")
                            for (i in 0 until coords.length()) {
                                val c = coords.getJSONArray(i)
                                points.add(GeoPoint(c.getDouble(1), c.getDouble(0), if (c.length() > 2) c.getDouble(2) else 0.0))
                            }
                        } else if (geomType == "Polygon" && typeProp == "pit_zone") {
                            val coordsOuter = geometry.getJSONArray("coordinates")
                            if (coordsOuter.length() > 0) {
                                val coords = coordsOuter.getJSONArray(0)
                                val polyPoints = mutableListOf<GeoPoint>()
                                for (i in 0 until coords.length()) {
                                    val c = coords.getJSONArray(i)
                                    polyPoints.add(GeoPoint(c.getDouble(1), c.getDouble(0)))
                                }
                                pitPoly = polyPoints
                            }
                        } else if (geomType == "Point" && typeProp == "pit_entrance") {
                            val c = geometry.getJSONArray("coordinates")
                            pitEntrancePt = GeoPoint(c.getDouble(1), c.getDouble(0))
                            pitEntranceRad = properties.optDouble("radius", 15.0)
                        } else if (geomType == "Point" && typeProp == "pit_exit") {
                            val c = geometry.getJSONArray("coordinates")
                            pitExitPt = GeoPoint(c.getDouble(1), c.getDouble(0))
                            pitExitRad = properties.optDouble("radius", 15.0)
                        }
                    }
                }
            } else {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    points.add(GeoPoint(obj.getDouble("lat"), obj.getDouble("lng"), obj.optDouble("alt", 0.0)))
                }
            }
            if (points.isNotEmpty()) {
                val pStart = points.first(); val pEnd = points.last()
                
                // 計算賽道規模 (對角線長度)
                val box = org.osmdroid.util.BoundingBox.fromGeoPoints(points)
                val diagMeters = GeoPoint(box.latNorth, box.lonWest).distanceToAsDouble(GeoPoint(box.latSouth, box.lonEast))
                
                // 判斷是否為環狀賽道 (起終點距離 < 20m)
                val isLoop = pStart.distanceToAsDouble(pEnd) < 20.0
                
                // 覆蓋同名賽道：先將 existing 的 TrackInfo 移除
                allTracks.removeAll { it.name.startsWith(trackName) }
                
                if (isLoop) {
                    // 環狀賽道：不分上下山，直接使用原名
                    allTracks.add(TrackInfo(trackName, points, pStart, pEnd, diagMeters, true, pitPoly, pitEntrancePt, pitEntranceRad, pitExitPt, pitExitRad))
                } else {
                    // 點對點賽道：區分上下山
                    if (trackName.endsWith("_上山") || trackName.endsWith("_下山") ||
                        trackName.endsWith("_順時針") || trackName.endsWith("_逆時針") ||
                        trackName.endsWith("_單向") || trackName.endsWith("_不分") ||
                        trackName.endsWith("_全段")) {
                        // 如果檔名已明確指定方向或不需拆分，直接使用檔名，不再重複切割後綴
                        allTracks.add(TrackInfo(trackName, points, pStart, pEnd, diagMeters, false, pitPoly, pitEntrancePt, pitEntranceRad, pitExitPt, pitExitRad))
                    } else {
                        // 否則自動分割為上下山兩個方向
                        val (n1, n2) = if (pStart.altitude < pEnd.altitude) 
                            Pair("${trackName}_上山", "${trackName}_下山") 
                        else 
                            Pair("${trackName}_下山", "${trackName}_上山")
                            
                        allTracks.add(TrackInfo(n1, points, pStart, pEnd, diagMeters, false, pitPoly, pitEntrancePt, pitEntranceRad, pitExitPt, pitExitRad))
                        allTracks.add(TrackInfo(n2, points.reversed(), pEnd, pStart, diagMeters, false, pitPoly, pitEntrancePt, pitEntranceRad, pitExitPt, pitExitRad))
                    }
                }
            }
        } catch (e: Exception) {}
    }

    private var referenceTracksFolder = org.osmdroid.views.overlay.FolderOverlay()
    private var summaryMarkersFolder = org.osmdroid.views.overlay.FolderOverlay()
    
    // 用於存取每個賽道對應的 Overlay 實體，以便動態調整
    private class TrackOverlayGroup(
        val polyline: Polyline, 
        val markers: List<Marker>, 
        val summaryMarker: Marker?, 
        val diagMeters: Double,
        val pitPolygonOverlay: org.osmdroid.views.overlay.Polygon? = null
    )
    private val trackOverlayMap = mutableMapOf<String, TrackOverlayGroup>()

    private fun setupMapView(mapView: MapView) {
        referenceTracksFolder = org.osmdroid.views.overlay.FolderOverlay()
        summaryMarkersFolder = org.osmdroid.views.overlay.FolderOverlay()
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setLayerType(if (GpsConfig.isHighFpsEnabled(requireContext())) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_SOFTWARE, null)

        if (GpsConfig.getMapStyle(requireContext()) == GpsConfig.MAP_STYLE_DARK) {
            val matrix = ColorMatrix(); matrix.setSaturation(0f); val scale = 0.5f
            matrix.postConcat(ColorMatrix(floatArrayOf(scale,0f,0f,0f,0f, 0f,scale,0f,0f,0f, 0f,0f,scale,0f,0f, 0f,0f,0f,1f,0f)))
            mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
        }

        // --- 優先嘗試使用定位點，若無定位點才退回預設顯示全台灣 ---
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnCompleteListener { task ->
                val loc = task.result
                if (loc != null) {
                    mapView.controller.setZoom(16.5)
                    mapView.controller.setCenter(org.osmdroid.util.GeoPoint(loc.latitude, loc.longitude))
                } else {
                    mapView.post {
                        val taiwan = org.osmdroid.util.BoundingBox(25.9, 122.6, 21.6, 119.5)
                        mapView.zoomToBoundingBox(taiwan, false, 0)
                    }
                }
            }
        } else {
            mapView.post {
                val taiwan = org.osmdroid.util.BoundingBox(25.9, 122.6, 21.6, 119.5)
                mapView.zoomToBoundingBox(taiwan, false, 0)
            }
        }

        mapView.addMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(e: org.osmdroid.events.ScrollEvent?): Boolean = true
            override fun onZoom(e: org.osmdroid.events.ZoomEvent?): Boolean { 
                lastZoomTime = System.currentTimeMillis()
                updateMapLOD()
                return true 
            }
        })

        var startX = 0f
        var startY = 0f
        var wasPinching = false
        mapView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    isPinching = false
                    wasPinching = false
                }
                android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                    isPinching = true
                    wasPinching = true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (!isPinching && !wasPinching && isFollowMode) {
                        val dx = event.x - startX
                        val dy = event.y - startY
                        if (kotlin.math.sqrt((dx * dx + dy * dy).toDouble()) > 20.0) {
                            isFollowMode = false
                            btnFollowMe.alpha = 0.4f
                        }
                    }
                }
                android.view.MotionEvent.ACTION_POINTER_UP -> {
                    isPinching = false
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (wasPinching) {
                        lastZoomTime = System.currentTimeMillis()
                    }
                    isPinching = false
                    wasPinching = false
                }
            }
            false
        }

        btnFollowMe.setOnClickListener { 
            isFollowMode = true 
            btnFollowMe.alpha = 1.0f 
            lastUserPoint?.let { 
                val currentZoom = map?.zoomLevelDouble ?: 16.5
                val targetZoom = if (currentZoom >= 16.5) currentZoom else 16.5
                map?.controller?.animateTo(it, targetZoom, 800L) 
            } 
        }

        mapView.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                closeAllMapInfoWindows(map)
                return false
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }))
        
        mapView.overlays.add(referenceTracksFolder)
        mapView.overlays.add(summaryMarkersFolder)
        drawAllReferenceTracks()
        updateMapLOD()
    }

    private fun updateMapLOD() {
        val currentMap = map ?: return
        
        // 使用通用公式計算當前縮放層級下的像素解析度 (Meters Per Pixel)
        val zoom = currentMap.zoomLevelDouble
        val lat = currentMap.mapCenter.latitude
        val mpp = 156543.03392 * kotlin.math.cos(Math.toRadians(lat)) / Math.pow(2.0, zoom)
        
        trackOverlayMap.forEach { (name, group) ->
            val sizeInPixels = group.diagMeters / mpp
            // 提高展開倍率 (從 120 提高到 200)
            val showAsPath = sizeInPixels > 200.0
            
            group.polyline.isEnabled = showAsPath
            group.markers.forEach { it.isEnabled = showAsPath } // 同步控制所有端點 Marker
            group.pitPolygonOverlay?.isEnabled = showAsPath
            group.summaryMarker?.isEnabled = !showAsPath
        }
        
        currentMap.invalidate()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            map?.onResume()
            map?.let { currentMap ->
                currentMap.setLayerType(if (GpsConfig.isHighFpsEnabled(requireContext())) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_SOFTWARE, null)
                if (GpsConfig.getMapStyle(requireContext()) == GpsConfig.MAP_STYLE_DARK) {
                    val matrix = ColorMatrix(); matrix.setSaturation(0f); val scale = 0.5f
                    matrix.postConcat(ColorMatrix(floatArrayOf(scale,0f,0f,0f,0f, 0f,scale,0f,0f,0f, 0f,0f,scale,0f,0f, 0f,0f,0f,1f,0f)))
                    currentMap.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
                } else {
                    currentMap.overlayManager.tilesOverlay.setColorFilter(null)
                }
                drawAllReferenceTracks()
                
                userLocationMarker?.let { if (!currentMap.overlays.contains(it)) currentMap.overlays.add(it) }
                predictedMarker?.let { if (!currentMap.overlays.contains(it)) currentMap.overlays.add(it) }
                
                currentMap.invalidate()
            }
        } else {
            map?.onPause()
        }
    }

    private fun closeAllMapInfoWindows(mapView: MapView?) {
        val currentMap = mapView ?: return
        fun closeRecursive(overlay: org.osmdroid.views.overlay.Overlay) {
            if (overlay is Marker) {
                overlay.closeInfoWindow()
            } else if (overlay is org.osmdroid.views.overlay.FolderOverlay) {
                val items = try { overlay.items.toList() } catch (e: Exception) { overlay.items }
                for (item in items) {
                    closeRecursive(item)
                }
            }
        }
        val overlays = try { currentMap.overlays.toList() } catch (e: Exception) { currentMap.overlays }
        for (overlay in overlays) {
            closeRecursive(overlay)
        }
    }

    private fun drawAllReferenceTracks() {
        val currentMap = map ?: return
        try {
            closeAllMapInfoWindows(currentMap)
            referenceTracksFolder.items.clear()
            summaryMarkersFolder.items.clear()
            trackOverlayMap.clear() // 清空舊的對照表
        } catch (e: Exception) { return }

        val isDarkMode = GpsConfig.getMapStyle(requireContext()) == GpsConfig.MAP_STYLE_DARK
        val infoWindowLayout = if (isDarkMode) R.layout.custom_info_window else R.layout.custom_info_window_light
        val customBubble = org.osmdroid.views.overlay.infowindow.MarkerInfoWindow(infoWindowLayout, currentMap)
        val drawnBaseNames = mutableSetOf<String>()
        allTracks.forEach { track ->
            val baseName = track.name.substringBefore("_")
            if (!drawnBaseNames.contains(baseName)) {
                // 1. 建立 Polyline (路徑)
                val polyline = Polyline()
                track.points.forEach { polyline.addPoint(it) }
                polyline.outlinePaint.color = GpsConfig.getTrackColor(requireContext())
                polyline.outlinePaint.strokeWidth = 15f
                polyline.outlinePaint.strokeCap = Paint.Cap.ROUND
                referenceTracksFolder.add(polyline)

                // 2. 建立 Summary Marker (圓點)
                var summaryMarker: Marker? = null
                if (track.points.isNotEmpty()) {
                    summaryMarker = Marker(map).apply {
                        position = track.points[track.points.size / 2]
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createCircleMarker(Color.parseColor("#00E5FF"), 60, false).toDrawable(resources)
                        title = baseName
                        setOnMarkerClickListener { _, _ -> 
                            val relatedPoints = allTracks.filter { it.name.startsWith(baseName) }.flatMap { it.points }
                            if (relatedPoints.isNotEmpty()) {
                                val box = org.osmdroid.util.BoundingBox.fromGeoPoints(relatedPoints)
                                map?.zoomToBoundingBox(box, true, 100)
                            }
                            true 
                        }
                    }
                    summaryMarkersFolder.add(summaryMarker)
                }
                
                // 3. 建立端點 Markers (起點與終點)
                val isLoop = track.startPoint.distanceToAsDouble(track.endPoint) < 20.0
                
                val startMarker = Marker(map).apply {
                    position = track.startPoint; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createCircleMarker(Color.parseColor("#00E5FF"), 40, true).toDrawable(resources)
                    
                    val direction = if (isLoop) "" else if (track.name.contains("_")) " " + track.name.substringAfterLast("_") else ""
                    title = "${baseName}${direction}"
                    snippet = "海拔: ${track.startPoint.altitude.toInt()}m"
                    infoWindow = customBubble
                    
                    setOnMarkerClickListener { m, _ -> closeAllMapInfoWindows(map); m.showInfoWindow(); true }
                }
                referenceTracksFolder.add(startMarker)
                
                val markers = mutableListOf(startMarker)
                
                // 如果不是環狀賽道 (起終點距離大於 20 米)，則額外標註終點
                if (!isLoop) {
                    val endMarker = Marker(map).apply {
                        position = track.endPoint; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createCircleMarker(Color.parseColor("#00E5FF"), 40, true).toDrawable(resources)
                        
                        val oppositeDir = if (track.name.endsWith("上山")) "下山" else "上山"
                        title = "${baseName} ${oppositeDir}"
                        snippet = "海拔: ${track.endPoint.altitude.toInt()}m"
                        infoWindow = customBubble

                        setOnMarkerClickListener { m, _ -> closeAllMapInfoWindows(map); m.showInfoWindow(); true }
                    }
                    referenceTracksFolder.add(endMarker)
                    markers.add(endMarker)
                }

                // 4. 建立 Pit 區 Overlays
                var pitPolyOverlay: org.osmdroid.views.overlay.Polygon? = null
                if (track.pitPolygon != null && track.pitPolygon.isNotEmpty()) {
                    pitPolyOverlay = org.osmdroid.views.overlay.Polygon().apply {
                        points = track.pitPolygon
                        fillPaint.color = Color.argb(45, 255, 152, 0) // 半透明橘色
                        outlinePaint.color = Color.parseColor("#FF9800")
                        outlinePaint.strokeWidth = 4f
                        title = "${baseName} Pit 區範圍"
                    }
                    referenceTracksFolder.add(pitPolyOverlay)
                }

                if (track.pitEntrance != null) {
                    val entranceMarker = Marker(map).apply {
                        position = track.pitEntrance
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createCircleMarker(Color.parseColor("#FFC107"), 35, false).toDrawable(resources)
                        title = "Pit 進場點"
                        snippet = "偵測半徑: ${track.pitEntranceRadius}m"
                        infoWindow = customBubble
                        setOnMarkerClickListener { m, _ -> closeAllMapInfoWindows(map); m.showInfoWindow(); true }
                    }
                    referenceTracksFolder.add(entranceMarker)
                    markers.add(entranceMarker)
                }

                if (track.pitExit != null) {
                    val exitMarker = Marker(map).apply {
                        position = track.pitExit
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createCircleMarker(Color.parseColor("#4CAF50"), 35, false).toDrawable(resources)
                        title = "Pit 出場點"
                        snippet = "偵測半徑: ${track.pitExitRadius}m"
                        infoWindow = customBubble
                        setOnMarkerClickListener { m, _ -> closeAllMapInfoWindows(map); m.showInfoWindow(); true }
                    }
                    referenceTracksFolder.add(exitMarker)
                    markers.add(exitMarker)
                }

                // 紀錄進對照表
                trackOverlayMap[baseName] = TrackOverlayGroup(polyline, markers, summaryMarker, track.diagonalMeters, pitPolyOverlay)
                
                drawnBaseNames.add(baseName)
            }
        }
        updateMapLOD() // 繪製完後立即計算初始 LOD
    }

    private fun createCircleMarker(color: Int, size: Int, hollow: Boolean): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        b.applyCanvas {
            val p = Paint(Paint.ANTI_ALIAS_FLAG); p.color = color
            if (hollow) { p.style = Paint.Style.STROKE; p.strokeWidth = size/4f; drawCircle(size/2f, size/2f, size/3f, p) }
            else { drawCircle(size/2f, size/2f, size/2f, p); p.color = Color.WHITE; drawCircle(size/2f, size/2f, size/4f, p) }
        }
        return b
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        
        // 嘗試取得最後已知位置，讓地圖能瞬間切換到用戶附近，大幅減少等待感
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null && isFirstLocationFix && isAdded && map != null) {
                handleNewLocation(loc)
            }
        }

        if (GpsConfig.getMode(requireContext()) == GpsConfig.MODE_TEST) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, nativeListener)
        } else {
            fusedLocationClient.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500).build(), locationCallback, Looper.getMainLooper())
        }
    }

    private var lastToastTime = 0L

    private fun handleNewLocation(location: Location) {
        if (!isAdded || map == null) return

        if (location.hasAccuracy()) {
            val acc = location.accuracy.toInt()
            gpsAccuracyText?.text = String.format(Locale.getDefault(), "GPS誤差: %d m", acc)
            if (acc > 20) {
                gpsAccuracyText?.setTextColor(Color.parseColor("#FF5252"))
            } else {
                gpsAccuracyText?.setTextColor(Color.parseColor("#8E8E93"))
            }
        } else {
            gpsAccuracyText?.text = "GPS誤差: -- m"
            gpsAccuracyText?.setTextColor(Color.parseColor("#8E8E93"))
        }
        
        // --- GPS 精度過濾 (Risk A) ---
        if (location.hasAccuracy() && location.accuracy > 20.0f) {
            val now = System.currentTimeMillis()
            if (now - lastToastTime > 5000) {
                val acc = location.accuracy.toInt()
                // GPS訊號微弱等通知訊息取消
                // Toast.makeText(requireContext(), "GPS 訊號微弱 (誤差 ${acc}m)，等待穩定...", Toast.LENGTH_SHORT).show()
                lastToastTime = now
            }
            if (isFirstLocationFix) {
                isFirstLocationFix = false
                map?.controller?.animateTo(GeoPoint(location.latitude, location.longitude), 16.5, 800L)
            }
            return
        }

        lastLocationObject = location 
        
        // Sensor Fusion: GPS G-force calculation
        val currentGpsSpeedKmh = location.speed * 3.6
        val currentGpsTime = location.time
        if (lastGpsTime > 0L && currentGpsTime > lastGpsTime) {
            val dtGps = (currentGpsTime - lastGpsTime) / 1000.0
            if (dtGps > 0) {
                val gpsAccel = ((currentGpsSpeedKmh - lastGpsSpeedKmh) / 3.6) / dtGps
                // 儲存真正的絕對加速度，供高頻 IMU 迴圈計算絕對仰角
                lastGpsAccel = gpsAccel
                
                // --- 步驟二：GPS 與 IMU 低頻融合 (坡度估計) ---
                val gpsG = gpsAccel / 9.80665
                // error = IMU_G - GPS_G (若上坡，IMU 覺得在加速，GPS 覺得沒加速，error 為正)
                val error = fusedLongG - gpsG
                // Low pass filter (極慢速適應)
                currentSlopeBiasG = 0.9 * currentSlopeBiasG + 0.1 * error
            }
        }
        lastGpsSpeedKmh = currentGpsSpeedKmh
        lastGpsTime = currentGpsTime
        
        checkCheckpoints(location); updateUI(location); lastUserPoint = GeoPoint(location.latitude, location.longitude)
    }

    private var lastFinishedTrackName = ""

    private var lastDebugReason = ""
    private var isEntranceLocked = false 
    private var lastStartDotProduct = 0.0 // 新增：紀錄上一次相對於起點的點積值
    private var lastFinishDotProduct = 0.0 // 新增：紀錄上一次相對於終點的點積值

    private fun checkStartTrigger(currentGeoPoint: GeoPoint, currentTime: Long, location: Location) {
        val isNativeGps = GpsConfig.getMode(requireContext()) == GpsConfig.MODE_TEST
        val triggerRadius = if (isNativeGps) 60.0 else 35.0
        val triggerMode = GpsConfig.getTriggerMode(requireContext())

        allTracks.forEach { track ->
            val d = currentGeoPoint.distanceToAsDouble(track.startPoint)
            val closestIdx = findClosestPointIndex(currentGeoPoint, track.points)
            if (closestIdx == -1) return@forEach
            val progress = closestIdx.toDouble() / track.points.size

            // 1. 中途進場鎖定邏輯 (暖胎圈機制)
            if (!isEntranceLocked && progress > 0.10 && progress < 0.90 && d < 100.0) {
                isEntranceLocked = true
                lastDebugReason = "偵測到中途進場，暖胎圈模式開啟"
            }

            // 2. 解鎖機制
            if (isEntranceLocked && progress > 0.95) {
                isEntranceLocked = false
                lastDebugReason = "已進入起點衝刺區，準備開始紀錄"
            }

            // 3. 起點觸發判定
            if (!isEntranceLocked && d < triggerRadius) {
                val isCorrectDir = isCorrectDirection(location, track)
                
                if (triggerMode == GpsConfig.TRIGGER_MODE_VECTOR) {
                    // --- 虛擬閘門 (向量) 模式 ---
                    if (track.points.isEmpty()) return@forEach
                    // 向量 A: 起點 -> 當前位置
                    val vectorA = doubleArrayOf(currentGeoPoint.latitude - track.startPoint.latitude, currentGeoPoint.longitude - track.startPoint.longitude)
                    // 向量 B: 賽道初始前進方向 (使用 track.points[0] -> [5])
                    val targetIdx = if(track.points.size > 5) 5 else track.points.size - 1
                    val vectorB = doubleArrayOf(track.points[targetIdx].latitude - track.startPoint.latitude, track.points[targetIdx].longitude - track.startPoint.longitude)
                    
                    // 計算點積 (Dot Product)
                    val currentDot = vectorA[0] * vectorB[0] + vectorA[1] * vectorB[1]
                    
                    // 觸發條件：點積從 負(後方) 變為 正(前方) 且 方向正確
                    if (lastStartDotProduct < 0 && currentDot >= 0 && isCorrectDir) {
                        if (progress < 0.15) { // 稍微放寬向量模式的進度判定
                            startRunForTrack(track, currentTime)
                            return
                        }
                    }
                    lastStartDotProduct = currentDot
                } else {
                    // --- 傳統範圍觸發模式 ---
                    if (isCorrectDir && progress < 0.10) {
                        val timeSinceLastFinish = currentTime - lastFinishTime
                        if (timeSinceLastFinish > 2000) {
                            startRunForTrack(track, currentTime)
                            return
                        }
                    }
                }
            }
        }
    }

    private fun checkCheckpoints(location: Location) {
        val currentGeoPoint = GeoPoint(location.latitude, location.longitude)
        val currentTime = System.currentTimeMillis()
        val isNativeGps = GpsConfig.getMode(requireContext()) == GpsConfig.MODE_TEST
        val triggerRadius = if (isNativeGps) 60.0 else 35.0
        val triggerMode = GpsConfig.getTriggerMode(requireContext())

        if (!isTiming) {
            // Check if we entered or exited the Pit zone of any nearby track
            for (track in allTracks) {
                val poly = track.pitPolygon
                val entrance = track.pitEntrance
                val entranceRadius = track.pitEntranceRadius
                val exit = track.pitExit
                val exitRadius = track.pitExitRadius
                
                val inPoly = if (poly != null && poly.isNotEmpty()) isPointInPolygon(currentGeoPoint, poly) else false
                val nearEntrance = if (entrance != null) currentGeoPoint.distanceToAsDouble(entrance) < entranceRadius else false
                val nearExit = if (exit != null) currentGeoPoint.distanceToAsDouble(exit) < exitRadius else false
                
                if (nearEntrance || inPoly) {
                    isInPit = true
                }
                if (nearExit) {
                    isInPit = false
                } else if (exit == null && poly != null && poly.isNotEmpty() && !inPoly && !nearEntrance) {
                    isInPit = false
                }
            }
            if (isInPit) {
                return
            }

            if (!isManualMode && !GpsConfig.isSensorAssistGpsEnabled(requireContext())) {
                checkStartTrigger(currentGeoPoint, currentTime, location)
            }
            // ...偵錯處理...
            var minStartDist = Double.MAX_VALUE
            allTracks.forEach { 
                val d = currentGeoPoint.distanceToAsDouble(it.startPoint)
                if (d < minStartDist) minStartDist = d
            }
            handleTriggerDebug(location, minStartDist, true)
        } else {
            val track = activeTrack ?: return
            if (track.points.isEmpty()) return
            val elapsed = currentTime - startTime

            // Check if we entered or exited the Pit zone
            val poly = track.pitPolygon
            val entrance = track.pitEntrance
            val entranceRadius = track.pitEntranceRadius
            val exit = track.pitExit
            val exitRadius = track.pitExitRadius

            val inPoly = if (poly != null && poly.isNotEmpty()) isPointInPolygon(currentGeoPoint, poly) else false
            val nearEntrance = if (entrance != null) currentGeoPoint.distanceToAsDouble(entrance) < entranceRadius else false
            val nearExit = if (exit != null) currentGeoPoint.distanceToAsDouble(exit) < exitRadius else false

            if (nearEntrance || inPoly) {
                isInPit = true
            }
            if (nearExit) {
                if (isInPit) {
                    finishRun()
                    isInPit = false
                    return
                }
                isInPit = false
            } else if (exit == null && poly != null && poly.isNotEmpty() && !inPoly && !nearEntrance) {
                isInPit = false
            }

            if (isInPit) {
                if (track.pitExit == null && location.speed < 0.1f) {
                    finishRun()
                    isInPit = false
                    return
                }
                wrongWayStartTime = 0L // Skip wrong-way warnings while in Pit
                return
            }

            // 1. 取得目前在路徑上的最近索引與橫向距離
            val closestIdx = findClosestPointIndex(currentGeoPoint, track.points)
            if (closestIdx == -1) return
            val lateralDist = currentGeoPoint.distanceToAsDouble(track.points[closestIdx])

            if (track.isLoop) {
                // --- 環狀賽道：路徑索引邏輯 ---
                val totalNodes = track.points.size
                val progressPercent = closestIdx.toDouble() / totalNodes

                // 終點門檻：橫向距離 < 15m (嚴格通道) 且 (接近末端 或 剛過起點)
                // 且必須已經跑了一段時間 (避免原地觸發)
                if (elapsed > 10000 && lateralDist < 15.0) {
                    val isNearEnd = progressPercent > 0.95
                    val isNearStart = progressPercent < 0.05

                    // 偵測從 95% 跳回 5% 的瞬間
                    if (lastTrackIndex >= totalNodes * 0.9 && closestIdx <= totalNodes * 0.1) {
                        lastTrackIndex = closestIdx // 更新索引防止重疊
                        finishRun()
                        // 自動開始下一圈
                        activeTrack = track
                        startNewRun()
                        return
                    }
                }
            } else {
                // --- 點對點賽道：終點觸發邏輯 ---
                val dEnd = currentGeoPoint.distanceToAsDouble(track.endPoint)
                
                if (elapsed > 10000 && dEnd < triggerRadius) {
                    if (triggerMode == GpsConfig.TRIGGER_MODE_VECTOR) {
                        if (track.points.isEmpty()) return
                        // 向量 A: 終點 -> 當前位置
                        val vectorA = doubleArrayOf(currentGeoPoint.latitude - track.endPoint.latitude, currentGeoPoint.longitude - track.endPoint.longitude)
                        // 向量 B: 賽道末端前進方向 (使用倒數第6點 -> 終點，若點數不夠則使用起點)
                        val startIdx = if(track.points.size > 5) track.points.size - 6 else 0
                        val vectorB = doubleArrayOf(track.endPoint.latitude - track.points[startIdx].latitude, track.endPoint.longitude - track.points[startIdx].longitude)
                        
                        val currentDot = vectorA[0] * vectorB[0] + vectorA[1] * vectorB[1]
                        
                        // 觸發條件：點積從 負(後方) 變為 正(前方) 瞬間觸發
                        if (lastFinishDotProduct < 0 && currentDot >= 0) {
                            finishRun()
                            resetDebugState()
                        }
                        lastFinishDotProduct = currentDot
                    } else {
                        finishRun()
                        resetDebugState()
                    }
                }
            }

            lastTrackIndex = closestIdx // 儲存本次索引供下次比對
        }
    }

    private fun startRunForTrack(track: TrackInfo, startTimeMs: Long) {
        activeTrack = track
        startNewRun()
        startTime = startTimeMs
    }

    private fun isCorrectDirection(location: Location, track: TrackInfo): Boolean {
        // --- 模擬器相容性優化 (更強健的偵測) ---
        val isEmulator = Build.FINGERPRINT.contains("generic") || 
                        Build.MODEL.contains("Emulator") || 
                        Build.HARDWARE.contains("goldfish") || 
                        Build.HARDWARE.contains("ranchu") ||
                        Build.PRODUCT.contains("sdk_gphone")
        
        val minSpeed = if (isEmulator) 0.1 else 2.0 
        
        if (location.speed < minSpeed) return false 
        
        val userBearing = if (location.hasBearing()) location.bearing else {
            val lastP = lastUserPoint ?: return true
            // 修正：使用 res[1] 取得方位角，而非 res[0] (距離)
            val res = FloatArray(2)
            Location.distanceBetween(lastP.latitude, lastP.longitude, location.latitude, location.longitude, res)
            if (res[1] < 0) res[1] + 360f else res[1]
        }
        
        // --- 智慧方位夾角門檻 ---
        val angleThreshold = when {
            isEmulator -> 90
            GpsConfig.getMode(requireContext()) == GpsConfig.MODE_TEST -> 90 
            else -> 45
        }
        
        if (track.points.isEmpty()) return true
        
        // 智慧軌跡向量
        var targetIdx = 10
        if (track.points.size > 10) {
            for (i in 5 until track.points.size) {
                if (track.points[0].distanceToAsDouble(track.points[i]) > 30.0) { targetIdx = i; break }
            }
        } else { targetIdx = track.points.size - 1 }
        
        // 修正：建立長度為 2 的陣列，並使用 res[1] 獲取真實賽道方位角
        val res = FloatArray(2)
        Location.distanceBetween(track.points[0].latitude, track.points[0].longitude, track.points[targetIdx].latitude, track.points[targetIdx].longitude, res)
        val trackBearing = if (res[1] < 0) res[1] + 360f else res[1]

        var diff = kotlin.math.abs(userBearing - trackBearing); if (diff > 180) diff = 360 - diff
        return diff < angleThreshold
    }

    private fun startNewRun() {
        isTiming = true; startTime = System.currentTimeMillis(); currentTrackPoints.clear(); currentRunDistance = 0.0; lastTrackIndex = -1; wrongWayStartTime = 0L; lastStartDotProduct = 0.0; lastFinishDotProduct = 0.0
        isPredictingFinish = false
        isInPit = false
        sessionSpeedMin = Double.MAX_VALUE
        sessionSpeedMax = 0.0
        
        val serviceIntent = Intent(requireContext(), TelemetryService::class.java).apply {
            putExtra("track_name", activeTrack?.name ?: "自定義賽道")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { requireContext().startForegroundService(serviceIntent) }
        else { requireContext().startService(serviceIntent) }

        if (GpsConfig.isAudioCuesEnabled(requireContext())) android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100).startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 200)
        handler.post { btnStopTiming.visibility = View.VISIBLE }
        map?.let { m -> dynamicOverlays.forEach { m.overlays.remove(it) }; dynamicOverlays.clear(); predictedMarker?.let { p -> m.overlays.remove(p) }; predictedMarker = null; m.invalidate() }
    }

    private fun abortRun(reason: String) {
        if (isTiming) {
            if (sessionLaps.isNotEmpty()) {
                // 環狀賽道 Session 處理
                val finalLaps = ArrayList(sessionLaps)
                
                // 例外處理：若開啟意外結束存檔，將殘圈標記為「未完賽 (false)」並併入 Session
                if (GpsConfig.isAccidentalSaveEnabled(requireContext()) && currentTrackPoints.isNotEmpty()) {
                    finalLaps.add(TrackManager.SessionLap(ArrayList(currentTrackPoints), false))
                }
                
                trackManager.saveSession(finalLaps, activeTrack?.name ?: "自定義賽道")
            } else if (GpsConfig.isAccidentalSaveEnabled(requireContext()) && currentTrackPoints.isNotEmpty()) {
                // ...其餘邏輯維持...
                val prefix = if (isManualMode) "手動紀錄" else "未完成紀錄"
                trackManager.saveTrack(ArrayList(currentTrackPoints), "${prefix}_${activeTrack?.name ?: "自錄賽道"}")
            }
        }
        try { requireContext().stopService(Intent(requireContext(), TelemetryService::class.java)) } catch (e: Exception) {}
        isTiming = false; currentTrackPoints.clear(); sessionLaps.clear(); activeTrack = null; trackManager.clearTempRecovery()
        isInPit = false
        handler.post { 
            if (isManualMode) {
                btnStopTiming.visibility = View.VISIBLE
                btnStopTiming.setImageResource(android.R.drawable.ic_media_play)
            } else {
                btnStopTiming.visibility = View.GONE
            }
            timerText.text = "00:00:00"; Toast.makeText(requireContext(), reason, Toast.LENGTH_LONG).show(); map?.invalidate() 
        }
    }

    private fun finishRun() {
        if (activeTrack?.isLoop == true) {
            // --- 環狀賽道：連刷模式 ---
            
            // 1. 【核心修正】將觸發過線的這個點也計入「目前這一圈」的結尾，達成視覺銜接
            lastLocationObject?.let { loc ->
                val speedKmh = loc.speed * 3.6
                currentTrackPoints.add(TrackPoint(loc.latitude, loc.longitude, speedKmh, System.currentTimeMillis(), currentLeanAngle, currentLatG, currentGForce, currentAccelG, currentBrakingG, currentRunDistance))
            }

            if (currentTrackPoints.isNotEmpty()) {
                // 標記為「完賽 (true)」
                sessionLaps.add(TrackManager.SessionLap(ArrayList(currentTrackPoints), true))
                currentTrackPoints.clear() 
            }
            
            val currentTrack = activeTrack
            startNewRun() 
            activeTrack = currentTrack

            // 2. 【核心修正】將剛才那個點也計入「新的一圈」的開頭，達成跨圈完美銜接
            lastLocationObject?.let { loc ->
                val speedKmh = loc.speed * 3.6
                currentTrackPoints.add(TrackPoint(loc.latitude, loc.longitude, speedKmh, System.currentTimeMillis(), currentLeanAngle, currentLatG, currentGForce, currentAccelG, currentBrakingG, currentRunDistance))
            }

            // 提示車手已完成一圈
            if (GpsConfig.isAudioCuesEnabled(requireContext())) {
                try {
                    android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                        .startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
                } catch (e: Exception) {}
            }
        } else {
            // --- 點對點賽道：維持原樣 ---
            isTiming = false; lastFinishTime = System.currentTimeMillis()
            lastFinishedTrackName = activeTrack?.name ?: "" 
            activePathOverlay = null 
            try { requireContext().stopService(Intent(requireContext(), TelemetryService::class.java)) } catch (e: Exception) {}
            if (GpsConfig.isAudioCuesEnabled(requireContext())) try { android.media.RingtoneManager.getRingtone(requireContext(), android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)).play() } catch (e: Exception) {}
            
            // 普通賽道存檔：手動模式使用專屬前綴
            val trackName = activeTrack?.name ?: "自錄賽道"
            val prefix = if (isManualMode) "手動紀錄_$trackName" else trackName
            trackManager.saveTrack(ArrayList(currentTrackPoints), prefix)

            handler.post { 
                if (isManualMode) {
                    btnStopTiming.visibility = View.VISIBLE
                    btnStopTiming.setImageResource(android.R.drawable.ic_media_play)
                } else {
                    btnStopTiming.visibility = View.GONE
                }
                val displayName = if (isManualMode) "手動" else activeTrack?.name ?: ""
                Toast.makeText(requireContext(), "${displayName}紀錄已存檔", Toast.LENGTH_SHORT).show(); map?.invalidate() 
            }
            activeTrack = null; currentTrackPoints.clear(); trackManager.clearTempRecovery()
            isInPit = false
        }
    }

    private fun startTimerUpdate() {
        handler.post(object : Runnable {
            override fun run() {
                if (isTiming && isAdded) {
                    val elapsed = System.currentTimeMillis() - startTime
                    timerText.text = String.format(Locale.getDefault(), "%02d:%02d.%03d", (elapsed/60000)%60, (elapsed/1000)%60, elapsed%1000)
                }
                handler.postDelayed(this, if (isAdded && GpsConfig.isHighFpsEnabled(requireContext())) 16L else 33L)
            }
        })
    }

    private var activePathOverlay: Polyline? = null
    private var lastPathColor: Int = -1
    private var lastIsBraking: Boolean = false

    private fun updateUI(location: Location) {
        val speedDouble = location.speed * 3.6; speedText.text = String.format(Locale.getDefault(), "%d km/h", speedDouble.toInt())
        val currentPoint = GeoPoint(location.latitude, location.longitude)
        
        if (userLocationMarker == null) {
            userLocationMarker = Marker(map).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); icon = createPlayerIcon().toDrawable(resources); setOnMarkerClickListener { _, _ -> true } }
            userLocationMarker?.let { map?.overlays?.add(it) }
        }
        userLocationMarker?.position = currentPoint
        
        if (isFollowMode) {
            if (isFirstLocationFix) {
                isFirstLocationFix = false
                map?.controller?.animateTo(currentPoint, 16.5, 800L) // 第一次定位強制放大到賽道視角
            } else {
                if (!isPinching && System.currentTimeMillis() - lastZoomTime > 1500) {
                    // 傳遞 null 作為 zoomLevel 參數，避免在使用者手動縮放時因為強制設定縮放層級而產生一階一階的卡頓感
                    map?.controller?.animateTo(currentPoint, null, 800L)
                }
            }
        }
        
        if (isTiming) {
            val isNativeGps = GpsConfig.getMode(requireContext()) == GpsConfig.MODE_TEST
            val wrongWayThreshold = if (isNativeGps) 40 else 20
            activeTrack?.let { track ->
                val closestIdx = findClosestPointIndex(currentPoint, track.points)
                if (lastTrackIndex != -1) {
                    if (closestIdx < lastTrackIndex - wrongWayThreshold) {
                        if (wrongWayStartTime == 0L) wrongWayStartTime = System.currentTimeMillis()
                        if (System.currentTimeMillis() - wrongWayStartTime > 5000) { abortRun("偵測到持續逆向行駛，紀錄已取消"); return }
                    } else if (closestIdx >= lastTrackIndex) { wrongWayStartTime = 0L; lastTrackIndex = closestIdx }
                } else lastTrackIndex = closestIdx
            }
            lastUserPoint?.let { currentRunDistance += it.distanceToAsDouble(currentPoint) }
            
            // --- 實作即時彩色路徑與煞車點加粗 ---
            val isDynamic = GpsConfig.getSpeedColorMode(requireContext()) == GpsConfig.SPEED_COLOR_DYNAMIC
            if (isDynamic) {
                if (speedDouble < sessionSpeedMin) sessionSpeedMin = speedDouble
                if (speedDouble > sessionSpeedMax) sessionSpeedMax = speedDouble
            }
            val colorMin = if (isDynamic && sessionSpeedMax > sessionSpeedMin) sessionSpeedMin else 0.0
            val colorMax = if (isDynamic && sessionSpeedMax > sessionSpeedMin) sessionSpeedMax else 120.0
            val currentColor = getSpeedColor(speedDouble, colorMin, colorMax)
            // 煞車判定：G力大於 1.0 且處於減速 G (currentBrakingG)
            val isBraking = currentBrakingG >= 0.3 && currentGForce >= 0.6 
            
            // 紀錄點位，若開發模式開啟則填入傳感器三軸數據
            val fullLog = GpsConfig.isFullSensorLogEnabled(requireContext())
            currentTrackPoints.add(TrackPoint(
                lat = location.latitude, 
                lng = location.longitude, 
                speed = speedDouble, 
                timestamp = System.currentTimeMillis(), 
                leanAngle = currentLeanAngle, 
                latG = currentLatG, // 新增側向G力參數
                gForce = currentGForce, 
                accelG = currentAccelG, 
                brakingG = currentBrakingG, 
                distance = currentRunDistance,
                fx = if(fullLog) filteredG[0] else null,
                fy = if(fullLog) filteredG[1] else null,
                fz = if(fullLog) filteredG[2] else null,
                rx = if(fullLog) lastRawAccel[0] else null,
                ry = if(fullLog) lastRawAccel[1] else null,
                rz = if(fullLog) lastRawAccel[2] else null,
                gx = if(fullLog) lastGravity[0] else null,
                gy = if(fullLog) lastGravity[1] else null,
                gz = if(fullLog) lastGravity[2] else null
            ))

            // 每隔 10 秒將數據背景備份一次，以防異常中斷丟失
            val nowTime = System.currentTimeMillis()
            if (nowTime - lastAutoSaveTime > 10000) {
                lastAutoSaveTime = nowTime
                trackManager.saveTempRecovery(ArrayList(currentTrackPoints), "circuit", activeTrack?.name ?: "自訂賽道")
            }
            
            // 點位抽樣：小於 1.5 米不重複繪製到地圖，避免大量繪製造成 OOM/卡頓
            var shouldDraw = true
            lastUserPoint?.let { lastPt ->
                if (lastPt.distanceToAsDouble(currentPoint) < 1.5) {
                    shouldDraw = false
                }
            }
            
            if (shouldDraw) {
                if (activePathOverlay == null || currentColor != lastPathColor || isBraking != lastIsBraking) {
                    // 當顏色或煞車狀態改變時，啟動新段落
                    activePathOverlay = Polyline().apply {
                        outlinePaint.strokeWidth = if (isBraking) 23f else 13f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.isAntiAlias = true
                        outlinePaint.color = currentColor
                    }
                    map?.overlays?.add(activePathOverlay)
                    activePathOverlay?.let { dynamicOverlays.add(it) }
                    
                    // 為了讓線段連續，將前一個點也加入新線段
                    lastUserPoint?.let { activePathOverlay?.addPoint(it) }
                    lastPathColor = currentColor
                    lastIsBraking = isBraking
                }
                activePathOverlay?.addPoint(currentPoint)
            }
        }
        map?.invalidate()
    }

    private fun getSpeedColor(speed: Double, minSpeed: Double = 0.0, maxSpeed: Double = 120.0): Int {
        val range = (maxSpeed - minSpeed).coerceAtLeast(1.0)
        val ratio = ((speed - minSpeed) / range).coerceIn(0.0, 1.0).toFloat()
        val hue = 240f - (ratio * 240f)
        return Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
    }

    private fun createPlayerIcon(mainColor: Int = Color.parseColor("#00E5FF")): Bitmap {
        val d = resources.displayMetrics.density; val s = (32 * d).toInt(); val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        Canvas(b).apply { val p = Paint(Paint.ANTI_ALIAS_FLAG); p.color = mainColor; p.alpha = 80; drawCircle(s/2f, s/2f, s/2.2f, p); p.style = Paint.Style.FILL; p.color = Color.WHITE; p.alpha = 255; drawCircle(s/2f, s/2f, s/5f, p); p.style = Paint.Style.STROKE; p.strokeWidth = 2*d; p.color = mainColor; drawCircle(s/2f, s/2f, s/4f, p) }
        return b
    }

    private fun findClosestPointIndex(point: GeoPoint, trackPoints: List<GeoPoint>): Int {
        var minD = Double.MAX_VALUE; var idx = -1
        for (i in trackPoints.indices) { val d = point.distanceToAsDouble(trackPoints[i]); if (d < minD) { minD = d; idx = i } }
        return idx
    }

    // --- 起點終點偵錯系統實作 ---
    private fun handleTriggerDebug(location: Location, currentDist: Double, isStart: Boolean) {
        if (!GpsConfig.isTriggerDebugEnabled(requireContext())) return
        
        val p = collectDebugInfo(location)
        debugBuffer.add(p)
        
        if (currentDist < 100.0) {
            isInsideDebugZone = true
            if (currentDist < closestDebugDist) closestDebugDist = currentDist
        } else if (isInsideDebugZone) {
            // 離開偵錯區，檢查是否發生「非預期」的漏觸發
            if (closestDebugDist < 60.0) {
                // 過濾掉正常的冷卻情況：如果是冷卻中導致的未觸發，不視為異常
                if (!lastDebugReason.contains("冷卻中")) {
                    saveDebugLog(if (isStart) "起點漏觸發: $lastDebugReason" else "終點漏觸發: $lastDebugReason")
                }
            }
            resetDebugState()
        }
    }

    private fun collectDebugInfo(l: Location): JSONObject {
        val obj = JSONObject()
        obj.put("t", System.currentTimeMillis())
        obj.put("lat", String.format("%.7f", l.latitude))
        obj.put("lng", String.format("%.7f", l.longitude))
        obj.put("speed", String.format("%.2f", l.speed * 3.6))
        obj.put("bearing", l.bearing)
        obj.put("lean", String.format("%.2f", currentLeanAngle))
        obj.put("g", String.format("%.2f", currentGForce))
        obj.put("ga", String.format("%.2f", currentAccelG))
        obj.put("gb", String.format("%.2f", currentBrakingG))
        // 抓取最近一次的感測器濾波值
        obj.put("fx", filteredG[0]); obj.put("fy", filteredG[1]); obj.put("fz", filteredG[2])
        return obj
    }

    private fun saveDebugLog(reason: String) {
        if (debugBuffer.isEmpty()) return
        val root = JSONObject()
        root.put("reason", reason)
        root.put("closest_dist", closestDebugDist)
        root.put("points", JSONArray(debugBuffer))
        
        val fileName = "DEBUG_${if(reason.contains("起點")) "START" else "FINISH"}_" + 
                      java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(java.util.Date()) + ".json"
        try {
            java.io.File(requireContext().filesDir, fileName).writeText(root.toString())
            handler.post { Toast.makeText(requireContext(), "偵測到異常已自動存檔: $reason", Toast.LENGTH_LONG).show() }
        } catch (e: Exception) {}
    }

    private fun resetDebugState() {
        isInsideDebugZone = false
        closestDebugDist = Double.MAX_VALUE
        debugBuffer.clear()
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    override fun onResume() { 
        super.onResume()
        map?.onResume()
        requestLocationUpdates()
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        // 監聽校正資料變化（與 SettingsFragment 連動）
        requireContext().getSharedPreferences("gps_config", android.content.Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(calibPrefsListener)
        // 回到頁面時重新檢查有無最新的校正資料
        GpsConfig.loadGravityBaseline(requireContext())
        updateCalibBtnColor()
    }
    override fun onPause() { 
        super.onPause()
        if (isTiming && GpsConfig.isAccidentalSaveEnabled(requireContext()) && currentTrackPoints.isNotEmpty()) trackManager.saveTrack(ArrayList(currentTrackPoints), "未完成紀錄_${activeTrack?.name ?: "賽道"}")
        map?.onPause(); fusedLocationClient.removeLocationUpdates(locationCallback); locationManager.removeUpdates(nativeListener); sensorManager.unregisterListener(this)
        requireContext().getSharedPreferences("gps_config", android.content.Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(calibPrefsListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        map?.onDetach()
        map = null
    }

    private var lastLocationObject: Location? = null
    private var isPredictingFinish = false

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                // 1. 校正模式處理
                if (isCalibrating) {
                    calibrationSamples.add(event.values.clone())
                    if (System.currentTimeMillis() - calibrationStartTime > 2000) {
                        isCalibrating = false
                        val avgBaseline = floatArrayOf(0f, 0f, 0f)
                        calibrationSamples.forEach { s -> for(i in 0..2) avgBaseline[i] += s[i] }
                        for(i in 0..2) avgBaseline[i] /= calibrationSamples.size.toFloat()
                        GpsConfig.saveGravityBaseline(requireContext(), avgBaseline)
                        handler.post {
                            Toast.makeText(requireContext(), "姿態校正完成！", Toast.LENGTH_SHORT).show()
                            updateCalibBtnColor()
                        }
                    }
                }
                lastGravity = event.values.clone()
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (lastGyroTime > 0) {
                    val dt = (event.timestamp - lastGyroTime) / 1000000000.0f
                    // 全維度陀螺儀投影：將角速度向量投影至橫向虛擬軸 (rightW)
                    // 這能保證無論手機直放、橫放或傾斜，都能精準擷取車身的 Pitch 變化
                    val gyroPitchRate = event.values[0]*currentRightW[0] + event.values[1]*currentRightW[1] + event.values[2]*currentRightW[2]
                    dynamicPitch += gyroPitchRate * dt
                }
                lastGyroTime = event.timestamp
            }
            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    lastRawAccel = event.values.clone()
                }
                for (i in 0..2) filteredG[i] = filteredG[i] + sensorAlpha * (event.values[i] - filteredG[i])
                
                // --- 即時重力剝離 + 虛擬軸投影算法 ---
                // 核心改進：使用即時重力感測器 (TYPE_GRAVITY) 的 lastGravity 作為「下方」向量，
                // 而非靜態校準基線。這讓座標系隨時跟蹤實際手機姿態，
                // 直接消除重力分量，不再需要容易偏移的 dynamicPitch 補償。
                val baseline = GpsConfig.gravityBaseline ?: floatArrayOf(0f, 0f, 9.80665f)
                
                // --- 步驟一：動態綁定「水平校準」基準投影 ---
                // 使用校準存檔的 baseline 作為「下方」向量 (固定座標系)
                // 絕不能用即時的 lastGravity，因為在加減速時它會往後/前傾斜，導致投影失真！
                val baseNorm = Math.sqrt((baseline[0]*baseline[0] + baseline[1]*baseline[1] + baseline[2]*baseline[2]).toDouble())
                val downW = if (baseNorm > 0.1) {
                    doubleArrayOf(baseline[0]/baseNorm, baseline[1]/baseNorm, baseline[2]/baseNorm)
                } else {
                    doubleArrayOf(0.0, 0.0, 1.0)
                }
                
                // 2. 定位前進軸 (Gram-Schmidt: 從手機 Y 軸投影至水平面)
                val yDotDown = 0 * downW[0] + 1 * downW[1] + 0 * downW[2]
                var forwardW = doubleArrayOf(0 - yDotDown * downW[0], 1 - yDotDown * downW[1], 0 - yDotDown * downW[2])
                val fNorm = Math.sqrt(forwardW[0]*forwardW[0] + forwardW[1]*forwardW[1] + forwardW[2]*forwardW[2])
                if (fNorm > 1e-6) {
                    forwardW = doubleArrayOf(forwardW[0]/fNorm, forwardW[1]/fNorm, forwardW[2]/fNorm)
                }
                
                // 3. 定位橫向軸 (叉積: forward × down)
                val rightW = doubleArrayOf(
                    forwardW[1]*downW[2] - forwardW[2]*downW[1],
                    forwardW[2]*downW[0] - forwardW[0]*downW[2],
                    forwardW[0]*downW[1] - forwardW[1]*downW[0]
                )
                currentRightW = rightW

                // 4. 不依賴即時低通重力 (避免吸收真實加速度)，直接將當下濾波加速度投影
                val longAccel = filteredG[0]*forwardW[0] + filteredG[1]*forwardW[1] + filteredG[2]*forwardW[2]
                val latAccel = filteredG[0]*rightW[0] + filteredG[1]*rightW[1] + filteredG[2]*rightW[2]
                
                currentLatG = Math.abs(latAccel / 9.80665)
                
                currentGForce = Math.sqrt((filteredG[0]*filteredG[0] + filteredG[1]*filteredG[1] + filteredG[2]*filteredG[2]).toDouble()) / 9.80665
                
                // 換算為 G 值
                currentImuG = longAccel / 9.80665
                
                // --- 步驟三：高頻動態補償 ---
                // 扣除長期累積的坡度與姿態誤差
                fusedLongG = currentImuG - currentSlopeBiasG
                
                // 加速時 fusedLongG 為正 (車架向前推設備)，煞車時為負
                if (fusedLongG > 0) {
                    currentAccelG = fusedLongG
                    currentBrakingG = 0.0
                } else {
                    currentAccelG = 0.0
                    currentBrakingG = Math.abs(fusedLongG)
                }

                // 計算動態傾角 (壓車角度)
                // 結合重力基準與即時重力向量的夾角計算
                val currNorm = Math.sqrt((lastGravity[0]*lastGravity[0] + lastGravity[1]*lastGravity[1] + lastGravity[2]*lastGravity[2]).toDouble())
                val dot = (lastGravity[0]*baseline[0] + lastGravity[1]*baseline[1] + lastGravity[2]*baseline[2]) / (currNorm * baseNorm)
                
                if (GpsConfig.getVehicleType(requireContext()) == GpsConfig.VEHICLE_CAR) {
                    currentLeanAngle = 0.0 // 汽車過彎車身幾乎不側傾，傾角記為0
                } else {
                    currentLeanAngle = Math.abs(Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot)))))
                }

                // 5. 傳感器輔助 GPS 過線預測邏輯 (擴增高頻顯示)
                if (lastLocationObject != null && GpsConfig.isSensorAssistGpsEnabled(requireContext())) {
                    val lastLoc = lastLocationObject ?: return
                    val dt = (System.currentTimeMillis() - lastLoc.time) / 1000.0
                    val isUnlock = GpsConfig.isSensorPredictUnlockEnabled(requireContext())
                    
                    if (dt > 0 && dt < 1.5) {
                        val a = (currentAccelG - currentBrakingG) * 9.80665
                        val v0 = lastLoc.speed.toDouble()
                        val displacement = v0 * dt + 0.5 * a * dt * dt
                        
                        val bearingRad = Math.toRadians(lastLoc.bearing.toDouble())
                        val dLat = Math.toDegrees(displacement * Math.cos(bearingRad) / 6378137.0)
                        val dLng = Math.toDegrees(displacement * Math.sin(bearingRad) / (6378137.0 * Math.cos(Math.toRadians(lastLoc.latitude))))
                        val pPt = GeoPoint(lastLoc.latitude + dLat, lastLoc.longitude + dLng)

                        // 判斷是否顯示小黃點預測點
                        var showPredictMarker = isUnlock
                        if (!showPredictMarker) {
                            if (!isTiming) {
                                // 距離任意起點 300m 內
                                var closeToAnyStart = false
                                for (track in allTracks) {
                                    val d = GeoPoint(lastLoc.latitude, lastLoc.longitude).distanceToAsDouble(track.startPoint)
                                    if (d < 300.0) {
                                        closeToAnyStart = true
                                        break
                                    }
                                }
                                showPredictMarker = closeToAnyStart
                            } else {
                                // 距離當前終點 300m 內
                                val track = activeTrack
                                if (track != null) {
                                    val distToFinish = GeoPoint(lastLoc.latitude, lastLoc.longitude).distanceToAsDouble(track.endPoint)
                                    showPredictMarker = distToFinish < 300.0
                                }
                            }
                        }

                        if (showPredictMarker) {
                            handler.post {
                                if (predictedMarker == null) {
                                    predictedMarker = Marker(map).apply {
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                        icon = createPlayerIcon(Color.parseColor("#FFD600")).toDrawable(resources)
                                        setOnMarkerClickListener { _, _ -> true }
                                    }
                                    predictedMarker?.let { map?.overlays?.add(it) }
                                }
                                predictedMarker?.position = pPt
                                map?.invalidate()
                            }
                        } else if (predictedMarker != null) {
                            handler.post { predictedMarker?.let { map?.overlays?.remove(it) }; predictedMarker = null; map?.invalidate() }
                        }
                        
                        if (!isTiming) {
                            if (!isManualMode && showPredictMarker) {
                                checkStartTrigger(pPt, System.currentTimeMillis(), lastLoc)
                            }
                        } else {
                            val track = activeTrack ?: return
                            val elapsed = System.currentTimeMillis() - startTime
                            if (elapsed > 10000) {
                                val finishPt = track.endPoint
                                val distToFinish = GeoPoint(lastLoc.latitude, lastLoc.longitude).distanceToAsDouble(finishPt)
                                if (distToFinish < 300.0 || isUnlock) {
                                    if (displacement >= distToFinish && !isPredictingFinish) {
                                        isPredictingFinish = true
                                        finishRun()
                                    }
                                }
                            }
                        }
                    } else if (predictedMarker != null) {
                        handler.post { predictedMarker?.let { map?.overlays?.remove(it) }; predictedMarker = null; map?.invalidate() }
                    }
                } else if (predictedMarker != null) {
                    handler.post { predictedMarker?.let { map?.overlays?.remove(it) }; predictedMarker = null; map?.invalidate() }
                }
            }
        }
    }

    private fun checkForRecovery() {
        val recoveryJson = trackManager.getTempRecovery() ?: return
        val type = recoveryJson.optString("type")
        if (type != "circuit") return // DragRaceFragment handles drag recovery
        val extraInfo = recoveryJson.optString("extraInfo")
        val pointsArray = recoveryJson.optJSONArray("points") ?: return
        if (pointsArray.length() == 0) return

        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("偵測到未正常結束的紀錄")
            .setMessage("系統偵測到一筆賽道紀錄（$extraInfo）因意外中斷未儲存，是否嘗試復原？")
            .setPositiveButton("復原") { _, _ ->
                val recoveredPoints = mutableListOf<TrackPoint>()
                for (i in 0 until pointsArray.length()) {
                    val obj = pointsArray.getJSONObject(i)
                    recoveredPoints.add(TrackPoint(
                        lat = obj.getDouble("lt"),
                        lng = obj.getDouble("lg"),
                        speed = obj.getDouble("s"),
                        timestamp = obj.getLong("t"),
                        leanAngle = obj.optDouble("l", 0.0),
                        latG = obj.optDouble("gl", 0.0),
                        gForce = obj.optDouble("g", 0.0),
                        accelG = obj.optDouble("ga", 0.0),
                        brakingG = obj.optDouble("gb", 0.0),
                        distance = obj.optDouble("d", 0.0),
                        fx = if (obj.has("fx")) obj.getDouble("fx").toFloat() else null,
                        fy = if (obj.has("fy")) obj.getDouble("fy").toFloat() else null,
                        fz = if (obj.has("fz")) obj.getDouble("fz").toFloat() else null,
                        rx = if (obj.has("rx")) obj.getDouble("rx").toFloat() else null,
                        ry = if (obj.has("ry")) obj.getDouble("ry").toFloat() else null,
                        rz = if (obj.has("rz")) obj.getDouble("rz").toFloat() else null
                    ))
                }
                
                trackManager.saveTrack(recoveredPoints, "復原_${extraInfo}")
                trackManager.clearTempRecovery()
                Toast.makeText(requireContext(), "復原成功，已儲存至歷史紀錄！", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("捨棄") { _, _ ->
                trackManager.clearTempRecovery()
            }
            .show()
    }

    private fun isPointInPolygon(point: GeoPoint, polygon: List<GeoPoint>): Boolean {
        var intersectCount = 0
        val x = point.longitude
        val y = point.latitude
        for (i in polygon.indices) {
            val p1 = polygon[i]
            val p2 = polygon[(i + 1) % polygon.size]
            
            val x1 = p1.longitude
            val y1 = p1.latitude
            val x2 = p2.longitude
            val y2 = p2.latitude
            
            if (((y1 > y) != (y2 > y)) && (x < (x2 - x1) * (y - y1) / (y2 - y1) + x1)) {
                intersectCount++
            }
        }
        return intersectCount % 2 != 0
    }
}
