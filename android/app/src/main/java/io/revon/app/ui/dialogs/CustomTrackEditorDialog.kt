package io.revon.app.ui.dialogs

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.location.Geocoder
import android.os.Looper
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import io.revon.app.data.config.GpsConfig
import io.revon.app.data.model.Difficulty
import io.revon.app.data.model.Track
import io.revon.app.data.model.TrackCategory
import io.revon.app.ui.theme.NeonGreen
import io.revon.app.ui.theme.RedPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.regex.Pattern

private fun format7Decimals(value: Double): String {
    return String.format(Locale.US, "%.7f", value)
}

private fun roundTo7Decimals(value: Double): Double {
    return String.format(Locale.US, "%.7f", value).toDoubleOrNull() ?: value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTrackEditorDialog(
    initialTrack: Track? = null,
    userLat: Double = 24.15,
    userLng: Double = 120.67,
    onDismiss: () -> Unit,
    onSaveTrack: (Track) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // 地圖引擎設定 (預設從 GpsConfig 讀取)
    var mapEngine by remember { mutableIntStateOf(GpsConfig.getMapEngine(context)) }

    // 外層捲動控制 State
    var isOuterScrollEnabled by remember { mutableStateOf(true) }

    // 使用者 GPS 座標
    var userCurrentLat by remember { mutableDoubleStateOf(if (userLat > 1.0) userLat else 24.15) }
    var userCurrentLng by remember { mutableDoubleStateOf(if (userLng > 1.0) userLng else 120.67) }

    // 區域偵測
    var detectedRegion by remember { mutableStateOf(initialTrack?.region ?: "Taiwan · Hualien") }

    // GPS 定位監聽器
    DisposableEffect(context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).build()
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                if (loc.latitude > 1.0 && loc.longitude > 1.0) {
                    userCurrentLat = roundTo7Decimals(loc.latitude)
                    userCurrentLng = roundTo7Decimals(loc.longitude)

                    if (initialTrack == null && detectedRegion == "Taiwan · Hualien") {
                        coroutineScope.launch {
                            val r = getRegionNameFromGps(context, loc.latitude, loc.longitude)
                            detectedRegion = r
                        }
                    }
                }
            }
        }
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // 忽略權限異常
        }
        onDispose {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    var trackName by remember { mutableStateOf(initialTrack?.name ?: "") }
    var selectedCategory by remember { mutableStateOf(initialTrack?.category ?: TrackCategory.CIRCUIT) }

    // 教學選單
    var showTutorial by remember { mutableStateOf(false) }

    // 搜尋與 URL 解析 State
    var addressSearchQuery by remember { mutableStateOf("") }
    var googleMapsUrlQuery by remember { mutableStateOf("") }
    var isParsingUrl by remember { mutableStateOf(false) }
    var isSearchingAddress by remember { mutableStateOf(false) }

    // 免責條款
    var isDisclaimerAgreed by remember { mutableStateOf(false) }

    // 選中點 index
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

    // 賽點列表：[Start (起點), Mid1 (審查1), Mid2 (審查2), End (終點)]
    val waypoints = remember {
        mutableStateListOf<GeoPoint>().apply {
            initialTrack?.let { t ->
                if (t.startLat != null && t.startLng != null && t.startLat > 1.0) add(GeoPoint(roundTo7Decimals(t.startLat), roundTo7Decimals(t.startLng)))
                if (t.mid1Lat != null && t.mid1Lng != null && t.mid1Lat > 1.0) add(GeoPoint(roundTo7Decimals(t.mid1Lat), roundTo7Decimals(t.mid1Lng)))
                if (t.mid2Lat != null && t.mid2Lng != null && t.mid2Lat > 1.0) add(GeoPoint(roundTo7Decimals(t.mid2Lat), roundTo7Decimals(t.mid2Lng)))
                if (t.endLat != null && t.endLng != null && t.endLat > 1.0) add(GeoPoint(roundTo7Decimals(t.endLat), roundTo7Decimals(t.endLng)))
            }
        }
    }

    // 手動座標輸入欄 Text State (精度為小數點後 7 位)
    var startLatText by remember { mutableStateOf("") }
    var startLngText by remember { mutableStateOf("") }
    var cp1LatText by remember { mutableStateOf("") }
    var cp1LngText by remember { mutableStateOf("") }
    var cp2LatText by remember { mutableStateOf("") }
    var cp2LngText by remember { mutableStateOf("") }
    var endLatText by remember { mutableStateOf("") }
    var endLngText by remember { mutableStateOf("") }

    // 同步 waypoints 座標至輸入框
    fun syncWaypointsToTextInputs() {
        startLatText = waypoints.getOrNull(0)?.latitude?.let { format7Decimals(it) } ?: ""
        startLngText = waypoints.getOrNull(0)?.longitude?.let { format7Decimals(it) } ?: ""

        when (waypoints.size) {
            0, 1 -> {
                cp1LatText = ""; cp1LngText = ""
                cp2LatText = ""; cp2LngText = ""
                endLatText = ""; endLngText = ""
            }
            2 -> {
                // index 0 = 起, index 1 = 終
                cp1LatText = ""; cp1LngText = ""
                cp2LatText = ""; cp2LngText = ""
                endLatText = format7Decimals(waypoints[1].latitude)
                endLngText = format7Decimals(waypoints[1].longitude)
            }
            3 -> {
                // index 0 = 起, index 1 = 審查1, index 2 = 終
                cp1LatText = format7Decimals(waypoints[1].latitude)
                cp1LngText = format7Decimals(waypoints[1].longitude)
                cp2LatText = ""; cp2LngText = ""
                endLatText = format7Decimals(waypoints[2].latitude)
                endLngText = format7Decimals(waypoints[2].longitude)
            }
            else -> {
                // index 0 = 起, index 1 = 審查1, index 2 = 審查2, index 3 = 終
                cp1LatText = format7Decimals(waypoints[1].latitude)
                cp1LngText = format7Decimals(waypoints[1].longitude)
                cp2LatText = format7Decimals(waypoints[2].latitude)
                cp2LngText = format7Decimals(waypoints[2].longitude)
                endLatText = format7Decimals(waypoints[3].latitude)
                endLngText = format7Decimals(waypoints[3].longitude)
            }
        }
    }

    LaunchedEffect(waypoints.size) {
        syncWaypointsToTextInputs()
    }

    // 依 index 取得點名稱與顏色
    fun getPointInfo(index: Int, size: Int): Pair<String, String> {
        return when (index) {
            0 -> Pair("S", "#FF2E55")
            size - 1 -> if (size > 1) Pair("E", "#FF2E55") else Pair("S", "#FF2E55")
            1 -> Pair("C1", "#FF9800")
            2 -> Pair("C2", "#FF9800")
            else -> Pair("P${index + 1}", "#00E5FF")
        }
    }

    // 新增賽點 (最多4個：起點、終點、審查1、審查2)
    fun addPointWithShifting(newPt: GeoPoint) {
        val roundedPt = GeoPoint(roundTo7Decimals(newPt.latitude), roundTo7Decimals(newPt.longitude))
        when (waypoints.size) {
            0 -> {
                waypoints.add(roundedPt)
                selectedPointIndex = 0
                io.revon.app.ui.components.RevonToastManager.success("已新增「起點」")
            }
            1 -> {
                waypoints.add(roundedPt)
                selectedPointIndex = 1
                io.revon.app.ui.components.RevonToastManager.success("已新增「終點」")
            }
            2 -> {
                waypoints.add(roundedPt)
                selectedPointIndex = 2
                io.revon.app.ui.components.RevonToastManager.info("已新增「審查點1」，終點自動移至次點")
            }
            3 -> {
                waypoints.add(roundedPt)
                selectedPointIndex = 3
                io.revon.app.ui.components.RevonToastManager.info("已新增「審查點2」，終點自動移至次點")
            }
            else -> {
                io.revon.app.ui.components.RevonToastManager.warning("最多僅支援 4 個賽點 (起點、審查1、審查2、終點)")
            }
        }
        syncWaypointsToTextInputs()
    }

    // 刪除指定點
    fun removePointAt(index: Int) {
        if (index in 0 until waypoints.size) {
            waypoints.removeAt(index)
            if (selectedPointIndex == index) {
                selectedPointIndex = null
            } else if (selectedPointIndex != null && selectedPointIndex!! > index) {
                selectedPointIndex = selectedPointIndex!! - 1
            }
            syncWaypointsToTextInputs()
            io.revon.app.ui.components.RevonToastManager.info("已刪除第 ${index + 1} 個賽點")
        }
    }

    // 位置變更觸發區域偵測
    fun checkAndDetectRegion(geoPt: GeoPoint) {
        coroutineScope.launch {
            val r = getRegionNameFromGps(context, geoPt.latitude, geoPt.longitude)
            detectedRegion = r
        }
    }

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    val googleCameraPositionState = rememberCameraPositionState {
        val initialPt = if (waypoints.isNotEmpty()) waypoints.first() else GeoPoint(userCurrentLat, userCurrentLng)
        position = CameraPosition.fromLatLngZoom(LatLng(initialPt.latitude, initialPt.longitude), 16.5f)
    }

    // 計算路線總長 (Derived State)
    val totalDistanceKm by remember {
        derivedStateOf {
            if (waypoints.size < 2) {
                if (waypoints.size == 1) 0.1 else 0.0
            } else {
                var distAcc = 0.0
                for (i in 1 until waypoints.size) {
                    distAcc += waypoints[i - 1].distanceToAsDouble(waypoints[i]) / 1000.0
                }
                (distAcc * 10).toInt() / 10.0
            }
        }
    }

    // 移動鏡頭至特定點
    fun moveCameraToPoint(geoPoint: GeoPoint) {
        if (mapEngine == GpsConfig.MAP_ENGINE_GOOGLE) {
            coroutineScope.launch {
                googleCameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(geoPoint.latitude, geoPoint.longitude), 16.5f))
            }
        } else {
            mapViewInstance?.let { mv ->
                mv.controller.animateTo(geoPoint)
                mv.controller.setZoom(16.5)
            }
        }
    }

    // OsmDroid 圖層更新
    fun updateOsmMapOverlays(mapView: MapView) {
        mapView.overlays.removeAll { it is Marker || it is Polyline }

        // 1. 繪製當前 GPS 位置 Marker
        val userGpsGeoPoint = GeoPoint(userCurrentLat, userCurrentLng)
        val userGpsMarker = Marker(mapView).apply {
            position = userGpsGeoPoint
            icon = createMarkerBitmapDrawable(context, "#00E5FF", "GPS", isSelected = false)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "當前 GPS 位置"
        }
        mapView.overlays.add(userGpsMarker)

        // 2. 繪製賽點
        if (waypoints.isNotEmpty()) {
            val polyline = Polyline().apply {
                outlinePaint.color = android.graphics.Color.parseColor("#00E5FF")
                outlinePaint.strokeWidth = 10f
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            }

            for (i in 0 until waypoints.size) {
                val pt = waypoints[i]
                polyline.addPoint(pt)

                val (label, colorStr) = getPointInfo(i, waypoints.size)
                val isSelected = (selectedPointIndex == i)

                val marker = Marker(mapView).apply {
                    position = pt
                    icon = createMarkerBitmapDrawable(context, colorStr, label, isSelected = isSelected)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = when (i) {
                        0 -> "起點 S"
                        waypoints.size - 1 -> if (waypoints.size > 1) "終點 E" else "起點 S"
                        1 -> "審查點 1"
                        2 -> "審查點 2"
                        else -> "賽點 ${i + 1}"
                    }
                    isDraggable = true
                    setOnMarkerClickListener { _, _ ->
                        selectedPointIndex = i
                        updateOsmMapOverlays(mapView)
                        true
                    }
                    setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                        override fun onMarkerDragStart(marker: Marker) {
                            selectedPointIndex = i
                            updateOsmMapOverlays(mapView)
                        }
                        override fun onMarkerDrag(marker: Marker) {}
                        override fun onMarkerDragEnd(marker: Marker) {
                            marker.position?.let { newPos ->
                                if (i < waypoints.size) {
                                    waypoints[i] = GeoPoint(roundTo7Decimals(newPos.latitude), roundTo7Decimals(newPos.longitude))
                                    syncWaypointsToTextInputs()
                                    updateOsmMapOverlays(mapView)
                                }
                            }
                        }
                    })
                }
                mapView.overlays.add(marker)
            }
            mapView.overlays.add(0, polyline)
        }

        mapView.invalidate()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF101014)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 頂部標題列
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A20))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (initialTrack == null) "自訂賽道編輯器 (Circuit Editor)" else "編輯賽點 (${initialTrack.name})",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "關閉", tint = Color.White)
                    }
                }

                // 主可滾動表單區
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState(), enabled = isOuterScrollEnabled)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 第一步：路線名稱 *
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row {
                            Text("路線名稱", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(" *", color = RedPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedTextField(
                            value = trackName,
                            onValueChange = { trackName = it },
                            placeholder = { Text("請輸入路線名稱", color = Color(0xFF666670), fontSize = 14.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF1A1A22),
                                unfocusedContainerColor = Color(0xFF1A1A22),
                                focusedBorderColor = Color(0xFF444450),
                                unfocusedBorderColor = Color(0xFF2C2C36)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 偵測區域
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("所在區域 (自動偵測)", color = Color.LightGray, fontSize = 13.sp)
                        OutlinedTextField(
                            value = detectedRegion,
                            onValueChange = { detectedRegion = it },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.LightGray,
                                unfocusedTextColor = Color.LightGray,
                                focusedContainerColor = Color(0xFF1A1A22),
                                unfocusedContainerColor = Color(0xFF1A1A22),
                                focusedBorderColor = Color(0xFF2C2C36),
                                unfocusedBorderColor = Color(0xFF2C2C36)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 第二步：座標教學 Switch
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1C1C24),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E3A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💡", fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("座標設定教學", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            Switch(
                                checked = showTutorial,
                                onCheckedChange = { showTutorial = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = RedPrimary,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color(0xFF2A2A34)
                                )
                            )
                        }
                    }

                    // 教學詳細步驟
                    AnimatedVisibility(
                        visible = showTutorial,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF181820),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333342)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("📍 座標設定 6 步驟指南", color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("1️⃣ 輸入路線名稱（必填）。", color = Color.LightGray, fontSize = 12.sp)
                                Text("2️⃣ 方法一：於搜尋欄輸入地標名稱（例如：台21線）。", color = Color.LightGray, fontSize = 12.sp)
                                Text("3️⃣ 方法二：貼上 Google Maps 共享連結，點擊「解析」。", color = Color.LightGray, fontSize = 12.sp)
                                Text("4️⃣ 方法三：移動地圖，點擊地圖即新增賽點。可拖曳賽點微調。", color = Color.LightGray, fontSize = 12.sp)
                                Text("5️⃣ 預設第 2 個點為終點。若繼續新增，終點將自動推延至審查1 / 審查2。", color = Color.LightGray, fontSize = 12.sp)
                                Text("6️⃣ 閱讀下方免責聲明並勾選同意，點擊「創建路線」即完成。", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                    }

                    // 第三步：座標設定
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row {
                            Text("座標設定（至少需設定起點）", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(" *", color = RedPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        // 方法一：輸入地標名稱
                        OutlinedTextField(
                            value = addressSearchQuery,
                            onValueChange = { addressSearchQuery = it },
                            placeholder = { Text("搜尋地標名稱（例如：台21線）", color = Color(0xFF666670), fontSize = 13.sp) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                            trailingIcon = {
                                if (addressSearchQuery.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            keyboardController?.hide()
                                            isSearchingAddress = true
                                            coroutineScope.launch {
                                                val foundPt = searchAddressToGeoPoint(context, addressSearchQuery)
                                                isSearchingAddress = false
                                                if (foundPt != null) {
                                                    addPointWithShifting(foundPt)
                                                    checkAndDetectRegion(foundPt)
                                                    moveCameraToPoint(foundPt)
                                                } else {
                                                    io.revon.app.ui.components.RevonToastManager.info("未找到該地標之座標")
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.ArrowForward, contentDescription = "搜尋", tint = NeonGreen)
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboardController?.hide()
                                isSearchingAddress = true
                                coroutineScope.launch {
                                    val foundPt = searchAddressToGeoPoint(context, addressSearchQuery)
                                    isSearchingAddress = false
                                    if (foundPt != null) {
                                        addPointWithShifting(foundPt)
                                        checkAndDetectRegion(foundPt)
                                        moveCameraToPoint(foundPt)
                                    } else {
                                        io.revon.app.ui.components.RevonToastManager.info("未找到該地標之座標")
                                    }
                                }
                            }),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF1A1A22),
                                unfocusedContainerColor = Color(0xFF1A1A22),
                                focusedBorderColor = Color(0xFF444450),
                                unfocusedBorderColor = Color(0xFF2C2C36)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 方法二：貼上 Google Maps 共享連結
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = googleMapsUrlQuery,
                                onValueChange = { googleMapsUrlQuery = it },
                                placeholder = { Text("貼上 Google Maps 共享網址", color = Color(0xFF666670), fontSize = 12.sp) },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = Color.Gray) },
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF1A1A22),
                                    unfocusedContainerColor = Color(0xFF1A1A22),
                                    focusedBorderColor = Color(0xFF444450),
                                    unfocusedBorderColor = Color(0xFF2C2C36)
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    if (googleMapsUrlQuery.isBlank()) {
                                        io.revon.app.ui.components.RevonToastManager.info("請先貼上 Google Maps 共享網址")
                                        return@Button
                                    }
                                    isParsingUrl = true
                                    coroutineScope.launch {
                                        val parsedPt = parseGoogleMapsUrlToLatLng(googleMapsUrlQuery)
                                        isParsingUrl = false
                                        if (parsedPt != null) {
                                            addPointWithShifting(parsedPt)
                                            checkAndDetectRegion(parsedPt)
                                            moveCameraToPoint(parsedPt)
                                        } else {
                                            io.revon.app.ui.components.RevonToastManager.info("無法從該網址解析經緯度座標")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                if (isParsingUrl) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text("解析", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 第四步：賽點 Chips (起點 / 審查1 / 審查2 / 終點)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val hasStart = waypoints.size >= 1
                            val hasEnd = waypoints.size >= 2
                            val hasCp1 = waypoints.size >= 3
                            val hasCp2 = waypoints.size >= 4

                            PointStatusChip(
                                modifier = Modifier.weight(1f),
                                title = "起點",
                                isFilled = hasStart,
                                activeColor = Color(0xFF00C853),
                                inactiveColor = RedPrimary,
                                isSelected = (selectedPointIndex == 0),
                                onClick = {
                                    if (hasStart) {
                                        selectedPointIndex = 0
                                        moveCameraToPoint(waypoints[0])
                                    }
                                }
                            )

                            PointStatusChip(
                                modifier = Modifier.weight(1f),
                                title = "審查1",
                                isFilled = hasCp1,
                                activeColor = Color(0xFF00C853),
                                inactiveColor = Color(0xFFFFD600),
                                isSelected = (selectedPointIndex == 1 && waypoints.size >= 3),
                                onClick = {
                                    if (waypoints.size >= 3) {
                                        selectedPointIndex = 1
                                        moveCameraToPoint(waypoints[1])
                                    }
                                }
                            )

                            PointStatusChip(
                                modifier = Modifier.weight(1f),
                                title = "審查2",
                                isFilled = hasCp2,
                                activeColor = Color(0xFF00C853),
                                inactiveColor = Color(0xFFFFD600),
                                isSelected = (selectedPointIndex == 2 && waypoints.size >= 4),
                                onClick = {
                                    if (waypoints.size >= 4) {
                                        selectedPointIndex = 2
                                        moveCameraToPoint(waypoints[2])
                                    }
                                }
                            )

                            PointStatusChip(
                                modifier = Modifier.weight(1f),
                                title = "終點",
                                isFilled = hasEnd,
                                activeColor = Color(0xFF00C853),
                                inactiveColor = RedPrimary,
                                isSelected = (selectedPointIndex == waypoints.size - 1 && waypoints.size >= 2),
                                onClick = {
                                    if (waypoints.size >= 2) {
                                        val endIdx = waypoints.size - 1
                                        selectedPointIndex = endIdx
                                        moveCameraToPoint(waypoints[endIdx])
                                    }
                                }
                            )
                        }

                        // 地圖容器 (加入 pointerInput 攔截手勢，防地圖操作與外層滾動衝突)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF2C2C36), RoundedCornerShape(12.dp))
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        isOuterScrollEnabled = false
                                        do {
                                            val event = awaitPointerEvent()
                                        } while (event.changes.any { it.pressed })
                                        isOuterScrollEnabled = true
                                    }
                                }
                        ) {
                            if (mapEngine == GpsConfig.MAP_ENGINE_GOOGLE) {
                                // Google Maps API 渲染
                                GoogleMap(
                                    modifier = Modifier.fillMaxSize(),
                                    cameraPositionState = googleCameraPositionState,
                                    uiSettings = MapUiSettings(
                                        zoomControlsEnabled = false,
                                        compassEnabled = true,
                                        myLocationButtonEnabled = false
                                    ),
                                    properties = MapProperties(mapType = MapType.NORMAL),
                                    onMapClick = { clickedLatLng ->
                                        selectedPointIndex = null
                                        addPointWithShifting(GeoPoint(clickedLatLng.latitude, clickedLatLng.longitude))
                                    }
                                ) {
                                    // 當前 GPS 位置 Marker
                                    val userLatLng = LatLng(userCurrentLat, userCurrentLng)
                                    Marker(
                                        state = rememberMarkerState(position = userLatLng),
                                        title = "當前 GPS 位置",
                                        icon = remember(context) { createGoogleMarkerBitmapDescriptor(context, "#00E5FF", "GPS", false) }
                                    )

                                    // 賽道路線 Polyline
                                    if (waypoints.size >= 2) {
                                        Polyline(
                                            points = waypoints.map { LatLng(it.latitude, it.longitude) },
                                            color = Color(0xFF00E5FF),
                                            width = 10f
                                        )
                                    }

                                    // 賽點 Markers (高亮選中與精確7位)
                                    waypoints.forEachIndexed { i, pt ->
                                        val (label, colorHex) = getPointInfo(i, waypoints.size)
                                        val isSelected = (selectedPointIndex == i)

                                        val markerState = rememberMarkerState(position = LatLng(pt.latitude, pt.longitude))
                                        LaunchedEffect(markerState.position) {
                                            val rLat = roundTo7Decimals(markerState.position.latitude)
                                            val rLng = roundTo7Decimals(markerState.position.longitude)
                                            if (rLat != pt.latitude || rLng != pt.longitude) {
                                                waypoints[i] = GeoPoint(rLat, rLng)
                                                syncWaypointsToTextInputs()
                                            }
                                        }

                                        Marker(
                                            state = markerState,
                                            title = when (i) {
                                                0 -> "起點 S"
                                                waypoints.size - 1 -> if (waypoints.size > 1) "終點 E" else "起點 S"
                                                1 -> "審查點 1"
                                                2 -> "審查點 2"
                                                else -> "賽點 ${i + 1}"
                                            },
                                            draggable = true,
                                            onClick = {
                                                selectedPointIndex = i
                                                false
                                            },
                                            icon = remember(context, label, colorHex, isSelected) {
                                                createGoogleMarkerBitmapDescriptor(context, colorHex, label, isSelected)
                                            }
                                        )
                                    }
                                }
                            } else {
                                // OsmDroid 渲染
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                                        Configuration.getInstance().userAgentValue = ctx.packageName

                                        MapView(ctx).apply {
                                            setTileSource(TileSourceFactory.MAPNIK)
                                            setMultiTouchControls(true)
                                            isClickable = true

                                            setOnTouchListener { v, event ->
                                                when (event.action) {
                                                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                                        v.parent.requestDisallowInterceptTouchEvent(true)
                                                    }
                                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                                        v.parent.requestDisallowInterceptTouchEvent(false)
                                                    }
                                                }
                                                false
                                            }

                                            val matrix = ColorMatrix()
                                            matrix.setSaturation(0.2f)
                                            val scale = 0.5f
                                            matrix.postConcat(ColorMatrix(floatArrayOf(
                                                scale, 0f, 0f, 0f, 0f,
                                                0f, scale, 0f, 0f, 0f,
                                                0f, 0f, scale, 0f, 0f,
                                                0f, 0f, 0f, 1f, 0f
                                            )))
                                            overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))

                                            val centerPt = if (waypoints.isNotEmpty()) waypoints.first() else GeoPoint(userCurrentLat, userCurrentLng)
                                            controller.setCenter(centerPt)
                                            controller.setZoom(16.5)

                                            val eventsReceiver = object : MapEventsReceiver {
                                                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                                    selectedPointIndex = null
                                                    addPointWithShifting(p)
                                                    updateOsmMapOverlays(this@apply)
                                                    return true
                                                }

                                                override fun longPressHelper(p: GeoPoint): Boolean { return false }
                                            }
                                            overlays.add(MapEventsOverlay(eventsReceiver))

                                            mapViewInstance = this
                                            updateOsmMapOverlays(this)
                                        }
                                    },
                                    update = { mapView ->
                                        mapViewInstance = mapView
                                        updateOsmMapOverlays(mapView)
                                    }
                                )
                            }

                            // 選中點快速編輯與刪除面板
                            selectedPointIndex?.let { selIdx ->
                                if (selIdx in 0 until waypoints.size) {
                                    val (selLabel, _) = getPointInfo(selIdx, waypoints.size)
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(10.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color(0xEE1E1E26),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD600))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("已選取：賽點 ${selIdx + 1} [$selLabel]", color = Color.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                                            // 刪除該選中點
                                            IconButton(
                                                onClick = {
                                                    removePointAt(selIdx)
                                                    mapViewInstance?.let { updateOsmMapOverlays(it) }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "刪除點", tint = RedPrimary, modifier = Modifier.size(16.dp))
                                            }

                                            // 關閉面版
                                            IconButton(
                                                onClick = {
                                                    selectedPointIndex = null
                                                    mapViewInstance?.let { updateOsmMapOverlays(it) }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "關閉", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            // 復原與清空按鈕
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    onClick = {
                                        if (waypoints.isNotEmpty()) {
                                            removePointAt(waypoints.size - 1)
                                            mapViewInstance?.let { updateOsmMapOverlays(it) }
                                        }
                                    },
                                    shape = CircleShape,
                                    color = Color(0xDD1A1A22)
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Delete, contentDescription = "復原", tint = RedPrimary, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("復原", color = Color.White, fontSize = 11.sp)
                                    }
                                }

                                Surface(
                                    onClick = {
                                        waypoints.clear()
                                        selectedPointIndex = null
                                        syncWaypointsToTextInputs()
                                        mapViewInstance?.let { updateOsmMapOverlays(it) }
                                    },
                                    shape = CircleShape,
                                    color = Color(0xDD1A1A22)
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Refresh, contentDescription = "清空", tint = Color.Yellow, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("清空", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }

                            // 定位按鈕
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    onClick = {
                                        val targetPt = LatLng(userCurrentLat, userCurrentLng)
                                        if (mapEngine == GpsConfig.MAP_ENGINE_GOOGLE) {
                                            coroutineScope.launch {
                                                googleCameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(targetPt, 16.5f))
                                            }
                                        } else {
                                            mapViewInstance?.let { mv ->
                                                mv.controller.animateTo(GeoPoint(userCurrentLat, userCurrentLng))
                                                mv.controller.setZoom(16.5)
                                            }
                                        }
                                        io.revon.app.ui.components.RevonToastManager.info("已移至當前 GPS 位置")
                                    },
                                    shape = CircleShape,
                                    color = Color(0xEE1C1C24),
                                    contentColor = NeonGreen
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.MyLocation, contentDescription = "定位", tint = NeonGreen, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }

                        // 使用當前位置設定新點按鈕
                        Surface(
                            onClick = {
                                val currentGpsPt = GeoPoint(roundTo7Decimals(userCurrentLat), roundTo7Decimals(userCurrentLng))
                                addPointWithShifting(currentGpsPt)
                                checkAndDetectRegion(currentGpsPt)
                                moveCameraToPoint(currentGpsPt)
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E1E26),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333342)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🎯", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "使用當前位置新增賽點",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // 分隔線：手動輸入座標
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2C2C36))
                            Text(
                                text = "  或手動輸入座標  ",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF2C2C36))
                        }

                        // 第五步：手動座標輸入欄位
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ManualCoordinateRow(
                                label = "起點座標 *",
                                latValue = startLatText,
                                lngValue = startLngText,
                                onLatChange = {
                                    startLatText = it
                                    updateWaypointFromText(0, startLatText, startLngText, waypoints)
                                },
                                onLngChange = {
                                    startLngText = it
                                    updateWaypointFromText(0, startLatText, startLngText, waypoints)
                                }
                            )

                            ManualCoordinateRow(
                                label = "審查點 1 座標 (選填)",
                                latValue = cp1LatText,
                                lngValue = cp1LngText,
                                onLatChange = {
                                    cp1LatText = it
                                    updateWaypointFromText(1, cp1LatText, cp1LngText, waypoints)
                                },
                                onLngChange = {
                                    cp1LngText = it
                                    updateWaypointFromText(1, cp1LatText, cp1LngText, waypoints)
                                }
                            )

                            ManualCoordinateRow(
                                label = "審查點 2 座標 (選填)",
                                latValue = cp2LatText,
                                lngValue = cp2LngText,
                                onLatChange = {
                                    cp2LatText = it
                                    updateWaypointFromText(2, cp2LatText, cp2LngText, waypoints)
                                },
                                onLngChange = {
                                    cp2LngText = it
                                    updateWaypointFromText(2, cp2LatText, cp2LngText, waypoints)
                                }
                            )

                            ManualCoordinateRow(
                                label = "終點座標 (設定第2點起生效)",
                                latValue = endLatText,
                                lngValue = endLngText,
                                onLatChange = {
                                    val targetIdx = if (waypoints.size >= 2) waypoints.size - 1 else 1
                                    endLatText = it
                                    updateWaypointFromText(targetIdx, endLatText, endLngText, waypoints)
                                },
                                onLngChange = {
                                    val targetIdx = if (waypoints.size >= 2) waypoints.size - 1 else 1
                                    endLngText = it
                                    updateWaypointFromText(targetIdx, endLatText, endLngText, waypoints)
                                }
                            )
                        }

                        // 免責聲明區
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("免責聲明與使用規範", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF16161D),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C36)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 130.dp)
                                        .verticalScroll(rememberScrollState())
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("1. 本平台自訂賽道功能僅供個人合法賽車場地或封閉路線記錄使用。", color = Color.LightGray, fontSize = 11.sp, lineHeight = 16.sp)
                                    Text("2. 路線標定必須符合國家道路交通法規，嚴禁於公眾道路進行危險駕駛。", color = Color.LightGray, fontSize = 11.sp, lineHeight = 16.sp)
                                    Text("3. 提交路線即表示同意 REV-ON 平台使用該路線資訊於統計與排行展示。", color = Color.LightGray, fontSize = 11.sp, lineHeight = 16.sp)
                                    Text("4. 玩家使用本平台進行任何活動，應自行確保自身安全，REV-ON 不承擔任何形式之危險駕駛責任。", color = Color.LightGray, fontSize = 11.sp, lineHeight = 16.sp)
                                    Text("5. 本平台保留隨時終止、修改或調整自訂賽道之權利。", color = Color.LightGray, fontSize = 11.sp, lineHeight = 16.sp)
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isDisclaimerAgreed = !isDisclaimerAgreed },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isDisclaimerAgreed,
                                    onCheckedChange = { isDisclaimerAgreed = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = RedPrimary,
                                        uncheckedColor = Color.Gray,
                                        checkmarkColor = Color.White
                                    )
                                )
                                Text(
                                    text = "已完整閱讀並同意以上免責聲明",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // 第六步：創建路線按鈕
                        Button(
                            onClick = {
                                if (trackName.isBlank()) {
                                    io.revon.app.ui.components.RevonToastManager.warning("請輸入自訂路線名稱")
                                    return@Button
                                }

                                if (!isDisclaimerAgreed) {
                                    io.revon.app.ui.components.RevonToastManager.warning("請勾選同意免責聲明")
                                    return@Button
                                }

                                if (waypoints.isEmpty()) {
                                    io.revon.app.ui.components.RevonToastManager.warning("請至少標定 1 個起點")
                                    return@Button
                                }

                                val currentDebugEngine = GpsConfig.getMapEngine(context)
                                mapEngine = currentDebugEngine

                                val startPt = waypoints.first()
                                val endPt = if (waypoints.size >= 2) waypoints.last() else startPt
                                val mid1Pt = if (waypoints.size >= 3) waypoints[1] else null
                                val mid2Pt = if (waypoints.size >= 4) waypoints[2] else null

                                val calcDist = if (waypoints.size == 1) 0.1 else totalDistanceKm

                                val newTrack = Track(
                                    code = trackName.trim(),
                                    name = trackName.trim(),
                                    nameZh = trackName.trim(),
                                    region = detectedRegion.ifBlank { "自訂賽道" },
                                    difficulty = if (calcDist > 8.0) Difficulty.HARD else Difficulty.NORMAL,
                                    category = selectedCategory,
                                    distanceKm = if (calcDist > 0) calcDist else 0.1,
                                    cornersCount = (calcDist * 4).toInt().coerceAtLeast(4),
                                    startLat = roundTo7Decimals(startPt.latitude),
                                    startLng = roundTo7Decimals(startPt.longitude),
                                    endLat = endPt.let { roundTo7Decimals(it.latitude) },
                                    endLng = endPt.let { roundTo7Decimals(it.longitude) },
                                    mid1Lat = mid1Pt?.latitude?.let { roundTo7Decimals(it) },
                                    mid1Lng = mid1Pt?.longitude?.let { roundTo7Decimals(it) },
                                    mid2Lat = mid2Pt?.latitude?.let { roundTo7Decimals(it) },
                                    mid2Lng = mid2Pt?.longitude?.let { roundTo7Decimals(it) },
                                    coverImage = null,
                                    activeDrivers = 1,
                                    subtitle = "台灣 · ${detectedRegion.ifBlank { "自訂賽道" }}",
                                    isCustom = true
                                )

                                onSaveTrack(newTrack)
                                io.revon.app.ui.components.RevonToastManager.success("已創建自訂路線並儲存")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                        ) {
                            Text("創建路線", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// Helper Component & Functions (Top-Level Scope)
// ──────────────────────────────────────────────────────────────────────

@Composable
fun PointStatusChip(
    modifier: Modifier = Modifier,
    title: String,
    isFilled: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val bgColor = if (isFilled) (if (isSelected) Color(0xFF1E3A2B) else activeColor) else Color(0xFF22222B)
    val borderColor = if (isSelected) Color(0xFFFFD600) else (if (isFilled) activeColor else inactiveColor.copy(alpha = 0.6f))
    val dotColor = if (isSelected) Color(0xFFFFD600) else (if (isFilled) Color.White else inactiveColor)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ManualCoordinateRow(
    label: String,
    latValue: String,
    lngValue: String,
    onLatChange: (String) -> Unit,
    onLngChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Color.LightGray, fontSize = 12.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = latValue,
                onValueChange = onLatChange,
                placeholder = { Text("緯度 (Lat)", color = Color(0xFF555560), fontSize = 12.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF181820),
                    unfocusedContainerColor = Color(0xFF181820),
                    focusedBorderColor = Color(0xFF444450),
                    unfocusedBorderColor = Color(0xFF282832)
                ),
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = lngValue,
                onValueChange = onLngChange,
                placeholder = { Text("經度 (Lng)", color = Color(0xFF555560), fontSize = 12.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF181820),
                    unfocusedContainerColor = Color(0xFF181820),
                    focusedBorderColor = Color(0xFF444450),
                    unfocusedBorderColor = Color(0xFF282832)
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

fun updateWaypointFromText(index: Int, latStr: String, lngStr: String, waypoints: MutableList<GeoPoint>) {
    val lat = latStr.toDoubleOrNull()
    val lng = lngStr.toDoubleOrNull()
    if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
        val newGeoPt = GeoPoint(roundTo7Decimals(lat), roundTo7Decimals(lng))
        while (waypoints.size <= index) {
            waypoints.add(newGeoPt)
        }
        if (index < waypoints.size) {
            waypoints[index] = newGeoPt
        }
    }
}

suspend fun searchAddressToGeoPoint(context: Context, addressStr: String): GeoPoint? = withContext(Dispatchers.IO) {
    try {
        val geocoder = Geocoder(context, Locale.TAIWAN)
        @Suppress("DEPRECATION")
        val results = geocoder.getFromLocationName(addressStr, 1)
        if (!results.isNullOrEmpty()) {
            val first = results[0]
            return@withContext GeoPoint(first.latitude, first.longitude)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext null
}

suspend fun getRegionNameFromGps(context: Context, lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
    try {
        val geocoder = Geocoder(context, Locale.TAIWAN)
        @Suppress("DEPRECATION")
        val results = geocoder.getFromLocation(lat, lng, 1)
        if (!results.isNullOrEmpty()) {
            val addr = results[0]
            val country = addr.countryName ?: "Taiwan"
            val city = addr.adminArea ?: addr.locality ?: addr.subAdminArea ?: "Hualien"
            return@withContext "$country · $city"
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext "Taiwan · Hualien"
}

suspend fun parseGoogleMapsUrlToLatLng(inputUrl: String): GeoPoint? = withContext(Dispatchers.IO) {
    try {
        var currentUrl = inputUrl.trim()

        // 1. 直接性經緯度
        val directPattern = Pattern.compile("(-\\d+\\.\\d+)\\s*,\\s*(-\\d+\\.\\d+)")
        val directMatcher = directPattern.matcher(currentUrl)
        if (directMatcher.find()) {
            val lat = directMatcher.group(1)?.toDoubleOrNull()
            val lng = directMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                return@withContext GeoPoint(lat, lng)
            }
        }

        // 2. 短網址跳轉追蹤
        if (currentUrl.contains("goo.gl") || currentUrl.contains("maps.app.goo.gl")) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            val redirect = connection.getHeaderField("Location")
            if (!redirect.isNullOrBlank()) {
                currentUrl = redirect
            }
        }

        // 3. 匹配 @lat,lng
        val atPattern = Pattern.compile("@(-\\d+\\.\\d+),(-\\d+\\.\\d+)")
        val atMatcher = atPattern.matcher(currentUrl)
        if (atMatcher.find()) {
            val lat = atMatcher.group(1)?.toDoubleOrNull()
            val lng = atMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null) {
                return@withContext GeoPoint(lat, lng)
            }
        }

        // 4. 匹配 q=lat,lng 或 ll=lat,lng
        val qPattern = Pattern.compile("[&?](?:q|ll|center)=(-\\d+\\.\\d+),(-\\d+\\.\\d+)")
        val qMatcher = qPattern.matcher(currentUrl)
        if (qMatcher.find()) {
            val lat = qMatcher.group(1)?.toDoubleOrNull()
            val lng = qMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null) {
                return@withContext GeoPoint(lat, lng)
            }
        }

        // 5. 通用浮點數數對
        val anyPair = Pattern.compile("(-\\d{1,2}\\.\\d{4,}),(-\\d{1,3}\\.\\d{4,})")
        val anyMatcher = anyPair.matcher(currentUrl)
        if (anyMatcher.find()) {
            val lat = anyMatcher.group(1)?.toDoubleOrNull()
            val lng = anyMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lng != null) {
                return@withContext GeoPoint(lat, lng)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext null
}

fun createMarkerBitmapDrawable(
    context: Context,
    colorHex: String,
    label: String,
    isSelected: Boolean = false
): android.graphics.drawable.BitmapDrawable {
    val size = if (isSelected) 84 else 70
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val paintCircle = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor(colorHex)
        style = android.graphics.Paint.Style.FILL
    }
    val paintBorder = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isSelected) android.graphics.Color.parseColor("#FFFFD600") else android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = if (isSelected) 8f else 5f
    }
    val paintText = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = if (isSelected) 26f else 22f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = android.graphics.Paint.Align.CENTER
    }

    canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 6f, paintCircle)
    canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 6f, paintBorder)

    val textY = (size / 2f) - ((paintText.descent() + paintText.ascent()) / 2f)
    canvas.drawText(label, size / 2f, textY, paintText)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

fun createGoogleMarkerBitmapDescriptor(
    context: Context,
    colorHex: String,
    label: String,
    isSelected: Boolean = false
): com.google.android.gms.maps.model.BitmapDescriptor {
    val drawable = createMarkerBitmapDrawable(context, colorHex, label, isSelected)
    return BitmapDescriptorFactory.fromBitmap(drawable.bitmap)
}
