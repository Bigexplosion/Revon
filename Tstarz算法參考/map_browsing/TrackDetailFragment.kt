package com.example.qstart

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.content.ClipData
import android.view.DragEvent
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Locale

class TrackDetailFragment : Fragment() {

    private lateinit var map: MapView
    private lateinit var chart: LineChart
    private lateinit var infoCard: View
    private lateinit var analysisView: View
    private lateinit var btnAnalyzeContainer: View
    
    private var allPoints = mutableListOf<TrackPoint>()
    private var sessionLapsData = mutableListOf<LapSummary>()
    private var selectedLapNum: Int? = null 
    private var replayMarker: Marker? = null
    private var predictedMarker: Marker? = null
    private var summaryMarker: Marker? = null
    private var currentReplayDist = Double.MAX_VALUE
    private var heatmapFolder = org.osmdroid.views.overlay.FolderOverlay()
    
    private var isPlaying = false
    private var isProgrammaticHighlight = false // 新增：區分是使用者點擊還是程式播放
    private var isMapFollowMode = true
    private var playHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var currentPlayIndex = 0
    private var markerAnimator: android.animation.ValueAnimator? = null
    private var predictedMarkerAnimator: android.animation.ValueAnimator? = null

    private var lastShowAsPoint: Boolean? = null
    private var trackDiagMeters = 0.0
    private var startMarker: Marker? = null
    private var finishMarker: Marker? = null
    private var dotsOverlay: org.osmdroid.views.overlay.Overlay? = null

    private fun animateMarker(marker: Marker, from: GeoPoint, to: GeoPoint, duration: Long) {
        markerAnimator?.cancel()
        markerAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            setDuration(duration)
            addUpdateListener { animation ->
                if (!isAdded || !::map.isInitialized) return@addUpdateListener
                val fraction = animation.animatedValue as Float
                val lat = from.latitude + (to.latitude - from.latitude) * fraction
                val lng = from.longitude + (to.longitude - from.longitude) * fraction
                marker.position = GeoPoint(lat, lng)
                map.invalidate()
            }
            start()
        }
    }

    private fun animatePredictedMarker(marker: Marker, from: GeoPoint, to: GeoPoint, duration: Long) {
        predictedMarkerAnimator?.cancel()
        predictedMarkerAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            setDuration(duration)
            addUpdateListener { animation ->
                if (!isAdded || !::map.isInitialized) return@addUpdateListener
                val fraction = animation.animatedValue as Float
                val lat = from.latitude + (to.latitude - from.latitude) * fraction
                val lng = from.longitude + (to.longitude - from.longitude) * fraction
                marker.position = GeoPoint(lat, lng)
                map.invalidate()
            }
            start()
        }
    }

    private var playRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying || allPoints.isEmpty()) return
            if (currentPlayIndex >= allPoints.size) {
                currentPlayIndex = 0
                pausePlayback()
                return
            }
            val point = allPoints[currentPlayIndex]
            if (::chart.isInitialized && chart.data != null && chart.data.dataSetCount > 0) {
                // 設定旗標，防止監聽器誤判為使用者手動觸摸而停止播放
                isProgrammaticHighlight = true
                chart.highlightValue(point.distance.toFloat(), 0)
                isProgrammaticHighlight = false
            }
            
            // 同步更新上方數據面板的數值
            updateInfoCardValues(point)
            
            currentPlayIndex++
            
            // 計算延時：依照錄製時的真實間隔 (限制在 50ms ~ 500ms 讓播放順暢)
            val nextDelay = if (currentPlayIndex < allPoints.size) {
                (allPoints[currentPlayIndex].timestamp - point.timestamp).coerceIn(50, 500)
            } else 200L
            
            playHandler.postDelayed(this, nextDelay)
        }
    }

    private fun updateInfoCardValues(p: TrackPoint) {
        val view = view ?: return
        view.findViewById<TextView>(R.id.txtMaxSpeed)?.text = String.format("%.1f km/h", p.speed)
        
        val maxA = p.accelG; val maxB = p.brakingG
        val txtG = view.findViewById<TextView>(R.id.txtMaxG)
        if (maxA > 0.001 || maxB > 0.001) {
            txtG?.text = String.format("↑%.2fG ↓%.2fG", maxA, maxB)
        } else {
            txtG?.text = String.format("%.2f G", p.gForce)
        }
        
        val txtL = view.findViewById<TextView>(R.id.txtMaxLean)
        val titleL = view.findViewById<TextView>(R.id.txtMaxLeanTitle)
        if (p.latG > 0.01 && p.leanAngle < 0.1) {
            titleL?.text = "即時側向G"
            txtL?.text = String.format("%.2fG", p.latG)
        } else {
            titleL?.text = "即時傾角"
            txtL?.text = String.format("%.1f°", p.leanAngle)
        }
    }
    
    private fun togglePlayback() {
        if (isPlaying) pausePlayback() else startPlayback()
    }
    
    private fun startPlayback() {
        if (allPoints.isEmpty()) return
        isPlaying = true
        isMapFollowMode = true
        toggleAnalysisMode(true)
        val btnAnalyze = view?.findViewById<ImageButton>(R.id.btnAnalyze)
        btnAnalyze?.setImageResource(android.R.drawable.ic_media_pause)
        
        if (selectedEntry != null) {
            val dist = selectedEntry!!.x.toDouble()
            currentPlayIndex = allPoints.indexOfFirst { it.distance >= dist }.coerceAtLeast(0)
        } else {
            currentPlayIndex = 0
        }
        playHandler.post(playRunnable)
    }
    
    private fun pausePlayback() {
        isPlaying = false
        playHandler.removeCallbacks(playRunnable)
        val btnAnalyze = view?.findViewById<ImageButton>(R.id.btnAnalyze)
        btnAnalyze?.setImageResource(android.R.drawable.ic_media_play)
    }
    
    data class LapSummary(
        val lapNum: Int,
        val timeMs: Long,
        val timeStr: String,
        val diffStr: String,
        val maxSpeed: Double,
        val minSpeed: Double,
        val avgSpeed: Double,
        val points: List<TrackPoint>,
        val isComplete: Boolean = true // 新增：是否完賽
    )
    
    private var selectedEntry: Entry? = null

    companion object {
        fun newInstance(filePath: String, headerInfo: String? = null): TrackDetailFragment {
            val fragment = TrackDetailFragment()
            val args = Bundle()
            args.putString("file_path", filePath)
            args.putString("header_info", headerInfo)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_track_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        map = view.findViewById(R.id.detailMap)
        heatmapFolder = org.osmdroid.views.overlay.FolderOverlay()
        chart = view.findViewById(R.id.telemetryChart)
        infoCard = view.findViewById(R.id.infoCard)
        analysisView = view.findViewById(R.id.analysisView)
        btnAnalyzeContainer = view.findViewById(R.id.btnAnalyzeContainer)
        val btnAnalyze = view.findViewById<View>(R.id.btnAnalyze)
        
        val txtMaxSpeed = view.findViewById<TextView>(R.id.txtMaxSpeed)
        val txtDuration = view.findViewById<TextView>(R.id.txtDuration)
        val txtMaxLean = view.findViewById<TextView>(R.id.txtMaxLean)
        val txtMaxG = view.findViewById<TextView>(R.id.txtMaxG)
        val txtNameHeader = view.findViewById<TextView>(R.id.txtTrackNameHeader)
        val txtTimeHeader = view.findViewById<TextView>(R.id.txtTrackTimeHeader)
        
        view.findViewById<View>(R.id.btnBack).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<View>(R.id.btnShare).setOnClickListener {
            showShareCardDialog(txtNameHeader.text.toString(), txtTimeHeader.text.toString(), txtMaxSpeed.text.toString(), txtDuration.text.toString(), txtMaxLean.text.toString(), txtMaxG.text.toString())
        }
        btnAnalyze.setOnClickListener { togglePlayback() }
        view.findViewById<View>(R.id.btnCloseAnalysis).setOnClickListener { 
            pausePlayback()
            toggleAnalysisMode(false) 
        }

        setupMap()
        setupChart()

        // --- 預設視角：全台灣 (優化視角範圍) ---
        map.post {
            val taiwan = org.osmdroid.util.BoundingBox(25.9, 122.6, 21.6, 119.5)
            map.zoomToBoundingBox(taiwan, false, 0)
        }

        val filePath = arguments?.getString("file_path") ?: return
        val headerInfo = arguments?.getString("header_info")
        val file = File(filePath)
        
        if (!file.exists()) {
            Toast.makeText(requireContext(), "檔案不存在", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        if (headerInfo != null) {
            val parts = headerInfo.split(" | ")
            txtNameHeader.text = parts.getOrNull(0) ?: "未知車手"
            txtTimeHeader.text = parts.getOrNull(1) ?: ""
        } else {
            updateInfoBar(file, txtNameHeader, txtTimeHeader)
        }
        
        loadTrackData(file, txtMaxSpeed, txtDuration, txtMaxLean, txtMaxG)
        setupFilters(view)
    }

    override fun onResume() {
        super.onResume()
        if (::map.isInitialized) map.onResume()
        applyMapSettings()
    }

    override fun onPause() {
        super.onPause()
        if (::map.isInitialized) map.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pausePlayback()
        if (::map.isInitialized) map.onDetach()
    }

    private fun applyMapSettings() {
        if (!isAdded) return
        map.setLayerType(if (GpsConfig.isHighFpsEnabled(requireContext())) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_SOFTWARE, null)
        if (GpsConfig.getMapStyle(requireContext()) == GpsConfig.MAP_STYLE_DARK) {
            val matrix = ColorMatrix(); matrix.setSaturation(0f); val scale = 0.5f
            matrix.postConcat(ColorMatrix(floatArrayOf(scale,0f,0f,0f,0f, 0f,scale,0f,0f,0f, 0f,0f,scale,0f,0f, 0f,0f,0f,1f,0f)))
            map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
        } else {
            map.overlayManager.tilesOverlay.setColorFilter(null)
        }
        map.invalidate()
    }

    private fun updateInfoBar(file: File, nameView: TextView, timeView: TextView) {
        val fileName = file.name.replace(".json", "")
        val parts = fileName.split("_")
        
        if (file.name.startsWith("賽道_") && parts.size >= 4) {
            nameView.text = "${parts[1]} - 本地紀錄"
            val rawDate = parts[parts.size - 2]
            val rawTime = parts[parts.size - 1]
            if (rawDate.length == 8 && rawTime.length == 6) {
                timeView.text = "${rawDate.substring(0, 4)}/${rawDate.substring(4, 6)}/${rawDate.substring(6, 8)} ${rawTime.substring(0, 2)}:${rawTime.substring(2, 4)}:${rawTime.substring(4, 6)}"
            } else {
                timeView.text = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(java.util.Date(file.lastModified()))
            }
            return
        }

        if (parts.size >= 4) {
            if (parts.size >= 5) nameView.text = "${parts[2]} - 本地紀錄" else nameView.text = "本地紀錄"
            val rawDate = parts[parts.size - 2]; val rawTime = parts[parts.size - 1]
            if (rawDate.length == 8 && rawTime.length == 6) {
                timeView.text = "${rawDate.substring(0, 4)}/${rawDate.substring(4, 6)}/${rawDate.substring(6, 8)} ${rawTime.substring(0, 2)}:${rawTime.substring(2, 4)}:${rawTime.substring(4, 6)}"
            }
        } else {
            nameView.text = "本地紀錄"
            timeView.text = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(java.util.Date(file.lastModified()))
        }
    }

    private fun loadTrackData(file: File, txtMaxS: TextView, txtDur: TextView, txtMaxL: TextView, txtMaxG: TextView) {
        try {
            val jsonStr = file.readText().trim()
            if (jsonStr.isEmpty()) return
            
            val isSession = if (jsonStr.startsWith("{")) {
                val root = JSONObject(jsonStr)
                root.optJSONObject("header")?.optString("type") == "session"
            } else false

            if (isSession) {
                val root = JSONObject(jsonStr)
                handleSessionData(root, txtMaxS, txtDur, txtMaxL, txtMaxG)
            } else {
                val root = if (jsonStr.startsWith("{")) JSONObject(jsonStr) else JSONObject()
                handleSingleTrackData(root, jsonStr, txtMaxS, txtDur, txtMaxL, txtMaxG)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "解析數據失敗", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSessionData(root: JSONObject, txtMaxS: TextView, txtDur: TextView, txtMaxL: TextView, txtMaxG: TextView) {
        val lapsArray = root.optJSONArray("laps") ?: return
        sessionLapsData.clear()
        val allPointsForBounds = mutableListOf<GeoPoint>()

        for (i in 0 until lapsArray.length()) {
            val lapObj = lapsArray.getJSONObject(i)
            val isComplete = lapObj.optBoolean("c", true)
            val ptsArray = lapObj.optJSONArray("points") ?: continue
            val points = parsePointsArray(ptsArray)
            if (points.isEmpty()) continue
            
            points.forEach { allPointsForBounds.add(GeoPoint(it.lat, it.lng)) }
            val duration = points.last().timestamp - points.first().timestamp
            val maxS = points.maxOf { it.speed }; val minS = points.minOf { it.speed }; val avgS = points.map { it.speed }.average()
            sessionLapsData.add(LapSummary(i + 1, duration, formatTime(duration), "", maxS, minS, avgS, points, isComplete))
        }

        if (sessionLapsData.isEmpty()) return
        
        if (allPointsForBounds.isNotEmpty()) {
            val bounds = org.osmdroid.util.BoundingBox.fromGeoPoints(allPointsForBounds)
            map.post { map.zoomToBoundingBox(bounds, true, 350) }
        }

        val referencePath = Polyline().apply {
            outlinePaint.color = Color.parseColor("#44888888")
            outlinePaint.strokeWidth = 8f
            setPoints(allPointsForBounds)
        }
        map.overlays.add(0, referencePath)

        // 僅從「已完賽」的圈數中計算最快時間
        val completedLaps = sessionLapsData.filter { it.isComplete }
        val bestTime = if (completedLaps.isNotEmpty()) completedLaps.minOf { it.timeMs } else 0L
        
        for (i in sessionLapsData.indices) {
            val lap = sessionLapsData[i]
            val diffStr = when {
                !lap.isComplete -> " (未完賽)"
                bestTime > 0 && lap.timeMs == bestTime -> " (BEST)"
                bestTime > 0 -> " (+${String.format("%.3f", (lap.timeMs - bestTime) / 1000.0)}s)"
                else -> ""
            }
            sessionLapsData[i] = lap.copy(diffStr = diffStr)
        }

        selectedLapNum = 1
        val firstLap = sessionLapsData[0]
        displayLap(firstLap, txtMaxS, txtDur, txtMaxL, txtMaxG)
        showLapListUI()
    }

    private fun handleSingleTrackData(root: JSONObject, jsonStr: String, txtMaxSpeed: TextView, txtDuration: TextView, txtMaxLean: TextView, txtMaxG: TextView) {
        val jsonArray = if (jsonStr.startsWith("{")) root.optJSONArray("points") else JSONArray(jsonStr)
        if (jsonArray == null || jsonArray.length() == 0) return
        
        val points = parsePointsArray(jsonArray)
        if (points.isEmpty()) return
        
        val geoPoints = points.map { GeoPoint(it.lat, it.lng) }
        if (geoPoints.isNotEmpty()) {
            val bounds = org.osmdroid.util.BoundingBox.fromGeoPoints(geoPoints)
            map.post { map.zoomToBoundingBox(bounds, true, 150) }
        }

        val durationMs = points.last().timestamp - points.first().timestamp
        val maxS = points.maxOf { it.speed }; val minS = points.minOf { it.speed }; val avgS = points.map { it.speed }.average()
        
        displayLap(LapSummary(1, durationMs, formatTime(durationMs), "", maxS, minS, avgS, points), txtMaxSpeed, txtDuration, txtMaxLean, txtMaxG)
    }

    private fun displayLap(lap: LapSummary?, txtMaxS: TextView, txtDur: TextView, txtMaxL: TextView, txtMaxG: TextView) {
        allPoints.clear()
        
        predictedMarker?.let { if (::map.isInitialized) map.overlays.remove(it) }
        predictedMarker = null
        replayMarker?.let { if (::map.isInitialized) map.overlays.remove(it) }
        replayMarker = null
        summaryMarker?.let { if (::map.isInitialized) map.overlays.remove(it) }
        summaryMarker = null
        startMarker = null
        finishMarker = null
        dotsOverlay = null
        lastShowAsPoint = null // force updateVisiblePath to rebuild
        
        if (lap == null) {
            trackDiagMeters = 0.0
            txtMaxS.text = "0.0 km/h"; txtDur.text = "00:00.000"; txtMaxL.text = "0.0°"; txtMaxG.text = "0.00G"
            heatmapFolder.items.clear()
            if (::chart.isInitialized) chart.clear()
            if (::map.isInitialized) map.invalidate()
            return
        }

        allPoints.addAll(lap.points)
        if (allPoints.isNotEmpty()) {
            val minLat = allPoints.minOf { it.lat }
            val maxLat = allPoints.maxOf { it.lat }
            val minLng = allPoints.minOf { it.lng }
            val maxLng = allPoints.maxOf { it.lng }
            trackDiagMeters = GeoPoint(minLat, minLng).distanceToAsDouble(GeoPoint(maxLat, maxLng))
        } else {
            trackDiagMeters = 0.0
        }
        txtMaxS.text = String.format("%.1f km/h", lap.maxSpeed)
        txtDur.text = lap.timeStr
        
        val maxLean = if (allPoints.isNotEmpty()) allPoints.maxOf { it.leanAngle } else 0.0
        val maxLatG = if (allPoints.isNotEmpty()) allPoints.maxOf { it.latG } else 0.0
        val maxG = if (allPoints.isNotEmpty()) allPoints.maxOf { it.gForce } else 0.0
        val maxA = if (allPoints.isNotEmpty()) allPoints.maxOf { it.accelG } else 0.0
        val maxB = if (allPoints.isNotEmpty()) allPoints.maxOf { it.brakingG } else 0.0
        
        val hasLatG = allPoints.any { it.latG > 0.01 }
        val titleView = view?.findViewById<TextView>(R.id.txtMaxLeanTitle)
        val cbLean = view?.findViewById<CheckBox>(R.id.cbLean)
        
        if (hasLatG && maxLean < 0.1) {
            titleView?.text = "最大側向 G 力"
            txtMaxL.text = String.format("%.2fG", maxLatG)
            cbLean?.text = "側向G"
        } else {
            titleView?.text = "最大傾角"
            txtMaxL.text = String.format("%.1f°", maxLean)
            cbLean?.text = "傾角"
        }

        // 加入 Log 方便調試 G 力顯示邏輯
        android.util.Log.d("TrackDetail", "G-force debug: maxG=$maxG, maxA=$maxA, maxB=$maxB, pointCount=${allPoints.size}")

        // --- 嚴格區分新舊格式顯示 ---
        if (maxA > 0.01 || maxB > 0.01) {
            // 新版格式：直接顯示加速與煞車分量
            txtMaxG.text = String.format("↑%.2fG ↓%.2fG", maxA, maxB)
        } else {
            // 舊版格式：僅顯示總 G 力
            txtMaxG.text = String.format("%.2f G", maxG)
        }

        if (map.overlays.contains(heatmapFolder)) map.overlays.remove(heatmapFolder)
        map.overlays.add(heatmapFolder)
        heatmapFolder.items.clear()
        
        prepareSegments()
        updateVisiblePath()
        updateChartData(true, false, false)
        map.invalidate()
    }

    private fun showLapListUI() {
        val view = view ?: return
        val container = view.findViewById<LinearLayout>(R.id.lapListContainer) ?: return
        val scrollParent = view.findViewById<View>(R.id.lapListScroll)
        
        scrollParent?.visibility = View.VISIBLE
        container.removeAllViews()
        
        sessionLapsData.forEach { lap ->
            val itemView = LayoutInflater.from(requireContext()).inflate(R.layout.item_lap_summary, container, false)
            val isSelected = lap.lapNum == selectedLapNum
            
            if (isSelected) {
                itemView.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00E5FF"))
                itemView.findViewById<TextView>(R.id.txtLapNum).setTextColor(Color.BLACK)
                itemView.findViewById<TextView>(R.id.txtLapTime).setTextColor(Color.BLACK)
                itemView.findViewById<TextView>(R.id.txtLapSpeeds).setTextColor(Color.parseColor("#333333"))
            }

            itemView.findViewById<TextView>(R.id.txtLapNum).text = "Lap ${lap.lapNum}"
            itemView.findViewById<TextView>(R.id.txtLapTime).text = lap.timeStr + lap.diffStr
            itemView.findViewById<TextView>(R.id.txtLapSpeeds).text = String.format("Max:%.1f / Min:%.1f / Avg:%.1f", lap.maxSpeed, lap.minSpeed, lap.avgSpeed)
            
            itemView.setOnClickListener {
                if (selectedLapNum == lap.lapNum) {
                    selectedLapNum = null
                    displayLap(null, view.findViewById(R.id.txtMaxSpeed), view.findViewById(R.id.txtDuration), view.findViewById(R.id.txtMaxLean), view.findViewById(R.id.txtMaxG))
                } else {
                    selectedLapNum = lap.lapNum
                    displayLap(lap, view.findViewById(R.id.txtMaxSpeed), view.findViewById(R.id.txtDuration), view.findViewById(R.id.txtMaxLean), view.findViewById(R.id.txtMaxG))
                }
                showLapListUI() 
            }
            container.addView(itemView)
        }
    }

    private fun parsePointsArray(jsonArray: JSONArray): List<TrackPoint> {
        val list = mutableListOf<TrackPoint>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            
            // 加入最最最原始的字串 Log，看看 JSON 長怎樣
            if (i < 3) {
                android.util.Log.d("TrackDetail", "Point[$i] absolute raw string: ${obj.toString()}")
            }

            val ga = obj.optDouble("ga", obj.optDouble("accelG", 0.0))
            val gb = obj.optDouble("gb", obj.optDouble("brakingG", 0.0))
            
            // 前 3 筆加入 Log，診斷 ga/gb 是否正確讀取
            if (i < 3) {
                android.util.Log.d("TrackDetail", "Point[$i] raw: has_ga=${obj.has("ga")}, ga=$ga, has_gb=${obj.has("gb")}, gb=$gb, g=${obj.optDouble("g", 0.0)}")
            }
            
            list.add(TrackPoint(
                lat = obj.optDouble("lt", obj.optDouble("lat", 0.0)),
                lng = obj.optDouble("lg", obj.optDouble("lng", 0.0)),
                speed = obj.optDouble("s", obj.optDouble("speed", 0.0)),
                timestamp = obj.optLong("t", obj.optLong("time", 0L)),
                leanAngle = obj.optDouble("l", obj.optDouble("lean", 0.0)),
                latG = obj.optDouble("gl", 0.0), // 解析側向 G 力
                gForce = obj.optDouble("g", obj.optDouble("gForce", 0.0)),
                accelG = ga,
                brakingG = gb,
                distance = obj.optDouble("d", obj.optDouble("dist", 0.0)),
                fx = if (obj.has("fx")) obj.optDouble("fx").toFloat() else null,
                fy = if (obj.has("fy")) obj.optDouble("fy").toFloat() else null,
                fz = if (obj.has("fz")) obj.optDouble("fz").toFloat() else null,
                rx = if (obj.has("rx")) obj.optDouble("rx").toFloat() else null,
                ry = if (obj.has("ry")) obj.optDouble("ry").toFloat() else null,
                rz = if (obj.has("rz")) obj.optDouble("rz").toFloat() else null
            ))
        }
        return list
    }

    private data class MapSegment(val polyline: Polyline, val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double, val maxDist: Double)
    private var smartSegments = mutableListOf<MapSegment>()

    private fun prepareSegments() {
        smartSegments.clear()
        if (allPoints.size < 2) return
        
        // 判斷這筆紀錄是否有新版的加速/煞車分量數據
        val hasDetailedG = allPoints.any { it.accelG > 0.01 || it.brakingG > 0.01 }
        android.util.Log.d("TrackDetail", "prepareSegments: hasDetailedG=$hasDetailedG, points=${allPoints.size}")
        
        var currentPoly = Polyline()
        var lastColor = getSpeedColor(allPoints[0].speed)
        var lastBraking = checkBraking(allPoints[0], hasDetailedG)
        
        currentPoly.outlinePaint.color = lastColor
        currentPoly.outlinePaint.strokeWidth = if (lastBraking) 28f else 13f
        currentPoly.outlinePaint.strokeCap = Paint.Cap.ROUND
        currentPoly.outlinePaint.strokeJoin = Paint.Join.ROUND
        
        var minLat = allPoints[0].lat; var maxLat = allPoints[0].lat
        var minLon = allPoints[0].lng; var maxLon = allPoints[0].lng

        for (i in 0 until allPoints.size) {
            val p = allPoints[i]
            val color = getSpeedColor(p.speed)
            val isBraking = checkBraking(p, hasDetailedG)
            
            if (color != lastColor || isBraking != lastBraking) {
                smartSegments.add(MapSegment(currentPoly, minLat, maxLat, minLon, maxLon, p.distance))
                val prevP = allPoints[maxOf(0, i - 1)]
                currentPoly = Polyline().apply { 
                    outlinePaint.color = color
                    outlinePaint.strokeWidth = if (isBraking) 28f else 13f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    addPoint(GeoPoint(prevP.lat, prevP.lng))
                }
                lastColor = color; lastBraking = isBraking
            }
            currentPoly.addPoint(GeoPoint(p.lat, p.lng))
            minLat = minOf(minLat, p.lat); maxLat = maxOf(maxLat, p.lat); minLon = minOf(minLon, p.lng); maxLon = maxOf(maxLon, p.lng)
        }
        smartSegments.add(MapSegment(currentPoly, minLat, maxLat, minLon, maxLon, allPoints.last().distance))
    }

    /**
     * 判斷某個點是否處於煞車狀態
     * - 新版紀錄 (有 ga/gb)：只看 brakingG >= 0.3
     * - 舊版紀錄 (只有 g)：看 gForce >= 1.2 (排除正常巡航的 1G 地心引力)
     */
    private fun checkBraking(p: TrackPoint, hasDetailedG: Boolean): Boolean {
        // 只有新版紀錄（有 ga/gb 分量）才判斷煞車加粗
        // 舊版紀錄缺少精確分量，不做推測
        return if (hasDetailedG) {
            p.brakingG >= 0.3
        } else {
            false
        }
    }

    private fun updateVisiblePath() {
        if (!isAdded || !::map.isInitialized) return
        val currentZoom = map.zoomLevelDouble
        
        // 動態適應縮放判斷：計算當前縮放下的像素解析度 (Meters Per Pixel)
        val showAsPoint = if (trackDiagMeters > 0.0) {
            val lat = map.mapCenter.latitude
            val mpp = 156543.03392 * kotlin.math.cos(Math.toRadians(lat)) / Math.pow(2.0, currentZoom)
            val sizeInPixels = trackDiagMeters / mpp
            sizeInPixels < 120.0 // 如果軌跡在螢幕上的投影大小小於 120 像素，才縮成一個小點
        } else {
            currentZoom < 14.0
        }
        
        if (lastShowAsPoint == showAsPoint) {
            return
        }
        
        lastShowAsPoint = showAsPoint
        heatmapFolder.items.clear()
        
        if (showAsPoint) {
            summaryMarker?.let { map.overlays.remove(it) }
            if (allPoints.isNotEmpty()) {
                val centerPt = allPoints[allPoints.size / 2]
                summaryMarker = Marker(map).apply {
                    position = GeoPoint(centerPt.lat, centerPt.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = BitmapDrawable(resources, createCircleMarker(Color.parseColor("#00E5FF"), 60, false))
                    setOnMarkerClickListener { _, _ -> 
                        if (allPoints.isNotEmpty()) {
                            val bounds = org.osmdroid.util.BoundingBox.fromGeoPoints(allPoints.map { GeoPoint(it.lat, it.lng) })
                            map.zoomToBoundingBox(bounds, true, 150)
                        } else {
                            map.controller.animateTo(position, 16.0, 500L)
                        }
                        true 
                    }
                }
                map.overlays.add(summaryMarker)
            }
        } else {
            summaryMarker?.let { map.overlays.remove(it) }
            
            // 1. 繪製彩色路徑段 (完整繪製，不依播放進度過濾)
            smartSegments.forEach { seg ->
                heatmapFolder.add(seg.polyline)
            }

            // 2. 重新繪製起點與終點標記
            if (allPoints.isNotEmpty()) {
                if (startMarker == null) {
                    startMarker = Marker(map).apply {
                        position = GeoPoint(allPoints.first().lat, allPoints.first().lng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = BitmapDrawable(resources, createCircleMarker(Color.WHITE, 30, true))
                        title = "START"
                    }
                }
                if (finishMarker == null) {
                    finishMarker = Marker(map).apply {
                        position = GeoPoint(allPoints.last().lat, allPoints.last().lng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = BitmapDrawable(resources, createCircleMarker(Color.CYAN, 30, true))
                        title = "FINISH"
                    }
                }
                heatmapFolder.add(startMarker)
                heatmapFolder.add(finishMarker)
            }
            
            // 3. 繪製偵錯用紀錄點 (如果開啟)
            if (GpsConfig.isShowTrackDots(requireContext())) {
                if (dotsOverlay == null) {
                    dotsOverlay = object : org.osmdroid.views.overlay.Overlay() {
                        private val p = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
                        private val pt = android.graphics.Point()
                        override fun draw(canvas: Canvas, map: org.osmdroid.views.MapView, shadow: Boolean) {
                            if (shadow) return
                            val proj = map.projection
                            allPoints.forEach { point ->
                                proj.toPixels(GeoPoint(point.lat, point.lng), pt)
                                canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 4f, p)
                            }
                        }
                    }
                }
                heatmapFolder.add(dotsOverlay)
            }
        }

        bringReplayMarkersToFront()
    }

    private fun bringReplayMarkersToFront() {
        if (!::map.isInitialized) return
        replayMarker?.let {
            map.overlays.remove(it)
            map.overlays.add(it)
        }
        predictedMarker?.let {
            if (map.overlays.contains(it)) {
                map.overlays.remove(it)
                map.overlays.add(it)
            }
        }
        map.invalidate()
    }

    private class LabelNode(val originalY: Float, var adjustedY: Float, val text: String, val color: Int, val isLight: Boolean)

    inner class AxisMarkerView(context: android.content.Context) : com.github.mikephil.charting.components.MarkerView(context, R.layout.fragment_track_detail) {
        private val bgPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
        private val textPaint = Paint().apply { color = Color.WHITE; textSize = 30f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT; isAntiAlias = true }
        private val linePaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true; pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f) }

        override fun draw(canvas: Canvas, posX: Float, posY: Float) {
            val vph = chart.viewPortHandler
            selectedEntry?.let { entry ->
                val activeSets = chart.data?.dataSets ?: return
                val labels = mutableListOf<LabelNode>()

                linePaint.color = Color.WHITE; linePaint.alpha = 200
                canvas.drawLine(posX, vph.contentTop(), posX, vph.contentBottom(), linePaint)

                activeSets.forEach { set ->
                    val yVal = set.getEntryForXValue(entry.x, Float.NaN, com.github.mikephil.charting.data.DataSet.Rounding.CLOSEST)?.y ?: return@forEach
                    val screenY = chart.getTransformer(set.axisDependency).getPixelForValues(entry.x, yVal).y.toFloat()
                    
                    linePaint.color = set.color; linePaint.alpha = 200
                    canvas.drawLine(vph.contentLeft(), screenY, vph.contentRight(), screenY, linePaint)

                    // 畫出中心圓形定位點
                    val circlePaint = Paint().apply { color = set.color; style = Paint.Style.FILL; isAntiAlias = true }
                    canvas.drawCircle(posX, screenY, 10f, circlePaint)
                    val strokePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
                    canvas.drawCircle(posX, screenY, 10f, strokePaint)

                    val isLightColor = set.color == Color.GREEN || set.color == Color.YELLOW || set.color == Color.CYAN
                    val yLabel = if (kotlin.math.abs(yVal) < 5f) String.format(Locale.getDefault(), "%.2f", yVal) else String.format(Locale.getDefault(), "%.1f", yVal)
                    labels.add(LabelNode(screenY, screenY, yLabel, set.color, isLightColor))
                }

                labels.sortBy { it.originalY }
                val minGap = 52f
                for (i in 1 until labels.size) { if (labels[i].adjustedY - labels[i-1].adjustedY < minGap) labels[i].adjustedY = labels[i-1].adjustedY + minGap }

                labels.forEach { label ->
                    bgPaint.color = label.color
                    val yRectW = textPaint.measureText(label.text) + 20f
                    canvas.drawRect(vph.contentLeft() - yRectW, label.adjustedY - 25f, vph.contentLeft(), label.adjustedY + 25f, bgPaint)
                    textPaint.color = if (label.isLight) Color.BLACK else Color.WHITE
                    canvas.drawText(label.text, vph.contentLeft() - 8f, label.adjustedY + 10f, textPaint)
                }

                // 畫出距離 X 軸標籤 (距離也要顯示)
                val distStr = String.format(Locale.getDefault(), "%.1f m", entry.x)
                bgPaint.color = Color.parseColor("#444444")
                val xRectW = textPaint.measureText(distStr) + 20f
                val bottomY = vph.contentBottom()
                canvas.drawRect(posX - xRectW/2, bottomY, posX + xRectW/2, bottomY + 50f, bgPaint)
                textPaint.color = Color.WHITE
                canvas.drawText(distStr, posX + xRectW/2 - 10f, bottomY + 35f, textPaint)
            }
        }
    }

    private fun setupMap() {
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(false)
        map.addMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(e: org.osmdroid.events.ScrollEvent?): Boolean { updateVisiblePath(); return true }
            override fun onZoom(e: org.osmdroid.events.ZoomEvent?): Boolean { updateVisiblePath(); return true }
        })

        var startX = 0f
        var startY = 0f
        var isPinching = false
        var wasPinching = false
        map.setOnTouchListener { v, event ->
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
                    if (!isPinching && !wasPinching && isMapFollowMode) {
                        val dx = event.x - startX
                        val dy = event.y - startY
                        if (kotlin.math.sqrt((dx * dx + dy * dy).toDouble()) > 20.0) {
                            isMapFollowMode = false
                        }
                    }
                }
                android.view.MotionEvent.ACTION_POINTER_UP -> {
                    isPinching = false
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    isPinching = false
                    wasPinching = false
                }
            }
            false
        }
    }

    private fun setupChart() {
        chart.apply {
            description.isEnabled = false; setTouchEnabled(true); isDragEnabled = true; isHighlightPerDragEnabled = true; setScaleXEnabled(true); setScaleYEnabled(false)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = Color.LTGRAY
            axisLeft.textColor = Color.LTGRAY
            legend.isEnabled = false
            axisRight.isEnabled = false
            marker = AxisMarkerView(requireContext()); setExtraOffsets(10f, 10f, 25f, 0f)
            
            // --- 核心修正：拖動圖表同步地圖點位 ---
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e == null) return
                    
                    // 1. 如果不是程式自動播放觸發的 (即使用者手指觸摸)，則暫停播放
                    if (isPlaying && !isProgrammaticHighlight) pausePlayback()
                    
                    selectedEntry = e
                    val targetDist = e.x.toDouble()
                    currentReplayDist = targetDist // 控制軌跡顯隱
                    
                    // 2. 找出最接近此里程的點位
                    val closestIdx = allPoints.indexOfFirst { it.distance >= targetDist }
                    if (closestIdx != -1) {
                        // 如果是手動拖動，同步索引以免播放跳回起點
                        if (!isProgrammaticHighlight) currentPlayIndex = closestIdx
                        
                        val point = allPoints[closestIdx]
                        updateReplayPosition(point)
                        
                        // 只有在手動拖動時才頻繁重繪彩色段落，以確保效能
                        // 播放時則由 updateReplayPosition 內部調用 bringReplayMarkersToFront
                        updateVisiblePath() 
                        
                        // 如果是手動拖動，即時更新面板數據
                        if (!isProgrammaticHighlight) updateInfoCardValues(point)
                        
                        if (isMapFollowMode) {
                            map.controller.setCenter(GeoPoint(point.lat, point.lng))
                        }
                    }
                }
                override fun onNothingSelected() { 
                    selectedEntry = null
                    if (!isPlaying) {
                        currentReplayDist = Double.MAX_VALUE
                        updateVisiblePath()
                    }
                }
            })
        }
    }

    private fun createPlayerIcon(mainColor: Int = Color.parseColor("#00E5FF")): Bitmap {
        val d = resources.displayMetrics.density; val s = (32 * d).toInt(); val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        Canvas(b).apply { val p = Paint(Paint.ANTI_ALIAS_FLAG); p.color = mainColor; p.alpha = 80; drawCircle(s/2f, s/2f, s/2.2f, p); p.style = Paint.Style.FILL; p.color = Color.WHITE; p.alpha = 255; drawCircle(s/2f, s/2f, s/5f, p); p.style = Paint.Style.STROKE; p.strokeWidth = 2*d; p.color = mainColor; drawCircle(s/2f, s/2f, s/4f, p) }
        return b
    }

    private fun createPredictedIcon(): Bitmap {
        val d = resources.displayMetrics.density; val s = (32 * d).toInt(); val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        Canvas(b).apply { val p = Paint(Paint.ANTI_ALIAS_FLAG); p.color = Color.parseColor("#FFD600"); p.style = Paint.Style.STROKE; p.strokeWidth = 3*d; drawCircle(s/2f, s/2f, s/4f, p) }
        return b
    }

    private fun updateReplayPosition(point: TrackPoint) {
        val targetPoint = GeoPoint(point.lat, point.lng)
        if (replayMarker == null) {
            replayMarker = Marker(map); replayMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            replayMarker?.icon = BitmapDrawable(resources, createPlayerIcon(Color.parseColor("#00E5FF")))
            map.overlays.add(replayMarker)
            replayMarker?.position = targetPoint
        } else {
            if (isPlaying) {
                val startPoint = replayMarker!!.position
                animateMarker(replayMarker!!, startPoint, targetPoint, 200L)
            } else {
                markerAnimator?.cancel()
                replayMarker!!.position = targetPoint
            }
        }
        
        if (point.fx != null && GpsConfig.isSensorPredictUnlockEnabled(requireContext())) {
            if (predictedMarker == null) {
                predictedMarker = Marker(map).apply {
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = BitmapDrawable(resources, createPredictedIcon())
                    setOnMarkerClickListener { _, _ -> true }
                }
                map.overlays.add(predictedMarker)
            }
            
            val idx = allPoints.indexOf(point)
            val bearing = if (idx < allPoints.size - 1) {
                GeoPoint(point.lat, point.lng).bearingTo(GeoPoint(allPoints[idx+1].lat, allPoints[idx+1].lng))
            } else if (idx > 0) {
                GeoPoint(allPoints[idx-1].lat, allPoints[idx-1].lng).bearingTo(GeoPoint(point.lat, point.lng))
            } else 0.0
            
            val dt = 1.0
            val a = (point.accelG - point.brakingG) * 9.80665
            val v0 = point.speed / 3.6
            val displacement = v0 * dt + 0.5 * a * dt * dt
            
            val bearingRad = Math.toRadians(bearing)
            val dLat = Math.toDegrees(displacement * Math.cos(bearingRad) / 6378137.0)
            val dLng = Math.toDegrees(displacement * Math.sin(bearingRad) / (6378137.0 * Math.cos(Math.toRadians(point.lat))))
            
            val targetPredPoint = GeoPoint(point.lat + dLat, point.lng + dLng)
            if (isPlaying) {
                val startPredPoint = predictedMarker!!.position ?: targetPredPoint
                animatePredictedMarker(predictedMarker!!, startPredPoint, targetPredPoint, 200L)
            } else {
                predictedMarkerAnimator?.cancel()
                predictedMarker!!.position = targetPredPoint
            }
            
            if (!map.overlays.contains(predictedMarker)) map.overlays.add(predictedMarker)
        } else {
            predictedMarker?.let { map.overlays.remove(it) }
        }

        bringReplayMarkersToFront()
    }

    private fun formatTime(ms: Long): String {
        val minutes = (ms / 60000) % 60; val seconds = (ms / 1000) % 60; val millis = ms % 1000
        return String.format(Locale.getDefault(), "%02d:%02d.%03d", minutes, seconds, millis)
    }

    private fun getSpeedColor(speed: Double): Int {
        // 1 km/h 量化 → 精細漸層，搭配 BUTT cap 避免視覺膨脹
        val ratio = (speed / 120.0).coerceIn(0.0, 1.0).toFloat()
        // HSV 色相：240(藍) → 120(綠) → 60(黃) → 0(紅)
        val hue = 240f - (ratio * 240f)
        return Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
    }

    private fun addMarker(point: GeoPoint, title: String, iconBitmap: Bitmap) {
        val marker = Marker(map)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.icon = BitmapDrawable(resources, iconBitmap)
        marker.title = title
        heatmapFolder.add(marker)
    }

    private fun createCircleMarker(color: Int, size: Int, hollow: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap); val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        if (hollow) { paint.style = Paint.Style.STROKE; paint.strokeWidth = size / 4f; canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint) }
        else { paint.style = Paint.Style.FILL; canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint) }
        return bitmap
    }

    private fun setupFilters(view: View) {
        val cbSpeed = view.findViewById<CheckBox>(R.id.cbSpeed)
        val cbLean = view.findViewById<CheckBox>(R.id.cbLean)
        val cbG = view.findViewById<CheckBox>(R.id.cbG)
        val listener = View.OnClickListener { _ -> updateChartData(cbSpeed.isChecked, cbLean.isChecked, cbG.isChecked) }
        cbSpeed?.setOnClickListener(listener)
        cbLean?.setOnClickListener(listener)
        cbG?.setOnClickListener(listener)
    }

    private fun updateChartData(showSpeed: Boolean, showLean: Boolean, showG: Boolean) {
        if (!::chart.isInitialized) return
        val lastX = selectedEntry?.x
        chart.highlightValues(null) // 先清除高亮，避免內部狀態錯誤
        selectedEntry = null
        val data = LineData()
        if (showSpeed && allPoints.isNotEmpty()) {
            data.addDataSet(LineDataSet(allPoints.map { Entry(it.distance.toFloat(), it.speed.toFloat()) }, "速度").apply { 
                color = Color.GREEN; setDrawCircles(false); mode = LineDataSet.Mode.CUBIC_BEZIER; setDrawValues(false)
                lineWidth = 2f; setDrawHighlightIndicators(false)
            })
        }
        if (showLean && allPoints.isNotEmpty()) {
            val hasLatG = allPoints.any { it.latG > 0.01 }
            val isCarMode = hasLatG && allPoints.maxOf { it.leanAngle } < 0.1
            
            val entries = if (isCarMode) {
                allPoints.map { Entry(it.distance.toFloat(), it.latG.toFloat()) }
            } else {
                allPoints.map { Entry(it.distance.toFloat(), it.leanAngle.toFloat()) }
            }
            val labelStr = if (isCarMode) "側向G" else "傾角"
            
            data.addDataSet(LineDataSet(entries, labelStr).apply { 
                color = Color.MAGENTA; setDrawCircles(false); mode = LineDataSet.Mode.CUBIC_BEZIER; setDrawValues(false)
                lineWidth = 2f; setDrawHighlightIndicators(false)
            })
        }
        if (showG && allPoints.isNotEmpty()) {
            // 智慧決定繪製加速分量還是總 G 力
            val hasDetailedG = allPoints.any { it.accelG > 0.05 || it.brakingG > 0.05 }
            val gEntries = if (hasDetailedG) {
                // 如果有加速分量，則繪製 (加速 - 煞車) 的縱向變化
                allPoints.map { Entry(it.distance.toFloat(), (it.accelG - it.brakingG).toFloat()) }
            } else {
                // 如果只有總 G 力 (舊版)，則繪製總 G 力
                allPoints.map { Entry(it.distance.toFloat(), it.gForce.toFloat()) }
            }
            
            data.addDataSet(LineDataSet(gEntries, "G力").apply {
                color = Color.YELLOW; setDrawCircles(false); mode = LineDataSet.Mode.CUBIC_BEZIER; setDrawValues(false)
                lineWidth = 2f; setDrawHighlightIndicators(false)
            })
        }
        chart.data = if (data.dataSetCount > 0) data else null
        
        // 恢復先前的選取點
        if (lastX != null && data.dataSetCount > 0) {
            chart.highlightValue(lastX, 0)
        }
        
        chart.invalidate()
    }

    private fun toggleAnalysisMode(show: Boolean) {
        analysisView.visibility = if (show) View.VISIBLE else View.GONE
        infoCard.visibility = if (show) View.GONE else View.VISIBLE
        btnAnalyzeContainer.visibility = View.VISIBLE
        
        val lapListScroll = view?.findViewById<View>(R.id.lapListScroll)
        if (lapListScroll != null) {
            val scrollParams = lapListScroll.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            if (show) {
                scrollParams.bottomToTop = R.id.analysisView
            } else {
                scrollParams.bottomToTop = R.id.infoCard
            }
            lapListScroll.layoutParams = scrollParams
        }
        
        val params = btnAnalyzeContainer.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        if (show) {
            params.bottomToTop = if (sessionLapsData.isNotEmpty()) R.id.lapListScroll else R.id.analysisView
        } else {
            params.bottomToTop = if (sessionLapsData.isNotEmpty()) R.id.lapListScroll else R.id.infoCard
        }
        btnAnalyzeContainer.layoutParams = params
    }

    private fun showShareCardDialog(name: String, time: String, speed: String, duration: String, lean: String, gForce: String) {
        if (!isAdded) return
        val ctx = requireContext()
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_share_card, null)
        val dialog = android.app.AlertDialog.Builder(ctx, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setView(dialogView).create()

        // ─── 數據清單（可拖曳重排）────────────────────────────────────────
        val metricData = mutableListOf(
            Pair("最高時速", speed),
            Pair("紀錄時間", duration),
            Pair("最大傾角", lean),
            Pair("最大 G 力", gForce)
        )

        // ─── 主題定義 ─────────────────────────────────────────────────────
        data class CardTheme(val bg: Int, val accent: Int, val label: String)
        val themes = listOf(
            CardTheme(Color.parseColor("#1A1A1A"), Color.parseColor("#00E5FF"), "深夜"),
            CardTheme(Color.parseColor("#0A0A0A"), Color.parseColor("#FF5252"), "碳纖維"),
            CardTheme(Color.parseColor("#1A1200"), Color.parseColor("#FFD600"), "賽道金")
        )
        val styleNames = listOf("極簡", "賽道風", "廣告風")

        var currentStyle = 0
        var currentTheme = 0
        var isLandscape = false

        // ─── 取得 View 參考 ───────────────────────────────────────────────
        val captureArea    = dialogView.findViewById<FrameLayout>(R.id.shareCardCaptureArea)
        val cardBg         = dialogView.findViewById<View>(R.id.cardBgMain)
        val cardBrandLine  = dialogView.findViewById<View>(R.id.cardBrandLine)
        val cardHeader     = dialogView.findViewById<TextView>(R.id.cardHeader)
        val cardTrackInfo  = dialogView.findViewById<TextView>(R.id.cardTrackInfo)
        val cardTime       = dialogView.findViewById<TextView>(R.id.cardTime)
        val cardMetrics    = dialogView.findViewById<FrameLayout>(R.id.cardMetricsContainer)
        val trackContainer = dialogView.findViewById<FrameLayout>(R.id.trackPathContainer)
        val badgeLimitBreak = dialogView.findViewById<TextView>(R.id.badgeLimitBreak)
        val cardLogo       = dialogView.findViewById<TextView>(R.id.cardLogo)
        val cardDivider    = dialogView.findViewById<View>(R.id.cardDivider)
        val cardFooter     = dialogView.findViewById<TextView>(R.id.cardFooter)
        val decoStrip      = dialogView.findViewById<View>(R.id.cardDecorationStrip)
        val rgOrientation  = dialogView.findViewById<RadioGroup>(R.id.rgOrientation)
        val spinnerStyle   = dialogView.findViewById<Spinner>(R.id.spinnerLayoutStyle)
        val spinnerTheme   = dialogView.findViewById<Spinner>(R.id.spinnerColorTheme)
        val dragSortContainer = dialogView.findViewById<LinearLayout>(R.id.dragSortContainer)

        // ─── 靜態文字 ─────────────────────────────────────────────────────
        cardHeader?.text = name
        cardTime?.text = time

        // ─── TrackPathView 注入 ───────────────────────────────────────────
        val trackPathView = TrackPathView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            points = allPoints
            colorMode = GpsConfig.getSpeedColorMode(ctx)
            if (colorMode == GpsConfig.SPEED_COLOR_DYNAMIC && allPoints.isNotEmpty()) {
                dynamicMin = allPoints.minOf { it.speed }
                dynamicMax = allPoints.maxOf { it.speed }.coerceAtLeast(dynamicMin + 1.0)
            }
        }
        trackContainer?.addView(trackPathView)

        // ─── 數據欄位建立（含拖曳排序）───────────────────────────────────
        fun rebuildMetrics() {
            cardMetrics?.removeAllViews()
            dragSortContainer?.removeAllViews()

            // 卡片內顯示區
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            metricData.forEachIndexed { idx, (title, value) ->
                val cell = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    gravity = android.view.Gravity.CENTER
                }
                cell.addView(TextView(ctx).apply {
                    text = value; setTextColor(Color.WHITE)
                    textSize = 14f; setTypeface(null, Typeface.BOLD)
                })
                cell.addView(TextView(ctx).apply {
                    text = title; setTextColor(Color.parseColor("#88FFFFFF"))
                    textSize = 10f; setPadding(0, 6, 0, 0)
                })
                row.addView(cell)

                // 拖曳排序 chip
                val chip = TextView(ctx).apply {
                    text = title; setTextColor(Color.WHITE); textSize = 11f
                    setPadding(20, 10, 20, 10)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#2A2A2A")); cornerRadius = 24f
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(4, 0, 4, 0) }
                    tag = idx
                }
                chip.setOnLongClickListener { v ->
                    val shadow = View.DragShadowBuilder(v)
                    v.startDragAndDrop(ClipData.newPlainText("idx", idx.toString()), shadow, v, 0)
                    true
                }
                chip.setOnDragListener { targetView, event ->
                    when (event.action) {
                        DragEvent.ACTION_DROP -> {
                            val fromIdx = (event.localState as? View)?.tag as? Int ?: return@setOnDragListener false
                            val toIdx   = targetView.tag as? Int ?: return@setOnDragListener false
                            if (fromIdx != toIdx) {
                                val moved = metricData.removeAt(fromIdx); metricData.add(toIdx, moved)
                                rebuildMetrics()
                            }
                            true
                        }
                        DragEvent.ACTION_DRAG_ENTERED -> { targetView.alpha = 0.5f; true }
                        DragEvent.ACTION_DRAG_EXITED,
                        DragEvent.ACTION_DRAG_ENDED   -> { targetView.alpha = 1f;   true }
                        else -> true
                    }
                }
                dragSortContainer?.addView(chip)
            }
            cardMetrics?.addView(row)
        }
        rebuildMetrics()

        // ─── 套用樣式 & 主題 ──────────────────────────────────────────────
        fun applyCard() {
            val theme = themes[currentTheme]
            cardBg?.setBackgroundColor(theme.bg)
            cardBrandLine?.setBackgroundColor(theme.accent)
            trackPathView.accentColor = theme.accent
            cardHeader?.setTextColor(Color.WHITE)
            cardTrackInfo?.setTextColor(Color.parseColor("#88FFFFFF"))
            cardTime?.setTextColor(Color.parseColor("#88FFFFFF"))
            cardLogo?.setTextColor(Color.parseColor("#44FFFFFF"))
            cardFooter?.setTextColor(Color.parseColor("#33FFFFFF"))
            cardDivider?.setBackgroundColor(Color.parseColor("#22FFFFFF"))
            cardDivider?.alpha = 1f
            decoStrip?.setBackgroundColor(Color.WHITE)

            when (currentStyle) {
                0 -> { // 極簡 Minimal
                    badgeLimitBreak?.visibility = View.GONE
                    decoStrip?.visibility = View.VISIBLE
                    decoStrip?.alpha = 0.07f
                    cardBrandLine?.setBackgroundColor(theme.accent)
                }
                1 -> { // 賽道風 Racing
                    badgeLimitBreak?.visibility = View.GONE
                    decoStrip?.visibility = View.GONE
                    // 分隔線改為強調色
                    cardDivider?.setBackgroundColor(theme.accent)
                    cardDivider?.alpha = 0.8f
                }
                2 -> { // 廣告風 Poster
                    badgeLimitBreak?.visibility = View.VISIBLE
                    badgeLimitBreak?.setBackgroundColor(theme.accent)
                    badgeLimitBreak?.setTextColor(
                        if (currentTheme == 2) Color.BLACK else Color.WHITE
                    )
                    decoStrip?.visibility = View.VISIBLE
                    decoStrip?.setBackgroundColor(theme.accent)
                    decoStrip?.alpha = 0.18f
                }
            }
        }

        // ─── 比例切換 ─────────────────────────────────────────────────────
        fun applyOrientation() {
            val dp = resources.displayMetrics.density
            captureArea?.layoutParams?.height = if (isLandscape) (200 * dp).toInt() else (380 * dp).toInt()
            captureArea?.requestLayout()
            // 縮放比例改變後重置軌跡視角
            trackPathView.resetView()
        }

        applyCard()

        // ─── Spinner 適配器 ───────────────────────────────────────────────
        spinnerStyle?.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, styleNames)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerStyle?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                currentStyle = pos; applyCard()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        spinnerTheme?.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, themes.map { it.label })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerTheme?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                currentTheme = pos; applyCard()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // ─── 比例 RadioGroup ─────────────────────────────────────────────
        rgOrientation?.setOnCheckedChangeListener { _, checkedId ->
            isLandscape = (checkedId == R.id.rbLandscape)
            applyOrientation()
        }

        // ─── 分享按鈕（截圖 → Intent）────────────────────────────────────
        dialogView.findViewById<View>(R.id.btnCancelShare)?.setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btnPerformShare)?.setOnClickListener {
            val area = captureArea ?: run {
                Toast.makeText(ctx, "截圖區域不存在", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            try {
                // 建立與 captureArea 相同尺寸的 Bitmap
                val bmp = Bitmap.createBitmap(area.width, area.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                area.draw(canvas)

                val cacheDir = File(ctx.cacheDir, "share").also { it.mkdirs() }
                val shareFile = File(cacheDir, "track_${System.currentTimeMillis()}.png")
                shareFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", shareFile)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(android.content.Intent.createChooser(intent, "分享成績曬單"))
                dialog.dismiss()
            } catch (e: Exception) {
                Toast.makeText(ctx, "分享失敗：${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        dialog.show()
    }
}
