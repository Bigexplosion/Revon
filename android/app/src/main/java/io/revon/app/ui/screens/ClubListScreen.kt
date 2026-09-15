package io.revon.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.revon.app.data.model.Club
import io.revon.app.ui.theme.DarkBackground
import io.revon.app.ui.theme.DarkSurface
import io.revon.app.ui.theme.NeonGreen
import io.revon.app.ui.theme.RedPrimary
import io.revon.app.ui.viewmodel.AuthViewModel
import io.revon.app.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ClubListScreen(
    chatViewModel: ChatViewModel,
    authViewModel: AuthViewModel,
    onNavigateToClubDetail: () -> Unit
) {
    val clubs by chatViewModel.clubs.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val errorMessage by chatViewModel.errorMessage.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var badgeInput by remember { mutableStateOf("") }
    var mottoInput by remember { mutableStateOf("") }

    var clubToDelete by remember { mutableStateOf<Club?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var showMyClubSheet by remember { mutableStateOf(false) }
    var showBrowseClubsSheet by remember { mutableStateOf(false) }

    // 完全只抓取真實存在的車隊列表 (不使用任何假資料/硬編碼)
    val sortedClubs = remember(clubs, searchQuery) {
        val sorted = clubs.sortedByDescending { it.totalPoints }
        if (searchQuery.isBlank()) sorted else sorted.filter { it.name.contains(searchQuery, ignoreCase = true) || it.badgeLetters.contains(searchQuery, ignoreCase = true) }
    }

    val top1 = sortedClubs.getOrNull(0)
    val top2 = sortedClubs.getOrNull(1)
    val top3 = sortedClubs.getOrNull(2)
    val listClubs = if (sortedClubs.size > 3) sortedClubs.subList(3, sortedClubs.size) else emptyList()

    // 檢查用戶是否已加入/建立車隊
    val myClub = remember(clubs, currentUser) {
        clubs.find { c -> c.captainNickname == currentUser?.nickname }
    }

    // 真實線上人數與最新完賽紀錄 State (與路線頁面同步)
    var realOnlineCount by remember { mutableStateOf(1) }
    var cloudLatestRecords by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    val currentUid = remember(currentUser) { currentUser?.id ?: "guest_${System.currentTimeMillis() % 10000}" }
    val currentNickname = remember(currentUser) {
        currentUser?.nickname.takeIf { !it.isNullOrBlank() } ?: (currentUser?.email?.substringBefore("@") ?: "車手")
    }

    val apiRaceRepo = remember { io.revon.app.data.repository.ApiRaceRepository(io.revon.app.di.NetworkModule.apiService) }

    DisposableEffect(currentUid) {
        val presenceListener = apiRaceRepo.listenToOnlineCount { count ->
            realOnlineCount = count
        }

        val recordsListener = apiRaceRepo.listenToLatestRecords { list ->
            cloudLatestRecords = list
        }

        onDispose { }
    }

    // 建立廣播跑馬燈動態紀錄項目 (完全去除硬編碼)
    val dynamicMarqueeItems = remember(cloudLatestRecords, context, currentNickname) {
        val localSessions = io.revon.app.data.repository.TrackSessionManager.getAllSessions(context)
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

        items.take(5)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // 2. 搜尋車隊 Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    placeholder = {
                        Text(
                            text = "搜尋車隊...",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.Gray
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (myClub != null) {
                                    showBrowseClubsSheet = true
                                } else {
                                    showMyClubSheet = true
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "車隊功能", tint = RedPrimary)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RedPrimary,
                        unfocusedBorderColor = Color(0xFF2B2B2B),
                        focusedContainerColor = Color(0xFF141414),
                        unfocusedContainerColor = Color(0xFF141414),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        // 3. 我的車隊 狀態欄 (點選跳出下方滑出視窗)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF2A2A2A),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (myClub != null) {
                                        chatViewModel.selectClub(myClub, currentUser?.nickname)
                                        onNavigateToClubDetail()
                                    } else {
                                        showMyClubSheet = true
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF121212))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val myClubBadgeBg = remember(myClub?.accentColor) {
                                    try {
                                        if (!myClub?.accentColor.isNullOrEmpty()) Color(android.graphics.Color.parseColor(myClub!!.accentColor)) else RedPrimary
                                    } catch (e: Exception) {
                                        RedPrimary
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .border(1.dp, myClubBadgeBg.copy(alpha = 0.5f), CircleShape)
                                        .background(myClubBadgeBg, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = myClub?.badgeLetters ?: "一",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "我的車隊",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (myClub != null) "【${myClub.name}】成員數：${myClub.memberCount} 人" else "你還沒加入任何車隊。瀏覽排行榜並申請加入一個車隊吧。",
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                                Text("›", color = Color.Gray, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }



                    item {
                        // 5. 前三名 頒獎台 Podiums - 圖層堆疊方式 (Stack Layout: 1st in center overlapping 2nd & 3rd)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                // 2nd Place (Left - Underneath Layer)
                                Box(modifier = Modifier.weight(1f)) {
                                    top2?.let { club ->
                                        PodiumsCard(
                                            rank = 2,
                                            club = club,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(175.dp),
                                            onSelect = {
                                                chatViewModel.selectClub(club, currentUser?.nickname)
                                                onNavigateToClubDetail()
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(60.dp)) // Reserve center space for overlapping 1st place

                                // 3rd Place (Right - Underneath Layer)
                                Box(modifier = Modifier.weight(1f)) {
                                    top3?.let { club ->
                                        PodiumsCard(
                                            rank = 3,
                                            club = club,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(165.dp),
                                            onSelect = {
                                                chatViewModel.selectClub(club, currentUser?.nickname)
                                                onNavigateToClubDetail()
                                            }
                                        )
                                    }
                                }
                            }

                            // 1st Place (Center - Top Elevated Layer with zIndex and Glow)
                            top1?.let { club ->
                                Box(
                                    modifier = Modifier
                                        .width(170.dp)
                                        .height(230.dp)
                                        .zIndex(2f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    PodiumsCard(
                                        rank = 1,
                                        club = club,
                                        isFirst = true,
                                        modifier = Modifier.fillMaxSize(),
                                        onSelect = {
                                            chatViewModel.selectClub(club, currentUser?.nickname)
                                            onNavigateToClubDetail()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 6. #4 ~ #10 車隊排行榜單
                    items(listClubs.size) { index ->
                        val club = listClubs[index]
                        val rankNum = index + 4
                        val isCaptain = (currentUser?.nickname?.isNotBlank() == true && club.captainNickname == currentUser?.nickname)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF222222), shape = RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = {
                                        chatViewModel.selectClub(club, currentUser?.nickname)
                                        onNavigateToClubDetail()
                                    },
                                    onLongClick = {
                                        if (isCaptain) clubToDelete = club
                                    }
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "#$rankNum",
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.width(32.dp)
                                    )

                                    val badgeBgColor = remember(club.accentColor) {
                                        try {
                                            Color(android.graphics.Color.parseColor(club.accentColor))
                                        } catch (e: Exception) {
                                            RedPrimary
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(badgeBgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = club.badgeLetters, color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(text = club.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                                        Text(text = "${club.region ?: "TAICHUNG"} • ${club.memberCount} 會員", color = Color.Gray, fontSize = 11.sp)
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    if (club.name == "AKINA SPEED STARS") {
                                        Surface(
                                            color = Color(0xFF3B0000),
                                            shape = RoundedCornerShape(4.dp),
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, RedPrimary)
                                        ) {
                                            Text("T21 霸主", color = RedPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    Text(
                                        text = String.format("%,d", club.totalPoints),
                                        color = NeonGreen,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text("積分", color = Color.Gray, fontSize = 9.sp)
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showCreateDialog = false },
            sheetState = sheetState,
            containerColor = Color(0xFF141414),
            scrimColor = Color.Black.copy(alpha = 0.7f),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Gray.copy(alpha = 0.5f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("創建專屬車隊", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)

                    IconButton(onClick = { showCreateDialog = false }) {
                        Text("✕", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF2B2B2B), RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("車隊名稱", color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RedPrimary,
                                unfocusedBorderColor = Color(0xFF333333),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        OutlinedTextField(
                            value = badgeInput,
                            onValueChange = { if (it.length <= 4) badgeInput = it.uppercase() },
                            label = { Text("隊徽縮寫 (最長 4 個英文字母如 AKN)", color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RedPrimary,
                                unfocusedBorderColor = Color(0xFF333333),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        OutlinedTextField(
                            value = mottoInput,
                            onValueChange = { mottoInput = it },
                            label = { Text("簡介", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RedPrimary,
                                unfocusedBorderColor = Color(0xFF333333),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (nameInput.isNotBlank() && badgeInput.isNotBlank()) {
                            chatViewModel.createClub(nameInput, badgeInput, mottoInput, currentUser?.nickname ?: "隊長")
                            showCreateDialog = false
                            nameInput = ""
                            badgeInput = ""
                            mottoInput = ""
                        } else {
                            io.revon.app.ui.components.RevonToastManager.warning("請填寫車隊名稱與隊徽縮寫")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("確 認 創 建", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (!errorMessage.isNullOrEmpty()) {
        val errText = errorMessage ?: ""
        AlertDialog(
            onDismissRequest = { chatViewModel.clearError() },
            containerColor = DarkSurface,
            title = { Text("⚠️ 連線異常訊息", color = RedPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("連線伺服器時發生問題，詳細錯誤紀錄如下：", color = Color.White, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = errText, color = NeonGreen, fontSize = 11.sp, modifier = Modifier.padding(10.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        chatViewModel.copyErrorToClipboard(context, errText)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("複製錯誤訊息", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { chatViewModel.clearError() }) {
                    Text("關閉", color = Color.Gray)
                }
            }
        )
    }

    if (clubToDelete != null) {
        val targetClub = clubToDelete!!
        AlertDialog(
            onDismissRequest = { clubToDelete = null },
            containerColor = DarkSurface,
            title = { Text("刪除車隊", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("確定要解散並刪除車隊【${targetClub.name}】嗎？刪除後無法復原。", color = Color.Gray, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        chatViewModel.deleteClub(targetClub.name)
                        clubToDelete = null
                        io.revon.app.ui.components.RevonToastManager.success("已刪除車隊【${targetClub.name}】")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("確定刪除", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { clubToDelete = null }) {
                    Text("取消", color = Color.Gray)
                }
            }
        )
    }

    // 7. 我的車隊（尚未加入）下方滑出視窗 (Modal Bottom Sheet) - 匹配參考圖設計
    if (showMyClubSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMyClubSheet = false },
            containerColor = Color(0xFF141414),
            scrimColor = Color.Black.copy(alpha = 0.7f),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Gray.copy(alpha = 0.5f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222222))
                            .clickable { showMyClubSheet = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF242424)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "尚未加入車隊",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "你還沒加入任何車隊。瀏覽排行榜並申請加入一個車隊吧。",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            showMyClubSheet = false
                            showBrowseClubsSheet = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                    ) {
                        Text("瀏覽車隊", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            showMyClubSheet = false
                            showCreateDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RedPrimary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedPrimary)
                    ) {
                        Text("創建車隊", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // 8. 瀏覽公開車隊列表下方滑出視窗 + 創建車隊按鈕 (Browse Clubs Bottom Sheet)
    if (showBrowseClubsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBrowseClubsSheet = false },
            containerColor = Color(0xFF141414),
            scrimColor = Color.Black.copy(alpha = 0.7f),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Gray.copy(alpha = 0.5f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("瀏覽公開車隊", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)

                    IconButton(onClick = { showBrowseClubsSheet = false }) {
                        Text("✕", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (clubs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("目前尚無任何公開車隊", color = Color.Gray, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    showBrowseClubsSheet = false
                                    showCreateDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                            ) {
                                Text("+ 創建第一個車隊", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(clubs) { club ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFF262626), RoundedCornerShape(12.dp))
                                    .clickable {
                                        chatViewModel.selectClub(club, currentUser?.nickname)
                                        showBrowseClubsSheet = false
                                        onNavigateToClubDetail()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(RedPrimary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(club.badgeLetters, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(club.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Text("隊長：${club.captainNickname ?: "車手"} • ${club.memberCount} 人", color = Color.Gray, fontSize = 11.sp)
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            chatViewModel.selectClub(club, currentUser?.nickname)
                                            showBrowseClubsSheet = false
                                            onNavigateToClubDetail()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary.copy(alpha = 0.2f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("申請 / 查看", color = RedPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            showBrowseClubsSheet = false
                            showCreateDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                    ) {
                        Text("+ 創 建 新 車 隊", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PodiumsCard(
    rank: Int,
    club: Club,
    modifier: Modifier = Modifier,
    isFirst: Boolean = false,
    onSelect: () -> Unit
) {
    Card(
        modifier = modifier
            .border(
                width = if (isFirst) 1.5.dp else 1.dp,
                color = if (isFirst) RedPrimary else Color(0xFF222222),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onSelect() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFirst) Color(0xFF190606) else Color(0xFF141414)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Rank Badge Number
            Text(
                text = "$rank",
                color = Color.Gray,
                fontSize = if (isFirst) 22.sp else 18.sp,
                fontWeight = FontWeight.Black
            )

            val badgeBgColor = remember(club.accentColor) {
                try {
                    Color(android.graphics.Color.parseColor(club.accentColor))
                } catch (e: Exception) {
                    RedPrimary
                }
            }

            // Circle Logo
            Box(
                modifier = Modifier
                    .size(if (isFirst) 52.dp else 44.dp)
                    .clip(CircleShape)
                    .background(badgeBgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = club.badgeLetters,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = if (isFirst) 16.sp else 14.sp
                )
            }

            // Club Name & Members
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = club.name,
                    color = Color.White,
                    fontSize = if (isFirst) 12.sp else 11.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(
                    text = "${club.memberCount} 會員",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }

            // Points Value (Formatted with comma)
            Text(
                text = String.format("%,d", club.totalPoints),
                color = NeonGreen,
                fontSize = if (isFirst) 16.sp else 14.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
