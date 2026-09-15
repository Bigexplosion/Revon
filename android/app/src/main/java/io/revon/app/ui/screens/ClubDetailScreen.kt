package io.revon.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.revon.app.data.model.MemberRole
import io.revon.app.data.repository.TrackRepository
import io.revon.app.ui.theme.DarkBackground
import io.revon.app.ui.theme.DarkSurface
import io.revon.app.ui.theme.NeonGreen
import io.revon.app.ui.theme.RedPrimary
import io.revon.app.ui.viewmodel.AuthViewModel
import io.revon.app.ui.viewmodel.ChatViewModel
import androidx.compose.ui.res.stringResource
import io.revon.app.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubDetailScreen(
    chatViewModel: ChatViewModel,
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val localCtx = LocalContext.current
    val club by chatViewModel.selectedClub.collectAsState()
    val messages by chatViewModel.messages.collectAsState()
    val members by chatViewModel.members.collectAsState()
    val events by chatViewModel.events.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) } // 0: 成員管理, 1: 聊天室, 2: 車隊設定, 3: 活動建立

    LaunchedEffect(club?.id) {
        val c = club
        val u = currentUser
        if (c != null) {
            chatViewModel.selectClub(c, u?.nickname)
        }
    }

    val isCaptain = remember(club, currentUser) {
        val nick = currentUser?.nickname ?: ""
        val cap = club?.captainNickname ?: ""
        nick == cap || cap == "賽車手" || cap == "車手隊長"
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            Column(modifier = Modifier.background(DarkSurface)) {
                val clubAccentColor = remember(club?.accentColor) {
                    try {
                        Color(android.graphics.Color.parseColor(club?.accentColor ?: "#E10600"))
                    } catch (e: Exception) {
                        RedPrimary
                    }
                }
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(clubAccentColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = club?.badgeLetters ?: "CLB", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = club?.name ?: "山道車隊", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(text = "隊長: ${club?.captainNickname ?: "未定"} · ${club?.region ?: "Taiwan"}", color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
                )

                // 車隊 4 大管理頁籤 Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = DarkSurface,
                    contentColor = RedPrimary,
                    divider = { HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f)) }
                ) {
                    val tabTitles = listOf(
                        stringResource(R.string.club_tab_members) to Icons.Default.People,
                        stringResource(R.string.club_tab_chat) to Icons.Default.Forum,
                        stringResource(R.string.club_tab_settings) to Icons.Default.Settings,
                        stringResource(R.string.club_tab_events) to Icons.Default.Event
                    )

                    tabTitles.forEachIndexed { idx, (title, icon) ->
                        Tab(
                            selected = selectedTabIndex == idx,
                            onClick = { selectedTabIndex = idx },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTabIndex == idx) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTabIndex == idx) RedPrimary else Color.Gray
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = if (selectedTabIndex == idx) RedPrimary else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val currentUid = remember(currentUser) { currentUser?.id ?: "" }
            val currentNickname = remember(currentUser) {
                val n = currentUser?.nickname
                if (!n.isNullOrBlank() && n != "車手") n else (currentUser?.account ?: "賽車手")
            }
            val isCaptainOrAdmin = remember(club, members, currentUser, currentNickname) {
                val cap = club?.captainNickname ?: ""
                val isCap = currentNickname == cap || cap == "賽車手" || cap == "車手隊長"
                val myMemRecord = members.find { it.memberNickname == currentNickname || (currentUid.isNotBlank() && it.memberNickname == currentUser?.account) }
                val isAdmin = myMemRecord?.role == io.revon.app.data.model.MemberRole.ADMIN || myMemRecord?.role == io.revon.app.data.model.MemberRole.CAPTAIN || currentNickname == "管理員"
                isCap || isAdmin
            }
            when (selectedTabIndex) {
                0 -> MemberManagementTab(chatViewModel = chatViewModel, members = members, isCaptain = isCaptain, currentUserNickname = currentNickname)
                1 -> ChatRoomTab(chatViewModel = chatViewModel, messages = messages, currentUserNickname = currentNickname, currentUid = currentUid, isCaptainOrAdmin = isCaptainOrAdmin)
                2 -> ClubSettingsTab(chatViewModel = chatViewModel, club = club, isCaptain = isCaptain)
                3 -> ClubEventsTab(chatViewModel = chatViewModel, events = events, creatorNickname = currentNickname, currentUid = currentUid, isCaptain = isCaptain)
            }
        }

        val errorMessage by chatViewModel.errorMessage.collectAsState()
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
                            chatViewModel.copyErrorToClipboard(localCtx, errText)
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
    }
}

/**
 * 1. 成員管理 Tab (Member Management)
 */
@Composable
private fun MemberManagementTab(
    chatViewModel: ChatViewModel,
    members: List<io.revon.app.data.model.ClubMember>,
    isCaptain: Boolean,
    currentUserNickname: String = ""
) {
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteNicknameInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "車隊成員 (${members.size} 位)",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = { showInviteDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("邀請隊員", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(members) { mem ->
                val isMemCaptain = mem.role == MemberRole.CAPTAIN
                val isMemAdmin = mem.role == MemberRole.ADMIN
                val rawName = mem.memberNickname
                val memName = if (isMemCaptain && (rawName.isNullOrBlank() || rawName == "賽車手" || rawName == "車手隊長" || rawName == "隊員")) {
                    if (currentUserNickname.isNotBlank()) currentUserNickname else "隊長"
                } else {
                    rawName ?: "隊員"
                }

                var showRoleMenu by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.DarkGray.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isMemCaptain) RedPrimary else if (isMemAdmin) Color(0xFFFF9800) else Color.DarkGray),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = memName.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = memName,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = when {
                                                isMemCaptain -> RedPrimary.copy(alpha = 0.2f)
                                                isMemAdmin -> Color(0xFFFF9800).copy(alpha = 0.2f)
                                                else -> Color.Gray.copy(alpha = 0.2f)
                                            },
                                            modifier = Modifier.clickable(enabled = isCaptain) { showRoleMenu = true }
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                Text(
                                                    text = when {
                                                        isMemCaptain -> "隊長"
                                                        isMemAdmin -> "管理員"
                                                        else -> "隊員"
                                                    },
                                                    color = when {
                                                        isMemCaptain -> RedPrimary
                                                        isMemAdmin -> Color(0xFFFF9800)
                                                        else -> Color.LightGray
                                                    },
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (isCaptain) {
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                                }
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = showRoleMenu,
                                            onDismissRequest = { showRoleMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("隊長 (CAPTAIN)") },
                                                onClick = {
                                                    chatViewModel.setMemberRole(memName, MemberRole.CAPTAIN)
                                                    showRoleMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("管理員 (ADMIN)") },
                                                onClick = {
                                                    chatViewModel.setMemberRole(memName, MemberRole.ADMIN)
                                                    showRoleMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("一般隊員 (MEMBER)") },
                                                onClick = {
                                                    chatViewModel.setMemberRole(memName, MemberRole.MEMBER)
                                                    showRoleMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                                val joinDateDisplay = remember(mem.joinedAt) {
                                    val raw = mem.joinedAt
                                    if (!raw.isNullOrBlank()) {
                                        val parts = raw.split(" ", "T")
                                        if (parts.isNotEmpty() && parts[0].contains("-")) {
                                            parts[0].replace("-", "/")
                                        } else raw
                                    } else {
                                        java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(java.util.Date())
                                    }
                                }
                                Text("加入時間: $joinDateDisplay", color = Color.Gray, fontSize = 11.sp)
                            }
                        }

                        if ((isCaptain || currentUserNickname == "管理員") && !isMemCaptain) {
                            IconButton(
                                onClick = { chatViewModel.removeMember(memName) }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "移出車隊", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInviteDialog) {
        var userSearchQuery by remember { mutableStateOf("") }
        var availableUsers by remember { mutableStateOf<List<String>>(emptyList()) }
        val currentMemberNames = remember(members) { members.mapNotNull { it.memberNickname } }

        LaunchedEffect(Unit) {
            val users = emptyList<String>() // TODO: API fetchRegisteredUsers
            availableUsers = users
        }

        val filteredUsers = remember(availableUsers, userSearchQuery, currentMemberNames) {
            availableUsers.filter { u ->
                !currentMemberNames.contains(u) &&
                (userSearchQuery.isBlank() || u.lowercase().contains(userSearchQuery.trim().lowercase()))
            }
        }

        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            containerColor = DarkSurface,
            title = { Text("邀請隊員 (點擊直接邀請)", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                    OutlinedTextField(
                        value = userSearchQuery,
                        onValueChange = { userSearchQuery = it },
                        placeholder = { Text("搜尋註冊玩家暱稱...", color = Color.Gray, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (filteredUsers.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("沒有可邀請的玩家", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            items(filteredUsers) { nick ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            chatViewModel.inviteMember(nick)
                                            showInviteDialog = false
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF222226),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier.size(32.dp).clip(CircleShape).background(RedPrimary.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(nick.take(1).uppercase(), color = RedPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(nick, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = RedPrimary
                                        ) {
                                            Text("一鍵邀請", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) {
                    Text("關閉", color = Color.Gray)
                }
            }
        )
    }
}

/**
 * 2. 聊天室 Tab (Club Chatroom)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRoomTab(
    chatViewModel: ChatViewModel,
    messages: List<io.revon.app.data.model.ClubChatMessage>,
    currentUserNickname: String,
    currentUid: String = "",
    isCaptainOrAdmin: Boolean = false
) {
    var textInput by remember { mutableStateOf("") }
    var selectedMessageToDelete by remember { mutableStateOf<io.revon.app.data.model.ClubChatMessage?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                val authorNick = msg.authorNickname ?: "車手"
                val msgBody = msg.body ?: ""
                val msgType = msg.messageType ?: "text"
                val authorUid = msg.authorUid ?: ""

                val isSelf = (currentUid.isNotBlank() && authorUid == currentUid) || (authorNick == currentUserNickname)
                val canDelete = isSelf || isCaptainOrAdmin

                if (msgType == "race_report") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    if (canDelete) selectedMessageToDelete = msg
                                }
                            )
                            .border(1.dp, RedPrimary.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = RedPrimary.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = msgBody,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start
                    ) {
                        if (!isSelf) {
                            Text(
                                text = authorNick,
                                color = Color.Gray,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 12.dp,
                                topEnd = 12.dp,
                                bottomStart = if (isSelf) 12.dp else 2.dp,
                                bottomEnd = if (isSelf) 2.dp else 12.dp
                            ),
                            color = if (isSelf) RedPrimary else DarkSurface,
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        if (canDelete) selectedMessageToDelete = msg
                                    }
                                )
                        ) {
                            Text(
                                text = msgBody,
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        Surface(color = DarkSurface, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("輸入隊伍訊息...", color = Color.Gray, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RedPrimary,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            chatViewModel.sendMessage(currentUserNickname, textInput, senderUid = currentUid)
                            textInput = ""
                            coroutineScope.launch {
                                if (messages.isNotEmpty()) {
                                    listState.animateScrollToItem(messages.size - 1)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(RedPrimary, shape = RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "傳送", tint = Color.White)
                }
            }
        }
    }

    val msgTarget = selectedMessageToDelete
    if (msgTarget != null) {
        AlertDialog(
            onDismissRequest = { selectedMessageToDelete = null },
            containerColor = DarkSurface,
            title = { Text("收回 / 刪除訊息", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("確定要刪除這筆訊息嗎？所有人聊天室將同步移除該訊息。", color = Color.Gray, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        chatViewModel.deleteMessage(msgTarget)
                        selectedMessageToDelete = null
                        io.revon.app.ui.components.RevonToastManager.success("已成功刪除訊息")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("確定刪除", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedMessageToDelete = null }) {
                    Text("取消", color = Color.Gray)
                }
            }
        )
    }
}

/**
 * 3. 車隊設定 Tab (Club Settings)
 */
@Composable
private fun ClubSettingsTab(
    chatViewModel: ChatViewModel,
    club: io.revon.app.data.model.Club?,
    isCaptain: Boolean
) {
    var nameInput by remember(club) { mutableStateOf(club?.name ?: "") }
    var badgeInput by remember(club) { mutableStateOf(club?.badgeLetters ?: "") }
    var mottoInput by remember(club) { mutableStateOf(club?.motto ?: "") }
    var accentColorInput by remember(club) { mutableStateOf(club?.accentColor ?: "#E10600") }

    var isSavedNotice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("車隊設定與資料修改", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            enabled = isCaptain,
            label = { Text("車隊名稱", color = Color.Gray) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )

        OutlinedTextField(
            value = badgeInput,
            onValueChange = { if (it.length <= 3) badgeInput = it },
            enabled = isCaptain,
            label = { Text("隊徽縮寫 (如 APX)", color = Color.Gray) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )

        OutlinedTextField(
            value = mottoInput,
            onValueChange = { mottoInput = it },
            enabled = isCaptain,
            label = { Text("簡介", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )

        Text("車隊代表色", color = Color.Gray, fontSize = 12.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val colors = listOf("#E10600", "#FF9800", "#00E5FF", "#9C27B0")
            colors.forEach { hex ->
                val parsedColor = Color(android.graphics.Color.parseColor(hex))
                val isSel = accentColorInput.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(parsedColor)
                        .border(
                            width = if (isSel) 3.dp else 0.dp,
                            color = if (isSel) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable(enabled = isCaptain) { accentColorInput = hex },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSel) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isCaptain) {
            Button(
                onClick = {
                    chatViewModel.updateClubSettings(nameInput, badgeInput, mottoInput, accentColorInput) { success ->
                        if (success) {
                            isSavedNotice = true
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("儲存車隊設定", color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else {
            Text("（僅隊長權限可編輯車隊設定）", color = Color.Gray, fontSize = 12.sp)
        }

        if (isSavedNotice) {
            Text("✓ 車隊設定已成功儲存並同步全站", color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 4. 活動建立 Tab (Club Events)
 */
@Composable
private fun ClubEventsTab(
    chatViewModel: ChatViewModel,
    events: List<io.revon.app.data.model.ClubEvent>,
    creatorNickname: String,
    currentUid: String = "",
    isCaptain: Boolean = false
) {
    var showCreateEventDialog by remember { mutableStateOf(false) }

    var titleInput by remember { mutableStateOf("") }
    var trackInput by remember { mutableStateOf("136") }
    var timeInput by remember { mutableStateOf("本週六 20:00") }
    var descInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("車隊賽事與聚會活動", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)

            Button(
                onClick = { showCreateEventDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("建立活動", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("目前尚未舉辦車隊活動", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(events) { evt ->
                    val isCreatorOrCaptain = (currentUid.isNotBlank() && evt.creatorUid == currentUid) || (evt.creatorNickname == creatorNickname) || isCaptain

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.DarkGray.copy(alpha = 0.5f), shape = RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = RedPrimary.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = evt.trackCode,
                                            color = RedPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(evt.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }

                                Text(evt.dateTimeDisplay, color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(evt.description, color = Color.LightGray, fontSize = 12.sp)

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isCreatorOrCaptain) "建立者: 您 (${evt.creatorNickname}) · ${evt.participantCount} 人已報名" else "建立者: ${evt.creatorNickname} · ${evt.participantCount} 人已報名",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isCreatorOrCaptain) {
                                        OutlinedButton(
                                            onClick = { chatViewModel.deleteClubEvent(evt.id) },
                                            border = androidx.compose.foundation.BorderStroke(1.dp, RedPrimary.copy(alpha = 0.6f)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("取消活動", color = RedPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        val userKey = if (currentUid.isNotBlank()) currentUid else creatorNickname
                                        val userJoined = evt.participants.contains(userKey) || (evt.isJoined && evt.participants.isEmpty())

                                        Button(
                                            onClick = { chatViewModel.toggleEventJoin(evt.id, userKey) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (userJoined) Color.DarkGray else RedPrimary
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (userJoined) "已報名 (取消)" else "立即參加",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
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
    }

    if (showCreateEventDialog) {
        val currentContext = LocalContext.current
        val realTracks = remember { TrackRepository.getTracks(currentContext) }

        // 即時搜尋與篩選賽道列表 (輸入時若符合資料庫已有賽道自動下拉)
        val filteredTracks = remember(trackInput, realTracks) {
            if (trackInput.isBlank()) {
                realTracks
            } else {
                realTracks.filter { tr ->
                    tr.name.contains(trackInput, ignoreCase = true) ||
                            (tr.nameZh?.contains(trackInput, ignoreCase = true) == true) ||
                            tr.code.contains(trackInput, ignoreCase = true)
                }
            }
        }

        var trackDropdownExpanded by remember { mutableStateOf(false) }

        // 原生日期與時間選擇器處理 (格式：月/日 24:xx，例如 "9/15 14:30")
        val calendar = remember { java.util.Calendar.getInstance() }

        val showDateTimePicker = {
            val datePicker = android.app.DatePickerDialog(
                currentContext,
                { _, year, monthOfYear, dayOfMonth ->
                    val timePicker = android.app.TimePickerDialog(
                        currentContext,
                        { _, hourOfDay, minute ->
                            val formattedMonth = monthOfYear + 1
                            val formattedTime = String.format("%d/%d %02d:%02d", formattedMonth, dayOfMonth, hourOfDay, minute)
                            timeInput = formattedTime
                        },
                        calendar.get(java.util.Calendar.HOUR_OF_DAY),
                        calendar.get(java.util.Calendar.MINUTE),
                        true // 24小時制
                    )
                    timePicker.show()
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            )
            datePicker.show()
        }

        AlertDialog(
            onDismissRequest = { showCreateEventDialog = false },
            containerColor = DarkSurface,
            title = { Text("建立新車隊活動", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("活動名稱 (如 136 週末盃)", color = Color.Gray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    // 賽道搜尋與即時選單
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = trackInput,
                            onValueChange = {
                                trackInput = it
                                trackDropdownExpanded = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("指定賽道 (輸入即時搜尋或自訂名稱)", color = Color.Gray) },
                            trailingIcon = {
                                IconButton(onClick = { trackDropdownExpanded = !trackDropdownExpanded }) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "選擇賽道",
                                        tint = RedPrimary
                                    )
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )

                        DropdownMenu(
                            expanded = trackDropdownExpanded && filteredTracks.isNotEmpty(),
                            onDismissRequest = { trackDropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .heightIn(max = 200.dp)
                                .background(DarkSurface)
                                .border(1.dp, RedPrimary.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                        ) {
                            filteredTracks.forEach { tr ->
                                val displayName = tr.nameZh ?: tr.name
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(text = "${tr.code} - $displayName", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    },
                                    onClick = {
                                        trackInput = displayName
                                        trackDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // 時間點擊呼叫原生 Android DatePicker + TimePicker (格式: 月/日 24:xx)
                    OutlinedTextField(
                        value = timeInput,
                        onValueChange = { timeInput = it },
                        readOnly = true,
                        label = { Text("活動時間 (點擊選擇日期時間)", color = Color.Gray) },
                        trailingIcon = {
                            IconButton(onClick = { showDateTimePicker() }) {
                                Icon(Icons.Default.Event, contentDescription = "選擇時間", tint = RedPrimary)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDateTimePicker() },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    OutlinedTextField(
                        value = descInput,
                        onValueChange = { descInput = it },
                        label = { Text("活動簡介說明", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uid = io.revon.app.data.local.TokenManager(currentContext).getUser()?.id ?: ""
                        chatViewModel.createClubEvent(titleInput, trackInput, timeInput, descInput, creatorNickname, creatorUid = uid)
                        titleInput = ""
                        descInput = ""
                        showCreateEventDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("發布活動", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateEventDialog = false }) {
                    Text("取消", color = Color.Gray)
                }
            }
        )
    }
}
