package io.revon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import io.revon.app.R
import io.revon.app.data.model.RaceResult
import io.revon.app.data.model.Track
import io.revon.app.data.model.VehicleType
import io.revon.app.data.repository.TrackRepository
import io.revon.app.data.repository.TrackSessionManager
import io.revon.app.ui.theme.DarkBackground
import io.revon.app.ui.theme.DarkSurface
import io.revon.app.ui.theme.NeonGreen
import io.revon.app.ui.theme.RedPrimary
import io.revon.app.ui.viewmodel.AuthViewModel
import io.revon.app.ui.viewmodel.ChatViewModel
import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

import androidx.compose.ui.draw.drawWithContent

// 擴充 Modifier：實現高斯模糊的物理光芒 (BlurMaskFilter Native Glow)
fun Modifier.neonGlow(
    color: Color = Color(0xFFE50914),
    glowRadius: Dp = 10.dp,
    cornerRadius: Dp = 8.dp,
    alpha: Float = 0.35f
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
            nativeCanvas.drawRoundRect(
                -radiusPx / 2f,
                -radiusPx / 2f,
                size.width + radiusPx / 2f,
                size.height + radiusPx / 2f,
                cornerRadius.toPx(),
                cornerRadius.toPx(),
                paint.asFrameworkPaint()
            )
        }
    }
    drawContent()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RankingScreen(
    authViewModel: AuthViewModel = viewModel(),
    chatViewModel: ChatViewModel? = null,
    onNavigateToProfile: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    val currentUser by authViewModel.currentUser.collectAsState()
    val clubs by (chatViewModel?.clubs ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }).collectAsState()
    val selectedClub by (chatViewModel?.selectedClub ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()

    val myClub = remember(clubs, selectedClub, currentUser) {
        clubs.find { c ->
            c.captainNickname == currentUser?.nickname ||
            c.captainNickname == "賽車手" ||
            c.captainNickname == "車手" ||
            c.captainNickname == "車手隊長"
        } ?: selectedClub
    }

    var userLat by remember { mutableStateOf(24.15) }
    var userLng by remember { mutableStateOf(120.67) }

    LaunchedEffect(Unit) {
        authViewModel.checkSavedSession()
    }

    DisposableEffect(Unit) {
        authViewModel.checkSavedSession()
        val fusedClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
        val locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                val loc = result.lastLocation ?: return
                if (loc.latitude > 1.0 && loc.longitude > 1.0) {
                    userLat = loc.latitude
                    userLng = loc.longitude
                }
            }
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && loc.latitude > 1.0 && loc.longitude > 1.0) {
                    userLat = loc.latitude
                    userLng = loc.longitude
                }
            }
            val req = com.google.android.gms.location.LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 1000L
            ).setMinUpdateIntervalMillis(500L).build()
            fusedClient.requestLocationUpdates(req, locationCallback, android.os.Looper.getMainLooper())
        }

        onDispose {
            fusedClient.removeLocationUpdates(locationCallback)
        }
    }

    var lastSortedLat by remember { mutableStateOf(0.0) }
    var lastSortedLng by remember { mutableStateOf(0.0) }
    var sortedTracksState by remember { mutableStateOf<List<Track>>(emptyList()) }

    LaunchedEffect(userLat, userLng) {
        if (userLat != 0.0 && userLng != 0.0) {
            val distMoved = if (lastSortedLat == 0.0) 999.0 else TrackRepository.haversineDistance(lastSortedLat, lastSortedLng, userLat, userLng)
            if (distMoved > 1.0 || sortedTracksState.isEmpty()) {
                lastSortedLat = userLat
                lastSortedLng = userLng
                val newlySorted = TrackRepository.getTracksSortedByGpsDistance(context, userLat, userLng)
                    .map { it.first }
                if (newlySorted.isNotEmpty()) {
                    if (sortedTracksState.isEmpty()) {
                        sortedTracksState = newlySorted
                    } else {
                        val nearbyCodes = newlySorted.filter { track ->
                            val sLat = track.startLat
                            val sLng = track.startLng
                            sLat != null && sLng != null && TrackRepository.haversineDistance(userLat, userLng, sLat, sLng) <= 1.0
                        }.map { it.code }.toSet()

                        val currentNearby = sortedTracksState.filter { it.code in nearbyCodes }
                        val currentFar = newlySorted.filter { it.code !in nearbyCodes }
                        sortedTracksState = currentNearby + currentFar
                    }
                }
            }
        }
    }

    val realTracks = remember(sortedTracksState, userLat, userLng) {
        val raw = if (sortedTracksState.isNotEmpty()) {
            sortedTracksState
        } else {
            TrackRepository.getTracksSortedByGpsDistance(context, userLat, userLng)
                .map { it.first }
        }
        raw.distinctBy { it.code }
    }

    var refreshTrigger by remember { mutableIntStateOf(0) }

    val allSessions = remember(refreshTrigger) {
        TrackSessionManager.getAllSessions(context)
    }

    val rankingPrefs = remember { context.getSharedPreferences("ranking_prefs", android.content.Context.MODE_PRIVATE) }

    var selectedMainTab by remember { mutableStateOf("LEADERBOARD") }
    var selectedTrackCode by remember {
        val savedTrack = rankingPrefs.getString("KEY_LAST_TRACK_CODE", null)
        mutableStateOf(savedTrack ?: (realTracks.firstOrNull()?.code ?: "136"))
    }
    var selectedVehicleType by remember { mutableStateOf<VehicleType?>(null) }
    var selectedFilter by remember {
        val savedFilter = rankingPrefs.getString("KEY_LAST_FILTER", "ALL")
        mutableStateOf(savedFilter ?: "ALL")
    }

    LaunchedEffect(selectedTrackCode) {
        if (selectedTrackCode.isNotBlank()) {
            rankingPrefs.edit().putString("KEY_LAST_TRACK_CODE", selectedTrackCode).apply()
        }
    }

    LaunchedEffect(selectedFilter) {
        rankingPrefs.edit().putString("KEY_LAST_FILTER", selectedFilter).apply()
    }

    val defaultDummyTrack = Track(
        code = "TOUGE_01",
        name = "北宜公路",
        nameZh = "北宜公路",
        region = "Taiwan",
        difficulty = io.revon.app.data.model.Difficulty.NORMAL,
        category = io.revon.app.data.model.TrackCategory.TOUGE,
        distanceKm = 15.0,
        cornersCount = 45,
        startLat = 24.9382,
        startLng = 121.7115,
        endLat = 24.8694,
        endLng = 121.7834,
        coverImage = null
    )

    val activeTrack = remember(selectedTrackCode, realTracks) {
        realTracks.find { it.code == selectedTrackCode }
            ?: realTracks.firstOrNull()
            ?: TrackRepository.getTracks(context).firstOrNull()
            ?: defaultDummyTrack
    }

    var sessionToDelete by remember { mutableStateOf<String?>(null) }
    var sessionToShare by remember { mutableStateOf<io.revon.app.data.model.RaceSessionRecord?>(null) }

    val allSessionPairs = remember(selectedTrackCode, allSessions, currentUser, myClub, refreshTrigger) {
        val filtered = allSessions.filter {
            it.trackCode.contains(selectedTrackCode, ignoreCase = true) ||
            it.trackName.contains(selectedTrackCode, ignoreCase = true)
        }.sortedBy { it.lapTimeMs }

        filtered.map { rec ->
            val vEnum = when (rec.vehicleType) {
                "MOTOR" -> VehicleType.MOTOR
                "OTHER" -> VehicleType.OTHER
                else -> VehicleType.CAR
            }
            val displayNickname = currentUser?.nickname ?: rec.playerNickname.ifBlank { "車手" }

            val res = RaceResult(
                trackCode = rec.trackCode,
                trackName = rec.trackName,
                playerNickname = displayNickname,
                vehicleType = vEnum,
                finishTimeMs = rec.lapTimeMs,
                finishTimeDisplay = rec.lapTimeDisplay,
                recordedAt = rec.recordedAt,
                region = activeTrack.region,
                clubId = myClub?.name,
                clubName = myClub?.name,
                isCustomRoute = false,
                customRouteId = null,
                customRouteName = null,
                avatarUrl = currentUser?.avatarUrl
            )
            Pair(rec.sessionId, res)
        }
    }

    val displayPairs = remember(selectedFilter, allSessionPairs, currentUser) {
        val filteredList = when (selectedFilter) {
            "CLUB" -> {
                val myNickname = currentUser?.nickname ?: "車手"
                allSessionPairs.filter { it.second.playerNickname.equals(myNickname, ignoreCase = true) }
            }
            "MONTH" -> {
                val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                allSessionPairs.filter { it.second.recordedAt?.startsWith(currentMonthStr) == true }
            }
            else -> allSessionPairs
        }
        filteredList.take(30)
    }

    val myNickname = currentUser?.nickname ?: "車手"
    val myPair = remember(allSessionPairs, myNickname) {
        allSessionPairs.find { it.second.playerNickname?.equals(myNickname, ignoreCase = true) == true }
    }
    val myResult = myPair?.second
    val mySessionId = myPair?.first
    val myIndex = if (myPair != null) allSessionPairs.indexOf(myPair) else -1

    val trackNameClean = (activeTrack.nameZh ?: activeTrack.name).uppercase()
    val subText = activeTrack.subtitle
    val headerSubtitle = if (!subText.isNullOrBlank()) {
        "$trackNameClean · $subText"
    } else {
        "$trackNameClean · ${activeTrack.region.uppercase()}"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(24.dp)
                        .background(RedPrimary, shape = RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.rank_title),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Track Carousel Selector with Solid Red Background Box & Automatic Snapping
            if (realTracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RedPrimary, modifier = Modifier.size(24.dp))
                }
            } else {
                val lazyListState = rememberLazyListState()
                val coroutineScope = rememberCoroutineScope()
                val snapFlingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = lazyListState)

                // 即時計算當前滾動或靜止時最靠近左側紅框 (offset 接近 4.dp) 的項目索引
                val centeredIndex by remember {
                    derivedStateOf {
                        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
                        if (visibleItems.isEmpty()) 0
                        else {
                            val targetOffset = 4
                            visibleItems.minByOrNull { Math.abs(it.offset - targetOffset) }?.index ?: 0
                        }
                    }
                }

                // 當停止滾動或切換到目標項目時，更新選中的賽道 selectedTrackCode
                LaunchedEffect(centeredIndex, lazyListState.isScrollInProgress) {
                    if (!lazyListState.isScrollInProgress && realTracks.isNotEmpty()) {
                        val safeIndex = centeredIndex.coerceIn(0, realTracks.size - 1)
                        if (realTracks[safeIndex].code != selectedTrackCode) {
                            selectedTrackCode = realTracks[safeIndex].code
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Fixed left red selection indicator frame (Solid Red background)
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(36.dp)
                            .neonGlow(color = RedPrimary, glowRadius = 8.dp, cornerRadius = 18.dp, alpha = 0.5f)
                            .border(2.dp, RedPrimary, shape = CircleShape)
                            .background(RedPrimary, shape = CircleShape)
                    )

                    LazyRow(
                        state = lazyListState,
                        flingBehavior = snapFlingBehavior,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(start = 4.dp, end = 220.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(realTracks.size) { idx ->
                            val tr = realTracks[idx]
                            val isSelected = tr.code == selectedTrackCode
                            val trackDisplayName = if (tr.nameZh.isNullOrBlank() || tr.nameZh == tr.code) tr.name else tr.nameZh
                            Box(
                                modifier = Modifier
                                    .width(122.dp)
                                    .height(34.dp)
                                    .clickable {
                                        selectedTrackCode = tr.code
                                        coroutineScope.launch {
                                            lazyListState.animateScrollToItem(idx)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = trackDisplayName,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Row (50/50 split width: 全部 / 本月)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filterTabs = listOf("ALL" to stringResource(R.string.rank_tab_all), "MONTH" to stringResource(R.string.rank_tab_monthly))
                filterTabs.forEach { (key, label) ->
                    val isSelected = selectedFilter == key
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clickable { selectedFilter = key }
                            .border(
                                1.dp,
                                if (isSelected) RedPrimary else Color(0xFF2B2B2B),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) RedPrimary.copy(alpha = 0.25f) else DarkSurface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                color = if (isSelected) RedPrimary else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 處理前 15 名與自己名次正負各 5 名邏輯
            val top15List = remember(allSessionPairs) { allSessionPairs.take(15) }
            
            // 找出自己的名次索引
            val myIndexInAll = remember(allSessionPairs, currentUser) {
                val myNick = currentUser?.nickname ?: "車手"
                allSessionPairs.indexOfFirst { 
                    it.second.playerNickname.equals(myNick, ignoreCase = true) || it.second.playerNickname == "車手"
                }
            }

            // 計算自己正負 5 名的名次範圍 (排除已包含在前 15 名中的項目)
            val myAroundPairs = remember(allSessionPairs, myIndexInAll) {
                if (myIndexInAll < 0) emptyList()
                else {
                    val startIdx = (myIndexInAll - 5).coerceAtLeast(0)
                    val endIdx = (myIndexInAll + 5).coerceAtMost(allSessionPairs.size - 1)
                    val rangeItems = mutableListOf<Triple<Int, String, RaceResult>>() // Triple(realRank, sid, result)
                    for (i in startIdx..endIdx) {
                        if (i >= 15) { // 僅取第 16 名 (index 15) 以後的項目，避免與上方前 15 名重複
                            rangeItems.add(Triple(i + 1, allSessionPairs[i].first, allSessionPairs[i].second))
                        }
                    }
                    rangeItems
                }
            }

            if (allSessionPairs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.rank_no_track_records, activeTrack.code),
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. 顯示前 15 名
                    itemsIndexed(top15List) { index, (sid, result) ->
                        val rank = index + 1
                        LeaderboardItemRow(
                            rank = rank,
                            sid = sid,
                            result = result,
                            currentUser = currentUser,
                            context = context,
                            onNavigateToProfile = onNavigateToProfile,
                            onLongClickShare = { sessionToShare = it }
                        )
                    }

                    // 2. 分隔線與標示 (若自己不在前 15 名內且有周圍名次)
                    if (myAroundPairs.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = RedPrimary.copy(alpha = 0.4f), thickness = 1.dp)
                                Text(
                                    text = "  我的排名位階 (前後 5 名)  ",
                                    color = RedPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f), color = RedPrimary.copy(alpha = 0.4f), thickness = 1.dp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // 3. 顯示自己的名次及正負 5 名
                        items(myAroundPairs.size) { i ->
                            val (realRank, sid, result) = myAroundPairs[i]
                            LeaderboardItemRow(
                                rank = realRank,
                                sid = sid,
                                result = result,
                                currentUser = currentUser,
                                context = context,
                                onNavigateToProfile = onNavigateToProfile,
                                onLongClickShare = { sessionToShare = it }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .neonGlow(
                        color = RedPrimary,
                        glowRadius = 14.dp,
                        cornerRadius = 14.dp,
                        alpha = 0.4f
                    )
                    .border(1.5.dp, RedPrimary, shape = RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier.padding(end = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = stringResource(R.string.rank_your_rank), color = Color.Gray, fontSize = 10.sp)
                            Text(
                                text = if (myIndex >= 0) "${myIndex + 1}" else "—",
                                color = RedPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Column {
                            Text(text = stringResource(R.string.rank_personal_best), color = Color.Gray, fontSize = 10.sp)
                            Text(
                                text = myResult?.finishTimeDisplay ?: "—",
                                color = NeonGreen,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.clickable { onNavigateToProfile(mySessionId) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.rank_view_my_records),
                            color = RedPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = RedPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }

    if (sessionToShare != null) {
        AlertDialog(
            onDismissRequest = { sessionToShare = null },
            containerColor = DarkSurface,
            title = { Text("個人紀錄管理", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("紀錄名稱: ${sessionToShare?.trackName}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("完賽時間: ${sessionToShare?.lapTimeDisplay}", color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("您可對該筆個人競速紀錄進行檔案分享或執行刪除。", color = Color.Gray, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val target = sessionToShare
                            sessionToShare = null
                            if (target != null) {
                                val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
                                val jsonStr = gson.toJson(target)
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "REV-ON 賽道軌跡 JSON")
                                    putExtra(android.content.Intent.EXTRA_TEXT, jsonStr)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "分享個人競速軌跡 JSON"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                    ) {
                        Text("分享檔案", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            sessionToDelete = sessionToShare?.sessionId
                            sessionToShare = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                    ) {
                        Text("刪除紀錄", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToShare = null }) {
                    Text("取消", color = Color.Gray)
                }
            }
        )
    }

    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            containerColor = DarkSurface,
            title = { Text("刪除成績紀錄", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("確定要刪除這筆山道競速成績與軌跡紀錄嗎？刪除後無法復原，且該賽道紀錄筆數將同步更新。", color = Color.Gray, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        sessionToDelete?.let { sid ->
                            TrackSessionManager.deleteSession(context, sid)
                            refreshTrigger++
                            io.revon.app.ui.components.RevonToastManager.success("已成功刪除成績紀錄，場地紀錄筆數已同步更新")
                        }
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("確定刪除", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("取消", color = Color.Gray)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LeaderboardItemRow(
    rank: Int,
    sid: String,
    result: RaceResult,
    currentUser: io.revon.app.data.model.User?,
    context: android.content.Context,
    onNavigateToProfile: (String?) -> Unit,
    onLongClickShare: (io.revon.app.data.model.RaceSessionRecord) -> Unit
) {
    val isMySession = (currentUser != null && result.playerNickname == currentUser.nickname) || result.playerNickname == "賽車手"
    val sessionObj = remember(sid) { TrackSessionManager.getSessionById(context, sid) }

    val trophyColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFE0E0E0)
        3 -> Color(0xFFCD7F32)
        else -> Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onNavigateToProfile(sid) },
                onLongClick = {
                    if (isMySession && sessionObj != null) {
                        onLongClickShare(sessionObj)
                    }
                }
            )
            .then(
                if (rank in 1..3) {
                    Modifier.neonGlow(
                        color = trophyColor,
                        glowRadius = 8.dp,
                        cornerRadius = 10.dp,
                        alpha = 0.35f
                    )
                } else Modifier
            )
            .border(
                width = if (rank in 1..3) 1.5.dp else 1.dp,
                color = when (rank) {
                    1 -> Color(0xFFFFD700)
                    2 -> Color(0xFFC0C0C0)
                    3 -> Color(0xFFCD7F32)
                    else -> Color(0xFF2C2C2E)
                },
                shape = RoundedCornerShape(10.dp)
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier.width(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (rank in 1..3) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "第$rank 名獎盃",
                            tint = trophyColor,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = "$rank",
                            color = Color(0xFFA0A0A5),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                val playerAvatarUrl = result.avatarUrl?.takeIf { it.isNotBlank() } ?: currentUser?.avatarUrl
                val initialLetter = (result.playerNickname.firstOrNull() ?: '?').toString().uppercase()
                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .border(1.dp, if (rank in 1..3) trophyColor.copy(alpha = 0.8f) else Color(0xFF3E3E42), CircleShape),
                    shape = CircleShape,
                    color = RedPrimary.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (!playerAvatarUrl.isNullOrBlank()) {
                            val formattedPath = if (playerAvatarUrl.startsWith("/")) playerAvatarUrl else "/$playerAvatarUrl"
                            val fullUrl = if (playerAvatarUrl.startsWith("http")) playerAvatarUrl else "https://revon88.synology.me/revon_android${formattedPath}"
                            val avatarUrlWithTs = if (fullUrl.contains("?")) "$fullUrl&t=${System.currentTimeMillis()}" else "$fullUrl?t=${System.currentTimeMillis()}"
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(avatarUrlWithTs)
                                    .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                                    .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "玩家頭像",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = initialLetter,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = result.playerNickname,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = result.clubName ?: "個人玩家",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = result.finishTimeDisplay,
                    color = NeonGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
