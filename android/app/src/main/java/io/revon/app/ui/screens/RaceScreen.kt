package io.revon.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.revon.app.data.engine.VirtualGateEngine
import io.revon.app.data.model.TrackPoint
import io.revon.app.data.repository.TrackRepository
import io.revon.app.data.service.GPSData
import io.revon.app.data.service.LocationService
import io.revon.app.ui.theme.DarkBackground
import io.revon.app.ui.theme.DarkSurface
import io.revon.app.ui.theme.NeonGreen
import io.revon.app.ui.theme.RedPrimary
import io.revon.app.ui.viewmodel.AuthViewModel
import io.revon.app.ui.viewmodel.RaceStep
import io.revon.app.ui.viewmodel.RaceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import io.revon.app.data.config.GpsConfig
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.Polyline as OsmPolyline
import org.osmdroid.views.overlay.Polygon as OsmPolygon
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RaceScreen(
    raceViewModel: RaceViewModel,
    authViewModel: AuthViewModel,
    onNavigateToRanking: () -> Unit,
    onNavigateToSessionDetail: (String) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val step by raceViewModel.step.collectAsState()
    val selectedVehicle by raceViewModel.selectedVehicle.collectAsState()
    val countdown by raceViewModel.countdown.collectAsState()
    val elapsedMs by raceViewModel.elapsedMs.collectAsState()
    val track by raceViewModel.selectedTrack.collectAsState()
    val lastResult by raceViewModel.lastResult.collectAsState()
    val lastSessionRecord by raceViewModel.lastSessionRecord.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    var realGpsData by remember { mutableStateOf(GPSData(latitude = 23.9738, longitude = 120.9820)) }
    var calculatedAccelG by remember { mutableStateOf(0.0f) }
    var smoothedAccelG by remember { mutableStateOf(0.0f) }
    var lastSpeedMs by remember { mutableStateOf(0f) }
    var lastSpeedTimeMs by remember { mutableStateOf(0L) }

    // 連接原生高精度 FusedLocationProviderClient 監聽真實 GPS 點位與計算 G 力 (含 LPF 低通濾波)
    DisposableEffect(Unit) {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
                val speedMs = speedKmh / 3.6f
                val now = if (loc.time > 0) loc.time else System.currentTimeMillis()
                
                if (lastSpeedTimeMs > 0L) {
                    val dt = (now - lastSpeedTimeMs) / 1000f
                    if (dt >= 0.15f) {
                        val dv = speedMs - lastSpeedMs
                        val rawAccelG = (dv / dt) / 9.80665f
                        val clampedG = rawAccelG.coerceIn(-2.0f, 2.0f)
                        smoothedAccelG = smoothedAccelG + 0.25f * (clampedG - smoothedAccelG)
                        calculatedAccelG = smoothedAccelG
                        lastSpeedMs = speedMs
                        lastSpeedTimeMs = now
                    }
                } else {
                    lastSpeedMs = speedMs
                    lastSpeedTimeMs = now
                }

                realGpsData = GPSData(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    speedKmh = speedKmh,
                    accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 5f,
                    bearing = if (loc.hasBearing()) loc.bearing else 0f,
                    timestamp = loc.time
                )
            }
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && loc.latitude > 1.0 && loc.longitude > 1.0) {
                    val speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
                    realGpsData = GPSData(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        speedKmh = speedKmh,
                        accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 5f,
                        bearing = if (loc.hasBearing()) loc.bearing else 0f,
                        timestamp = loc.time
                    )
                }
            }
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 400L)
                .setMinUpdateIntervalMillis(200L)
                .build()
            fusedClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        }

        onDispose {
            fusedClient.removeLocationUpdates(locationCallback)
            raceViewModel.resetToVehicleSelect()
        }
    }

    // Request Location & Foreground Service Permissions
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            val intent = Intent(context, LocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            launcher.launch(perms.toTypedArray())
        } else {
            val intent = Intent(context, LocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    val scope = rememberCoroutineScope()

    val handleBack: () -> Unit = {
        // Navigate back FIRST, then let onDispose reset state naturally.
        // Resetting state before popBackStack caused VehicleSelectStep to flash
        // during the exit animation (Compose re-rendered it immediately).
        onNavigateBack()
    }

    BackHandler(enabled = step == RaceStep.VEHICLE_SELECT || step == RaceStep.FINISHED) {
        handleBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        when (step) {
            RaceStep.VEHICLE_SELECT, RaceStep.NOTICE_COUNTDOWN, RaceStep.NAVIGATING, RaceStep.RACING -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 滿版共享地圖計時主頁面
                    SnappedRoadLiveRaceStep(
                        trackCode = track.code,
                        startLat = track.startLat ?: 24.1,
                        startLng = track.startLng ?: 120.7,
                        endLat = track.endLat ?: 24.2,
                        endLng = track.endLng ?: 120.8,
                        elapsedMs = elapsedMs,
                        gpsData = realGpsData,
                        accelG = calculatedAccelG,
                        isRacing = step == RaceStep.RACING,
                        onStartTimer = { raceViewModel.startRaceTimer() },
                        onFinishRace = {},
                        onStopRun = handleBack,
                        onNavigateToSessionDetail = onNavigateToSessionDetail,
                        currentUserNickname = currentUser?.nickname ?: "車手",
                        raceViewModel = raceViewModel,
                        isVehicleSelectStep = step == RaceStep.VEHICLE_SELECT
                    )

                    // 載具選擇頁面 (於地圖上方浮動：包含左上角返回/路線卡片，以及下方滑出車輛選擇欄位)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = step == RaceStep.VEHICLE_SELECT,
                        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(350)),
                        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(350)) + androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(350)) { it / 2 }
                    ) {
                        VehicleSelectStep(
                            track = track,
                            userGps = realGpsData,
                            selectedVehicle = selectedVehicle,
                            onSelectVehicle = { raceViewModel.setVehicle(it) },
                            onStart = { raceViewModel.skipCountdown() },
                            onBack = handleBack
                        )
                    }

                    // 倒數 3 2 1 不透明層級覆蓋地圖上方，倒數結束以淡出與向上滑出動畫露出現場 UI
                    androidx.compose.animation.AnimatedVisibility(
                        visible = step == RaceStep.NOTICE_COUNTDOWN,
                        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300)),
                        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(400)) + androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(400)) { -it / 2 }
                    ) {
                        CountdownStepOverlay(
                            countdown = countdown,
                            onSkip = { raceViewModel.skipCountdown() }
                        )
                    }
                }
            }
            RaceStep.FINISHED -> ResultsStep(
                result = lastResult,
                lastSessionRecord = lastSessionRecord,
                onRaceAgain = { raceViewModel.resetToVehicleSelect() },
                onViewLeaderboard = onNavigateToRanking,
                onViewDetail = { sid -> onNavigateToSessionDetail(sid) },
                onBack = handleBack
            )
        }
    }
}

@Composable
private fun VehicleSelectStep(
    track: io.revon.app.data.model.Track,
    userGps: GPSData,
    selectedVehicle: String,
    onSelectVehicle: (String) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    val trackCode = track.code
    val trackNameDisplay = if (track.nameZh.isNullOrBlank() || track.nameZh == track.code) track.name else track.nameZh

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // 左上角 返回按鈕與路線名稱 (浮動卡片樣式)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    .border(1.dp, Color(0xFF3A3A42), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.75f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A3A42)),
                modifier = Modifier.wrapContentWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = trackCode,
                        color = RedPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    if (trackNameDisplay != trackCode) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = trackNameDisplay,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Bottom Section: Vehicle Selection (Square Buttons) + Circular Glowing GO Button on the Right
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = DarkSurface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2A30))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "選擇駕駛載具",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 三個正方形/方形載具按鈕
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val vehicles = listOf(
                            Triple("CAR", "🚗\n汽車", "CAR"),
                            Triple("MOTOR", "🏍️\n機車", "MOTOR"),
                            Triple("OTHER", "🏎️\n其他", "OTHER")
                        )

                        vehicles.forEach { (type, label, _) ->
                            val isSelected = selectedVehicle == type
                            Surface(
                                onClick = { onSelectVehicle(type) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) RedPrimary.copy(alpha = 0.2f) else DarkBackground,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) RedPrimary else Color(0xFF33333C)
                                )
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize().padding(4.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else Color(0xFF9E9EA8),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // 右側「圓形」紅色實心 GO 按鈕 (帶外光暈)
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .neonGlow(color = RedPrimary, glowRadius = 12.dp, cornerRadius = 34.dp, alpha = 0.6f)
                            .clip(CircleShape)
                            .background(RedPrimary)
                            .clickable { onStart() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "GO",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownStepOverlay(countdown: Int, onSkip: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "山道駕駛安全告示", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(text = "請保持螢幕常亮，高精度 GPS 圈速紀錄即將開啟", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            Text(text = "$countdown", color = NeonGreen, fontSize = 120.sp, fontWeight = FontWeight.Black)

            Spacer(modifier = Modifier.height(28.dp))

            OutlinedButton(
                onClick = onSkip,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Text("跳過倒數", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * 透過 OSRM (Open Source Routing Machine) 獲取真實地圖道路轉彎指引線
 */
private suspend fun fetchOsrmRoadRoute(
    userLat: Double,
    userLng: Double,
    destLat: Double,
    destLng: Double
): List<GeoPoint> = withContext(Dispatchers.IO) {
    try {
        val urlStr = "https://router.project-osrm.org/route/v1/driving/$userLng,$userLat;$destLng,$destLat?overview=full&geometries=geojson"
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("User-Agent", "REV-ON-Android-App")
        }
        if (conn.responseCode == 200) {
            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonObj = JSONObject(jsonStr)
            val routes = jsonObj.optJSONArray("routes")
            if (routes != null && routes.length() > 0) {
                val geom = routes.getJSONObject(0).optJSONObject("geometry")
                val coords = geom?.optJSONArray("coordinates")
                if (coords != null) {
                    val points = mutableListOf<GeoPoint>()
                    for (i in 0 until coords.length()) {
                        val pt = coords.getJSONArray(i)
                        val lg = pt.getDouble(0)
                        val lt = pt.getDouble(1)
                        points.add(GeoPoint(lt, lg))
                    }
                    return@withContext points
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext emptyList()
}

/**
 * 完全沿地圖真實 GeoJSON/OSRM 道路彎道引向起點之極致競速頁面
 */
@Composable
private fun SnappedRoadLiveRaceStep(
    trackCode: String,
    startLat: Double,
    startLng: Double,
    endLat: Double,
    endLng: Double,
    elapsedMs: Long,
    gpsData: GPSData,
    accelG: Float,
    isRacing: Boolean,
    onStartTimer: () -> Unit,
    onFinishRace: () -> Unit,
    onStopRun: () -> Unit,
    onNavigateToSessionDetail: (String) -> Unit,
    currentUserNickname: String,
    raceViewModel: RaceViewModel,
    isVehicleSelectStep: Boolean = false
) {
    val context = LocalContext.current
    val imuFusion = remember { io.revon.app.data.sensor.IMUSensorFusion() }
    var realImuAccelG by remember { mutableStateOf(0.0f) }
    var realImuBrakingG by remember { mutableStateOf(0.0f) }
    var realImuLeanAngle by remember { mutableStateOf(0.0f) }
    var realImuSignedLatG by remember { mutableStateOf(0.0f) }
    var realImuLongG by remember { mutableStateOf(0.0f) }


    val trackPath = remember(trackCode) { TrackRepository.getTrackPath(context, trackCode) }

    var userMarkerInstance by remember { mutableStateOf<OsmMarker?>(null) }
    var gyroMarkerInstance by remember { mutableStateOf<OsmMarker?>(null) }
    var navPolylineInstance by remember { mutableStateOf<OsmPolyline?>(null) }

    val liveDrivenPoints = remember { mutableStateListOf<TrackPoint>() }

    // OSRM 地圖真實道路導航點集
    var osrmRoadPoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var lastFetchedUserLat by remember { mutableStateOf(0.0) }
    var lastFetchedUserLng by remember { mutableStateOf(0.0) }

    var isMapFollowing by remember { mutableStateOf(true) }
    var hasCameraFocusedOnUser by remember { mutableStateOf(false) }
    var satelliteCount by remember { mutableIntStateOf(0) }
    var deviceBearing by remember { mutableFloatStateOf(0f) }
    var deviceTilt by remember { mutableFloatStateOf(0f) }
    var isGpsHeadingLocked by remember { mutableStateOf(false) }
    var lastValidDrivenBearing by remember { mutableFloatStateOf(0f) }

    // 初始化 Android 原生女聲 TextToSpeech (語音提示即時完賽與計時語音提示)
    val ttsEngine = remember(context) {
        var instance: android.speech.tts.TextToSpeech? = null
        instance = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                try {
                    instance?.language = Locale.TAIWAN
                    val voices = instance?.voices
                    val femaleVoice = voices?.firstOrNull { v ->
                        (v.name.contains("cmn", ignoreCase = true) || v.name.contains("zh", ignoreCase = true)) &&
                        (v.name.contains("female", ignoreCase = true) || v.name.contains("f00", ignoreCase = true) || v.name.contains("network", ignoreCase = true))
                    } ?: voices?.firstOrNull { v -> v.name.contains("cmn", ignoreCase = true) || v.name.contains("zh", ignoreCase = true) }
                    if (femaleVoice != null) {
                        instance?.voice = femaleVoice
                    }
                } catch (_: Exception) {}
            }
        }
        instance
    }

    DisposableEffect(ttsEngine) {
        onDispose {
            try {
                ttsEngine?.stop()
                ttsEngine?.shutdown()
            } catch (_: Exception) {}
        }
    }

    val speakCue: (String) -> Unit = remember(context, ttsEngine) {
        { text ->
            if (GpsConfig.isAudioCuesEnabled(context)) {
                try {
                    ttsEngine?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "cue_${System.currentTimeMillis()}")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    var globalGoogleMapInstance by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }
    var globalTargetLat by remember { mutableDoubleStateOf(0.0) }
    var globalTargetLng by remember { mutableDoubleStateOf(0.0) }
    var globalCurrentZoom by remember { mutableFloatStateOf(17.5f) }
    var globalIs3D by remember { mutableStateOf(false) }
    var globalIsMapFollowing by remember { mutableStateOf(true) }
    var globalIsGpsHeadingLocked by remember { mutableStateOf(false) }
    var globalHasValidGps by remember { mutableStateOf(false) }
    var globalIsVehicleSelectStep by remember { mutableStateOf(isVehicleSelectStep) }

    LaunchedEffect(isVehicleSelectStep) {
        globalIsVehicleSelectStep = isVehicleSelectStep
    }

    // 註冊 IMU 硬體加速度計 (Sensor.TYPE_ACCELEROMETER) 精算動態加速 G 力、煞車 G 力與傾角
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val accelSensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val rotationSensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR)
        val gravityBaseline = GpsConfig.loadGravityBaseline(context)

        val sensorListener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == android.hardware.Sensor.TYPE_ACCELEROMETER) {
                    val isCarMode = raceViewModel.selectedVehicle.value == "CAR" || raceViewModel.selectedVehicle.value == "汽車"
                    val output = imuFusion.processAccelerometerSample(event.values, gravityBaseline, isCarMode)
                    realImuAccelG = output.accelG.toFloat()
                    realImuBrakingG = output.brakingG.toFloat()
                    realImuSignedLatG = output.signedLatG.toFloat()
                    realImuLongG = output.imuLongG.toFloat()
                    if (!isCarMode && output.leanAngle > 0.5) {
                        realImuLeanAngle = output.leanAngle.toFloat()
                    }
                } else if (event.sensor.type == android.hardware.Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    android.hardware.SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientationAngles = FloatArray(3)
                    android.hardware.SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    val rawYaw = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360) % 360).toFloat()
                    val rawTilt = (-Math.toDegrees(orientationAngles[1].toDouble())).toFloat().coerceIn(0f, 67.5f)

                    // gpstest 極速感測器模式：直接更新全域變數並調用原生地圖 moveCamera，零延遲零迴圈卡頓
                    val currentB = deviceBearing
                    var diff = rawYaw - currentB
                    while (diff < -180f) diff += 360f
                    while (diff > 180f) diff -= 360f

                    // 低通濾波
                    if (Math.abs(diff) > 0.2f) {
                        val smoothedBearing = (currentB + diff * 0.25f + 360f) % 360f
                        deviceBearing = smoothedBearing
                    }
                    if (Math.abs(rawTilt - deviceTilt) > 0.2f) {
                        deviceTilt = deviceTilt + (rawTilt - deviceTilt) * 0.25f
                    }

                    // ⚡【核心關鍵】直接在感測器 SensorEvent 觸發時即刻發送 moveCamera() 給原生 GoogleMap！ (在載具選擇期間靜默)
                    val gMap = globalGoogleMapInstance
                    if (gMap != null && globalIs3D && globalIsMapFollowing && !globalIsGpsHeadingLocked && globalHasValidGps && !globalIsVehicleSelectStep) {
                        try {
                            val camera = CameraPosition.Builder()
                                .target(LatLng(globalTargetLat, globalTargetLng))
                                .zoom(globalCurrentZoom)
                                .bearing(deviceBearing)
                                .tilt(deviceTilt)
                                .build()
                            gMap.moveCamera(CameraUpdateFactory.newCameraPosition(camera))
                        } catch (e: Exception) {}
                    }
                }
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }


        if (accelSensor != null) {
            sensorManager.registerListener(sensorListener, accelSensor, android.hardware.SensorManager.SENSOR_DELAY_GAME)
        }
        if (rotationSensor != null) {
            sensorManager.registerListener(sensorListener, rotationSensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        var callback: android.location.GnssStatus.Callback? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && lm != null) {
            callback = object : android.location.GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: android.location.GnssStatus) {
                    var usedCount = 0
                    for (i in 0 until status.satelliteCount) {
                        if (status.usedInFix(i)) usedCount++
                    }
                    satelliteCount = if (usedCount > 0) usedCount else status.satelliteCount
                }
            }
            try {
                lm.registerGnssStatusCallback(callback, android.os.Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        onDispose {
            sensorManager.unregisterListener(sensorListener)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && callback != null && lm != null) {
                try {
                    lm.unregisterGnssStatusCallback(callback)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // 🐛 偵錯日誌紀錄面板狀態
    var showDebugPanel by remember { mutableStateOf(false) }
    val debugLogList = remember { mutableStateListOf<String>() }
    var currentZoomLevel by remember { mutableStateOf(17.5) }

    // Apple Dynamic Island 頂部彈簧動態島狀態 (未選擇載具前隱藏，按下 GO 進入計時頁面後展開)
    var islandExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(isVehicleSelectStep) {
        if (isVehicleSelectStep) {
            islandExpanded = false
        } else {
            delay(120)
            islandExpanded = true
        }
    }

    val islandScaleX by animateFloatAsState(
        targetValue = if (islandExpanded) 1f else 0.15f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "islandScaleX"
    )

    val islandScaleY by animateFloatAsState(
        targetValue = if (islandExpanded) 1f else 0.2f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "islandScaleY"
    )

    val islandOffsetY by animateDpAsState(
        targetValue = if (islandExpanded) 0.dp else (-24).dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "islandOffsetY"
    )

    val islandContentAlpha by animateFloatAsState(
        targetValue = if (islandExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 280, delayMillis = 260),
        label = "islandContentAlpha"
    )

    // bottomControlPanel Apple 官方動畫狀態 (未選擇載具前隱藏)
    var panelVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isVehicleSelectStep) {
        if (isVehicleSelectStep) {
            panelVisible = false
        } else {
            delay(180)
            panelVisible = true
        }
    }

    val panelOffsetY by animateDpAsState(
        targetValue = if (panelVisible) 0.dp else 70.dp,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "panelOffsetY"
    )

    val panelAlpha by animateFloatAsState(
        targetValue = if (panelVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "panelAlpha"
    )

    val navInteractionSource = remember { MutableInteractionSource() }
    val isNavPressed by navInteractionSource.collectIsPressedAsState()
    val navBtnScale by animateFloatAsState(
        targetValue = if (isNavPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "navBtnScale"
    )

    val stopInteractionSource = remember { MutableInteractionSource() }
    val isStopPressed by stopInteractionSource.collectIsPressedAsState()
    val stopBtnScale by animateFloatAsState(
        targetValue = if (isStopPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "stopBtnScale"
    )

    var isScreenLocked by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (isScreenLocked) {
            io.revon.app.ui.components.RevonToastManager.warning("螢幕已鎖定，請向右滑動下方滑軌解鎖")
        } else if (isRacing) {
            io.revon.app.ui.components.RevonToastManager.warning("計時進行中，請按下右下方「終止」按鈕以結束計時")
        } else {
            onStopRun()
        }
    }

    // 當開啟螢幕防誤觸鎖定時，隱藏系統導航條與狀態列 (Immersive Sticky Mode)，防範手機底部往上滑返回桌面
    LaunchedEffect(isScreenLocked) {
        var currentCtx: Context = context
        var activity: Activity? = null
        while (currentCtx is android.content.ContextWrapper) {
            if (currentCtx is Activity) {
                activity = currentCtx
                break
            }
            currentCtx = currentCtx.baseContext
        }

        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isScreenLocked) {
                // 隱藏狀態列與底部導航手勢條
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                try {
                    activity.startLockTask()
                } catch (e: Exception) {
                    android.util.Log.w("RaceScreen", "startLockTask not available: ${e.message}")
                }
            } else {
                // 恢復系統狀態列與手勢條
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                try {
                    activity.stopLockTask()
                } catch (e: Exception) {
                    android.util.Log.w("RaceScreen", "stopLockTask failed: ${e.message}")
                }
            }
        }
    }

    var debugClickCount by remember { mutableIntStateOf(0) }
    var lastDebugClickTimeMs by remember { mutableLongStateOf(0L) }
    var circuitLapNumber by remember { mutableIntStateOf(0) }
    var circuitLapStartTimeMs by remember { mutableLongStateOf(0L) }
    var lastCircuitGateTimeMs by remember { mutableLongStateOf(0L) }
    val circuitCompletedLaps = remember { mutableStateListOf<io.revon.app.data.model.LapInfo>() }
    val currentLapPoints = remember { mutableStateListOf<TrackPoint>() }

    val apiRaceRepo = remember { io.revon.app.data.repository.ApiRaceRepository(io.revon.app.di.NetworkModule.apiService) }
    val safeNickname = currentUserNickname.ifBlank { "driver" }.replace(Regex("[^a-zA-Z0-9_]"), "_")
    var activeSessionId by remember { mutableStateOf("sess_${safeNickname}_${System.currentTimeMillis()}") }
    var lastFirebaseTelemetryPushTimeMs by remember { mutableLongStateOf(0L) }
    var firebasePushCount by remember { mutableIntStateOf(0) }
    var firebasePushStatus by remember { mutableStateOf("等待首筆廣播...") }

    var saveDraftOnAbortedRace by remember { mutableStateOf(true) }
    var draftSaveCount by remember { mutableIntStateOf(0) }
    var lastSavedDraftName by remember { mutableStateOf("") }

    val selectedTrack by raceViewModel.selectedTrack.collectAsState()
    val selectedVehicle by raceViewModel.selectedVehicle.collectAsState()

    val isCircuitMode = remember(selectedTrack) {
        selectedTrack.category == io.revon.app.data.model.TrackCategory.CIRCUIT ||
        (startLat != 0.0 && Math.abs(startLat - endLat) < 0.0001 && Math.abs(startLng - endLng) < 0.0001)
    }

    DisposableEffect(activeSessionId) {
        onDispose {
            if (isCircuitMode && activeSessionId.isNotBlank()) {
                apiRaceRepo.stopLiveTelemetry(uid = activeSessionId)
            }
            if (saveDraftOnAbortedRace && liveDrivenPoints.size >= 2) {
                io.revon.app.data.repository.RaceDraftRepository.saveAbortedSession(
                    context = context,
                    sessionId = activeSessionId,
                    trackCode = selectedTrack.code,
                    trackName = selectedTrack.nameZh ?: selectedTrack.name,
                    driverNickname = currentUserNickname,
                    vehicleType = selectedVehicle,
                    points = liveDrivenPoints.toList(),
                    elapsedMs = elapsedMs,
                    reason = "UNEXPECTED_EXIT_OR_ABORT"
                )
            }
            var currentCtx: Context = context
            var activity: Activity? = null
            while (currentCtx is android.content.ContextWrapper) {
                if (currentCtx is Activity) {
                    activity = currentCtx
                    break
                }
                currentCtx = currentCtx.baseContext
            }
            activity?.window?.let { window ->
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                try { activity.stopLockTask() } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(activeSessionId) {
        if (activeSessionId.isNotBlank()) {
            val curLat = if (gpsData.latitude != 0.0) gpsData.latitude else startLat
            val curLng = if (gpsData.longitude != 0.0) gpsData.longitude else startLng
            apiRaceRepo.updateLiveTelemetry(
                uid = activeSessionId,
                nickname = currentUserNickname,
                vehicleType = selectedVehicle,
                trackCode = selectedTrack.code,
                trackName = selectedTrack.nameZh ?: selectedTrack.name,
                speedKmh = 0f,
                accelG = 0f,
                lat = curLat,
                lng = curLng,
                isSos = false,
                status = "PREPARING"
            )
        }
    }

    LaunchedEffect(isRacing) {
        if (isRacing) {
            activeSessionId = "sess_${currentUserNickname.ifBlank { "driver" }}_${System.currentTimeMillis()}"
            liveDrivenPoints.clear()
            currentLapPoints.clear()
        } else {
            circuitLapNumber = 0
            circuitCompletedLaps.clear()
            currentLapPoints.clear()
            if (isCircuitMode && activeSessionId.isNotBlank()) {
                apiRaceRepo.stopLiveTelemetry(uid = activeSessionId)
            }
        }
    }

    val finishCircuitRun: () -> Unit = {
        if (isCircuitMode && isRacing) {
            val nowMs = System.currentTimeMillis()
            if (circuitCompletedLaps.isEmpty() && currentLapPoints.size > 5) {
                val lapDuration = (nowMs - circuitLapStartTimeMs).coerceAtLeast(1000L)
                val lapDisplay = raceViewModel.formatLapTime(lapDuration)
                circuitCompletedLaps.add(
                    io.revon.app.data.model.LapInfo(
                        lapNumber = 1,
                        lapTimeMs = lapDuration,
                        lapTimeDisplay = lapDisplay,
                        diffToBestMs = 0L,
                        pointsList = currentLapPoints.toList()
                    )
                )
            }

            val bestLapMs = circuitCompletedLaps.minOfOrNull { it.lapTimeMs } ?: elapsedMs
            val finalLaps = circuitCompletedLaps.map { lap ->
                lap.copy(diffToBestMs = lap.lapTimeMs - bestLapMs)
            }
            if (isCircuitMode) {
                apiRaceRepo.stopLiveTelemetry(uid = activeSessionId)
            }
            raceViewModel.finishRace(
                driverNickname = currentUserNickname,
                realGpsPoints = liveDrivenPoints.toList(),
                laps = finalLaps,
                bestLapMs = bestLapMs,
                sessionId = activeSessionId
            )
            io.revon.app.data.repository.RaceDraftRepository.clearDraft(context, activeSessionId)
        }
    }

    val gateEngine = remember { VirtualGateEngine() }
    var distToStartMeters by remember { mutableStateOf(0) }
    var distToEndMeters by remember { mutableStateOf(0) }

    // 實時更新 GPS，檢測 起點 S 觸發與 終點 E 觸發
    LaunchedEffect(gpsData.latitude, gpsData.longitude, isRacing, elapsedMs) {
        val curLat = if (gpsData.latitude != 0.0) gpsData.latitude else startLat
        val curLng = if (gpsData.longitude != 0.0) gpsData.longitude else startLng

        // 1. 直線真實距離計算 (用於精準過線判定)
        val directDistStartKm = TrackRepository.haversineDistance(curLat, curLng, startLat, startLng)
        val directDistStartM = (directDistStartKm * 1000).roundToInt()

        val directDistEndKm = TrackRepository.haversineDistance(curLat, curLng, endLat, endLng)
        val directDistEndM = (directDistEndKm * 1000).roundToInt()

        // 2. 導航/路線剩餘距離計算 (用於 UI 顯示)
        val distStartNavKm = if (osrmRoadPoints.size >= 2) {
            val closestStartIdx = osrmRoadPoints.indices.minByOrNull { i ->
                TrackRepository.haversineDistance(curLat, curLng, osrmRoadPoints[i].latitude, osrmRoadPoints[i].longitude)
            } ?: 0
            var sum = TrackRepository.haversineDistance(curLat, curLng, osrmRoadPoints[closestStartIdx].latitude, osrmRoadPoints[closestStartIdx].longitude)
            for (i in closestStartIdx until osrmRoadPoints.size - 1) {
                sum += TrackRepository.haversineDistance(
                    osrmRoadPoints[i].latitude, osrmRoadPoints[i].longitude,
                    osrmRoadPoints[i + 1].latitude, osrmRoadPoints[i + 1].longitude
                )
            }
            sum
        } else {
            directDistStartKm
        }
        val distStartM = (distStartNavKm * 1000).roundToInt()
        distToStartMeters = if (!isRacing) distStartM else directDistStartM

        val distEndKm = if (trackPath.size >= 2) {
            val closestIdx = trackPath.indices.minByOrNull { i ->
                TrackRepository.haversineDistance(curLat, curLng, trackPath[i].first, trackPath[i].second)
            } ?: 0
            
            var sum = TrackRepository.haversineDistance(curLat, curLng, trackPath[closestIdx].first, trackPath[closestIdx].second)
            for (i in closestIdx until trackPath.size - 1) {
                sum += TrackRepository.haversineDistance(
                    trackPath[i].first, trackPath[i].second,
                    trackPath[i + 1].first, trackPath[i + 1].second
                )
            }
            sum
        } else {
            directDistEndKm
        }
        val distEndM = (distEndKm * 1000).roundToInt()
        distToEndMeters = distEndM

        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        // 3. 起點 S 觸發檢查 / 賽道多圈過線檢查
        if (gpsData.latitude != 0.0 && gpsData.longitude != 0.0) {
            val currentGeoPt = VirtualGateEngine.GeoPoint2D(gpsData.latitude, gpsData.longitude)
            val startGeoPt = VirtualGateEngine.GeoPoint2D(startLat, startLng)
            val trackGeoPts = trackPath.map { VirtualGateEngine.GeoPoint2D(it.first, it.second) }

            val dummyLocation = android.location.Location("gps").apply {
                latitude = gpsData.latitude
                longitude = gpsData.longitude
            }
            val gateTriggered = gateEngine.checkStartTrigger(currentGeoPt, dummyLocation, startGeoPt, trackGeoPts, triggerRadius = 7.0)

            val isFreeRecordingTrack = (startLat == 0.0 && startLng == 0.0) || selectedTrack.name.contains("錄製") || selectedTrack.code.contains("錄製")

            if (!isRacing) {
                if (gateTriggered || isFreeRecordingTrack) {
                    if (isFreeRecordingTrack) {
                        debugLogList.add(0, "[$timeStr] 🎙️ 自由錄製模式：無起終點限制，進入即自動開啟實時計時與廣播！")
                        speakCue("自由錄製模式，開啟計時")
                    } else if (isCircuitMode) {
                        circuitLapNumber = 1
                        circuitLapStartTimeMs = System.currentTimeMillis()
                        lastCircuitGateTimeMs = System.currentTimeMillis()
                        circuitCompletedLaps.clear()
                        currentLapPoints.clear()
                        debugLogList.add(0, "[$timeStr] 🏁 賽道模式：跨越起終點閘門，開始第 1 圈計時！")
                        speakCue("通過起點閘門，開始第 1 圈計時")
                    } else {
                        debugLogList.add(0, "[$timeStr] 🔴 起點 S 跨越閘門，觸發開始計時！")
                        speakCue("通過起點，計時開始")
                    }
                    onStartTimer()
                }
            } else if (isCircuitMode) {
                val nowMs = System.currentTimeMillis()
                if (gateTriggered && (nowMs - lastCircuitGateTimeMs > 10000L)) {
                    val lapDuration = (nowMs - circuitLapStartTimeMs).coerceAtLeast(1000L)
                    val lapDisplay = raceViewModel.formatLapTime(lapDuration)
                    val lapInfo = io.revon.app.data.model.LapInfo(
                        lapNumber = circuitLapNumber,
                        lapTimeMs = lapDuration,
                        lapTimeDisplay = lapDisplay,
                        diffToBestMs = 0L,
                        pointsList = currentLapPoints.toList()
                    )
                    circuitCompletedLaps.add(lapInfo)
                    debugLogList.add(0, "[$timeStr] 🏁 第 ${circuitLapNumber} 圈完成：$lapDisplay，進入第 ${circuitLapNumber + 1} 圈計時！")
                    io.revon.app.ui.components.RevonToastManager.success("第 ${circuitLapNumber} 圈完賽：$lapDisplay")
                    speakCue("完成第 ${circuitLapNumber} 圈，單圈時間 $lapDisplay")

                    circuitLapNumber += 1
                    circuitLapStartTimeMs = nowMs
                    lastCircuitGateTimeMs = nowMs
                    currentLapPoints.clear()
                }
            }
        }

        // 4. 終點 E 自動觸發完賽檢查 (僅山道單向模式)
        if (!isCircuitMode && isRacing && minOf(directDistEndM, distEndM) < 15 && elapsedMs > 5000L && gpsData.latitude != 0.0) {
            debugLogList.add(0, "[$timeStr] 🟢 終點 E 自動觸發完賽！(距離終點 ${minOf(directDistEndM, distEndM)}m)")
            speakCue("到達終點，完成計時")
            raceViewModel.finishRace(currentUserNickname, liveDrivenPoints.toList(), sessionId = activeSessionId)
            io.revon.app.data.repository.RaceDraftRepository.clearDraft(context, activeSessionId)
        }

        if (isRacing && gpsData.latitude != 0.0 && gpsData.longitude != 0.0) {
            // 根據兩點間轉向角變化率與軌跡曲率計算動態壓車傾角 (Lean Angle = atan(v^2 / (r * g)))
            val calculatedLeanAngle = if (liveDrivenPoints.size >= 2) {
                val pPrev = liveDrivenPoints.last()
                val distM = TrackRepository.haversineDistance(pPrev.latitude, pPrev.longitude, gpsData.latitude, gpsData.longitude) * 1000.0
                val dtSec = ((System.currentTimeMillis() - pPrev.timestampMs) / 1000.0).coerceAtLeast(0.1)
                val speedMs = (gpsData.speedKmh / 3.6).coerceAtLeast(1.0)
                
                val heading1 = Math.atan2(gpsData.longitude - pPrev.longitude, gpsData.latitude - pPrev.latitude)
                val heading0 = if (liveDrivenPoints.size >= 3) {
                    val pPrev2 = liveDrivenPoints[liveDrivenPoints.size - 2]
                    Math.atan2(pPrev.longitude - pPrev2.longitude, pPrev.latitude - pPrev2.latitude)
                } else heading1
                var dHeading = Math.abs(heading1 - heading0)
                if (dHeading > Math.PI) dHeading = (2 * Math.PI - dHeading)
                val omega = dHeading / dtSec
                val latG = (speedMs * omega) / 9.80665
                val leanDeg = Math.toDegrees(Math.atan(latG)).toFloat().coerceIn(0f, 62f)
                
                if (leanDeg > 0.5f) leanDeg else (Math.abs(accelG) * 12.5f).coerceIn(0f, 45f)
            } else 0f

            val finalLeanAngle = if (realImuLeanAngle > 0.5f) realImuLeanAngle else calculatedLeanAngle
            // 獨立 G 力計算：若有煞車 G 則記為負 G 力，加速則記為正 G 力 (直接採用 Tstarz 演算法)
            val netLongitudinalG = if (realImuBrakingG > 0.05f) -realImuBrakingG else (if (realImuAccelG > 0.05f) realImuAccelG else accelG)

            val newPt = TrackPoint(
                latitude = gpsData.latitude,
                longitude = gpsData.longitude,
                speedKmh = gpsData.speedKmh,
                timestampMs = System.currentTimeMillis(),
                accelG = netLongitudinalG,
                leanAngle = finalLeanAngle,
                latG = realImuSignedLatG,
                longG = realImuLongG
            )


            if (liveDrivenPoints.isEmpty() ||
                TrackRepository.haversineDistance(liveDrivenPoints.last().latitude, liveDrivenPoints.last().longitude, newPt.latitude, newPt.longitude) > 0.002) {
                liveDrivenPoints.add(newPt)
                if (isCircuitMode) {
                    currentLapPoints.add(newPt)
                }
            }
        }

        // OSRM 路線更新 (導航狀態下)
        if (!isCircuitMode && !isRacing && gpsData.latitude != 0.0 && gpsData.longitude != 0.0) {
            val dChange = TrackRepository.haversineDistance(lastFetchedUserLat, lastFetchedUserLng, gpsData.latitude, gpsData.longitude)
            if (osrmRoadPoints.isEmpty() || dChange > 0.025) {
                lastFetchedUserLat = gpsData.latitude
                lastFetchedUserLng = gpsData.longitude
                withContext(Dispatchers.IO) {
                    val fetched = fetchOsrmRoadRoute(gpsData.latitude, gpsData.longitude, startLat, startLng)
                    if (fetched.isNotEmpty()) {
                        osrmRoadPoints = fetched
                    }
                }
            }
        }
    }

    val currentGpsDataState by rememberUpdatedState(gpsData)
    val currentAccelGState by rememberUpdatedState(accelG)
    val currentElapsedMsState by rememberUpdatedState(elapsedMs)
    val currentNicknameState by rememberUpdatedState(currentUserNickname)
    val currentTrackState by rememberUpdatedState(selectedTrack)
    val currentVehicleState by rememberUpdatedState(selectedVehicle)
    val currentCircuitLapNumberState by rememberUpdatedState(circuitLapNumber)

    // Dedicated continuous telemetry streaming loop (every 500ms) to Firebase Firestore
    LaunchedEffect(activeSessionId, isRacing) {
        while (true) {
            val liveGps = currentGpsDataState
            val liveAccelG = currentAccelGState
            val liveElapsedMs = currentElapsedMsState
            val liveNickname = currentNicknameState
            val liveTrack = currentTrackState
            val liveVehicle = currentVehicleState
            val liveCircuitLap = currentCircuitLapNumberState

            val curPushLat = if (liveGps.latitude != 0.0) liveGps.latitude else startLat
            val curPushLng = if (liveGps.longitude != 0.0) liveGps.longitude else startLng
            val rawPts = if (isCircuitMode) {
                if (currentLapPoints.isNotEmpty()) currentLapPoints.toList() else emptyList()
            } else {
                if (liveDrivenPoints.isNotEmpty()) liveDrivenPoints.toList() else emptyList()
            }
            val ptsMap = rawPts.takeLast(500).map { pt ->
                mapOf("lt" to pt.latitude, "lg" to pt.longitude, "s" to pt.speedKmh, "g" to pt.accelG)
            }
            val currentLapMs = if (isCircuitMode && circuitLapStartTimeMs > 0L) {
                (System.currentTimeMillis() - circuitLapStartTimeMs).coerceAtLeast(0L)
            } else {
                liveElapsedMs
            }
            val completedLapsMap = circuitCompletedLaps.map { lap ->
                mapOf(
                    "lapNumber" to lap.lapNumber,
                    "lapTimeMs" to lap.lapTimeMs,
                    "lapTimeDisplay" to lap.lapTimeDisplay
                )
            }

            if (curPushLat != 0.0 && curPushLng != 0.0) {
                val currentStatus = if (isRacing) "RACING" else "PREPARING"
                apiRaceRepo.updateLiveTelemetry(
                    uid = activeSessionId,
                    nickname = liveNickname,
                    vehicleType = liveVehicle,
                    trackCode = liveTrack.code,
                    trackName = liveTrack.nameZh ?: liveTrack.name,
                    speedKmh = liveGps.speedKmh,
                    accelG = liveAccelG,
                    lat = curPushLat,
                    lng = curPushLng,
                    isSos = false,
                    status = currentStatus
                )
                firebasePushCount++
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val modeLabel = if (isRacing) "比賽中 (3s/次)" else "準備中 (5s/次)"
                firebasePushStatus = "成功 [$timeStr] API 進度更新 $modeLabel (${liveGps.speedKmh.toInt()}km/h)"
            }
            // 每 3 秒刷寫本地草稿 JSON 檔至 App 私有目錄，減輕 RAM 記憶體壓力
            val nowTimeMs = System.currentTimeMillis()
            if (rawPts.isNotEmpty() && (nowTimeMs - lastFirebaseTelemetryPushTimeMs >= 3000L)) {
                lastFirebaseTelemetryPushTimeMs = nowTimeMs
                val dFile = io.revon.app.data.repository.RaceDraftRepository.saveDraftPoints(
                    context = context,
                    sessionId = activeSessionId,
                    trackCode = liveTrack.code,
                    trackName = liveTrack.nameZh ?: liveTrack.name,
                    driverNickname = liveNickname,
                    vehicleType = liveVehicle,
                    points = rawPts,
                    isRacing = isRacing
                )
                if (dFile != null) {
                    draftSaveCount++
                    lastSavedDraftName = dFile.name
                }
            }

            // 準備狀態保持每 5 秒更新一次；開始計時/比賽中保持每 3 秒更新一次
            delay(if (isRacing) 3000L else 5000L)
        }
    }

    var currentMapEngine by remember { mutableStateOf(GpsConfig.getMapEngine(context)) }
    var googleCameraZoom by remember { mutableStateOf(17.5f) }
    var bottomPanelHeightDp by remember { mutableStateOf(120.dp) }
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        // 地圖區域 Container (動態測量下方資訊欄高度，精準切齊其上緣)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPanelHeightDp)
        ) {
            if (currentMapEngine == GpsConfig.MAP_ENGINE_GOOGLE || currentMapEngine == GpsConfig.MAP_ENGINE_GOOGLE_3D) {
                // 1. GOOGLE MAPS VIEW
                val is3D = currentMapEngine == GpsConfig.MAP_ENGINE_GOOGLE_3D
            val defaultZoom = 17.5f
            val validStartLat = if (startLat > 1.0) startLat else 23.7000
            val validStartLng = if (startLng > 1.0) startLng else 120.9000
            val targetLat = if (gpsData.latitude > 1.0) gpsData.latitude else validStartLat
            val targetLng = if (gpsData.longitude > 1.0) gpsData.longitude else validStartLng

            var hasGoogleCameraFocusedOnUser by remember { mutableStateOf(false) }

            val initialVehicleSelectBounds = remember(startLat, startLng, endLat, endLng, trackPath, gpsData.latitude, gpsData.longitude) {
                val builder = com.google.android.gms.maps.model.LatLngBounds.builder()
                var count = 0
                if (gpsData.latitude > 1.0 && gpsData.longitude > 1.0) {
                    builder.include(LatLng(gpsData.latitude, gpsData.longitude))
                    count++
                }
                if (startLat > 1.0 && startLng > 1.0) {
                    builder.include(LatLng(startLat, startLng))
                    count++
                }
                if (endLat > 1.0 && endLng > 1.0) {
                    builder.include(LatLng(endLat, endLng))
                    count++
                }
                trackPath.forEach { (lt, lg) ->
                    builder.include(LatLng(lt, lg))
                    count++
                }
                if (count > 0) builder.build() else null
            }

            val cameraPositionState = rememberCameraPositionState(key = "${currentMapEngine}_${startLat}_${startLng}") {
                position = CameraPosition.Builder()
                    .target(LatLng(targetLat, targetLng))
                    .zoom(if (isVehicleSelectStep) 13.5f else defaultZoom)
                    .bearing(0f)
                    .tilt(0f)
                    .build()
            }

            LaunchedEffect(cameraPositionState.position.zoom) {
                googleCameraZoom = cameraPositionState.position.zoom
            }

            val hasValidGps = gpsData.latitude > 1.0 && gpsData.longitude > 1.0

            LaunchedEffect(is3D, targetLat, targetLng, cameraPositionState.position.zoom, isMapFollowing, isGpsHeadingLocked, hasValidGps) {
                globalIs3D = is3D
                globalTargetLat = targetLat
                globalTargetLng = targetLng
                globalCurrentZoom = cameraPositionState.position.zoom
                globalIsMapFollowing = isMapFollowing
                globalIsGpsHeadingLocked = isGpsHeadingLocked
                globalHasValidGps = hasValidGps
            }

            // 載具選擇階段：相機涵蓋完整範圍 (包含 GPS 點, 起點 S, 終點 E, 全賽道)
            LaunchedEffect(isVehicleSelectStep, initialVehicleSelectBounds, hasValidGps) {
                if (isVehicleSelectStep && initialVehicleSelectBounds != null) {
                    try {
                        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(initialVehicleSelectBounds, 140))
                    } catch (e: Exception) {
                        try {
                            cameraPositionState.animate(
                                update = CameraUpdateFactory.newLatLngBounds(initialVehicleSelectBounds, 140),
                                durationMs = 300
                            )
                        } catch (_: Exception) {}
                    }
                }
            }

            // 離開載具選擇 (按 GO 後)：平滑移動 (Animate) 到使用者 GPS 點視角
            var wasVehicleSelectStep by remember { mutableStateOf(false) }
            LaunchedEffect(isVehicleSelectStep) {
                if (wasVehicleSelectStep && !isVehicleSelectStep && hasValidGps) {
                    val initialBearing = if (is3D) deviceBearing else 0f
                    val initialTilt = if (is3D) 45f else 0f
                    val targetCamera = CameraPosition.Builder()
                        .target(LatLng(gpsData.latitude, gpsData.longitude))
                        .zoom(defaultZoom)
                        .bearing(initialBearing)
                        .tilt(initialTilt)
                        .build()
                    cameraPositionState.animate(
                        update = CameraUpdateFactory.newCameraPosition(targetCamera),
                        durationMs = 800
                    )
                    hasGoogleCameraFocusedOnUser = true
                }
                wasVehicleSelectStep = isVehicleSelectStep
            }

            // 首次取得 GPS 時初始化相機位置 (非載具選擇模式時)
            LaunchedEffect(hasValidGps) {
                if (hasValidGps && !hasGoogleCameraFocusedOnUser && !isVehicleSelectStep) {
                    val initialBearing = if (is3D) deviceBearing else 0f
                    val initialTilt = if (is3D) 45f else 0f
                    cameraPositionState.position = CameraPosition.Builder()
                        .target(LatLng(gpsData.latitude, gpsData.longitude))
                        .zoom(defaultZoom)
                        .bearing(initialBearing)
                        .tilt(initialTilt)
                        .build()
                    hasGoogleCameraFocusedOnUser = true
                }
            }

            // ── Pipeline 1：GPS 定位追蹤 ──────────────────────────────────────
            // 只監聽 GPS 位置/速度/heading 變更，完全不訂閱感測器 State
            // >= 5 km/h 鎖定 GPS heading；若「3D地圖陀螺儀」未開啟，則始終保持 GPS heading 視角鎖定
            val is3DGyroEnabled = remember(context) { GpsConfig.is3DMapGyroEnabled(context) }

            LaunchedEffect(gpsData.latitude, gpsData.longitude, gpsData.speedKmh, gpsData.bearing, isMapFollowing, isGpsHeadingLocked, is3DGyroEnabled, isVehicleSelectStep) {
                if (!hasValidGps || !hasGoogleCameraFocusedOnUser || !isMapFollowing || isVehicleSelectStep) return@LaunchedEffect

                // 記錄停止前的有效行車方向 (Speed >= 3km/h)
                if (gpsData.speedKmh >= 3.0f && gpsData.bearing != 0f) {
                    lastValidDrivenBearing = gpsData.bearing
                }

                // 若未開啟 3D地圖陀螺儀，或是高於 5km/h，均鎖定 GPS 導航方向
                if (is3D) {
                    if (!is3DGyroEnabled || gpsData.speedKmh >= 5.0f) {
                        isGpsHeadingLocked = true
                    }
                }

                val currentCamZoom = cameraPositionState.position.zoom
                // 保留使用者手動縮放倍率；只在首次或完全縮出時才重設
                val activeZoom = if (currentCamZoom < 5f) defaultZoom else currentCamZoom

                if (is3D && isGpsHeadingLocked) {
                    val activeBearing = if (gpsData.speedKmh >= 5.0f && gpsData.bearing != 0f) {
                        gpsData.bearing
                    } else if (lastValidDrivenBearing != 0f) {
                        lastValidDrivenBearing
                    } else {
                        deviceBearing
                    }

                    // 導航方向鎖定：只由 GPS 更新驅動，平滑跟隨車輛位移
                    val camera = CameraPosition.Builder()
                        .target(LatLng(gpsData.latitude, gpsData.longitude))
                        .zoom(activeZoom)
                        .bearing(activeBearing)
                        .tilt(45f)
                        .build()
                    cameraPositionState.animate(
                        update = CameraUpdateFactory.newCameraPosition(camera),
                        durationMs = 150
                    )
                } else if (!is3D) {
                    // 2D 模式：只更新位置，不帶角度，速度 >= 5 才動畫否則直接跳
                    if (gpsData.speedKmh >= 5.0f) {
                        cameraPositionState.animate(
                            update = CameraUpdateFactory.newLatLng(LatLng(gpsData.latitude, gpsData.longitude)),
                            durationMs = 150
                        )
                    } else {
                        cameraPositionState.position = CameraPosition.Builder()
                            .target(LatLng(gpsData.latitude, gpsData.longitude))
                            .zoom(activeZoom)
                            .build()
                    }
                }
                // 3D 低速 (<5km/h) 且有開啟陀螺儀時由 Pipeline 2 負責
            }

            var rawGoogleMap by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }

            // ── Pipeline 2：感測器視角更新與原生地圖 Direct Camera API (GPSTest 高效模式) ──
            val displayRefreshRate = remember(context) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        context.display?.refreshRate ?: 60f
                    } else {
                        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
                        wm?.defaultDisplay?.refreshRate ?: 60f
                    }
                } catch (e: Exception) {
                    60f
                }
            }
            val dynamicFrameDelayMs = remember(displayRefreshRate) {
                (1000f / displayRefreshRate.coerceAtLeast(30f)).toLong().coerceIn(4L, 33L)
            }

            LaunchedEffect(is3D, isMapFollowing, rawGoogleMap, dynamicFrameDelayMs, is3DGyroEnabled, lastValidDrivenBearing) {
                if (!is3D || !isMapFollowing || rawGoogleMap == null || !is3DGyroEnabled) return@LaunchedEffect
                while (true) {
                    kotlinx.coroutines.delay(dynamicFrameDelayMs) // 自動匹配 60Hz/90Hz/120Hz 高刷新率
                    val gMap = rawGoogleMap ?: continue
                    if (!hasValidGps || !hasGoogleCameraFocusedOnUser || !isMapFollowing) continue
                    if (isGpsHeadingLocked) continue // 高速時由 Pipeline 1 控制，感測器靜默

                    val currentCamZoom = cameraPositionState.position.zoom
                    val activeZoom = if (currentCamZoom < 5f) defaultZoom else currentCamZoom

                    // ⚡ 低速與停止狀態 (<5km/h)：優先鎖定停止前的有效行車方向 (lastValidDrivenBearing)，防止 90 度視角錯位旋轉
                    val stationaryBearing = if (lastValidDrivenBearing != 0f) lastValidDrivenBearing else deviceBearing

                    val camera = CameraPosition.Builder()
                        .target(LatLng(gpsData.latitude, gpsData.longitude))
                        .zoom(activeZoom)
                        .bearing(stationaryBearing)
                        .tilt(deviceTilt)
                        .build()
                    gMap.moveCamera(CameraUpdateFactory.newCameraPosition(camera))
                }
            }

            LaunchedEffect(cameraPositionState.isMoving) {
                if (cameraPositionState.isMoving && cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
                    isMapFollowing = false
                    // 只有在車速低於 5km/h 且手動滑動地圖時才解除 GPS Heading 鎖定，回歸陀螺儀感測器
                    if (gpsData.speedKmh < 5.0f) {
                        isGpsHeadingLocked = false
                    }
                }
            }

            val startLatLng = remember(startLat, startLng) { LatLng(startLat, startLng) }
            val endLatLng = remember(endLat, endLng) { LatLng(endLat, endLng) }
            val isLoopTrack = remember(startLat, startLng, endLat, endLng) {
                TrackRepository.haversineDistance(startLat, startLng, endLat, endLng) < 0.02
            }

            val startMarkerIcon = remember(context) {
                try {
                    com.google.android.gms.maps.MapsInitializer.initialize(context)
                    BitmapDescriptorFactory.fromBitmap(createCircleMarker(android.graphics.Color.parseColor("#4CAF50"), 40, hollow = true).bitmap)
                } catch (e: Exception) {
                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                }
            }
            val finishMarkerIcon = remember(context) {
                try {
                    com.google.android.gms.maps.MapsInitializer.initialize(context)
                    BitmapDescriptorFactory.fromBitmap(createCircleMarker(android.graphics.Color.parseColor("#FF5252"), 40, hollow = true).bitmap)
                } catch (e: Exception) {
                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                }
            }
            val playerMarkerIcon = remember(context) {
                try {
                    com.google.android.gms.maps.MapsInitializer.initialize(context)
                    BitmapDescriptorFactory.fromBitmap(createPlayerIcon(context, android.graphics.Color.parseColor("#00E5FF")).bitmap)
                } catch (e: Exception) {
                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)
                }
            }
            val predictedMarkerIcon = remember(context) {
                try {
                    com.google.android.gms.maps.MapsInitializer.initialize(context)
                    BitmapDescriptorFactory.fromBitmap(createPredictedIcon(context).bitmap)
                } catch (e: Exception) {
                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)
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
                    mapStyleOptions = null,
                    isBuildingEnabled = is3D,
                    isMyLocationEnabled = false
                )
            ) {
                MapEffect(Unit) { map ->
                    rawGoogleMap = map
                    globalGoogleMapInstance = map
                }
                // 起點 7m 虛擬感測範圍半徑圓
                Circle(
                    center = startLatLng,
                    radius = 7.0,
                    fillColor = Color(0x3300FF66),
                    strokeColor = Color(0xFF00FF66),
                    strokeWidth = 3f
                )

                // 終點 7m 虛擬感測範圍半徑圓
                if (!isLoopTrack) {
                    Circle(
                        center = endLatLng,
                        radius = 7.0,
                        fillColor = Color(0x33FF2A55),
                        strokeColor = Color(0xFFFF2A55),
                        strokeWidth = 3f
                    )
                }

                // 起點標記
                Marker(
                    state = rememberMarkerState(position = startLatLng),
                    icon = startMarkerIcon,
                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                    title = "起點 S"
                )

                // 終點標記
                if (!isLoopTrack) {
                    Marker(
                        state = rememberMarkerState(position = endLatLng),
                        icon = finishMarkerIcon,
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        title = "終點 E"
                    )
                }

                // 1. 賽道軌跡 (起點 S -> 檢查點 CP1/CP2 -> 終點 E 紅色實線)
                val googleTrackPts = remember(trackPath, startLat, startLng, endLat, endLng, selectedTrack.mid1Lat, selectedTrack.mid1Lng, selectedTrack.mid2Lat, selectedTrack.mid2Lng) {
                    val pts = mutableListOf<LatLng>()
                    if (startLat > 1.0 && startLng > 1.0) pts.add(LatLng(startLat, startLng))
                    val m1Lat = selectedTrack.mid1Lat
                    val m1Lng = selectedTrack.mid1Lng
                    if (m1Lat != null && m1Lng != null && m1Lat > 1.0) pts.add(LatLng(m1Lat, m1Lng))
                    val m2Lat = selectedTrack.mid2Lat
                    val m2Lng = selectedTrack.mid2Lng
                    if (m2Lat != null && m2Lng != null && m2Lat > 1.0) pts.add(LatLng(m2Lat, m2Lng))
                    if (endLat > 1.0 && endLng > 1.0) pts.add(LatLng(endLat, endLng))

                    if (trackPath.isNotEmpty()) {
                        trackPath.map { LatLng(it.first, it.second) }
                    } else {
                        pts
                    }
                }
                if (googleTrackPts.isNotEmpty()) {
                    Polyline(
                        points = googleTrackPts,
                        color = Color(0xFFFF1744),
                        width = 12f
                    )
                }

                // 2. 中間檢查點 1 & 2 標記 (Mid1 / Mid2 Checkpoints)
                val m1Lat = selectedTrack.mid1Lat
                val m1Lng = selectedTrack.mid1Lng
                if (m1Lat != null && m1Lng != null && m1Lat > 1.0) {
                    val mid1Pos = LatLng(m1Lat, m1Lng)
                    val mid1Icon = remember(context) {
                        try {
                            com.google.android.gms.maps.MapsInitializer.initialize(context)
                            BitmapDescriptorFactory.fromBitmap(createCircleMarker(android.graphics.Color.parseColor("#FF9800"), 36, hollow = true).bitmap)
                        } catch (e: Exception) { BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE) }
                    }
                    Circle(
                        center = mid1Pos,
                        radius = 7.0,
                        fillColor = Color(0x33FF9800),
                        strokeColor = Color(0xFFFF9800),
                        strokeWidth = 2f
                    )
                    Marker(
                        state = rememberMarkerState(position = mid1Pos),
                        icon = mid1Icon,
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        title = "檢查點 1 (CP1)"
                    )
                }

                val m2Lat = selectedTrack.mid2Lat
                val m2Lng = selectedTrack.mid2Lng
                if (m2Lat != null && m2Lng != null && m2Lat > 1.0) {
                    val mid2Pos = LatLng(m2Lat, m2Lng)
                    val mid2Icon = remember(context) {
                        try {
                            com.google.android.gms.maps.MapsInitializer.initialize(context)
                            BitmapDescriptorFactory.fromBitmap(createCircleMarker(android.graphics.Color.parseColor("#FF9800"), 36, hollow = true).bitmap)
                        } catch (e: Exception) { BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE) }
                    }
                    Circle(
                        center = mid2Pos,
                        radius = 7.0,
                        fillColor = Color(0x33FF9800),
                        strokeColor = Color(0xFFFF9800),
                        strokeWidth = 2f
                    )
                    Marker(
                        state = rememberMarkerState(position = mid2Pos),
                        icon = mid2Icon,
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        title = "檢查點 2 (CP2)"
                    )
                }

                // 3. 到達起點之天藍色導航路線 (when !isRacing and !isCircuitMode)
                if (!isRacing && !isCircuitMode) {
                    val navPoints = remember(osrmRoadPoints, gpsData.latitude, gpsData.longitude, trackPath) {
                        val userGeoPt = GeoPoint(gpsData.latitude, gpsData.longitude)
                        if (osrmRoadPoints.isNotEmpty()) {
                            osrmRoadPoints.map { LatLng(it.latitude, it.longitude) }
                        } else {
                            val roadSnappedPath = mutableListOf<LatLng>()
                            roadSnappedPath.add(LatLng(gpsData.latitude, gpsData.longitude))
                            if (trackPath.isNotEmpty()) {
                                val closestIdx = trackPath.indices.minByOrNull { i ->
                                    TrackRepository.haversineDistance(userGeoPt.latitude, userGeoPt.longitude, trackPath[i].first, trackPath[i].second)
                                } ?: 0
                                val subPath = if (closestIdx > 0) {
                                    trackPath.subList(0, closestIdx + 1).reversed()
                                } else {
                                    listOf(trackPath.first())
                                }
                                subPath.forEach { (lt, lg) -> roadSnappedPath.add(LatLng(lt, lg)) }
                            }
                            roadSnappedPath.add(LatLng(startLat, startLng))
                            roadSnappedPath
                        }
                    }
                    Polyline(
                        points = navPoints,
                        color = Color(0xFF00E5FF),
                        width = 12f
                    )
                }

                // 車手 GPS 定位點
                if (hasValidGps) {
                    val playerMarkerState = rememberMarkerState(key = "player_pos", position = LatLng(gpsData.latitude, gpsData.longitude))
                    LaunchedEffect(gpsData.latitude, gpsData.longitude) {
                        playerMarkerState.position = LatLng(gpsData.latitude, gpsData.longitude)
                    }
                    Marker(
                        state = playerMarkerState,
                        icon = playerMarkerIcon,
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        title = "車手定位",
                        zIndex = 1f
                    )
                }

                // 陀螺儀預測點 (Gyro Prediction Point - 確保 zIndex 高於 GPS 定位點)
                val dt = ((System.currentTimeMillis() - gpsData.timestamp) / 1000.0).coerceIn(0.0, 1.5)
                if (hasValidGps && dt < 1.2 && gpsData.speedKmh > 1.0f) {
                    val netAccel = (accelG * 9.80665).toDouble()
                    val v0 = (gpsData.speedKmh / 3.6).toDouble()
                    val displacement = v0 * dt + 0.5 * netAccel * dt * dt
                    val bearingRad = if (liveDrivenPoints.size >= 2) {
                        val p1 = liveDrivenPoints.last()
                        val p2 = liveDrivenPoints[liveDrivenPoints.size - 2]
                        Math.atan2(p1.longitude - p2.longitude, p1.latitude - p2.latitude)
                    } else {
                        0.0
                    }
                    val dLat = Math.toDegrees(displacement * Math.cos(bearingRad) / 6378137.0)
                    val dLng = Math.toDegrees(displacement * Math.sin(bearingRad) / (6378137.0 * Math.cos(Math.toRadians(gpsData.latitude))))
                    val predLatLng = LatLng(gpsData.latitude + dLat, gpsData.longitude + dLng)

                    val predMarkerState = rememberMarkerState(key = "pred_pos", position = predLatLng)
                    LaunchedEffect(predLatLng.latitude, predLatLng.longitude) {
                        predMarkerState.position = predLatLng
                    }
                    Marker(
                        state = predMarkerState,
                        icon = predictedMarkerIcon,
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                        title = "陀螺儀預測",
                        zIndex = 2f
                    )
                }

                // 競速動態熱力航跡 (Speed HSV Heatmap)
                if (isRacing && liveDrivenPoints.size >= 2) {
                    for (i in 0 until liveDrivenPoints.size - 1) {
                        val p1 = liveDrivenPoints[i]
                        val p2 = liveDrivenPoints[i + 1]
                        val segColor = Color(getSpeedHsvColor(p1.speedKmh))
                        Polyline(
                            points = listOf(LatLng(p1.latitude, p1.longitude), LatLng(p2.latitude, p2.longitude)),
                            color = segColor,
                            width = 14f
                        )
                    }
                }
            }
        } else {
            // 1. FULL-SCREEN OSMDROID MAP VIEW
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                Configuration.getInstance().userAgentValue = ctx.packageName

                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)
                    isClickable = true
                    isLongClickable = true
                    
                    addMapListener(object : org.osmdroid.events.MapListener {
                        override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                            isMapFollowing = false
                            return false
                        }
                        override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                            return false
                        }
                    })

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

                    val startGeoPt = GeoPoint(startLat, startLng)
                    val endGeoPt = GeoPoint(endLat, endLng)
                    val isLoopTrack = startGeoPt.distanceToAsDouble(endGeoPt) < 20.0

                    // 起點 7m 氣泡範圍圓 (OSM)
                    val startCircle = OsmPolygon().apply {
                        points = OsmPolygon.pointsAsCircle(startGeoPt, 7.0)
                        fillColor = android.graphics.Color.parseColor("#3300FF66")
                        strokeColor = android.graphics.Color.parseColor("#00FF66")
                        strokeWidth = 3f
                    }
                    overlays.add(startCircle)

                    // 終點 7m 氣泡範圍圓 (OSM)
                    if (!isLoopTrack) {
                        val finishCircle = OsmPolygon().apply {
                            points = OsmPolygon.pointsAsCircle(endGeoPt, 7.0)
                            fillColor = android.graphics.Color.parseColor("#33FF2A55")
                            strokeColor = android.graphics.Color.parseColor("#FF2A55")
                            strokeWidth = 3f
                        }
                        overlays.add(finishCircle)
                    }

                    // 2.1 起點標記 (Start Point: 40px 鮮綠色空心圓環 #4CAF50)
                    val startMarker = OsmMarker(this).apply {
                        position = startGeoPt
                        icon = createCircleMarker(android.graphics.Color.parseColor("#4CAF50"), 40, hollow = true)
                        setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                        title = "起點 S"
                    }
                    overlays.add(startMarker)

                    // 2.1 終點標記 (Finish Point: 若非環狀賽道則顯示 40px 鮮紅色空心圓環 #FF5252)
                    if (!isLoopTrack) {
                        val finishMarker = OsmMarker(this).apply {
                            position = endGeoPt
                            icon = createCircleMarker(android.graphics.Color.parseColor("#FF5252"), 40, hollow = true)
                            setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                            title = "終點 E"
                        }
                        overlays.add(finishMarker)
                    }

                    // 中間檢查點 1 & 2 (OSM)
                    val osmM1Lat = selectedTrack.mid1Lat
                    val osmM1Lng = selectedTrack.mid1Lng
                    if (osmM1Lat != null && osmM1Lng != null && osmM1Lat > 1.0) {
                        val mid1GeoPt = GeoPoint(osmM1Lat, osmM1Lng)
                        val m1Circle = OsmPolygon().apply {
                            points = OsmPolygon.pointsAsCircle(mid1GeoPt, 7.0)
                            fillColor = android.graphics.Color.parseColor("#33FF9800")
                            strokeColor = android.graphics.Color.parseColor("#FF9800")
                            strokeWidth = 2f
                        }
                        overlays.add(m1Circle)
                        val m1Marker = OsmMarker(this).apply {
                            position = mid1GeoPt
                            icon = createCircleMarker(android.graphics.Color.parseColor("#FF9800"), 36, hollow = true)
                            setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                            title = "檢查點 1 (CP1)"
                        }
                        overlays.add(m1Marker)
                    }

                    val osmM2Lat = selectedTrack.mid2Lat
                    val osmM2Lng = selectedTrack.mid2Lng
                    if (osmM2Lat != null && osmM2Lng != null && osmM2Lat > 1.0) {
                        val mid2GeoPt = GeoPoint(osmM2Lat, osmM2Lng)
                        val m2Circle = OsmPolygon().apply {
                            points = OsmPolygon.pointsAsCircle(mid2GeoPt, 7.0)
                            fillColor = android.graphics.Color.parseColor("#33FF9800")
                            strokeColor = android.graphics.Color.parseColor("#FF9800")
                            strokeWidth = 2f
                        }
                        overlays.add(m2Circle)
                        val m2Marker = OsmMarker(this).apply {
                            position = mid2GeoPt
                            icon = createCircleMarker(android.graphics.Color.parseColor("#FF9800"), 36, hollow = true)
                            setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                            title = "檢查點 2 (CP2)"
                        }
                        overlays.add(m2Marker)
                    }

                    val navLine = OsmPolyline().apply {
                        addPoint(startGeoPt)
                        addPoint(startGeoPt)
                        outlinePaint.color = android.graphics.Color.parseColor("#00E5FF")
                        outlinePaint.strokeWidth = 12f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    }
                    overlays.add(navLine)
                    navPolylineInstance = navLine

                    val hasValidGps = gpsData.latitude != 0.0 && gpsData.longitude != 0.0 && gpsData.latitude > 1.0
                    val initialGpsPoint = if (hasValidGps) GeoPoint(gpsData.latitude, gpsData.longitude) else GeoPoint(24.15, 120.67)

                    // 2.2 GPS點 (User Location Point - 雙層天藍色 #00E5FF)
                    val uMarker = OsmMarker(this).apply {
                        position = initialGpsPoint
                        icon = createPlayerIcon(context, android.graphics.Color.parseColor("#00E5FF"))
                        setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                        isEnabled = hasValidGps
                    }
                    overlays.add(uMarker)
                    userMarkerInstance = uMarker

                    // 2.3 陀螺儀預測點 (Gyro Prediction Point - 黃色空心圓環 #FFD600，後加入確保圖層高於 GPS 點)
                    val gMarker = OsmMarker(this).apply {
                        position = initialGpsPoint
                        icon = createPredictedIcon(context)
                        setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_CENTER)
                        isEnabled = false
                    }
                    overlays.add(gMarker)
                    gyroMarkerInstance = gMarker

                    // 地圖初始化：直接以 GPS 定位點為中心 (Zoom 17.5)，絕不從賽道起點跳躍
                    post {
                        controller.setZoom(17.5)
                        if (hasValidGps) {
                            controller.setCenter(initialGpsPoint)
                            hasCameraFocusedOnUser = true
                        }
                    }
                }
            },
            update = { map ->
                currentZoomLevel = map.zoomLevelDouble
                val hasGps = gpsData.latitude != 0.0 && gpsData.longitude != 0.0 && gpsData.latitude > 1.0
                val userGeoPt = GeoPoint(gpsData.latitude, gpsData.longitude)

                if (hasGps) {
                    userMarkerInstance?.isEnabled = true
                    userMarkerInstance?.position = userGeoPt

                    // 2.3 航跡推算與陀螺儀預測點 (Gyroscope Prediction Point - 黃色 #FFD600) 實時更新
                    val lastTimeMs = gpsData.timestamp
                    val dt = ((System.currentTimeMillis() - lastTimeMs) / 1000.0).coerceIn(0.0, 1.5)
                    if (dt < 1.2 && gpsData.speedKmh > 1.0f) {
                        val netAccel = (accelG * 9.80665).toDouble()
                        val v0 = (gpsData.speedKmh / 3.6).toDouble()
                        val displacement = v0 * dt + 0.5 * netAccel * dt * dt

                        val bearingRad = if (liveDrivenPoints.size >= 2) {
                            val p1 = liveDrivenPoints.last()
                            val p2 = liveDrivenPoints[liveDrivenPoints.size - 2]
                            Math.atan2(p1.longitude - p2.longitude, p1.latitude - p2.latitude)
                        } else {
                            0.0
                        }

                        val dLat = Math.toDegrees(displacement * Math.cos(bearingRad) / 6378137.0)
                        val dLng = Math.toDegrees(displacement * Math.sin(bearingRad) / (6378137.0 * Math.cos(Math.toRadians(gpsData.latitude))))
                        val predictedPt = GeoPoint(gpsData.latitude + dLat, gpsData.longitude + dLng)

                        gyroMarkerInstance?.isEnabled = true
                        gyroMarkerInstance?.position = predictedPt
                    } else {
                        gyroMarkerInstance?.isEnabled = false
                    }

                    if (!hasCameraFocusedOnUser) {
                        // 首次載入有效 GPS：焦點瞬間定位 GPS 點位，放大倍率設為 17.5
                        map.controller.setCenter(userGeoPt)
                        map.controller.setZoom(17.5)
                        hasCameraFocusedOnUser = true
                    } else if (isMapFollowing) {
                        map.controller.animateTo(userGeoPt)
                    }
                } else {
                    userMarkerInstance?.isEnabled = false
                    gyroMarkerInstance?.isEnabled = false
                }

                if (!isRacing && !isCircuitMode) {
                    navPolylineInstance?.isEnabled = true

                    if (osrmRoadPoints.isNotEmpty()) {
                        navPolylineInstance?.setPoints(osrmRoadPoints)
                    } else {
                        val roadSnappedPath = mutableListOf<GeoPoint>()
                        roadSnappedPath.add(userGeoPt)

                        if (trackPath.isNotEmpty()) {
                            val closestIdx = trackPath.indices.minByOrNull { i ->
                                TrackRepository.haversineDistance(userGeoPt.latitude, userGeoPt.longitude, trackPath[i].first, trackPath[i].second)
                            } ?: 0

                            val subPath = if (closestIdx > 0) {
                                trackPath.subList(0, closestIdx + 1).reversed()
                            } else {
                                listOf(trackPath.first())
                            }
                            subPath.forEach { (lt, lg) -> roadSnappedPath.add(GeoPoint(lt, lg)) }
                        }
                        roadSnappedPath.add(GeoPoint(startLat, startLng))

                        navPolylineInstance?.setPoints(roadSnappedPath)
                    }
                } else {
                    navPolylineInstance?.isEnabled = false

                    if (liveDrivenPoints.size >= 2) {
                        for (i in 0 until liveDrivenPoints.size - 1) {
                            val p1 = liveDrivenPoints[i]
                            val p2 = liveDrivenPoints[i + 1]
                            val segColor = getSpeedHsvColor(p1.speedKmh)

                            val segPolyline = OsmPolyline().apply {
                                addPoint(GeoPoint(p1.latitude, p1.longitude))
                                addPoint(GeoPoint(p2.latitude, p2.longitude))
                                outlinePaint.color = segColor
                                outlinePaint.strokeWidth = 14f
                                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                            }
                            map.overlays.add(0, segPolyline)
                        }
                    }
                }
                map.invalidate()
            }
        )
    }
        }

        // 2. 獨立頂部懸浮單行膠囊 (Apple Dynamic Island)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = islandScaleX
                        scaleY = islandScaleY
                        translationY = islandOffsetY.toPx()
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
            ) {
                Row(
                    modifier = Modifier
                        .graphicsLayer { alpha = islandContentAlpha }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 導航資訊精簡呈現 (距離 + 轉向圖示 + 直行/到達)
                    val navDistText = remember(distToStartMeters, distToEndMeters, isRacing) {
                        val m = if (!isRacing) distToStartMeters else distToEndMeters
                        if (m >= 1000) {
                            String.format(Locale.getDefault(), "%.1f km", m / 1000f)
                        } else {
                            "${m} m"
                        }
                    }

                    val navActionIcon = remember(distToStartMeters, distToEndMeters, isRacing, isCircuitMode) {
                        val m = if (!isRacing) distToStartMeters else distToEndMeters
                        when {
                            m <= 15 -> Icons.Default.CheckCircle
                            isCircuitMode && isRacing -> Icons.Default.Navigation
                            !isRacing -> Icons.Default.Straight
                            else -> Icons.Default.Navigation
                        }
                    }

                    val navActionLabel = remember(distToStartMeters, distToEndMeters, isRacing, isCircuitMode) {
                        val m = if (!isRacing) distToStartMeters else distToEndMeters
                        when {
                            !isRacing && m <= 15 -> "抵達起點 S"
                            isRacing && !isCircuitMode && m <= 15 -> "抵達終點 E"
                            !isRacing -> "後 直行"
                            isCircuitMode -> "後 直行"
                            else -> "後 直行"
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = navActionIcon,
                            contentDescription = null,
                            tint = if (isRacing) RedPrimary else Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = navDistText,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = navActionLabel,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        var debugBtnPressed by remember { mutableStateOf(false) }
                        val debugBtnScale by animateFloatAsState(
                            targetValue = if (debugBtnPressed) 0.90f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                            label = "debugBtnScale"
                        )

                        // 狀態按鈕：連續快速點擊 3 下可觸發展開/隱藏偵錯日誌面板，具備縮放彈跳動畫效果
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isRacing) RedPrimary.copy(alpha = 0.25f) else Color(0xFF00E5FF).copy(alpha = 0.25f),
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = debugBtnScale
                                    scaleY = debugBtnScale
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            debugBtnPressed = true
                                            tryAwaitRelease()
                                            debugBtnPressed = false
                                        },
                                        onTap = {
                                            val now = System.currentTimeMillis()
                                            if (now - lastDebugClickTimeMs < 500L) {
                                                debugClickCount++
                                            } else {
                                                debugClickCount = 1
                                            }
                                            lastDebugClickTimeMs = now
                                            if (debugClickCount >= 3) {
                                                showDebugPanel = !showDebugPanel
                                                debugClickCount = 0
                                            }
                                        }
                                    )
                                }
                        ) {
                            Text(
                                text = if (isRacing) {
                                    if (isCircuitMode && circuitLapNumber > 0) "第 ${circuitLapNumber} 圈" else "計時中"
                                } else {
                                    if (isCircuitMode) "等待過線" else "導航至起點"
                                },
                                color = if (isRacing) RedPrimary else Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 🐛 實時 偵錯/紀錄 面板 (可收合/展開)
            if (showDebugPanel) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.9f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RedPrimary.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🐛 實時 GPS 與門檻感應偵錯面板", color = RedPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                            // 📋 一鍵複製剪貼簿按鈕
                            Surface(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val logText = buildString {
                                        appendLine("=== REVON 賽道偵錯日誌 ===")
                                        appendLine("賽道代碼: $trackCode")
                                        appendLine("GPS: ${gpsData.latitude}, ${gpsData.longitude} (±${gpsData.accuracyMeters.toInt()}m)")
                                        appendLine("起點距離: ${distToStartMeters}m | 終點距離: ${distToEndMeters}m")
                                        appendLine("地圖縮放: ${String.format(Locale.getDefault(), "%.2f", currentZoomLevel)}")
                                        appendLine("--- 歷史事件日誌 ---")
                                        if (debugLogList.isEmpty()) {
                                            appendLine("(無歷史紀錄)")
                                        } else {
                                            debugLogList.forEach { appendLine(it) }
                                        }
                                    }
                                    val clip = android.content.ClipData.newPlainText("RevonDebugLog", logText)
                                    clipboard.setPrimaryClip(clip)
                                    io.revon.app.ui.components.RevonToastManager.info("已複製偵錯訊息至剪貼簿！")
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF2C2C2E)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📋 複製日誌", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "📡 跑山進度更新: 已推送 ${firebasePushCount} 次 | 狀態: $firebasePushStatus",
                            color = if (firebasePushStatus.contains("成功")) NeonGreen else Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "GPS點位: ${String.format(Locale.getDefault(), "%.5f", gpsData.latitude)}, ${String.format(Locale.getDefault(), "%.5f", gpsData.longitude)} (±${gpsData.accuracyMeters.toInt()}m) · 時速: ${gpsData.speedKmh.toInt()} km/h · G力: ${String.format(Locale.getDefault(), "%.2f", accelG)}G",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "距起點 S: ${distToStartMeters}m (門檻 < 7m) · 距終點 E: ${distToEndMeters}m (門檻 < 7m)",
                            color = NeonGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val debugMapZoom = if (currentMapEngine == GpsConfig.MAP_ENGINE_GOOGLE || currentMapEngine == GpsConfig.MAP_ENGINE_GOOGLE_3D) {
                            googleCameraZoom
                        } else 16.5f
                        Text(
                            text = "🔍 地圖縮放倍率 (Zoom): ${String.format(Locale.getDefault(), "%.2f", debugMapZoom)}",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // 💾 本地 JSON 草稿串流與意外結束防丟失開關
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💾 本地 JSON 草稿: 已寫入 ${draftSaveCount} 次 (${if (lastSavedDraftName.isNotBlank()) lastSavedDraftName else "無草稿"})",
                                color = Color(0xFFFFD700),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Surface(
                                onClick = {
                                    saveDraftOnAbortedRace = !saveDraftOnAbortedRace
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = if (saveDraftOnAbortedRace) Color(0xFF1E3A29) else Color(0xFF3A1E1E),
                                border = BorderStroke(1.dp, if (saveDraftOnAbortedRace) NeonGreen else RedPrimary)
                            ) {
                                Text(
                                    text = if (saveDraftOnAbortedRace) "🛡️ 意外存檔: ON" else "⚠️ 意外存檔: OFF",
                                    color = if (saveDraftOnAbortedRace) NeonGreen else RedPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        // 📂 複製 / 匯出意外中斷 JSON 檔案路徑
                        Surface(
                            onClick = {
                                val abortedFiles = io.revon.app.data.repository.RaceDraftRepository.getAbortedSessions(context)
                                if (abortedFiles.isEmpty()) {
                                    io.revon.app.ui.components.RevonToastManager.info("目前尚無意外中斷備份紀錄")
                                } else {
                                    val latest = abortedFiles.first()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("AbortedJsonPath", latest.absolutePath)
                                    clipboard.setPrimaryClip(clip)
                                    io.revon.app.ui.components.RevonToastManager.info("已複製中斷 JSON 檔路徑 (${abortedFiles.size} 筆備份):\n${latest.name}")
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF2C2C2E)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📂 複製意外中斷 JSON 檔路徑", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(6.dp))

                        Text("觸播歷史與日誌:", color = Color.Gray, fontSize = 10.sp)
                        LazyColumn(modifier = Modifier.heightIn(max = 90.dp)) {
                            items(debugLogList) { log ->
                                Text(log, color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // 3. 滿版底部控制與資訊欄 (直切橫向滿版，無圓弧外邊角，動態測量高度供地圖對齊)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val measuredPx = coordinates.size.height
                    val measuredDp = with(density) { measuredPx.toDp() }
                    if (measuredDp > 0.dp && measuredDp != bottomPanelHeightDp) {
                        bottomPanelHeightDp = measuredDp
                    }
                }
                .graphicsLayer {
                    shadowElevation = 24f
                    translationY = panelOffsetY.toPx()
                    alpha = panelAlpha
                },
            shape = androidx.compose.ui.graphics.RectangleShape,
            color = Color(0xEB121216),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.04f)
                    )
                )
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                // 頂部 Row：大型綠色數位碼表 (左) + 三顆正方形功能按鈕 [鎖定, 定位, 終止] (右)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 大型綠色數位碼表
                    val currentDisplayMs = remember(elapsedMs, isCircuitMode, circuitLapStartTimeMs) {
                        if (isCircuitMode && circuitLapStartTimeMs > 0L) {
                            (System.currentTimeMillis() - circuitLapStartTimeMs).coerceAtLeast(0L)
                        } else {
                            elapsedMs
                        }
                    }
                    Text(
                        text = formatDigitalClock(currentDisplayMs),
                        color = NeonGreen,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f)
                    )

                    var showAbortConfirm by remember { mutableStateOf(false) }

                    if (showAbortConfirm) {
                        AlertDialog(
                            onDismissRequest = { showAbortConfirm = false },
                            containerColor = Color(0xFF1C1C1E),
                            title = {
                                Text("中止計時？", color = Color.White, fontWeight = FontWeight.Bold)
                            },
                            text = {
                                Text(
                                    "計時將強制終止，本次成績不會被記錄。確定要返回路線頁面嗎？",
                                    color = Color.LightGray,
                                    fontSize = 14.sp
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showAbortConfirm = false
                                        if (liveDrivenPoints.isNotEmpty()) {
                                            raceViewModel.finishRace(
                                                driverNickname = currentUserNickname,
                                                realGpsPoints = liveDrivenPoints.toList(),
                                                sessionId = activeSessionId
                                            )
                                            io.revon.app.data.repository.RaceDraftRepository.clearDraft(context, activeSessionId)
                                        } else {
                                            onStopRun()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                                ) {
                                    Text("結算並儲存", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAbortConfirm = false }) {
                                    Text("繼續計時", color = Color.Gray)
                                }
                            }
                        )
                    }

                    // 右下角三顆正方形按鈕區 [鎖定 (黃) | 定位 (綠) | 終止 (紅)]
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. 🔒 鎖定按鈕 (黃框正方形)
                        Surface(
                            onClick = {
                                isScreenLocked = true
                                io.revon.app.ui.components.RevonToastManager.info("螢幕防誤觸鎖定已啟用")
                            },
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF24242A),
                            border = BorderStroke(1.5.dp, Color(0xFFFFB800))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Lock, contentDescription = "鎖定", tint = Color(0xFFFFB800), modifier = Modifier.size(20.dp))
                            }
                        }

                        // 2. 📍 定位按鈕 (綠框正方形)
                        Surface(
                            onClick = {
                                isMapFollowing = true
                                io.revon.app.ui.components.RevonToastManager.info("📍 地圖已鎖定跟隨 GPS 點位")
                            },
                            interactionSource = navInteractionSource,
                            modifier = Modifier
                                .size(42.dp)
                                .graphicsLayer {
                                    scaleX = navBtnScale
                                    scaleY = navBtnScale
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isMapFollowing) NeonGreen.copy(alpha = 0.15f) else Color(0xFF24242A),
                            border = BorderStroke(1.5.dp, if (isMapFollowing) NeonGreen else Color.White.copy(alpha = 0.3f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MyLocation, contentDescription = "定位", tint = if (isMapFollowing) NeonGreen else Color.White, modifier = Modifier.size(20.dp))
                            }
                        }

                        // 3. 🛑 終止按鈕 (紅框正方形)
                        Surface(
                            onClick = {
                                if (isCircuitMode && isRacing) {
                                    finishCircuitRun()
                                } else if (isRacing) {
                                    showAbortConfirm = true
                                } else {
                                    onStopRun()
                                }
                            },
                            interactionSource = stopInteractionSource,
                            modifier = Modifier
                                .size(42.dp)
                                .graphicsLayer {
                                    scaleX = stopBtnScale
                                    scaleY = stopBtnScale
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF24242A),
                            border = BorderStroke(1.5.dp, RedPrimary)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Stop, contentDescription = "終止", tint = RedPrimary, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(8.dp))

                // 下方 Row：即時 Telemetry 數據 (車速、GPS 偏差顆數、G力、剩餘距離)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 車速 (紅色大字 + KM/H)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${gpsData.speedKmh.toInt()}",
                            color = RedPrimary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "KM/H",
                            color = RedPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    // GPS 偏差 + 衛星顆數
                    val (accuracyDot, accuracyColor) = when {
                        gpsData.accuracyMeters <= 10f -> Pair("🟢", NeonGreen)
                        gpsData.accuracyMeters <= 25f -> Pair("🟡", Color(0xFFFFD700))
                        else -> Pair("🔴", RedPrimary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(accuracyDot, fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "偏差:${gpsData.accuracyMeters.toInt()}m (${satelliteCount}顆)",
                            color = accuracyColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 即時 G 力
                    val displayG = if (realImuBrakingG > 0.05f) -realImuBrakingG else (if (realImuAccelG > 0.05f) realImuAccelG else (if (realImuLongG != 0f) realImuLongG else accelG))
                    val gSign = if (displayG >= 0) "+" else ""
                    val gColor = if (displayG > 0.05f) NeonGreen else if (displayG < -0.05f) RedPrimary else Color.White
                    Text(
                        text = "G:${gSign}${String.format(Locale.getDefault(), "%.2f", displayG)}",
                        color = gColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // 距起點/終點距離
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isRacing) "距終點: " else "距起點: ",
                            color = RedPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isRacing) "${distToEndMeters}m" else "${distToStartMeters}m",
                            color = RedPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // 4. 全螢幕防誤觸鎖定層 (當 isScreenLocked 為 true 時覆蓋並阻斷觸控)
        if (isScreenLocked) {
            LockScreenOverlay(
                onUnlock = {
                    isScreenLocked = false
                }
            )
        }
    }
}

/**
 * 賽道計時全螢幕防誤觸鎖定浮層與滑動解鎖 slider
 */
@Composable
fun LockScreenOverlay(
    onUnlock: () -> Unit
) {
    val context = LocalContext.current
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val maxDragPx = with(LocalDensity.current) { 230.dp.toPx() }
    val isUnlocked = dragOffsetX >= maxDragPx * 0.8f

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            try {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(80, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(80)
                }
            } catch (_: Exception) {}
            onUnlock()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                // 阻斷下方按鈕的所有點擊手勢
                detectTapGestures { }
            }
    ) {
        // 頂部防誤觸狀態提示標籤
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 95.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xEE1C1C24),
            border = BorderStroke(1.dp, Color(0xFFFFB800).copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🔒", fontSize = 15.sp)
                Text(
                    text = "螢幕防誤觸鎖定中 · 請向右滑動滑塊解鎖",
                    color = Color(0xFFFFB800),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 下方滑動解鎖 Bar (Slide to Unlock)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp, start = 32.dp, end = 32.dp)
                .fillMaxWidth()
                .height(62.dp)
                .background(Color(0xFA14151B), shape = RoundedCornerShape(31.dp))
                .border(
                    1.5.dp,
                    Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFF00FF66))),
                    shape = RoundedCornerShape(31.dp)
                )
        ) {
            // 文字動態指引
            Text(
                text = ">>> 向右滑動解鎖螢幕 >>>",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.align(Alignment.Center)
            )

            val animOffsetX by animateFloatAsState(
                targetValue = dragOffsetX,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                label = "unlockThumbAnim"
            )

            // 可拖曳滑塊 Thumb
            Box(
                modifier = Modifier
                    .offset { IntOffset(animOffsetX.toInt(), 0) }
                    .size(62.dp)
                    .padding(4.dp)
                    .background(
                        brush = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF00FF66))),
                        shape = RoundedCornerShape(27.dp)
                    )
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragOffsetX < maxDragPx * 0.8f) {
                                    dragOffsetX = 0f
                                }
                            },
                            onDragCancel = {
                                dragOffsetX = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                dragOffsetX = (dragOffsetX + dragAmount).coerceIn(0f, maxDragPx)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔓",
                    fontSize = 20.sp
                )
            }
        }
    }
}




/**
 * 依時速將軌跡顏色轉換為 HSV 熱力色彩 (藍 0km/h -> 綠 60km/h -> 紅 120+km/h)
 */
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

/**
 * 陀螺儀預測點 (黃色 #FFD600) Icon 生成函式
 * 32dp 黃色空心圓環 (Paint.Style.STROKE, 線條寬度 3dp)
 */
private fun createPredictedIcon(context: Context): BitmapDrawable {
    val d = context.resources.displayMetrics.density
    val s = (32 * d).toInt()
    val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(b).apply {
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = android.graphics.Color.parseColor("#FFD600")
        p.style = android.graphics.Paint.Style.STROKE
        p.strokeWidth = 3 * d
        drawCircle(s / 2f, s / 2f, s / 4f, p)
    }
    return BitmapDrawable(b)
}

@Composable
private fun ResultsStep(
    result: io.revon.app.data.model.RaceResult?,
    lastSessionRecord: io.revon.app.data.model.RaceSessionRecord?,
    onRaceAgain: () -> Unit,
    onViewLeaderboard: () -> Unit,
    onViewDetail: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "紀錄已完成", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(text = result?.trackName ?: "", color = Color.Gray, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NeonGreen, shape = RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "計時成績",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result?.finishTimeDisplay ?: "00:00.000",
                    color = NeonGreen,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val sid = lastSessionRecord?.sessionId ?: ""
                if (sid.isNotBlank()) {
                    onViewDetail(sid)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
        ) {
            Icon(Icons.Default.Map, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "瀏覽詳細軌跡紀錄", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onViewLeaderboard,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
        ) {
            Icon(Icons.Default.Leaderboard, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "查看山道名人堂", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onRaceAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = "再次挑戰山道", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color.Gray)
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color.Gray)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "離開", color = Color.Gray, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatDigitalClock(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val millis = (ms % 1000) / 10
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", minutes, seconds, millis)
}
