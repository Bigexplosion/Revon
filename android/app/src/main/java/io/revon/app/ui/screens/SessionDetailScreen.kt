package io.revon.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.revon.app.data.repository.TrackSessionManager
import io.revon.app.ui.theme.DarkBackground
import io.revon.app.ui.theme.DarkSurface
import io.revon.app.ui.theme.NeonGreen
import io.revon.app.ui.theme.RedPrimary
import kotlinx.coroutines.delay
import io.revon.app.data.config.GpsConfig
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.Polyline as OsmPolyline
import java.util.Locale

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.res.stringResource
import io.revon.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val record = remember(sessionId) { TrackSessionManager.getSessionById(context, sessionId) }

    var markerToastText by remember { mutableStateOf<String?>(null) }

    if (record == null) {
        Box(modifier = Modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.race_session_not_found), color = Color.White)
        }
        return
    }

    var selectedLapNumber by remember { mutableIntStateOf(0) }
    val activePoints = remember(record, selectedLapNumber) {
        if (selectedLapNumber == 0 || record.safeLaps.isEmpty()) {
            record.safePointsList
        } else {
            val foundLap = record.safeLaps.find { it.lapNumber == selectedLapNumber }
            foundLap?.safePointsList?.takeIf { it.isNotEmpty() } ?: record.safePointsList
        }
    }
    val points = activePoints

    var isPlaying by remember { mutableStateOf(false) }
    var currentFrameIndex by remember { mutableStateOf(0) }
    var replaySpeed by remember { mutableStateOf(1) } // 1x, 2x, 4x
    var showRawPoints by remember(context) { mutableStateOf(GpsConfig.isShowTrackDots(context)) }

    LaunchedEffect(selectedLapNumber) {
        currentFrameIndex = 0
        isPlaying = false
    }

    var userMarkerInstance by remember { mutableStateOf<OsmMarker?>(null) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(isPlaying, replaySpeed) {
        if (isPlaying && points.isNotEmpty()) {
            while (currentFrameIndex < points.size - 1 && isPlaying) {
                val p1 = points[currentFrameIndex]
                val p2 = points[currentFrameIndex + 1]
                val rawDiff = (p2.timestampMs - p1.timestampMs).coerceIn(20L, 500L)
                val duration = (rawDiff / (replaySpeed * 3)).coerceAtLeast(16L)

                var animator: android.animation.ValueAnimator? = null

                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                            setDuration(duration)
                            addUpdateListener { anim ->
                                val fraction = anim.animatedValue as Float
                                val lat = p1.latitude + (p2.latitude - p1.latitude) * fraction
                                val lng = p1.longitude + (p2.longitude - p1.longitude) * fraction
                                userMarkerInstance?.position = GeoPoint(lat, lng)
                                mapViewInstance?.invalidate()
                            }
                        }
                        animator?.start()
                    }
                    
                    delay(duration)
                } finally {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        animator?.cancel()
                    }
                }

                if (isPlaying) {
                    currentFrameIndex++
                }
            }
            if (currentFrameIndex >= points.size - 1) {
                isPlaying = false
            }
        }
    }

    val currentPoint = points.getOrNull(currentFrameIndex)

    var showJsonDialog by remember { mutableStateOf(false) }

    val handleSessionDetailBack = remember {
        {
            isPlaying = false
            onBack()
        }
    }

    androidx.activity.compose.BackHandler(onBack = handleSessionDetailBack)

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = record.trackName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(text = "${record.recordedAt} · ${record.playerNickname}", color = Color.Gray, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = handleSessionDetailBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = { showJsonDialog = true }) {
                        Text("📥 偵錯 JSON", color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { innerPadding ->
        Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.DarkGray, shape = RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val mapEngine = remember(context) { GpsConfig.getMapEngine(context) }
                if (mapEngine == GpsConfig.MAP_ENGINE_GOOGLE || mapEngine == GpsConfig.MAP_ENGINE_GOOGLE_3D) {
                    val cameraPositionState = rememberCameraPositionState()

                    val darkMapStyle = remember {
                        MapStyleOptions(
                            """
                            [
                              {"elementType": "geometry", "stylers": [{"color": "#212121"}]},
                              {"elementType": "labels.icon", "stylers": [{"visibility": "off"}]},
                              {"elementType": "labels.text.fill", "stylers": [{"color": "#757575"}]},
                              {"elementType": "labels.text.stroke", "stylers": [{"color": "#212121"}]},
                              {"featureType": "administrative", "elementType": "geometry", "stylers": [{"color": "#757575"}]},
                              {"featureType": "poi", "elementType": "labels.text.fill", "stylers": [{"color": "#757575"}]},
                              {"featureType": "road", "elementType": "geometry.fill", "stylers": [{"color": "#2c2c2c"}]},
                              {"featureType": "road", "elementType": "labels.text.fill", "stylers": [{"color": "#8a8a8a"}]},
                              {"featureType": "water", "elementType": "geometry", "stylers": [{"color": "#000000"}]}
                            ]
                            """.trimIndent()
                        )
                    }

                    LaunchedEffect(points) {
                        if (points.isNotEmpty()) {
                            val builder = LatLngBounds.builder()
                            points.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
                            try {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), 80))
                            } catch (e: Exception) {
                                cameraPositionState.position = CameraPosition.fromLatLngZoom(
                                    LatLng(points.first().latitude, points.first().longitude), 15.5f
                                )
                            }
                        }
                    }

                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = false,
                            compassEnabled = false,
                            myLocationButtonEnabled = false
                        ),
                        properties = MapProperties(
                            mapType = com.google.maps.android.compose.MapType.NORMAL,
                            mapStyleOptions = null
                        )
                    ) {
                        if (points.isNotEmpty()) {
                            val firstPt = LatLng(points.first().latitude, points.first().longitude)
                            val lastPt = LatLng(points.last().latitude, points.last().longitude)
                            val isLoopTrack = io.revon.app.data.repository.TrackRepository.haversineDistance(
                                points.first().latitude, points.first().longitude,
                                points.last().latitude, points.last().longitude
                            ) < 0.02

                            val startIcon = remember(context) {
                                try {
                                    com.google.android.gms.maps.MapsInitializer.initialize(context)
                                    BitmapDescriptorFactory.fromBitmap(createCircleMarker(android.graphics.Color.parseColor("#4CAF50"), 40, hollow = true).bitmap)
                                } catch (e: Exception) {
                                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                                }
                            }
                            val finishIcon = remember(context) {
                                try {
                                    com.google.android.gms.maps.MapsInitializer.initialize(context)
                                    BitmapDescriptorFactory.fromBitmap(createCircleMarker(android.graphics.Color.parseColor("#FF5252"), 40, hollow = true).bitmap)
                                } catch (e: Exception) {
                                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                                }
                            }
                            val playerIcon = remember(context) {
                                try {
                                    com.google.android.gms.maps.MapsInitializer.initialize(context)
                                    BitmapDescriptorFactory.fromBitmap(createPlayerIcon(context, android.graphics.Color.parseColor("#00E5FF")).bitmap)
                                } catch (e: Exception) {
                                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)
                                }
                            }

                            // 起點
                            Marker(
                                state = rememberMarkerState(position = firstPt),
                                icon = startIcon,
                                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                title = "起點 S",
                                onClick = {
                                    markerToastText = "📍 起點 S (START)\n座標：${String.format(Locale.getDefault(), "%.5f, %.5f", firstPt.latitude, firstPt.longitude)}"
                                    true
                                }
                            )

                            // 終點
                            if (!isLoopTrack) {
                                Marker(
                                    state = rememberMarkerState(position = lastPt),
                                    icon = finishIcon,
                                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                    title = "終點 E",
                                    onClick = {
                                        markerToastText = "🏁 終點 E (FINISH)\n座標：${String.format(Locale.getDefault(), "%.5f, %.5f", lastPt.latitude, lastPt.longitude)}"
                                        true
                                    }
                                )
                            }

                            // Heatmap segments
                            for (i in 0 until points.size - 1) {
                                val p1 = points[i]
                                val p2 = points[i + 1]
                                val segColor = Color(getSpeedHsvColor(p1.speedKmh))
                                Polyline(
                                    points = listOf(LatLng(p1.latitude, p1.longitude), LatLng(p2.latitude, p2.longitude)),
                                    color = segColor,
                                    width = 12f
                                )
                            }

                            // 顯示 GPS 紀錄點位小圓點
                            if (showRawPoints) {
                                points.forEach { pt ->
                                    Circle(
                                        center = LatLng(pt.latitude, pt.longitude),
                                        radius = 1.2,
                                        fillColor = Color.White,
                                        strokeColor = NeonGreen,
                                        strokeWidth = 2f
                                    )
                                }
                            }

                            // 車輛定位 Marker
                            val currentPos = if (currentPoint != null) LatLng(currentPoint.latitude, currentPoint.longitude) else firstPt
                            val googlePlayerMarkerState = rememberMarkerState(key = "google_player_marker", position = currentPos)
                            LaunchedEffect(currentPos) {
                                googlePlayerMarkerState.position = currentPos
                            }

                            Marker(
                                state = googlePlayerMarkerState,
                                icon = playerIcon,
                                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                title = "車輛定位點",
                                onClick = {
                                    val pt = currentPoint ?: points.firstOrNull()
                                    val speed = pt?.speedKmh?.toInt() ?: 0
                                    val lean = pt?.leanAngle?.toInt() ?: 0
                                    val gVal = pt?.accelG ?: 0f
                                    val gStr = if (gVal >= 0f) "+${String.format(Locale.getDefault(), "%.2f", gVal)}G (加速)" else "${String.format(Locale.getDefault(), "%.2f", gVal)}G (煞車減速)"
                                    markerToastText = "🏎️ 車輛定位點\n時速 $speed km/h · 傾角 $lean° · G值 $gStr"
                                    true
                                }
                            )
                        }
                    }
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                            Configuration.getInstance().userAgentValue = ctx.packageName

                            MapView(ctx).apply {
                                mapViewInstance = this
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                setOnTouchListener { v, event ->
                                    when (event.action) {
                                        android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
                                            v.parent.requestDisallowInterceptTouchEvent(true)
                                        }
                                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                            v.parent.requestDisallowInterceptTouchEvent(false)
                                        }
                                    }
                                    false
                                }

                                val matrix = ColorMatrix()
                                matrix.setSaturation(0f)
                                val scale = 0.45f
                                matrix.postConcat(ColorMatrix(floatArrayOf(
                                    scale, 0f, 0f, 0f, 0f,
                                    0f, scale, 0f, 0f, 0f,
                                    0f, 0f, scale, 0f, 0f,
                                    0f, 0f, 0f, 1f, 0f
                                )))
                                overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))

                                if (points.isNotEmpty()) {
                                    val geoPts = points.map { GeoPoint(it.latitude, it.longitude) }

                                    val isLoopTrack = geoPts.first().distanceToAsDouble(geoPts.last()) < 20.0

                                    // 1. 起點標記 (40px 鮮綠色空心圓環 #4CAF50)
                                    val startMarker = OsmMarker(this).apply {
                                        position = geoPts.first()
                                        icon = createCircleMarker(android.graphics.Color.parseColor("#4CAF50"), 40, hollow = true)
                                        setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                                        setOnMarkerClickListener { m, map ->
                                            markerToastText = "📍 起點 S (START)\n座標：${String.format(Locale.getDefault(), "%.5f, %.5f", m.position.latitude, m.position.longitude)}"
                                            map.controller.animateTo(m.position)
                                            true
                                        }
                                    }
                                    overlays.add(startMarker)

                                    // 2. 終點標記 (若非環狀賽道則顯示 40px 鮮紅色空心圓環 #FF5252)
                                    if (!isLoopTrack) {
                                        val finishMarker = OsmMarker(this).apply {
                                            position = geoPts.last()
                                            icon = createCircleMarker(android.graphics.Color.parseColor("#FF5252"), 40, hollow = true)
                                            setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                                            setOnMarkerClickListener { m, map ->
                                                markerToastText = "🏁 終點 E (FINISH)\n座標：${String.format(Locale.getDefault(), "%.5f, %.5f", m.position.latitude, m.position.longitude)}"
                                                map.controller.animateTo(m.position)
                                                true
                                            }
                                        }
                                        overlays.add(finishMarker)
                                    }

                                    // 3. 軌跡 segments (HSV 車速漸層色)
                                    for (i in 0 until points.size - 1) {
                                        val p1 = points[i]
                                        val p2 = points[i + 1]
                                        val segColor = getSpeedHsvColor(p1.speedKmh)

                                        val segPolyline = OsmPolyline().apply {
                                            addPoint(GeoPoint(p1.latitude, p1.longitude))
                                            addPoint(GeoPoint(p2.latitude, p2.longitude))
                                            outlinePaint.color = segColor
                                            outlinePaint.strokeWidth = 12f
                                            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                        }
                                        overlays.add(0, segPolyline)
                                    }

                                    // 4. 天藍色 32dp 雙層光圈定位 Marker (#00E5FF)
                                    val uMarker = OsmMarker(this).apply {
                                        position = geoPts.first()
                                        icon = createPlayerIcon(context, android.graphics.Color.parseColor("#00E5FF"))
                                        setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                                        setOnMarkerClickListener { m, map ->
                                            val pt = currentPoint ?: points.firstOrNull()
                                            val speed = pt?.speedKmh?.toInt() ?: 0
                                            val lean = pt?.leanAngle?.toInt() ?: 0
                                            val gVal = pt?.accelG ?: 0f
                                            val gStr = if (gVal >= 0f) "+${String.format(Locale.getDefault(), "%.2f", gVal)}G (加速)" else "${String.format(Locale.getDefault(), "%.2f", gVal)}G (煞車減速)"
                                            markerToastText = "🏎️ 車輛定位點\n時速 $speed km/h · 傾角 $lean° · G值 $gStr"
                                            map.controller.animateTo(m.position)
                                            true
                                        }
                                    }
                                    overlays.add(uMarker)
                                    userMarkerInstance = uMarker

                                    post {
                                        try {
                                            val boundingBox = BoundingBox.fromGeoPoints(geoPts)
                                            zoomToBoundingBox(boundingBox, false, 80)
                                        } catch (e: Exception) {
                                            controller.setZoom(15.5)
                                            controller.setCenter(geoPts.first())
                                        }
                                    }
                                }
                            }
                        },
                        update = { map ->
                            map.overlays.clear()
                            if (points.isNotEmpty()) {
                                val geoPts = points.map { GeoPoint(it.latitude, it.longitude) }
                                val isLoopTrack = geoPts.first().distanceToAsDouble(geoPts.last()) < 20.0

                                val startMarker = OsmMarker(map).apply {
                                    position = geoPts.first()
                                    icon = createCircleMarker(android.graphics.Color.parseColor("#4CAF50"), 40, hollow = true)
                                    setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                                    setOnMarkerClickListener { m, _ ->
                                        markerToastText = "📍 起點 S (START)\n座標：${String.format(Locale.getDefault(), "%.5f, %.5f", m.position.latitude, m.position.longitude)}"
                                        map.controller.animateTo(m.position)
                                        true
                                    }
                                }
                                map.overlays.add(startMarker)

                                if (!isLoopTrack) {
                                    val finishMarker = OsmMarker(map).apply {
                                        position = geoPts.last()
                                        icon = createCircleMarker(android.graphics.Color.parseColor("#FF5252"), 40, hollow = true)
                                        setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                                        setOnMarkerClickListener { m, _ ->
                                            markerToastText = "🏁 終點 E (FINISH)\n座標：${String.format(Locale.getDefault(), "%.5f, %.5f", m.position.latitude, m.position.longitude)}"
                                            map.controller.animateTo(m.position)
                                            true
                                        }
                                    }
                                    map.overlays.add(finishMarker)
                                }

                                for (i in 0 until points.size - 1) {
                                    val p1 = points[i]
                                    val p2 = points[i + 1]
                                    val segColor = getSpeedHsvColor(p1.speedKmh)

                                    val segPolyline = OsmPolyline().apply {
                                        addPoint(GeoPoint(p1.latitude, p1.longitude))
                                        addPoint(GeoPoint(p2.latitude, p2.longitude))
                                        outlinePaint.color = segColor
                                        outlinePaint.strokeWidth = 12f
                                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                    }
                                    map.overlays.add(0, segPolyline)
                                }

                                if (showRawPoints) {
                                    points.forEach { pt ->
                                        val ptMarker = OsmMarker(map).apply {
                                            position = GeoPoint(pt.latitude, pt.longitude)
                                            icon = createCircleMarker(android.graphics.Color.parseColor("#00E676"), 14, hollow = false)
                                            setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                                        }
                                        map.overlays.add(ptMarker)
                                    }
                                }

                                val currentPt = currentPoint ?: points.first()
                                val uMarker = OsmMarker(map).apply {
                                    position = GeoPoint(currentPt.latitude, currentPt.longitude)
                                    icon = createPlayerIcon(context, android.graphics.Color.parseColor("#00E5FF"))
                                    setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                                    setOnMarkerClickListener { m, _ ->
                                        val speed = currentPt.speedKmh.toInt()
                                        val lean = currentPt.leanAngle.toInt()
                                        val gVal = currentPt.accelG
                                        val gStr = if (gVal >= 0f) "+${String.format(Locale.getDefault(), "%.2f", gVal)}G (加速)" else "${String.format(Locale.getDefault(), "%.2f", gVal)}G (煞車減速)"
                                        markerToastText = "🏎️ 車輛定位點\n時速 $speed km/h · 傾角 $lean° · G值 $gStr"
                                        map.controller.animateTo(m.position)
                                        true
                                    }
                                }
                                map.overlays.add(uMarker)
                                userMarkerInstance = uMarker
                            }
                            map.invalidate()
                        }
                    )
                }

                if (markerToastText != null) {
                    Surface(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .align(Alignment.TopCenter)
                            .clickable { markerToastText = null },
                        color = Color.Black.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF))
                    ) {
                        Text(
                            text = markerToastText!!,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Replay Controller Toolbar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { isPlaying = !isPlaying },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(RedPrimary, shape = CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "軌跡回放控制",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Replay Speed Chips (1x, 2x, 4x)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 4).forEach { spd ->
                                val isSel = replaySpeed == spd
                                Surface(
                                    modifier = Modifier
                                        .clickable { replaySpeed = spd }
                                        .border(1.dp, if (isSel) RedPrimary else Color.DarkGray, shape = RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSel) RedPrimary.copy(alpha = 0.2f) else Color.DarkGray.copy(alpha = 0.3f)
                                ) {
                                    Text(
                                        text = "${spd}x",
                                        color = if (isSel) RedPrimary else Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val maxIndex = (points.size - 1).coerceAtLeast(1)
                    val progressValue = if (points.size > 1) {
                        (currentFrameIndex.toFloat() / maxIndex).coerceIn(0f, 1f)
                    } else 0f

                    Slider(
                        value = if (progressValue.isNaN()) 0f else progressValue,
                        onValueChange = { frac ->
                            if (points.size > 1) {
                                currentFrameIndex = (frac * maxIndex).toInt().coerceIn(0, points.size - 1)
                            }
                        },
                        enabled = points.size > 1,
                        colors = SliderDefaults.colors(
                            thumbColor = RedPrimary,
                            activeTrackColor = RedPrimary,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                }
            }

            // Real-time Telemetry Stats Cards Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("時速", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = "${currentPoint?.speedKmh?.toInt() ?: 0}",
                            color = NeonGreen,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text("KM/H", color = Color.Gray, fontSize = 9.sp)
                    }
                }

                val isCarMode = record.vehicleType == "CAR" || record.vehicleType == "汽車"

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isCarMode) "側向G值" else "傾角", color = Color.Gray, fontSize = 11.sp)
                        if (isCarMode) {
                            val liveLatG = currentPoint?.latG ?: 0f
                            val latGText = String.format(Locale.getDefault(), "%.2fG", kotlin.math.abs(liveLatG))
                            Text(
                                text = latGText,
                                color = RedPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                            val dirText = if (liveLatG > 0.05f) "右離心力" else if (liveLatG < -0.05f) "左離心力" else "直線"
                            Text(dirText, color = Color.Gray, fontSize = 9.sp)
                        } else {
                            Text(
                                text = String.format(Locale.getDefault(), "%.1f°", currentPoint?.leanAngle ?: 0f),
                                color = RedPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text("最高 ${record.maxLeanAngle}°", color = Color.Gray, fontSize = 9.sp)
                        }
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("縱向 G 力", color = Color.Gray, fontSize = 11.sp)
                        val liveG = if (isCarMode && currentPoint?.longG != 0f) currentPoint?.longG ?: 0f else currentPoint?.accelG ?: 0f
                        val liveGText = if (liveG >= 0f) "+${String.format(Locale.getDefault(), "%.2f", liveG)}G" else String.format(Locale.getDefault(), "%.2f", liveG) + "G"
                        val liveGColor = if (liveG >= 0.05f) NeonGreen else if (liveG <= -0.05f) RedPrimary else Color.Yellow

                        Text(
                            text = liveGText,
                            color = liveGColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text("最大 ${String.format(Locale.getDefault(), "%.2fG", record.maxBrakingG)}", color = Color.Gray, fontSize = 9.sp)
                    }
                }
            }

            // 🏎️ 汽車 / 賽車專用 2D G-Ball 向量動態顯示器 (Friction Circle G-Matrix)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF3A3A3C), shape = RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("G力分析圖", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        val totalG = kotlin.math.sqrt(
                            (currentPoint?.latG ?: 0f) * (currentPoint?.latG ?: 0f) +
                            (currentPoint?.longG ?: 0f) * (currentPoint?.longG ?: 0f)
                        )
                        Text(
                            text = "合量: ${String.format(Locale.getDefault(), "%.2f", totalG)}G",
                            color = NeonGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF121216))
                    ) {
                        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                        val radius = size.width / 2f - 8.dp.toPx()

                        // 繪製 G 向量環狀座標格網 (0.5G, 1.0G, 1.5G 標線)
                        drawCircle(color = androidx.compose.ui.graphics.Color(0xFF2C2C2E), radius = radius, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
                        drawCircle(color = androidx.compose.ui.graphics.Color(0xFF2C2C2E), radius = radius * 0.66f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
                        drawCircle(color = androidx.compose.ui.graphics.Color(0xFF2C2C2E), radius = radius * 0.33f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))

                        // 十字十字軸線 (Longitudinal & Lateral Axes)
                        drawLine(color = androidx.compose.ui.graphics.Color(0xFF3A3A3C), start = androidx.compose.ui.geometry.Offset(center.x, center.y - radius), end = androidx.compose.ui.geometry.Offset(center.x, center.y + radius), strokeWidth = 1.5.dp.toPx())
                        drawLine(color = androidx.compose.ui.graphics.Color(0xFF3A3A3C), start = androidx.compose.ui.geometry.Offset(center.x - radius, center.y), end = androidx.compose.ui.geometry.Offset(center.x + radius, center.y), strokeWidth = 1.5.dp.toPx())

                        // 繪製過往 15 幀尾跡點軌跡 (Trail Effect)
                        val trailStart = (currentFrameIndex - 15).coerceAtLeast(0)
                        for (idx in trailStart until currentFrameIndex) {
                            val ptTrail = points[idx]
                            val trailLatG = ptTrail.latG
                            val trailLongG = if (ptTrail.longG != 0f) ptTrail.longG else ptTrail.accelG
                            val normX = (trailLatG / 1.5f).coerceIn(-1f, 1f)
                            val normY = (-trailLongG / 1.5f).coerceIn(-1f, 1f)
                            val tX = center.x + normX * radius
                            val tY = center.y + normY * radius
                            val alphaFrac = (idx - trailStart + 1) / 15f
                            drawCircle(color = androidx.compose.ui.graphics.Color(0xFFFF2A55).copy(alpha = alphaFrac * 0.45f), radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(tX, tY))
                        }

                        // 繪製當前動態 G-Ball 小紅球 (Current Ball Position)
                        val curLatG = currentPoint?.latG ?: 0f
                        val curLongG = if (currentPoint?.longG != 0f) currentPoint?.longG ?: 0f else currentPoint?.accelG ?: 0f
                        val gBallX = center.x + (curLatG / 1.5f).coerceIn(-1f, 1f) * radius
                        val gBallY = center.y + (-curLongG / 1.5f).coerceIn(-1f, 1f) * radius

                        // 發光紅球外圈與實心球
                        drawCircle(color = androidx.compose.ui.graphics.Color(0xFFFF2A55).copy(alpha = 0.35f), radius = 12.dp.toPx(), center = androidx.compose.ui.geometry.Offset(gBallX, gBallY))
                        drawCircle(color = androidx.compose.ui.graphics.Color(0xFFFF2A55), radius = 7.dp.toPx(), center = androidx.compose.ui.geometry.Offset(gBallX, gBallY))
                        drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = 2.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(gBallX, gBallY))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Text("⬅️ 左轉", color = Color.Gray, fontSize = 10.sp)
                        Text("⬆️ 加速", color = NeonGreen, fontSize = 10.sp)
                        Text("⬇️ 煞車", color = RedPrimary, fontSize = 10.sp)
                        Text("➡️ 右轉", color = Color.Gray, fontSize = 10.sp)
                    }
                }
            }


            // Overview Lap Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonGreen.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (selectedLapNumber == 0) "計時成績 (全場)" else "第 $selectedLapNumber 圈成績",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val displayT = if (selectedLapNumber == 0 || record.safeLaps.isEmpty()) {
                            record.lapTimeDisplay
                        } else {
                            record.safeLaps.find { it.lapNumber == selectedLapNumber }?.lapTimeDisplay ?: record.lapTimeDisplay
                        }
                        Text(displayT, color = NeonGreen, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            // 🏁 賽道單圈數據分析列表
            if (record.safeLaps.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF2C2C2E))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🏁 賽道單圈分析 (共 ${record.safeLaps.size} 圈)",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Surface(
                                modifier = Modifier.clickable { selectedLapNumber = 0 },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedLapNumber == 0) RedPrimary else Color(0xFF2C2C2E)
                            ) {
                                Text(
                                    text = "全場總覽",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            record.safeLaps.forEach { lap ->
                                val isSelected = selectedLapNumber == lap.lapNumber
                                val isBest = lap.diffToBestMs == 0L

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedLapNumber = lap.lapNumber },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.15f) else Color(0xFF1E1E24),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF00E5FF) else if (isBest) NeonGreen.copy(alpha = 0.6f) else Color.Transparent
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isBest) NeonGreen else Color(0xFF3A3A3C),
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "${lap.lapNumber}",
                                                        color = if (isBest) Color.Black else Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "圈數 ${lap.lapNumber}",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = lap.lapTimeDisplay,
                                                    color = if (isBest) NeonGreen else Color.LightGray,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isBest) NeonGreen.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.4f)
                                        ) {
                                            Text(
                                                text = if (isBest) "👑 最快圈 (BEST)" else "+${String.format(Locale.getDefault(), "%.3f", lap.diffToBestMs / 1000.0)}s",
                                                color = if (isBest) NeonGreen else Color(0xFFFF9800),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showJsonDialog) {
            val jsonString = remember(record) {
                com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(record)
            }

            AlertDialog(
                onDismissRequest = { showJsonDialog = false },
                containerColor = DarkSurface,
                title = { Text("📥 偵錯 JSON 軌跡資料", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        Text(
                            text = "包含全部 ${record.safePointsList.size} 個真實 GPS 採樣點點位、時速與時間戳：",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = jsonString,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = Color.LightGray)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("race_session_json", jsonString)
                            clipboard.setPrimaryClip(clip)
                            io.revon.app.ui.components.RevonToastManager.success("已複製 JSON 軌跡資料至剪貼簿！")
                            showJsonDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                    ) {
                        Text("複製 JSON", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showJsonDialog = false }) {
                        Text("關閉", color = Color.Gray)
                    }
                }
            )
        }
    }
}

private fun getSpeedHsvColor(speedKmh: Float): Int {
    val ratio = (speedKmh / 120f).coerceIn(0f, 1f)
    val hue = (1f - ratio) * 240f
    val hsv = floatArrayOf(hue, 1f, 1f)
    return android.graphics.Color.HSVToColor(hsv)
}

/**
 * 圓形 Marker Icon 生成函式 (起點與終點: 40px 天藍色空心圓環 #00E5FF)
 */
private fun createCircleMarker(color: Int, size: Int = 40, hollow: Boolean = true): BitmapDrawable {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
    }
    if (hollow) {
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = size / 4f
        canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
    } else {
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 4f, paint)
    }
    return BitmapDrawable(bitmap)
}

/**
 * 車手定位 Icon / 陀螺儀預測點 Icon 生成函式 (32dp 雙層圈標標記)
 * 外層半透明光圈 (Alpha 80) + 內層實心白點 + 2dp 邊框
 */
private fun createPlayerIcon(context: Context, mainColor: Int = android.graphics.Color.parseColor("#00E5FF")): BitmapDrawable {
    val d = context.resources.displayMetrics.density
    val s = (32 * d).toInt()
    val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(b).apply {
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        // 1. 外層光圈 (半透明 Alpha 80)
        p.color = mainColor
        p.alpha = 80
        drawCircle(s / 2f, s / 2f, s / 2.2f, p)

        // 2. 中心實心白點
        p.style = android.graphics.Paint.Style.FILL
        p.color = android.graphics.Color.WHITE
        p.alpha = 255
        drawCircle(s / 2f, s / 2f, s / 5f, p)

        // 3. 內層彩色邊框
        p.style = android.graphics.Paint.Style.STROKE
        p.strokeWidth = 2 * d
        p.color = mainColor
        p.alpha = 255
        drawCircle(s / 2f, s / 2f, s / 4f, p)
    }
    return BitmapDrawable(b)
}
