package io.revon.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.revon.app.data.model.RaceSessionRecord
import io.revon.app.data.repository.PendingUploadManager
import io.revon.app.ui.navigation.AppNavHost
import io.revon.app.ui.navigation.NavRoutes
import io.revon.app.ui.theme.DarkBackground
import io.revon.app.ui.theme.DarkSurface
import io.revon.app.ui.theme.NeonGreen
import io.revon.app.ui.theme.RedPrimary
import io.revon.app.ui.viewmodel.AuthViewModel
import io.revon.app.ui.viewmodel.ChatViewModel
import io.revon.app.ui.viewmodel.RaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    authViewModel: AuthViewModel = viewModel(),
    raceViewModel: RaceViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

    val showBottomBar = currentRoute in listOf(
        NavRoutes.ROUTES,
        NavRoutes.RANKING,
        NavRoutes.CLUBS,
        NavRoutes.PROFILE
    )

    val navigateToTab: (String) -> Unit = { targetRoute ->
        if (currentRoute != targetRoute) {
            navController.navigate(targetRoute) {
                popUpTo(NavRoutes.ROUTES) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // 最外層主頁分頁（排行、車隊、個人）按下系統 Back 按鍵時，直接切回首頁分頁（路線），避免歷史堆疊縮放動畫
    val isNonHomeRootTab = currentRoute in listOf(NavRoutes.RANKING, NavRoutes.CLUBS, NavRoutes.PROFILE)
    androidx.activity.compose.BackHandler(enabled = isNonHomeRootTab) {
        navigateToTab(NavRoutes.ROUTES)
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        io.revon.app.data.config.LanguageManager.init(context)
        PendingUploadManager.init(context)
        // ⚡ 方案二融入一：背景非同步預加載 (Pre-fetch) 賽道與歷史紀錄至記憶體快取
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            io.revon.app.data.repository.TrackRepository.getTracks(context)
            io.revon.app.data.repository.TrackSessionManager.getAllSessions(context)
        }
    }
    val currentLang by io.revon.app.data.config.LanguageManager.currentLanguage.collectAsState()
    val isEn = currentLang == io.revon.app.data.config.AppLanguage.EN

    val networkMonitor = remember { io.revon.app.data.network.NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnline.collectAsState()
    val pendingCount by PendingUploadManager.pendingCount.collectAsState()
    val pendingList by PendingUploadManager.pendingList.collectAsState()
    val currentlyUploadingId by PendingUploadManager.currentlyUploadingId.collectAsState()
    val uploadProgressMap by PendingUploadManager.uploadProgressMap.collectAsState()

    var showPendingUploadsDialog by remember { mutableStateOf(false) }

    DisposableEffect(networkMonitor) {
        onDispose { networkMonitor.unregister() }
    }

    LaunchedEffect(isOnline) {
        if (isOnline) {
            PendingUploadManager.triggerAutoSync(context)
        } else if (pendingCount > 0) {
            io.revon.app.utils.SyncNotificationManager.showOfflinePendingNotification(context, pendingCount)
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            androidx.compose.animation.AnimatedVisibility(
                visible = showBottomBar,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100))
            ) {
                NavigationBar(
                    containerColor = DarkSurface,
                    contentColor = Color.White,
                    tonalElevation = 8.dp,
                    modifier = Modifier.height(70.dp)
                ) {
                    val itemModifier = Modifier.offset(y = (8).dp)

                    // 1. 路線 (Routes)
                    NavigationBarItem(
                        modifier = itemModifier,
                        selected = currentRoute == NavRoutes.ROUTES,
                        onClick = { navigateToTab(NavRoutes.ROUTES) },
                        icon = { Icon(Icons.Default.Explore, contentDescription = if (isEn) "Routes" else "路線", modifier = Modifier.size(20.dp)) },
                        label = { Text(if (isEn) "Routes" else "路線", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RedPrimary,
                            selectedTextColor = RedPrimary,
                            indicatorColor = RedPrimary.copy(alpha = 0.15f)
                        )
                    )

                    // 2. 排行 (Ranking)
                    NavigationBarItem(
                        modifier = itemModifier,
                        selected = currentRoute == NavRoutes.RANKING,
                        onClick = { navigateToTab(NavRoutes.RANKING) },
                        icon = { Icon(Icons.Default.EmojiEvents, contentDescription = if (isEn) "Ranking" else "排行", modifier = Modifier.size(20.dp)) },
                        label = { Text(if (isEn) "Ranking" else "排行", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RedPrimary,
                            selectedTextColor = RedPrimary,
                            indicatorColor = RedPrimary.copy(alpha = 0.15f)
                        )
                    )

                    // 3. 車隊 (Club)
                    NavigationBarItem(
                        modifier = itemModifier,
                        selected = currentRoute == NavRoutes.CLUBS || currentRoute == NavRoutes.CLUB_DETAIL,
                        onClick = { navigateToTab(NavRoutes.CLUBS) },
                        icon = { Icon(Icons.Default.Group, contentDescription = if (isEn) "Club" else "車隊", modifier = Modifier.size(20.dp)) },
                        label = { Text(if (isEn) "Club" else "車隊", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RedPrimary,
                            selectedTextColor = RedPrimary,
                            indicatorColor = RedPrimary.copy(alpha = 0.15f)
                        )
                    )

                    // 4. 個人 (Profile)
                    NavigationBarItem(
                        modifier = itemModifier,
                        selected = currentRoute == NavRoutes.PROFILE,
                        onClick = { navigateToTab(NavRoutes.PROFILE) },
                        icon = { Icon(Icons.Default.Person, contentDescription = if (isEn) "Profile" else "個人", modifier = Modifier.size(20.dp)) },
                        label = { Text(if (isEn) "Profile" else "個人", fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RedPrimary,
                            selectedTextColor = RedPrimary,
                            indicatorColor = RedPrimary.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // 雲端同步與離線補傳頂部提示 Bar (可點擊開啟詳細佇列對話框)
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isOnline || pendingCount > 0,
                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = pendingCount > 0) { showPendingUploadsDialog = true },
                        color = if (!isOnline) Color(0xFFD32F2F) else Color(0xFF00897B)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val icon = if (!isOnline) "⚡" else "↻"
                            val text = when {
                                !isOnline && pendingCount > 0 -> "網路連線中斷 | $pendingCount 筆計時紀錄離線保存中 (點擊查看與管理)"
                                !isOnline -> "網路連線中斷 | 已切換至離線紀錄保護模式"
                                else -> "網路已恢復 | 正在自動補傳 $pendingCount 筆計時紀錄 (點擊查看進度/取消)"
                            }
                            Text(
                                text = "$icon $text",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 全域共用頂部跑馬燈 (在 路線 / 排行 / 車隊 / 個人 頁面持續保持運作與平滑播放，切換頁面不重設)
                if (showBottomBar) {
                    var realOnlineCount by remember { mutableStateOf(1) }
                    var cloudLatestRecords by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
                    val tokenManager = remember { io.revon.app.data.local.TokenManager(context) }
                    val currentUser = remember { tokenManager.getUser() }
                    val currentNickname = remember(currentUser) {
                        currentUser?.nickname.takeIf { !it.isNullOrBlank() } ?: (currentUser?.email?.substringBefore("@") ?: "車手")
                    }

                    val apiRaceRepo = remember { io.revon.app.data.repository.ApiRaceRepository(io.revon.app.di.NetworkModule.apiService) }
                    DisposableEffect(Unit) {
                        val countListener = apiRaceRepo.listenToOnlineCount { count -> realOnlineCount = count }
                        val recordsListener = apiRaceRepo.listenToLatestRecords { list -> cloudLatestRecords = list }
                        onDispose { }
                    }

                    val dynamicMarqueeItems = remember(cloudLatestRecords, context, currentNickname) {
                        val localSessions = io.revon.app.data.repository.TrackSessionManager.getAllSessions(context)
                        val items = mutableListOf<Pair<String, String>>()
                        cloudLatestRecords.forEach { data ->
                            val nick = data["playerNickname"]?.toString() ?: "車手"
                            val trCode = data["trackCode"]?.toString() ?: data["trackName"]?.toString() ?: "賽道"
                            val timeDisp = data["lapTimeDisplay"]?.toString() ?: ""
                            items.add(Pair("$nick 完賽", "$trCode $timeDisp"))
                        }
                        localSessions.forEach { record ->
                            val baseNick = if (record.playerNickname.isNotBlank() && record.playerNickname != "車手") record.playerNickname else currentNickname
                            val nickWithMe = if (baseNick.endsWith("(我)")) baseNick else "$baseNick(我)"
                            items.add(Pair("$nickWithMe 本地紀錄", "${record.trackCode} ${record.lapTimeDisplay}"))
                        }
                        items.take(5)
                    }

                    io.revon.app.ui.screens.TopMarqueeBanner(
                        onlineCount = realOnlineCount,
                        marqueeItems = dynamicMarqueeItems
                    )
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    color = DarkBackground
                ) {
                    AppNavHost(
                        navController = navController,
                        authViewModel = authViewModel,
                        raceViewModel = raceViewModel,
                        chatViewModel = chatViewModel,
                        startDestination = if (isLoggedIn) NavRoutes.ROUTES else NavRoutes.LOGIN
                    )
                }
            }
            io.revon.app.ui.components.RevonTopNotificationHost()
        }
    }

    // 自動補傳佇列與進度管理對話框
    if (showPendingUploadsDialog && pendingCount > 0) {
        PendingUploadsDialog(
            pendingList = pendingList,
            currentlyUploadingId = currentlyUploadingId,
            uploadProgressMap = uploadProgressMap,
            onCancelUpload = { session ->
                PendingUploadManager.removePendingUpload(context, session.sessionId)
            },
            onDismiss = { showPendingUploadsDialog = false }
        )
    }
}

/**
 * 正在自動補傳紀錄與進度管理對話框
 */
@Composable
fun PendingUploadsDialog(
    pendingList: List<RaceSessionRecord>,
    currentlyUploadingId: String?,
    uploadProgressMap: Map<String, Int>,
    onCancelUpload: (RaceSessionRecord) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141418)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C2C36))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "自動補傳計時紀錄",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "關閉",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "共 ${pendingList.size} 筆紀錄等待補傳。點擊取消可移除該紀錄，避免自動重傳。",
                    color = Color(0xFF90909A),
                    fontSize = 11.5.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(pendingList, key = { it.sessionId }) { record ->
                        val isUploading = record.sessionId == currentlyUploadingId
                        val progress = uploadProgressMap[record.sessionId] ?: if (isUploading) 50 else 0

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF1E1E24),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (isUploading) Color(0xFF00E5FF).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = record.trackName,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = record.lapTimeDisplay,
                                            color = NeonGreen,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "紀錄時間: ${record.recordedAt}",
                                            color = Color(0xFF90909A),
                                            fontSize = 10.5.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }

                                    Button(
                                        onClick = { onCancelUpload(record) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary.copy(alpha = 0.85f))
                                    ) {
                                        Text(
                                            text = "取消",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LinearProgressIndicator(
                                        progress = { (progress / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(5.dp),
                                        color = if (isUploading) Color(0xFF00E5FF) else Color(0xFF4A4A5A),
                                        trackColor = Color(0xFF2C2C36)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isUploading) "補傳中 $progress%" else "等待補傳",
                                        color = if (isUploading) Color(0xFF00E5FF) else Color.Gray,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
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
