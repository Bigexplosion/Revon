package io.revon.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
    // Firebase imports removed

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import android.graphics.BlurMaskFilter

import io.revon.app.data.model.Difficulty
import io.revon.app.data.model.LeaderboardItem
import io.revon.app.data.model.Track
import io.revon.app.data.model.TrackCategory
import io.revon.app.data.repository.TrackRepository
import io.revon.app.data.repository.TrackSessionManager

import io.revon.app.ui.theme.DarkBackground
import io.revon.app.ui.theme.NeonGreen
import io.revon.app.ui.theme.RedPrimary
import io.revon.app.ui.viewmodel.RaceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.cos
import kotlin.math.sin

private fun Modifier.glowingShadow(
    color: Color = Color(0xFFFF1744),
    glowRadius: Dp = 10.dp,
    cornerRadius: Dp = 14.dp,
    alpha: Float = 0.5f
): Modifier = this.drawWithContent {
    if (alpha > 0f) {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                val frameworkPaint = asFrameworkPaint()
                frameworkPaint.color = color.copy(alpha = alpha).toArgb()
                frameworkPaint.maskFilter = BlurMaskFilter(
                    glowRadius.toPx(),
                    BlurMaskFilter.Blur.NORMAL
                )
            }
            val radiusPx = glowRadius.toPx()
            val nativeCanvas = canvas.nativeCanvas

            // 裁切掉按鈕內部區域 (Inner Path)，只在邊框外部繪製光暈，保持內部 100% 透明
            val innerPath = android.graphics.Path().apply {
                addRoundRect(
                    0f,
                    0f,
                    size.width,
                    size.height,
                    cornerRadius.toPx(),
                    cornerRadius.toPx(),
                    android.graphics.Path.Direction.CW
                )
            }

            nativeCanvas.save()
            nativeCanvas.clipOutPath(innerPath)

            nativeCanvas.drawRoundRect(
                -radiusPx / 2f,
                -radiusPx / 2f,
                size.width + radiusPx / 2f,
                size.height + radiusPx / 2f,
                cornerRadius.toPx(),
                cornerRadius.toPx(),
                paint.asFrameworkPaint()
            )

            nativeCanvas.restore()
        }
    }
    drawContent()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(
    raceViewModel: RaceViewModel,
    onSelectTrackAndRace: () -> Unit
) {
    val context = LocalContext.current

    var userLat by remember { mutableStateOf(24.15) }
    var userLng by remember { mutableStateOf(120.67) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val allLocalSessions = remember(refreshTrigger) {
        TrackSessionManager.getAllSessions(context)
    }

    val realTracks = remember { TrackRepository.getTracks(context) }

    // 1. 真實 Firebase 上線人數、各賽道 1km 在線人數與最新完賽紀錄 State
    var realOnlineCount by remember { mutableStateOf(1) }
    var realTrackOnlineCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var cloudLatestRecords by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var cloudLeaderboardsMap by remember { mutableStateOf<Map<String, List<LeaderboardItem>>>(emptyMap()) }

    val selectedCategory by raceViewModel.savedCategory.collectAsState()
    val searchQuery by raceViewModel.searchQuery.collectAsState()
    var isLaunchingRace by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var showCustomTrackEditor by remember { mutableStateOf(false) }
    var editingCustomTrack by remember { mutableStateOf<Track?>(null) }

    // 當前使用者資訊
    val tokenManager = remember { io.revon.app.data.local.TokenManager(context) }
    val currentUser = remember { tokenManager.getUser() }
    val currentUid = remember(currentUser) { currentUser?.id ?: "guest_${System.currentTimeMillis() % 10000}" }
    val currentNickname = remember(currentUser) {
        currentUser?.nickname.takeIf { !it.isNullOrBlank() } ?: (currentUser?.email?.substringBefore("@") ?: "車手")
    }

    // 預先計算所有賽道的 Top3 (畫面層級快取，徹底消除滑動中的 IO 與 JSON 解析開銷)
    val top3Map = remember(allLocalSessions, cloudLeaderboardsMap, currentNickname) {
        realTracks.associate { track ->
            val trackCode = track.code
            val localSessions = allLocalSessions.filter {
                it.trackCode.equals(trackCode, ignoreCase = true) || it.trackName.contains(trackCode, ignoreCase = true)
            }
            val allList = mutableListOf<LeaderboardItem>()
            localSessions.forEach { record ->
                val baseNick = if (record.playerNickname.isNotBlank() && record.playerNickname != "車手") record.playerNickname else currentNickname
                val nickWithMe = if (baseNick.endsWith("(我)")) baseNick else "$baseNick(我)"
                allList.add(LeaderboardItem(0, nickWithMe, record.lapTimeDisplay, record.lapTimeMs))
            }
            val cloudItems = cloudLeaderboardsMap[trackCode]
            if (!cloudItems.isNullOrEmpty()) {
                allList.addAll(cloudItems)
            }
            val sortedDistinct = allList.sortedBy { it.finishTimeMs }.distinctBy { "${it.playerNickname}_${it.finishTimeMs}" }
            val top3 = sortedDistinct.take(3).mapIndexed { idx, item ->
                LeaderboardItem(idx + 1, item.playerNickname, item.timeDisplay, item.finishTimeMs)
            }
            trackCode to top3
        }
    }

    // 同步上線 Presence 並即時監聽 (Mocked)
    val apiRaceRepo = remember { io.revon.app.data.repository.ApiRaceRepository(io.revon.app.di.NetworkModule.apiService) }
    DisposableEffect(currentUid) {
        val presenceListener = apiRaceRepo.listenToOnlineCount { count ->
            realOnlineCount = count
        }

        val trackCountsListener = apiRaceRepo.listenToTrackOnlineCounts { counts ->
            realTrackOnlineCounts = counts
        }

        val recordsListener = apiRaceRepo.listenToLatestRecords { list ->
            cloudLatestRecords = list
        }

        onDispose {
            // No-op for mock listeners
        }
    }

    // 讀取各賽道之雲端 Leaderboard 紀錄
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val tracks = TrackRepository.getTracks(context)
            val map = mutableMapOf<String, List<LeaderboardItem>>()
            val apiRaceRepo = io.revon.app.data.repository.ApiRaceRepository(io.revon.app.di.NetworkModule.apiService)
            tracks.forEach { tr ->
                val result = apiRaceRepo.getLeaderboard(tr.code)
                if (result.isSuccess) {
                    val rawList = result.getOrDefault(emptyList())
                    val items = rawList.mapIndexed { idx, mapData ->
                        val nickname = mapData["playerNickname"]?.toString() ?: "雲端車手"
                        val timeDisp = mapData["lapTimeDisplay"]?.toString() ?: "00:00.000"
                        val timeMs = (mapData["lapTimeMs"] as? Number)?.toLong() ?: 999999L
                        LeaderboardItem(idx + 1, nickname, timeDisp, timeMs)
                    }
                    if (items.isNotEmpty()) {
                        map[tr.code] = items
                    }
                }
            }
            cloudLeaderboardsMap = map
        }
    }

    // 穿越沉浸式過渡動畫規格 (Portal Zoom Scale & Fade)
    val surroundingsAlpha by animateFloatAsState(
        targetValue = if (isLaunchingRace) 0.0f else 1.0f,
        animationSpec = tween(durationMillis = 400, easing = LinearEasing),
        label = "SurroundingsAlpha"
    )

    val handleStartRaceWithFlyInAnimation: (Track) -> Unit = { targetTrack ->
        raceViewModel.selectTrack(targetTrack)
        raceViewModel.resetToVehicleSelect()
        onSelectTrackAndRace()
    }

    DisposableEffect(Unit) {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                userLat = loc.latitude
                userLng = loc.longitude
            }
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    userLat = loc.latitude
                    userLng = loc.longitude
                }
            }

            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500L)
                .setMinUpdateIntervalMillis(200L)
                .build()
            fusedClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        }

        onDispose {
            fusedClient.removeLocationUpdates(locationCallback)
        }
    }

    var sortedTrackPairsState by remember { mutableStateOf(TrackRepository.getTracksSortedByGpsDistance(context, userLat, userLng)) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            TrackRepository.getTracks(context)
        }
        sortedTrackPairsState = TrackRepository.getTracksSortedByGpsDistance(context, userLat, userLng)
    }

    LaunchedEffect(userLat, userLng) {
        sortedTrackPairsState = TrackRepository.getTracksSortedByGpsDistance(context, userLat, userLng)
    }
    val sortedTrackPairs = sortedTrackPairsState

    val userCityEnglish = remember(userLat, userLng) {
        getCityEnglishFromGps(userLat, userLng)
    }

    // 當 GPS 位置更新時，判斷位於附近 1km 範圍內的賽道並寫入資料庫
    LaunchedEffect(userLat, userLng, currentUid, currentNickname) {
        val apiRaceRepo = io.revon.app.data.repository.ApiRaceRepository(io.revon.app.di.NetworkModule.apiService)
        val nearbyTrackCodes = sortedTrackPairs.filter { it.second <= 1.0 }.map { it.first.code }
        apiRaceRepo.updateOnlinePresence(currentUid, currentNickname, nearbyTrackCodes)
    }

    val filteredTrackPairs = remember(searchQuery, selectedCategory, sortedTrackPairs) {
        val categoryFiltered = sortedTrackPairs.filter { (tr, _) -> tr.category == selectedCategory }
        android.util.Log.d("RoutesScreenDebug", "sortedTrackPairs 總數量=${sortedTrackPairs.size}, 當前選取分類=${selectedCategory}, 符合該分類數量=${categoryFiltered.size}, 搜尋關鍵字='${searchQuery}'")
        if (searchQuery.isBlank()) {
            categoryFiltered
        } else {
            val q = searchQuery.trim().lowercase()
            val result = categoryFiltered.filter { (tr, _) ->
                tr.code.lowercase().contains(q) ||
                tr.name.lowercase().contains(q) ||
                (tr.nameZh ?: "").lowercase().contains(q) ||
                (tr.subtitle ?: "").lowercase().contains(q) ||
                tr.region.lowercase().contains(q)
            }
            android.util.Log.d("RoutesScreenDebug", "關鍵字搜尋結果筆數: ${result.size}")
            result
        }
    }

    // 建立廣播跑馬燈動態紀錄項目 (僅限真實紀錄，格式為 名稱(我)，顯示最新五筆，同行不換行)
    val dynamicMarqueeItems = remember(cloudLatestRecords, context, currentNickname) {
        val localSessions = TrackSessionManager.getAllSessions(context)
        val items = mutableListOf<Pair<String, String>>()

        // 1. 雲端最新紀錄
        cloudLatestRecords.forEach { data ->
            val nick = data["playerNickname"]?.toString() ?: "車手"
            val trCode = data["trackCode"]?.toString() ?: data["trackName"]?.toString() ?: "賽道"
            val timeDisp = data["lapTimeDisplay"]?.toString() ?: ""
            val displayName = if (nick == currentNickname || nick.endsWith("(我)")) nick else nick
            items.add(Pair("$displayName 完賽", "$trCode $timeDisp"))
        }

        // 2. 本地紀錄 (標註 名稱(我))
        localSessions.forEach { record ->
            val baseNick = if (record.playerNickname.isNotBlank() && record.playerNickname != "車手") record.playerNickname else currentNickname
            val nickWithMe = if (baseNick.endsWith("(我)")) baseNick else "$baseNick(我)"
            items.add(Pair("$nickWithMe 本地紀錄", "${record.trackCode} ${record.lapTimeDisplay}"))
        }

        // 僅保留最新 5 筆紀錄，完全禁止虛假硬編碼 fallback
        items.take(5)
    }

    val currentLang by io.revon.app.data.config.LanguageManager.currentLanguage.collectAsState()
    val isEn = currentLang == io.revon.app.data.config.AppLanguage.EN

    val circuitText = if (isEn) "CIRCUIT" else "賽道"
    val tougeText = if (isEn) "TOUGE" else "山路"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {

        val categoryPagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = if (selectedCategory == TrackCategory.TOUGE) 1 else 0,
            pageCount = { 2 }
        )

        LaunchedEffect(categoryPagerState.currentPage) {
            val targetCat = if (categoryPagerState.currentPage == 1) TrackCategory.TOUGE else TrackCategory.CIRCUIT
            if (selectedCategory != targetCat) {
                raceViewModel.setCategory(targetCat)
            }
        }

        LaunchedEffect(selectedCategory) {
            val targetPage = if (selectedCategory == TrackCategory.TOUGE) 1 else 0
            if (categoryPagerState.currentPage != targetPage) {
                categoryPagerState.animateScrollToPage(targetPage)
            }
        }

        // 固定於最頂部的頂部標題區 (REV-ON + 賽道/山路圓腳滑塊切換鈕 + 搜尋列)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("REV", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Text(" - ", color = RedPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("ON", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                }

                // 固定頂部 Category Switcher與跟隨觸控動態移動的紅色框框
                Surface(
                    modifier = Modifier.height(28.dp),
                    color = Color(0xFF0F0F0F).copy(alpha = 0.85f),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B2B2B))
                ) {
                    Box(
                        modifier = Modifier.fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val progress = (categoryPagerState.currentPage + categoryPagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 2.dp)
                                .graphicsLayer {
                                    translationX = progress * 46.dp.toPx()
                                }
                                .width(44.dp)
                                .background(RedPrimary, shape = CircleShape)
                        )
                        Row(modifier = Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                onClick = { coroutineScope.launch { categoryPagerState.animateScrollToPage(0) } },
                                modifier = Modifier.fillMaxHeight(),
                                color = Color.Transparent,
                                shape = CircleShape
                            ) {
                                Box(modifier = Modifier.fillMaxHeight().padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(circuitText, color = if (categoryPagerState.currentPage == 0) Color.White else Color(0xFF8E8E98), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                                }
                            }
                            Surface(
                                onClick = { coroutineScope.launch { categoryPagerState.animateScrollToPage(1) } },
                                modifier = Modifier.fillMaxHeight(),
                                color = Color.Transparent,
                                shape = CircleShape
                            ) {
                                Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                    Text(tougeText, color = if (categoryPagerState.currentPage == 1) Color.White else Color(0xFF8E8E98), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, modifier = Modifier.padding(horizontal = 10.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Search Bar
            androidx.compose.foundation.text.BasicTextField(
                value = searchQuery,
                onValueChange = { raceViewModel.setSearchQuery(it) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF2C2C34), shape = RoundedCornerShape(12.dp)),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = RedPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = if (isEn) "Search tracks (136, 139, Lihpao...)" else "搜尋賽道 (136, 139, 麗寶...)",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                            innerTextField()
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { raceViewModel.setSearchQuery("") },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "清除",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            )
        }

        // 下方滑動區域：僅卡片 UI 隨手勢左右滑動改變
        HorizontalPager(
            state = categoryPagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) { page ->
            val pageCategory = if (page == 1) TrackCategory.TOUGE else TrackCategory.CIRCUIT
            val pageFilteredTrackPairs = remember(searchQuery, pageCategory, sortedTrackPairs) {
                val categoryFiltered = sortedTrackPairs.filter { (tr, _) -> tr.category == pageCategory }
                if (searchQuery.isBlank()) {
                    categoryFiltered
                } else {
                    val q = searchQuery.trim().lowercase()
                    categoryFiltered.filter { (tr, _) ->
                        tr.code.lowercase().contains(q) ||
                        tr.name.lowercase().contains(q) ||
                        (tr.nameZh ?: "").lowercase().contains(q) ||
                        (tr.subtitle ?: "").lowercase().contains(q) ||
                        tr.region.lowercase().contains(q)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = surroundingsAlpha }
            ) {
                if (pageFilteredTrackPairs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (isEn) "No matching tracks found" else "搜尋不到符合條件的賽道", color = Color.Gray, fontSize = 14.sp)
                    }
                } else if (pageCategory == TrackCategory.CIRCUIT) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "CIRCUITS",
                                        color = RedPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 2.sp
                                    )
                                    Text(
                                        text = if (isEn) "CIRCUIT" else "賽道",
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 2.sp
                                    )
                                }

                                Surface(color = Color.Transparent) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(RedPrimary, shape = RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(text = "TW", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "TAIWAN", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "▾", color = Color.Gray, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        items(pageFilteredTrackPairs.size) { index ->
                            val (track, _) = pageFilteredTrackPairs[index]
                            val mergedTop3 = top3Map[track.code] ?: emptyList()
                            val bestTime = mergedTop3.firstOrNull()?.timeDisplay

                            val localSessionsForTrack = allLocalSessions.filter {
                                it.trackCode.equals(track.code, ignoreCase = true) || it.trackName.contains(track.code, ignoreCase = true)
                            }
                            val cloudLeaderboardForTrack = cloudLeaderboardsMap[track.code] ?: emptyList()
                            val totalRecordsCount = localSessionsForTrack.size + cloudLeaderboardForTrack.size

                            CircuitTrackCard(
                                track = track,
                                distKm = pageFilteredTrackPairs[index].second,
                                activeDriversCount = realTrackOnlineCounts[track.code] ?: 0,
                                totalRecordsCount = totalRecordsCount,
                                bestRecordTime = bestTime,
                                onRaceClick = { handleStartRaceWithFlyInAnimation(track) },
                                onEditClick = {
                                    editingCustomTrack = track
                                    showCustomTrackEditor = true
                                },
                                onDeleteClick = {
                                    TrackRepository.deleteCustomTrack(context, track.code)
                                    sortedTrackPairsState = TrackRepository.getTracksSortedByGpsDistance(context, userLat, userLng)
                                }
                            )
                        }

                        item {
                            AddCustomTrackCard(
                                onCreateClick = {
                                    editingCustomTrack = null
                                    showCustomTrackEditor = true
                                }
                            )
                        }
                    }
                } else {
                    // TOUGE 滿版 Hero Card 模式
                    val pagerState = rememberPagerState(pageCount = { pageFilteredTrackPairs.size })

                    Box(modifier = Modifier.fillMaxSize()) {
                        VerticalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            pageSpacing = 16.dp,
                            beyondViewportPageCount = 1
                        ) { tougePage ->
                            val (track, distKm) = pageFilteredTrackPairs[tougePage]
                            val mergedTop3 = top3Map[track.code] ?: emptyList()

                            TougeTrackHeroCard(
                                track = track,
                                distKm = distKm,
                                userCity = userCityEnglish,
                                activeDriversCount = realTrackOnlineCounts[track.code] ?: 0,
                                mergedTop3 = mergedTop3,
                                onRaceClick = { handleStartRaceWithFlyInAnimation(track) }
                            )
                        }
                    }
                }
            }
        }

        if (showCustomTrackEditor) {
            io.revon.app.ui.dialogs.CustomTrackEditorDialog(
                initialTrack = editingCustomTrack,
                userLat = userLat,
                userLng = userLng,
                onDismiss = {
                    showCustomTrackEditor = false
                    editingCustomTrack = null
                },
                onSaveTrack = { newTrack ->
                    TrackRepository.saveCustomTrack(context, newTrack)
                    sortedTrackPairsState = TrackRepository.getTracksSortedByGpsDistance(context, userLat, userLng)
                    showCustomTrackEditor = false
                    editingCustomTrack = null
                }
            )
        }
    }
}

/**
 * 頂部極速跑馬燈 (TopMarqueeBanner) - 顯示真實上線人數與真實完賽成績
 */
@Composable
fun TopMarqueeBanner(
    onlineCount: Int,
    marqueeItems: List<Pair<String, String>>
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        color = Color.Black,
        border = androidx.compose.foundation.BorderStroke(width = 1.dp, color = Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left fixed status label (顯示真實線上人數)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp)
                    .background(Color.Black)
                    .zIndex(2f)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(NeonGreen)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$onlineCount",
                    color = NeonGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "競速中",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight(0.5f)
                        .background(Color.White.copy(alpha = 0.15f))
                )
            }

            val infiniteTransition = rememberInfiniteTransition(label = "marquee")
            val offsetX by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -600f,
                animationSpec = infiniteRepeatable(
                    animation = tween(18000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "marqueeOffset"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (marqueeItems.isNotEmpty()) {
                    androidx.compose.ui.layout.SubcomposeLayout(
                        modifier = Modifier.fillMaxHeight()
                    ) { constraints ->
                        val placeables = subcompose("content") {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(start = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(32.dp)
                            ) {
                                // 複製多份確保畫面寬度不留白，實現無限滾動
                                (marqueeItems + marqueeItems + marqueeItems + marqueeItems).forEach { (prefix, event) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "● ", color = RedPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                        Text(text = prefix, color = RedPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = event, color = NeonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }.map { it.measure(constraints.copy(minWidth = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity)) }

                        val contentWidth = placeables.maxOfOrNull { it.width } ?: 1
                        val contentHeight = placeables.maxOfOrNull { it.height } ?: constraints.maxHeight
                        // 一組完整 marqueeItems 的寬度（因為渲染 4 份）
                        val singleWidth = (contentWidth / 4f).coerceAtLeast(1f)

                        layout(constraints.maxWidth, contentHeight) {
                            placeables.forEach {
                                val currentOffset = ((offsetX % singleWidth) - singleWidth) % singleWidth
                                it.placeRelative(currentOffset.toInt(), 0)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "尚無完賽紀錄",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                }
            }
        }
    }
}

/**
 * 滿版卡片上下滑動賽道組件 (TougeTrackHeroCard)
 */
@Composable
private fun TougeTrackHeroCard(
    track: Track,
    distKm: Double,
    userCity: String,
    activeDriversCount: Int,
    mergedTop3: List<LeaderboardItem>,
    onRaceClick: () -> Unit
) {
    val context = LocalContext.current
    val trackPathPoints = remember(track.code) {
        TrackRepository.getTrackPath(context, track.code)
    }

    val normalizedPoints = remember(track.code, trackPathPoints) {
        if (trackPathPoints.size >= 2) {
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLng = Double.MAX_VALUE
            var maxLng = -Double.MAX_VALUE
            for (p in trackPathPoints) {
                if (p.first < minLat) minLat = p.first
                if (p.first > maxLat) maxLat = p.first
                if (p.second < minLng) minLng = p.second
                if (p.second > maxLng) maxLng = p.second
            }
            val midLat = (minLat + maxLat) / 2.0
            val cosLat = kotlin.math.cos(Math.toRadians(midLat)).coerceAtLeast(0.0001)
            val deltaLat = (maxLat - minLat).coerceAtLeast(0.00001)
            val deltaLng = ((maxLng - minLng) * cosLat).coerceAtLeast(0.00001)
            val maxExtent = maxOf(deltaLat, deltaLng)

            trackPathPoints.map { (lt, lg) ->
                val x = 0.5f + (((lg - minLng) * cosLat - deltaLng / 2.0) / maxExtent).toFloat()
                val y = 0.5f - (((lt - minLat) - deltaLat / 2.0) / maxExtent).toFloat()
                Pair(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
            }
        } else emptyList()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .border(
                width = 1.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        RedPrimary.copy(alpha = 0.8f),
                        Color(0xFF3A0609),
                        RedPrimary.copy(alpha = 0.4f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Touge Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1E0507), Color(0xFF08080C), Color.Black),
                        center = Offset(w * 0.5f, h * 0.4f),
                        radius = w * 0.8f
                    )
                )

                if (normalizedPoints.size >= 2) {
                    // 縮放倍率 70% (保留 15% 邊界安全留白，完整呈送起點白圈與終點雙圈標示)
                    val scaleFactor = 0.70f
                    val offsetX = w * (1f - scaleFactor) / 2f
                    val offsetY = h * (1f - scaleFactor) / 2f

                    val path = Path()
                    var startPt = Offset.Zero
                    var endPt = Offset.Zero

                    normalizedPoints.forEachIndexed { idx, (nx, ny) ->
                        val x = offsetX + nx * (w * scaleFactor)
                        val y = offsetY + ny * (h * scaleFactor)
                        val pt = Offset(x, y)
                        if (idx == 0) {
                            path.moveTo(x, y)
                            startPt = pt
                        } else {
                            path.lineTo(x, y)
                        }
                        if (idx == normalizedPoints.lastIndex) {
                            endPt = pt
                        }
                    }

                    // 底層微光渲染
                    drawPath(
                        path = path,
                        color = Color(0xFFFF1A2D).copy(alpha = 0.35f),
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // 主亮紅路線 (鮮紅線條)
                    drawPath(
                        path = path,
                        color = Color(0xFFFF2A38),
                        style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // 繪製起點白圈 (如範例圖樣)
                    drawCircle(
                        color = Color.White,
                        radius = 5.dp.toPx(),
                        center = startPt
                    )
                    drawCircle(
                        color = Color(0xFFFF2A38),
                        radius = 2.5.dp.toPx(),
                        center = startPt
                    )

                    // 繪製終點同心圓
                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = endPt,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = endPt
                    )
                }
            }

            // Dark Gradient Vignette Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            // Header Inside Card: Drivers Online Pill (Left) + City & Difficulty (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 36.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左上方：在線車手人數 + 距起點 km
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(NeonGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$activeDriversCount",
                                color = NeonGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "位車手在線",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }


                // 右上方：城市與難度
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(RedPrimary)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (userCity.isNotBlank()) userCity else track.region,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val (bgColor, textColor, diffLabel) = when (track.difficulty) {
                        Difficulty.EXTREME -> Triple(RedPrimary, Color.White, "極限")
                        Difficulty.HARD -> Triple(Color(0xFFFFC107), Color.Black, "困難")
                        else -> Triple(NeonGreen, Color.Black, "一般")
                    }
                    Surface(
                        color = bgColor,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = diffLabel,
                            color = textColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Bottom Info Overlay Container
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Track Code + Title + Specs Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val mainTitle = (track.nameZh ?: track.code).trim()
                        val titleFontSize = when {
                            mainTitle.length >= 10 -> 22.sp
                            mainTitle.length >= 8 -> 26.sp
                            mainTitle.length >= 6 -> 32.sp
                            else -> 38.sp
                        }
                        val titleLineHeight = when {
                            mainTitle.length >= 10 -> 26.sp
                            mainTitle.length >= 8 -> 30.sp
                            mainTitle.length >= 6 -> 36.sp
                            else -> 42.sp
                        }
                        Text(
                            text = mainTitle,
                            color = Color.White,
                            fontSize = titleFontSize,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            lineHeight = titleLineHeight,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = RedPrimary.copy(alpha = 0.65f),
                                    offset = Offset(0f, 0f),
                                    blurRadius = 16f
                                )
                            )
                        )
                    }

                    // 對調位置：第一行為鮮艷大號綠字「距起點/長度 km」，第二行為右對齊小字「x 彎」 (如範例圖樣)
                    Column(horizontalAlignment = Alignment.End) {
                        val distanceDisplayStr = if (distKm > 0 && distKm < 9000.0) {
                            "%.1f".format(distKm)
                        } else if ((track.distanceKm ?: 0.0) > 0) {
                            "%.1f".format(track.distanceKm)
                        } else {
                            "--"
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = distanceDisplayStr,
                                color = NeonGreen,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 30.sp
                            )
                            Text(
                                text = "km",
                                color = NeonGreen,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${track.cornersCount ?: 0}",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "彎",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Top 3 Leaderboard Box (顯示線上與本地真實成績排名)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (mergedTop3.isEmpty()) {
                        Text(
                            text = "尚無完賽紀錄",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        mergedTop3.take(3).forEach { item ->
                            val isFirst = item.rank == 1
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isFirst) RedPrimary.copy(alpha = 0.15f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = if (isFirst) 1.dp else 0.dp,
                                        color = if (isFirst) RedPrimary.copy(alpha = 0.5f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val itemAvatarUrl = item.avatarUrl
                                    Box(
                                        modifier = Modifier
                                            .size(if (isFirst) 24.dp else 20.dp)
                                            .clip(CircleShape)
                                            .background(if (isFirst) RedPrimary else Color.White.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!itemAvatarUrl.isNullOrBlank()) {
                                             val formattedPath = if (itemAvatarUrl.startsWith("/")) itemAvatarUrl else "/$itemAvatarUrl"
                                             val fullUrl = if (itemAvatarUrl.startsWith("http")) itemAvatarUrl else "https://revon88.synology.me/revon_android${formattedPath}"
                                            coil.compose.AsyncImage(
                                                model = fullUrl,
                                                contentDescription = "車手頭像",
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = "${item.rank}",
                                                color = Color.White,
                                                fontSize = if (isFirst) 11.sp else 10.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Text(
                                        text = item.playerNickname,
                                        color = if (isFirst) Color.White else Color.White.copy(alpha = 0.8f),
                                        fontSize = if (isFirst) 13.sp else 12.sp,
                                        fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = item.timeDisplay,
                                    color = NeonGreen,
                                    fontSize = if (isFirst) 13.sp else 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Dominant CTA Transparent Race Button with Red Border (競速 ›)
                Button(
                    onClick = onRaceClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFF1744)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Text(
                        text = "競 速",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "›",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

/**
 * 整合真實線上 Firebase 成績與本地設備 Saved Sessions，排序出該賽道前三名 (完全去除硬編碼 seed 數據)
 */
private fun computeRealMergedTop3(
    context: Context,
    track: Track,
    cloudItems: List<LeaderboardItem>?,
    currentNickname: String = ""
): List<LeaderboardItem> {
    val allList = mutableListOf<LeaderboardItem>()

    // 1. 本地儲存的 Session 紀錄 (格式標註 名稱(我))
    val localSessions = TrackSessionManager.getAllSessions(context).filter {
        it.trackCode.equals(track.code, ignoreCase = true) || it.trackName.contains(track.code, ignoreCase = true)
    }
    localSessions.forEach { record ->
        val baseNick = if (record.playerNickname.isNotBlank() && record.playerNickname != "車手") record.playerNickname else (if (currentNickname.isNotBlank()) currentNickname else "我")
        val nickWithMe = if (baseNick.endsWith("(我)")) baseNick else "$baseNick(我)"
        allList.add(LeaderboardItem(0, nickWithMe, record.lapTimeDisplay, record.lapTimeMs))
    }

    // 2. 雲端 Firebase Leaderboard 紀錄
    if (!cloudItems.isNullOrEmpty()) {
        allList.addAll(cloudItems)
    }

    // 排序、去重與指派前三名名次 (完全禁止使用虛假硬編碼 track.top3)
    val sortedDistinct = allList.sortedBy { it.finishTimeMs }.distinctBy { "${it.playerNickname}_${it.finishTimeMs}" }
    return sortedDistinct.take(3).mapIndexed { idx, item ->
        LeaderboardItem(idx + 1, item.playerNickname, item.timeDisplay, item.finishTimeMs)
    }
}

private fun getCityEnglishFromGps(lat: Double, lng: Double): String {
    val cityCenters = listOf(
        Triple("TAICHUNG", 24.15, 120.67),
        Triple("TAIPEI", 25.03, 121.56),
        Triple("NEW TAIPEI", 24.99, 121.51),
        Triple("TAOYUAN", 24.99, 121.30),
        Triple("TAINAN", 22.99, 120.21),
        Triple("KAOHSIUNG", 22.62, 120.30),
        Triple("GUNMA", 36.63, 139.06)
    )

    val closest = cityCenters.minByOrNull { (_, cLat, cLng) ->
        val dLat = lat - cLat
        val dLng = (lng - cLng) * cos(Math.toRadians(lat))
        dLat * dLat + dLng * dLng
    }
    return closest?.first ?: "TAICHUNG"
}

/**
 * CIRCUIT 賽車場卡片 (CircuitTrackCard)
 * 包含縮減高度之 TW 區域標籤、2 LAYOUTS 標籤、中文主標題、英文副標題、累計跑過玩家總數與賽道最佳紀錄
 */
@Composable
private fun CircuitTrackCard(
    track: Track,
    distKm: Double = 0.0,
    activeDriversCount: Int,
    totalRecordsCount: Int = 0,
    bestRecordTime: String?,
    onRaceClick: () -> Unit,
    onEditClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null
) {
    val isRecordTrack = track.code.contains("錄製") || track.name.contains("錄製")

    val englishTitle = remember(track.code, track.subtitle) {
        if (!track.subtitle.isNullOrBlank()) {
            track.subtitle
        } else {
            track.code.uppercase()
        }
    }

    val chineseTitle = remember(track.name, track.nameZh) {
        if (!track.nameZh.isNullOrBlank()) {
            track.nameZh
        } else {
            track.name
        }
    }

    val regionTag = remember(track.region) {
        when (track.region.uppercase()) {
            "TAICHUNG" -> "TAICHUNG"
            "PINGTUNG" -> "PINGTUNG"
            "CHANGHUA" -> "CHANGHUA"
            else -> track.region.uppercase()
        }
    }

    val context = LocalContext.current
    val trackPathPoints = remember(track.code) {
        TrackRepository.getTrackPath(context, track.code)
    }

    val normalizedPoints = remember(track.code, trackPathPoints) {
        if (trackPathPoints.size >= 2) {
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLng = Double.MAX_VALUE
            var maxLng = -Double.MAX_VALUE
            for (p in trackPathPoints) {
                if (p.first < minLat) minLat = p.first
                if (p.first > maxLat) maxLat = p.first
                if (p.second < minLng) minLng = p.second
                if (p.second > maxLng) maxLng = p.second
            }
            val midLat = (minLat + maxLat) / 2.0
            val cosLat = kotlin.math.cos(Math.toRadians(midLat)).coerceAtLeast(0.0001)
            val deltaLat = (maxLat - minLat).coerceAtLeast(0.00001)
            val deltaLng = ((maxLng - minLng) * cosLat).coerceAtLeast(0.00001)
            val maxExtent = maxOf(deltaLat, deltaLng)

            trackPathPoints.map { (lt, lg) ->
                val x = 0.5f + (((lg - minLng) * cosLat - deltaLng / 2.0) / maxExtent).toFloat()
                val y = 0.5f - (((lt - minLat) - deltaLat / 2.0) / maxExtent).toFloat()
                Pair(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
            }
        } else emptyList()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRaceClick() }
            .border(
                width = 1.dp,
                color = Color(0xFF2A1517),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 背景暗色網格線與賽道路徑軌跡
            Canvas(modifier = Modifier.matchParentSize()) {
                val gridStep = 24.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.025f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += gridStep
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.025f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += gridStep
                }

                if (normalizedPoints.size >= 2) {
                    // 縮放倍率 70% (保留 15% 邊界安全留白)
                    val scaleFactor = 0.70f
                    val offsetX = size.width * (1f - scaleFactor) / 2f
                    val offsetY = size.height * (1f - scaleFactor) / 2f

                    val path = Path()
                    var startPt = Offset.Zero
                    var endPt = Offset.Zero

                    normalizedPoints.forEachIndexed { idx, (nx, ny) ->
                        val px = offsetX + nx * (size.width * scaleFactor)
                        val py = offsetY + ny * (size.height * scaleFactor)
                        val pt = Offset(px, py)
                        if (idx == 0) {
                            path.moveTo(px, py)
                            startPt = pt
                        } else {
                            path.lineTo(px, py)
                        }
                        if (idx == normalizedPoints.lastIndex) {
                            endPt = pt
                        }
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFFFF1A2D).copy(alpha = 0.20f),
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawPath(
                        path = path,
                        color = Color(0xFFFF2A38).copy(alpha = 0.60f),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawCircle(color = Color.White.copy(alpha = 0.8f), radius = 4.dp.toPx(), center = startPt)
                    drawCircle(color = Color.White.copy(alpha = 0.8f), radius = 4.dp.toPx(), center = endPt, style = Stroke(width = 1.2.dp.toPx()))
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Row: TW Region Pill (Left) + 2 LAYOUTS Pill (Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // TW Region Pill (簡潔緊湊型高與邊距)
                    Surface(
                        color = Color(0xFF16161C),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(RedPrimary, shape = RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 0.5.dp)
                            ) {
                                Text(
                                    text = "TW",
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black,
                                    lineHeight = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = regionTag,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 距起點 km Badge
                        Surface(
                            color = Color(0xFF16161C),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📍 距起點 ",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (distKm > 0 && distKm < 9000.0) "${"%.1f".format(distKm)} km" else "-- km",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (!isRecordTrack) {
                            Spacer(modifier = Modifier.width(8.dp))
                            // 在線車手 Badge
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(NeonGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$activeDriversCount 位車手在線",
                                    color = NeonGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 第一行：中文賽道名稱
                Text(
                    text = chineseTitle,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 第二行：英文灰色字樣
                Text(
                    text = englishTitle,
                    color = Color(0xFF90909A),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                if (!isRecordTrack) {
                    Spacer(modifier = Modifier.height(18.dp))

                    // Bottom Specs Row: 競速紀錄筆數 + 場地最速成績 (無紀錄則顯示暫無成績)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 真實動態競速紀錄筆數 (無硬編碼)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFFA0A0A0),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$totalRecordsCount 筆競速紀錄",
                                color = Color(0xFFA0A0A0),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // 場地最速成績，無則顯示 暫無成績
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (!bestRecordTime.isNullOrBlank()) NeonGreen else Color(0xFF90909A),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            if (!bestRecordTime.isNullOrBlank()) {
                                Text(
                                    text = "場地最速成績 ",
                                    color = NeonGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = bestRecordTime,
                                    color = NeonGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Text(
                                    text = "暫無成績",
                                    color = Color(0xFF90909A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 如果為自訂賽道，額外提供「✏️ 編輯點位」與「🗑️ 刪除路線」按鈕
                if (track.isCustom) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { onEditClick?.invoke() },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E2836),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✏️ 編輯點位", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Surface(
                            onClick = { onDeleteClick?.invoke() },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF2E1A1C),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RedPrimary.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🗑️ 刪除路線", color = RedPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 新增自訂賽道大卡片 (AddCustomTrackCard)
 * 與 CircuitTrackCard 完全等高、等寬、對齊
 */
@Composable
private fun AddCustomTrackCard(
    onCreateClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCreateClick() }
            .border(
                width = 1.dp,
                color = Color(0xFF2A1517),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D11))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val gridStep = 24.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = RedPrimary.copy(alpha = 0.05f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += gridStep
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = RedPrimary.copy(alpha = 0.05f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += gridStep
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = RedPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✨ CUSTOM BUILDER",
                                color = RedPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Text(
                        text = "+ 自由繪製點位",
                        color = RedPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "➕ 新增自訂賽道",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "在地圖上記錄與拖拉起終點/CP點位 · 自動寫入 SQL 資料庫 · 預覽即時計時",
                    color = Color(0xFF90909A),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onCreateClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text(
                        text = "開 始 繪 製 賽 道 ›",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}
