package io.revon.app.ui.screens

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.itemsIndexed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.revon.app.ui.components.RevonToastManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wc
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.revon.app.data.config.CrashLogger
import io.revon.app.data.config.GpsConfig
import io.revon.app.data.model.RaceSessionRecord
import io.revon.app.data.repository.TrackRepository
import io.revon.app.data.repository.TrackSessionManager
import io.revon.app.ui.theme.DarkBackground
import io.revon.app.ui.theme.DarkSurface
import io.revon.app.ui.theme.NeonGreen
import io.revon.app.ui.theme.RedPrimary
import io.revon.app.ui.viewmodel.AuthViewModel
import io.revon.app.ui.viewmodel.ChatViewModel
import androidx.compose.ui.res.stringResource
import io.revon.app.R

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

private val DividerColor = Color(0xFF222228)
private val TextGray = Color(0xFF888894)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel? = null,
    onLogout: () -> Unit = {},
    onNavigateToSessionDetail: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val currentUser by authViewModel.currentUser.collectAsState()
    val clubs by (chatViewModel?.clubs ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }).collectAsState()
    val selectedClub by (chatViewModel?.selectedClub ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()

    val userClub = remember(clubs, selectedClub, currentUser) {
        clubs.find { c ->
            c.captainNickname == currentUser?.nickname ||
            c.captainNickname == "賽車手" ||
            c.captainNickname == "車手" ||
            c.captainNickname == "車手隊長"
        } ?: selectedClub
    }

    val vehiclePrefs = remember { context.getSharedPreferences("vehicle_prefs", Context.MODE_PRIVATE) }
    var selectedCategory by remember {
        mutableStateOf(vehiclePrefs.getString("KEY_VEHICLE_TYPE", "MOTOR") ?: "MOTOR")
    }
    var selectedModalTrack by remember { mutableStateOf<String?>(null) }
    var modalSortMode by remember { mutableStateOf("DATE") }
    var sessionToDelete by remember { mutableStateOf<RaceSessionRecord?>(null) }
    var isAscending by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showRegionSection by remember { mutableStateOf(false) }

    var editNickname by remember { mutableStateOf(currentUser?.nickname ?: "賽車手") }
    var editRealName by remember { mutableStateOf(currentUser?.realName ?: "") }
    var editGender by remember { mutableStateOf(currentUser?.gender ?: "male") }
    var editGenderExpanded by remember { mutableStateOf(false) }
    var editPhone by remember { mutableStateOf(currentUser?.phone ?: "") }
    var editBirthday by remember { mutableStateOf(currentUser?.birthday ?: "") }
    var editEmail by remember { mutableStateOf(currentUser?.email ?: "") }
    var editCountry by remember { mutableStateOf(sanitizeCountry(currentUser?.country)) }
    var editCity by remember { mutableStateOf(sanitizeCity(currentUser?.city)) }

    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }

    // 我的區域獨立 Dropdown 狀態 (純中文選項)
    var regionCountry by remember(currentUser?.country) { mutableStateOf(sanitizeCountry(currentUser?.country)) }
    var regionCity by remember(currentUser?.city) { mutableStateOf(sanitizeCity(currentUser?.city)) }
    var regionCountryExpanded by remember { mutableStateOf(false) }
    var regionCityExpanded by remember { mutableStateOf(false) }

    // 預設進入畫面時自動向伺服器/資料庫同步最新個人檔案資訊與最新頭像
    LaunchedEffect(Unit) {
        authViewModel.checkSavedSession()
    }

    LaunchedEffect(currentUser) {
        currentUser?.let { u ->
            editNickname = u.nickname ?: ""
            editRealName = u.realName ?: ""
            editGender = if (u.gender.isNullOrBlank()) "male" else u.gender
            editPhone = u.phone ?: ""
            editBirthday = u.birthday ?: ""
            editEmail = u.email ?: ""
        }
    }

    // 當開啟編輯個人資料視窗時，自動向伺服器查詢最新的 SQL 個人檔案資料
    LaunchedEffect(showEditProfileDialog) {
        if (showEditProfileDialog) {
            authViewModel.checkSavedSession()
            currentUser?.let { u ->
                editNickname = u.nickname ?: ""
                editRealName = u.realName ?: ""
                editGender = if (u.gender.isNullOrBlank()) "male" else u.gender
                editPhone = u.phone ?: ""
                editBirthday = u.birthday ?: ""
                editEmail = u.email ?: ""
                editCountry = sanitizeCountry(u.country)
                editCity = sanitizeCity(u.city)
            }
        }
    }

    // 支援與法律對話框狀態
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showTermsOfServiceDialog by remember { mutableStateOf(false) }
    var showRefundPolicyDialog by remember { mutableStateOf(false) }

    // 🛠️ 開發者偵錯選項 (GpsConfig) 狀態
    val profilePrefs = remember { context.getSharedPreferences("profile_prefs", Context.MODE_PRIVATE) }
    var showDebugSection by remember {
        mutableStateOf(profilePrefs.getBoolean("KEY_SHOW_DEBUG_SECTION", false))
    }
    var sessionOptionsRecord by remember { mutableStateOf<RaceSessionRecord?>(null) }

    var debugMode by remember { mutableStateOf(GpsConfig.getMode(context)) }
    var debugMapEngine by remember { mutableStateOf(GpsConfig.getMapEngine(context)) }
    var debugSensorAssist by remember { mutableStateOf(GpsConfig.isSensorAssistGpsEnabled(context)) }
    var debugPredictUnlock by remember { mutableStateOf(GpsConfig.isSensorPredictUnlockEnabled(context)) }
    var debugTriggerDebug by remember { mutableStateOf(GpsConfig.isTriggerDebugEnabled(context)) }
    var debugShowTrackDots by remember { mutableStateOf(GpsConfig.isShowTrackDots(context)) }
    var debugInterpolationMode by remember { mutableStateOf(GpsConfig.getInterpolationMode(context)) }
    var debugInterpolationType by remember { mutableStateOf(GpsConfig.getInterpolationType(context)) }
    var debugSpeedColorMode by remember { mutableStateOf(GpsConfig.getSpeedColorMode(context)) }
    var debugFullSensorLog by remember { mutableStateOf(GpsConfig.isFullSensorLogEnabled(context)) }
    var debugAudioCues by remember { mutableStateOf(GpsConfig.isAudioCuesEnabled(context)) }
    var debug3DMapGyro by remember { mutableStateOf(GpsConfig.is3DMapGyroEnabled(context)) }

    var showCrashLogDialog by remember { mutableStateOf(false) }
    var crashLogText by remember { mutableStateOf(CrashLogger.getLastCrashLog(context)) }
    var crashLogTime by remember { mutableStateOf(CrashLogger.getLastCrashTime(context)) }
    var logTabMode by remember { mutableStateOf("CRASH") }
    var systemLogcatText by remember { mutableStateOf("") }

    var cityDropdownExpanded by remember { mutableStateOf(false) }
    var countryDropdownExpanded by remember { mutableStateOf(false) }

    // 國家與城市對應 Map (格式統一為 英文 (中文))
    val countryCityMap = remember {
        mapOf(
            "Taiwan (台灣)" to listOf(
                "Taichung (台中市)", "Taipei (台北市)", "New Taipei (新北市)", "Taoyuan (桃園市)",
                "Tainan (台南市)", "Kaohsiung (高雄市)", "Hsinchu (新竹市)", "Keelung (基隆市)",
                "Chiayi (嘉義市)", "Changhua (彰化縣)", "Nantou (南投縣)", "Yunlin (雲林縣)",
                "Pingtung (屏東縣)", "Yilan (宜蘭縣)", "Hualien (花蓮縣)", "Taitung (台東縣)",
                "Kinmen (金門縣)", "Penghu (澎湖縣)", "Lienchiang (連江縣/馬祖)"
            ),
            "Japan (日本)" to listOf(
                "Tokyo (東京)", "Osaka (大阪)", "Kyoto (京都)", "Yokohama (橫濱)",
                "Nagoya (名古屋)", "Sapporo (札幌)", "Fukuoka (福岡)", "Kobe (神戶)",
                "Hiroshima (廣島)", "Sendai (仙台)", "Gunma (群馬)"
            ),
            "United States (美國)" to listOf(
                "New York (紐約)", "Los Angeles (洛杉磯)", "San Francisco (舊金山)",
                "Chicago (芝加哥)", "Seattle (西雅圖)", "Houston (休士頓)",
                "Miami (邁阿密)", "Las Vegas (拉斯維加斯)", "Boston (波士頓)"
            ),
            "Hong Kong (香港)" to listOf(
                "Central (中環)", "Kowloon (九龍)", "Mong Kok (旺角)",
                "Tsim Sha Tsui (尖沙咀)", "Shatin (沙田)", "Tuen Mun (屯門)"
            ),
            "Singapore (新加坡)" to listOf(
                "Singapore (新加坡市)", "Jurong (裕廊)", "Woodlands (兀蘭)", "Tampines (淡濱尼)"
            ),
            "Malaysia (馬來西亞)" to listOf(
                "Kuala Lumpur (吉隆坡)", "Penang (檳城)", "Johor Bahru (新山)",
                "Ipoh (怡保)", "Melaka (馬六甲)"
            ),
            "Australia (澳洲)" to listOf(
                "Sydney (雪梨)", "Melbourne (墨爾本)", "Brisbane (布里斯本)",
                "Perth (伯斯)", "Adelaide (阿德雷德)"
            ),
            "United Kingdom (英國)" to listOf(
                "London (倫敦)", "Manchester (曼徹斯特)", "Birmingham (伯明罕)",
                "Edinburgh (愛丁堡)", "Glasgow (格拉斯哥)"
            ),
            "Canada (加拿大)" to listOf(
                "Toronto (多倫多)", "Vancouver (溫哥華)", "Montreal (蒙特婁)",
                "Calgary (卡加立)", "Ottawa (渥太華)"
            ),
            "Germany (德國)" to listOf(
                "Berlin (柏林)", "Munich (慕尼黑)", "Frankfurt (法蘭克福)",
                "Hamburg (漢堡)", "Stuttgart (斯圖加特)"
            ),
            "France (法國)" to listOf(
                "Paris (巴黎)", "Marseille (馬賽)", "Lyon (里昂)",
                "Nice (尼斯)", "Toulouse (土魯斯)"
            )
        )
    }

    val countries = remember(countryCityMap) { countryCityMap.keys.toList() }
    val currentAvailableCities = remember(editCountry, countryCityMap) {
        countryCityMap[editCountry] ?: countryCityMap["Taiwan (台灣)"] ?: emptyList()
    }
    val regionAvailableCities = remember(regionCountry, countryCityMap) {
        countryCityMap[regionCountry] ?: countryCityMap["Taiwan (台灣)"] ?: emptyList()
    }

    val decoupleNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return Offset(0f, available.y)
            }
        }
    }

    var allSessions by remember { mutableStateOf(TrackSessionManager.getAllSessions(context)) }
    val allTracks = remember { TrackRepository.getTracks(context) }

    val filteredSessions = remember(selectedCategory, allSessions) {
        allSessions.filter { session ->
            when (selectedCategory) {
                "CAR" -> session.vehicleType == "CAR"
                "MOTOR" -> session.vehicleType == "MOTOR"
                else -> session.vehicleType == "OTHER" || session.vehicleType.isBlank()
            }
        }
    }

    // 以「最近完成紀錄」排序賽道，且僅顯示有測試紀錄的賽道
    val sortedTracks = remember(filteredSessions, allTracks) {
        allTracks.filter { track ->
            filteredSessions.any {
                it.trackCode.contains(track.code, ignoreCase = true) ||
                it.trackName.contains(track.code, ignoreCase = true)
            }
        }.sortedWith(Comparator { a, b ->
            val latestA = filteredSessions
                .filter { it.trackCode.contains(a.code, ignoreCase = true) || it.trackName.contains(a.code, ignoreCase = true) }
                .maxOfOrNull { it.recordedAt }
            val latestB = filteredSessions
                .filter { it.trackCode.contains(b.code, ignoreCase = true) || it.trackName.contains(b.code, ignoreCase = true) }
                .maxOfOrNull { it.recordedAt }
            when {
                latestA == null && latestB == null -> 0
                latestA == null -> 1
                latestB == null -> -1
                else -> latestB.compareTo(latestA)
            }
        })
    }

    // 統計資料 (真實計算今日場次與本月場次)
    val totalSessions = allSessions.size
    val todayDateStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val monthDateStr = remember { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()) }

    val todaySessions = remember(allSessions, todayDateStr) {
        allSessions.count { it.recordedAt.startsWith(todayDateStr) }
    }
    val monthSessions = remember(allSessions, monthDateStr) {
        allSessions.count { it.recordedAt.startsWith(monthDateStr) }
    }

    val favoriteTrack = allSessions
        .groupBy { it.trackCode.substringBefore(" ").trim().ifBlank { it.trackName } }
        .maxByOrNull { it.value.size }?.key ?: "—"

    val avatarInitials = (currentUser?.nickname ?: "?").trim().uppercase().take(2)
    val avatarUrl = currentUser?.avatarUrl

    var avatarKey by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var isUploadingAvatar by remember { mutableStateOf(false) }
    var selectedCropUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val avatarPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            selectedCropUri = it
        }
    }

    selectedCropUri?.let { cropUri ->
        AvatarCropPreviewDialog(
            imageUri = cropUri,
            onDismiss = { selectedCropUri = null },
            onConfirm = { finalUri ->
                selectedCropUri = null
                isUploadingAvatar = true
                authViewModel.uploadAvatar(context, finalUri) { success, msg ->
                    isUploadingAvatar = false
                    if (success) {
                        avatarKey = System.currentTimeMillis()
                        authViewModel.checkSavedSession()
                        RevonToastManager.success("頭像已更新並上傳至伺服器！")
                    } else {
                        RevonToastManager.error(msg)
                    }
                }
            }
        )
    }

    val lazyListState = rememberLazyListState()
    val isAtTop by remember { derivedStateOf { lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0 } }
    val coroutineScope = rememberCoroutineScope()

    val infiniteTransition = rememberInfiniteTransition(label = "ArrowBounce")
    val arrowOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ArrowOffset"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 主佈局 ──
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // 1. 頁首
            item {
                ProfileHeaderSection(
                    avatarUrl = avatarUrl,
                    avatarInitials = avatarInitials,
                    nickname = currentUser?.nickname ?: "賽車手",
                    email = currentUser?.email ?: "driver@revon.io",
                    points = currentUser?.rPoints ?: 500,
                    clubName = userClub?.name,
                    isUploading = isUploadingAvatar,
                    onAvatarClick = {
                        avatarPickerLauncher.launch("image/*")
                    },
                    onEditClick = {
                        currentUser?.let { u ->
                            editNickname = u.nickname ?: "賽車手"
                            editRealName = u.realName ?: ""
                            editGender = if (u.gender.isNullOrBlank()) "male" else u.gender
                            editPhone = u.phone ?: ""
                            editBirthday = u.birthday ?: ""
                            editEmail = u.email ?: ""
                            editCountry = sanitizeCountry(u.country)
                            editCity = sanitizeCity(u.city)
                        }
                        showEditProfileDialog = true
                    }
                )
            }

            // 2. 我的成績 – 統計卡 + 類別切換
            item {
                SectionBlock(title = stringResource(R.string.profile_my_stats)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StatCard(modifier = Modifier.weight(1f), value = totalSessions.toString(), label = stringResource(R.string.profile_total_sessions), shape = CircleShape)
                        StatCard(modifier = Modifier.weight(1f), value = monthSessions.toString(), label = stringResource(R.string.profile_monthly_sessions), shape = CircleShape)
                        StatCard(modifier = Modifier.weight(1f), value = todaySessions.toString(), label = stringResource(R.string.profile_today_sessions), shape = CircleShape)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "CAR" to stringResource(R.string.profile_car),
                            "MOTOR" to stringResource(R.string.profile_scooter),
                            "OTHER" to stringResource(R.string.profile_all)
                        ).forEach { (key, label) ->
                            val isSelected = selectedCategory == key
                            Surface(
                                modifier = Modifier.weight(1f).height(42.dp).clickable {
                                    selectedCategory = key
                                    vehiclePrefs.edit().putString("KEY_VEHICLE_TYPE", key).apply()
                                },
                                shape = CircleShape,
                                color = if (isSelected) RedPrimary else DarkSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) RedPrimary else DividerColor)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(label, color = if (isSelected) Color.White else TextGray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

        // 3. 路線細項對話框或展開列表
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SectionBlock(title = stringResource(R.string.profile_my_records)) {
                if (sortedTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.profile_no_records), color = TextGray, fontSize = 13.sp)
                    }
                } else {
                    val decoupleNestedScrollConnection = remember {
                        object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                return Offset.Zero
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(decoupleNestedScrollConnection)
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(sortedTracks) { _, track ->
                                val trackRecords = filteredSessions.filter {
                                    it.trackCode.contains(track.code, ignoreCase = true) ||
                                    it.trackName.contains(track.code, ignoreCase = true)
                                }
                                val bestLap = trackRecords.minByOrNull { it.lapTimeMs }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(DarkSurface)
                                        .border(1.dp, DividerColor, RoundedCornerShape(10.dp))
                                        .clickable { selectedModalTrack = track.code }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(track.code, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                        Text(stringResource(R.string.profile_record_count_fmt, trackRecords.size), color = TextGray, fontSize = 11.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (bestLap != null) {
                                            Text(
                                                text = format3Decimals(bestLap.lapTimeDisplay, bestLap.lapTimeMs),
                                                color = NeonGreen,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                        } else {
                                            Text("尚未測試", color = TextGray, fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. 我的區域 (無須展開，直接列出國家與城市兩個欄位)
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SectionBlock(title = stringResource(R.string.profile_my_region)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 1. 國家選單
                    Column {
                        Text(stringResource(R.string.profile_country), color = TextGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { regionCountryExpanded = true },
                                shape = RoundedCornerShape(8.dp),
                                color = DarkBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(regionCountry, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = regionCountryExpanded,
                                onDismissRequest = { regionCountryExpanded = false },
                                modifier = Modifier.heightIn(max = 240.dp)
                            ) {
                                countries.forEach { ctyName ->
                                    DropdownMenuItem(
                                        text = { Text(ctyName) },
                                        onClick = {
                                            regionCountry = ctyName
                                            val newCity = countryCityMap[ctyName]?.firstOrNull() ?: "台中市"
                                            regionCity = newCity
                                            regionCountryExpanded = false
                                            val oldNick = currentUser?.nickname ?: "賽車手"
                                            val current = currentUser
                                            authViewModel.updateFullProfile(
                                                nickname = oldNick,
                                                realName = current?.realName ?: "",
                                                gender = current?.gender ?: "other",
                                                phone = current?.phone ?: "",
                                                birthday = current?.birthday ?: "",
                                                email = current?.email ?: "",
                                                country = ctyName,
                                                city = newCity
                                            )
                                            RevonToastManager.success("區域已更新 ($ctyName · $newCity)")
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 2. 城市選單
                    Column {
                        Text("城市", color = TextGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { regionCityExpanded = true },
                                shape = RoundedCornerShape(8.dp),
                                color = DarkBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(regionCity, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = regionCityExpanded,
                                onDismissRequest = { regionCityExpanded = false },
                                modifier = Modifier.heightIn(max = 240.dp)
                            ) {
                                regionAvailableCities.forEach { cName ->
                                    DropdownMenuItem(
                                        text = { Text(cName) },
                                        onClick = {
                                            regionCity = cName
                                            regionCityExpanded = false
                                            val oldNick = currentUser?.nickname ?: "賽車手"
                                            val current = currentUser
                                            authViewModel.updateFullProfile(
                                                nickname = oldNick,
                                                realName = current?.realName ?: "",
                                                gender = current?.gender ?: "other",
                                                phone = current?.phone ?: "",
                                                birthday = current?.birthday ?: "",
                                                email = current?.email ?: "",
                                                country = regionCountry,
                                                city = cName
                                            )
                                            RevonToastManager.success("區域已更新 ($regionCountry · $cName)")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. 帳號設定
        item {
            SectionBlock(title = "帳號") {
                MenuRow(label = "編輯個人資料", icon = Icons.Default.ManageAccounts) {
                    currentUser?.let { u ->
                        editNickname = u.nickname ?: "賽車手"
                        editRealName = u.realName ?: ""
                        editGender   = if (u.gender.isNullOrBlank()) "male" else u.gender
                        editPhone    = u.phone ?: ""
                        editBirthday = u.birthday ?: ""
                        editEmail    = u.email ?: ""
                        editCountry  = sanitizeCountry(u.country)
                        editCity     = sanitizeCity(u.city)
                    }
                    showEditProfileDialog = true
                }
                HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                MenuRow(label = "修改密碼", icon = Icons.Default.Key) {
                    val userEmail = currentUser?.email ?: ""
                    if (userEmail.isNotBlank()) {
                        showChangePasswordDialog = true
                        RevonToastManager.info("正在發送重設密碼驗證碼至信箱...")
                        authViewModel.sendPasswordReset(userEmail) { success, msg ->
                            if (success) {
                                RevonToastManager.success("驗證碼已發送至信箱：$userEmail")
                            } else {
                                RevonToastManager.error(msg)
                            }
                        }
                    } else {
                        RevonToastManager.error("請先設定或綁定 Email 電子信箱")
                    }
                }
            }
        }

        // 語言 (Language Selection: EN / 中)
        item {
            val currentLang by io.revon.app.data.config.LanguageManager.currentLanguage.collectAsState()
            val isEn = currentLang == io.revon.app.data.config.AppLanguage.EN

            SectionBlock(title = if (isEn) "Language" else "語言") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEn) "App Interface Language" else "應用程式介面語言",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // EN Option
                        Surface(
                            onClick = {
                                io.revon.app.data.config.LanguageManager.setLanguage(context, io.revon.app.data.config.AppLanguage.EN)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isEn) RedPrimary else DarkBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isEn) RedPrimary else DividerColor)
                        ) {
                            Text(
                                text = "EN",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        // 中 Option
                        Surface(
                            onClick = {
                                io.revon.app.data.config.LanguageManager.setLanguage(context, io.revon.app.data.config.AppLanguage.ZH)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (!isEn) RedPrimary else DarkBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (!isEn) RedPrimary else DividerColor)
                        ) {
                            Text(
                                text = "中",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // 6. 支援與法律
        item {
            val currentLang by io.revon.app.data.config.LanguageManager.currentLanguage.collectAsState()
            val isEn = currentLang == io.revon.app.data.config.AppLanguage.EN

            SectionBlock(title = if (isEn) "Support & Legal" else "支援與法律") {
                MenuRow(label = if (isEn) "Privacy Policy" else "隱私權政策") {
                    showPrivacyPolicyDialog = true
                }
                HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                MenuRow(label = if (isEn) "Terms of Service" else "服務條款") {
                    showTermsOfServiceDialog = true
                }
                HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                MenuRow(label = if (isEn) "Refund Policy" else "退換貨與取消訂閱政策") {
                    showRefundPolicyDialog = true
                }
                HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                MenuRow(label = if (isEn) "Contact Support" else "聯絡客服") {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lin.ee/vtUgRyie"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        RevonToastManager.error("無法開啟客服連結: https://lin.ee/vtUgRyie")
                    }
                }
            }
        }

        // 7. 開發者偵錯選項 (預設收起，記得切換狀態，已移除 60FPS 與過門判定算法)
        item {
            SectionBlock(title = "開發者偵錯選項") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val nextState = !showDebugSection
                            showDebugSection = nextState
                            profilePrefs.edit().putBoolean("KEY_SHOW_DEBUG_SECTION", nextState).apply()
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showDebugSection) "點擊收起偵錯選項" else "點擊展開進階偵錯與感測器選項",
                        color = TextGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = if (showDebugSection) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = TextGray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (showDebugSection) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // 0. 地圖 API 引擎切換
                    DebugDropdownRow(
                        label = "地圖 API 引擎切換",
                        options = listOf("OsmDroid 開源地圖 (OSMDROID)", "Google Maps API (GOOGLE)", "3D Google Maps API (3D_GOOGLE)"),
                        selectedIndex = debugMapEngine,
                        onSelect = { idx ->
                            debugMapEngine = idx
                            GpsConfig.setMapEngine(context, idx)
                        }
                    )
                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)

                    // 2. IMU 陀螺儀輔助 GPS 定位
                    DebugSwitchRow(
                        label = "IMU 陀螺儀輔助 GPS 定位",
                        subtitle = "結合加速度計與姿態角推算高頻航跡",
                        checked = debugSensorAssist,
                        onCheckedChange = { chk ->
                            debugSensorAssist = chk
                            GpsConfig.setSensorAssistGpsEnabled(context, chk)
                        }
                    )
                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)

                    // 3. 全區陀螺儀航跡推算與預測點
                    DebugSwitchRow(
                        label = "全區陀螺儀航跡推算與預測點",
                        subtitle = "在地圖上即時顯示黃色高頻航跡預測標記",
                        checked = debugPredictUnlock,
                        onCheckedChange = { chk ->
                            debugPredictUnlock = chk
                            GpsConfig.setSensorPredictUnlockEnabled(context, chk)
                        }
                    )
                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)

                    // 4. 虛擬閘門過線偵錯與日誌
                    DebugSwitchRow(
                        label = "虛擬閘門過線偵錯與日誌",
                        subtitle = "在競速畫面上方顯示起終點過線向量日誌",
                        checked = debugTriggerDebug,
                        onCheckedChange = { chk ->
                            debugTriggerDebug = chk
                            GpsConfig.setTriggerDebugEnabled(context, chk)
                        }
                    )
                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)

                    // 4.5. 顯示 GPS 紀錄點位 (顯示點位)
                    DebugSwitchRow(
                        label = "顯示 GPS 紀錄點位 (顯示點位)",
                        subtitle = "在紀錄回顧與地圖畫面上標示所有原始 GPS 採樣點位",
                        checked = debugShowTrackDots,
                        onCheckedChange = { chk ->
                            debugShowTrackDots = chk
                            GpsConfig.setShowTrackDots(context, chk)
                        }
                    )
                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)

                    // 5. 軌跡內插頻率提升
                    DebugDropdownRow(
                        label = "軌跡內插頻率提升",
                        options = listOf("關閉內插 (OFF)", "+10Hz 內插 (2+1)", "+20Hz 內插 (2+2)"),
                        selectedIndex = debugInterpolationMode,
                        onSelect = { idx ->
                            debugInterpolationMode = idx
                            GpsConfig.setInterpolationMode(context, idx)
                        }
                    )
                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)

                    // 6. 軌跡內插演算法
                    DebugDropdownRow(
                        label = "軌跡內插演算法",
                        options = listOf("線性內插 (LINEAR)", "曲線 Spline (CURVE)"),
                        selectedIndex = debugInterpolationType,
                        onSelect = { idx ->
                            debugInterpolationType = idx
                            GpsConfig.setInterpolationType(context, idx)
                        }
                    )
                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)

                    // 7. 車速熱力圖色彩分級
                    DebugDropdownRow(
                        label = "車速熱力圖色彩分級",
                        options = listOf("固定速域 (120 km/h)", "動態極限縮放 (DYNAMIC)"),
                        selectedIndex = debugSpeedColorMode,
                        onSelect = { idx ->
                            debugSpeedColorMode = idx
                            GpsConfig.setSpeedColorMode(context, idx)
                        }
                    )
                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)

                    // 8. 全感測器細節紀錄 (Full Sensor Log)
                    DebugSwitchRow(
                        label = "全感測器細節紀錄 (Full Sensor Log)",
                        subtitle = "匯出包含陀螺儀與三軸加速度完整點集",
                        checked = debugFullSensorLog,
                        onCheckedChange = { chk ->
                            debugFullSensorLog = chk
                            GpsConfig.setFullSensorLogEnabled(context, chk)
                        }
                    )
                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)

                    // 9. 計時語音提示 (Audio Cues)
                    DebugSwitchRow(
                        label = "計時語音提示",
                        subtitle = "起跑、通過起終點與刷新個人紀錄時進行語音提示播報",
                        checked = debugAudioCues,
                        onCheckedChange = { chk ->
                            debugAudioCues = chk
                            GpsConfig.setAudioCuesEnabled(context, chk)
                        }
                    )
                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)

                    // 10. 校正重力感應器基準線
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("校正重力感應器基準線", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("將手機固定於車架上時手動歸零基準線", color = TextGray, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        Button(
                            onClick = {
                                val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
                                val accel = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
                                if (sensorManager != null && accel != null) {
                                    val listener = object : android.hardware.SensorEventListener {
                                        override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                                            if (event?.sensor?.type == android.hardware.Sensor.TYPE_ACCELEROMETER) {
                                                val baseline = floatArrayOf(event.values[0], event.values[1], event.values[2])
                                                GpsConfig.saveGravityBaseline(context, baseline)
                                                sensorManager.unregisterListener(this)
                                                io.revon.app.ui.components.RevonToastManager.success("水平傾角基準已校正！")
                                            }
                                        }
                                        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
                                    }
                                    sensorManager.registerListener(listener, accel, android.hardware.SensorManager.SENSOR_DELAY_UI)
                                } else {
                                    GpsConfig.saveGravityBaseline(context, floatArrayOf(0f, 0f, 9.80665f))
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("校正歸零", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)

                    // 11. 3D地圖陀螺儀
                    DebugSwitchRow(
                        label = "3D地圖陀螺儀",
                        subtitle = "開啟後 <5km/h 才會利用手機角度轉動地圖，否則保持導航方向視角鎖定",
                        checked = debug3DMapGyro,
                        onCheckedChange = { chk ->
                            debug3DMapGyro = chk
                            GpsConfig.set3DMapGyroEnabled(context, chk)
                        }
                    )

                    HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)

                    // 11. 閃退訊息紀錄與偵錯
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("閃退與系統日誌紀錄", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (!crashLogText.isNullOrEmpty()) "最新閃退: ${crashLogTime ?: "未知"}" else "目前無閃退 (含即時 Logcat)",
                                    color = if (!crashLogText.isNullOrEmpty()) RedPrimary else TextGray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = {
                                        crashLogText = CrashLogger.getLastCrashLog(context)
                                        crashLogTime = CrashLogger.getLastCrashTime(context)
                                        systemLogcatText = CrashLogger.getSystemLogcat(150)
                                        showCrashLogDialog = true
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary.copy(alpha = 0.85f)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.BugReport, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("查看日誌", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        crashLogText = CrashLogger.getLastCrashLog(context)
                                        CrashLogger.copyCrashLogToClipboard(context, crashLogText)
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("複製閃退", color = NeonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    CrashLogger.clearCrashLog(context)
                                    crashLogText = null
                                    crashLogTime = null
                                    RevonToastManager.info("紀錄已清除")
                                },
                                modifier = Modifier.height(34.dp),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TextGray.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text("清除紀錄", color = TextGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        // 8. 登出按鈕（紅色外框輪廓）
        item {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    authViewModel.logout()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, RedPrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RedPrimary)
            ) {
                Text("登出", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = RedPrimary)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

        // Bouncing Gold Arrow at the bottom of the screen (above bottom nav bar)
        androidx.compose.animation.AnimatedVisibility(
            visible = isAtTop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(3)
                    }
                },
                modifier = Modifier
                    .offset(y = arrowOffsetY.dp)
                    .size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "往下滾動提示",
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }

    // ── 賽道成績詳情 Dialog ──
    val trackCodeModal = selectedModalTrack
    if (trackCodeModal != null) {
        val rawRecords = filteredSessions.filter {
            it.trackCode.contains(trackCodeModal, ignoreCase = true) ||
            it.trackName.contains(trackCodeModal, ignoreCase = true)
        }
        val recordsForTrack = remember(modalSortMode, isAscending, rawRecords) {
            if (modalSortMode == "TIME") {
                if (isAscending) rawRecords.sortedBy { it.lapTimeMs } else rawRecords.sortedByDescending { it.lapTimeMs }
            } else {
                if (isAscending) rawRecords.sortedBy { it.recordedAt } else rawRecords.sortedByDescending { it.recordedAt }
            }
        }

        Dialog(onDismissRequest = { selectedModalTrack = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1C1C1E),
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$trackCodeModal 賽道成績", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        IconButton(onClick = { selectedModalTrack = null }) {
                            Icon(Icons.Default.Close, contentDescription = "關閉", tint = TextGray)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isDate = modalSortMode == "DATE"
                        Surface(
                            modifier = Modifier.weight(1f).clickable {
                                modalSortMode = "DATE"
                                isAscending = false // 最新紀錄預設：最新成績在上往下排序 (降序)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDate) RedPrimary else Color(0xFF2C2C2E),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDate) RedPrimary else Color.DarkGray)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                Text("最新紀錄", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        val isTime = modalSortMode == "TIME"
                        Surface(
                            modifier = Modifier.weight(1f).clickable {
                                modalSortMode = "TIME"
                                isAscending = true // 最佳秒數預設：最快紀錄在上排序下去 (升序)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isTime) RedPrimary else Color(0xFF2C2C2E),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isTime) RedPrimary else Color.DarkGray)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                Text("最佳秒數", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(
                            onClick = { isAscending = !isAscending },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isAscending) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = if (isAscending) "升序" else "降序",
                                tint = NeonGreen,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (recordsForTrack.isEmpty()) {
                        Text("您目前沒有任何該賽道的成績", color = TextGray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 20.dp))
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.heightIn(max = 360.dp)
                        ) {
                            itemsIndexed(recordsForTrack) { index, rec ->
                                val isFastest = rec.lapTimeMs == rawRecords.minOfOrNull { it.lapTimeMs }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isFastest) Color(0xFFFFD700).copy(alpha = 0.12f) else Color(0xFF2C2C2E),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isFastest) Color(0xFFFFD700) else Color.Transparent),
                                    modifier = Modifier.combinedClickable(
                                        onClick = { selectedModalTrack = null; onNavigateToSessionDetail(rec.sessionId) },
                                        onLongClick = { sessionOptionsRecord = rec }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("#${index + 1}", color = if (isFastest) Color(0xFFFFD700) else TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(rec.recordedAt, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Text("最高時速 ${rec.maxSpeedKmh.toInt()} km/h", color = TextGray, fontSize = 11.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = format3Decimals(rec.lapTimeDisplay, rec.lapTimeMs),
                                                color = if (isFastest) Color(0xFFFFD700) else NeonGreen,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                            if (isFastest) Text("★ BEST LAP", color = Color(0xFFFFD700), fontSize = 10.sp, fontWeight = FontWeight.Black)
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

    // ── 成績檔長按選單 Dialog (分享 JSON / 刪除) ──
    val optionsRec = sessionOptionsRecord
    if (optionsRec != null) {
        AlertDialog(
            onDismissRequest = { sessionOptionsRecord = null },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "成績紀錄功能選單",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "對象：${optionsRec.trackName} (${optionsRec.lapTimeDisplay})",
                        color = TextGray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // 按鈕 1: 重新上傳成績
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val target = optionsRec
                                sessionOptionsRecord = null
                                io.revon.app.data.repository.PendingUploadManager.enqueuePendingUpload(context, target)
                                io.revon.app.data.repository.PendingUploadManager.triggerAutoSync(context)
                                RevonToastManager.info("已觸發重新上傳程序")
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2C2C2E)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF00E5FF))
                            Column {
                                Text("重新上傳成績", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("立即手動補傳此筆成績至伺服器資料庫", color = TextGray, fontSize = 11.sp)
                            }
                        }
                    }

                    // 按鈕 2: 分享 / 導出 JSON 原始檔
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                shareSessionJson(context, optionsRec)
                                sessionOptionsRecord = null
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2C2C2E)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = NeonGreen)
                            Column {
                                Text("分享 / 導出 JSON 原始檔", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("包含 GPS 點位、時速與感測器紀錄數據", color = TextGray, fontSize = 11.sp)
                            }
                        }
                    }

                    // 按鈕 2: 刪除紀錄
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val target = optionsRec
                                sessionOptionsRecord = null
                                sessionToDelete = target
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2C2C2E)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = RedPrimary)
                            Column {
                                Text("刪除成績紀錄", color = RedPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("自本機快取與資料庫中徹底移除", color = TextGray, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { sessionOptionsRecord = null }) {
                    Text("取消", color = TextGray)
                }
            }
        )
    }

    // ── 編輯個人資料 Dialog (統一 Revon 登入/註冊高質感賽車風格) ──
    if (showEditProfileDialog) {
        val isNicknameValid = editNickname.trim().isNotEmpty()
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Rev-On", color = RedPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("編輯個人資料", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(scrollState)
                        .padding(top = 4.dp)
                ) {
                    // 頭像上傳區域
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF16161B))
                                .border(2.dp, RedPrimary, CircleShape)
                                .clickable { avatarPickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!avatarUrl.isNullOrBlank()) {
                                val formattedPath = if (avatarUrl.startsWith("/")) avatarUrl else "/$avatarUrl"
                                val fullUrl = if (avatarUrl.startsWith("http")) avatarUrl else "https://revon88.synology.me/revon_android${formattedPath}"
                                val avatarUrlWithTs = if (fullUrl.contains("?")) "$fullUrl&t=$avatarKey" else "$fullUrl?t=$avatarKey"
                                coil.compose.AsyncImage(
                                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                                        .data(avatarUrlWithTs)
                                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "用戶頭像預覽",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = avatarInitials,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "📷 點擊更換 / 上傳頭像",
                            color = RedPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { avatarPickerLauncher.launch("image/*") }
                        )
                    }
                    // 暱稱
                    Column {
                        OutlinedTextField(
                            value = editNickname,
                            onValueChange = { editNickname = it },
                            label = { Text("車手暱稱 (Nickname)", color = Color.Gray) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = RedPrimary) },
                            singleLine = true,
                            isError = !isNicknameValid,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RedPrimary, unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            )
                        )
                        if (!isNicknameValid) {
                            Text("暱稱不能空白", color = RedPrimary, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                        }
                    }

                    // 真實姓名
                    OutlinedTextField(
                        value = editRealName,
                        onValueChange = { editRealName = it },
                        label = { Text("真實姓名 (Real Name)", color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = RedPrimary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RedPrimary, unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        )
                    )

                    // Email
                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("電子郵件 (Email)", color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = RedPrimary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RedPrimary, unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        )
                    )

                    // 性別 (選單)
                    val genderLabel = when (editGender) {
                        "male" -> "男 (Male)"
                        "female" -> "女 (Female)"
                        else -> "不透漏 (Other)"
                    }
                    Box(modifier = Modifier.fillMaxWidth().clickable { editGenderExpanded = true }) {
                        OutlinedTextField(
                            value = genderLabel, onValueChange = {}, readOnly = true, enabled = false,
                            label = { Text("性別 (Gender)", color = Color.Gray) },
                            leadingIcon = { Icon(Icons.Default.Wc, contentDescription = null, tint = RedPrimary) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White) },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = Color.White, disabledBorderColor = Color.DarkGray,
                                disabledLabelColor = Color.Gray, disabledLeadingIconColor = RedPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = editGenderExpanded, onDismissRequest = { editGenderExpanded = false }) {
                            DropdownMenuItem(text = { Text("男 (Male)") }, onClick = { editGender = "male"; editGenderExpanded = false })
                            DropdownMenuItem(text = { Text("女 (Female)") }, onClick = { editGender = "female"; editGenderExpanded = false })
                            DropdownMenuItem(text = { Text("不透漏 (Other)") }, onClick = { editGender = "other"; editGenderExpanded = false })
                        }
                    }

                    // 電話
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("聯絡電話 (Phone)", color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = RedPrimary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RedPrimary, unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        )
                    )

                    // 出生年月日
                    Box(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val calendar = java.util.Calendar.getInstance()
                            val year = calendar.get(java.util.Calendar.YEAR) - 20
                            val month = calendar.get(java.util.Calendar.MONTH)
                            val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                            val datePicker = android.app.DatePickerDialog(context, { _, y, m, d ->
                                editBirthday = String.format("%04d-%02d-%02d", y, m + 1, d)
                            }, year, month, day)
                            datePicker.show()
                        }
                    ) {
                        OutlinedTextField(
                            value = editBirthday, onValueChange = {}, readOnly = true, enabled = false,
                            label = { Text("出生年月日 (Birthday)", color = Color.Gray) },
                            placeholder = { Text("點擊選擇日期", color = Color.Gray) },
                            leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = RedPrimary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = Color.White, disabledBorderColor = Color.DarkGray,
                                disabledLabelColor = Color.Gray, disabledLeadingIconColor = RedPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = isNicknameValid,
                    onClick = {
                        if (isNicknameValid) {
                            val oldNick = currentUser?.nickname ?: "賽車手"
                            val newNick = editNickname.trim()
                            authViewModel.updateFullProfile(
                                nickname = newNick,
                                realName = editRealName.trim(),
                                gender = editGender,
                                phone = editPhone.trim(),
                                birthday = editBirthday.trim(),
                                email = editEmail.trim(),
                                country = editCountry,
                                city = editCity
                            ) { success, msg ->
                                if (success) RevonToastManager.success(msg) else RevonToastManager.error(msg)
                            }
                            chatViewModel?.updateCaptainNickname(oldNick, newNick)
                            showEditProfileDialog = false
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) { Text("儲 存 更 新", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditProfileDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("取消", color = TextGray) }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = DarkSurface,
            modifier = Modifier.border(1.dp, RedPrimary.copy(alpha = 0.5f), shape = RoundedCornerShape(20.dp))
        )
    }

    // ── 修改密碼 Dialog (統一 Revon 登入/註冊高質感賽車風格，結合 6 位數驗證碼與綠色打勾驗證) ──
    if (showChangePasswordDialog) {
        var resetCode by remember { mutableStateOf("") }
        var newPasswordVisible by remember { mutableStateOf(false) }
        var confirmPasswordVisible by remember { mutableStateOf(false) }

        val isCodeValid = resetCode.trim().length == 6
        val isNewPasswordValid = newPassword.length >= 6
        val isPasswordMatch = isNewPasswordValid && confirmNewPassword.isNotEmpty() && newPassword == confirmNewPassword

        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Rev-On", color = RedPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("修改密碼", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text("已發送驗證碼至您的信箱 (${currentUser?.email ?: ""})，請輸入 6 位數驗證碼及新密碼：", color = Color.Gray, fontSize = 12.sp)

                    // 6 位數驗證碼 (不顯示打勾，僅一般紅黑邊框輸入)
                    Column {
                        OutlinedTextField(
                            value = resetCode,
                            onValueChange = { resetCode = it.take(6) },
                            label = { Text("6 位數驗證碼", color = Color.Gray) },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = RedPrimary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RedPrimary, unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            )
                        )
                    }

                    // 新密碼 (打了先不打勾，當確認新密碼也輸入且兩者一致時同時打勾)
                    Column {
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("新密碼 (至少6位)", color = Color.Gray) },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = RedPrimary) },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (confirmNewPassword.isNotEmpty() && newPassword.isNotEmpty()) {
                                        if (isPasswordMatch) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "密碼一致", tint = Color(0xFF00C853))
                                        } else {
                                            Icon(
                                                Icons.Default.Cancel,
                                                contentDescription = "密碼不一致",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                        Icon(
                                            imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            },
                            visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (confirmNewPassword.isNotEmpty() && newPassword.isNotEmpty()) {
                                    if (isPasswordMatch) Color(0xFF00C853) else MaterialTheme.colorScheme.error
                                } else RedPrimary,
                                unfocusedBorderColor = if (confirmNewPassword.isNotEmpty() && newPassword.isNotEmpty()) {
                                    if (isPasswordMatch) Color(0xFF00C853) else MaterialTheme.colorScheme.error
                                } else Color.DarkGray,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            )
                        )
                        if (newPassword.isNotEmpty() && !isNewPasswordValid) {
                            Text("密碼至少需要 6 個字元", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                        }
                    }

                    // 確認新密碼 (當確認新密碼打完且兩者一致時同時打勾)
                    Column {
                        OutlinedTextField(
                            value = confirmNewPassword,
                            onValueChange = { confirmNewPassword = it },
                            label = { Text("確認新密碼", color = Color.Gray) },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = RedPrimary) },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (confirmNewPassword.isNotEmpty() && newPassword.isNotEmpty()) {
                                        if (isPasswordMatch) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "密碼一致", tint = Color(0xFF00C853))
                                        } else {
                                            Icon(
                                                Icons.Default.Cancel,
                                                contentDescription = "點擊清空確認密碼",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.clickable { confirmNewPassword = "" }
                                            )
                                        }
                                    }
                                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                        Icon(
                                            imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            },
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (confirmNewPassword.isNotEmpty() && newPassword.isNotEmpty()) {
                                    if (isPasswordMatch) Color(0xFF00C853) else MaterialTheme.colorScheme.error
                                } else RedPrimary,
                                unfocusedBorderColor = if (confirmNewPassword.isNotEmpty() && newPassword.isNotEmpty()) {
                                    if (isPasswordMatch) Color(0xFF00C853) else MaterialTheme.colorScheme.error
                                } else Color.DarkGray,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            )
                        )
                        if (confirmNewPassword.isNotEmpty() && !isPasswordMatch) {
                            Text("兩次密碼輸入不一致", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = isCodeValid && isPasswordMatch,
                    onClick = {
                        val userEmail = currentUser?.email ?: ""
                        authViewModel.resetPasswordWithCode(userEmail, resetCode, newPassword) { success, msg ->
                            if (success) {
                                showChangePasswordDialog = false
                                newPassword = ""
                                confirmNewPassword = ""
                                resetCode = ""
                                RevonToastManager.success("密碼修改成功，請重新登入。")
                                authViewModel.logout()
                            } else {
                                RevonToastManager.error(msg)
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) { Text("儲 存 密 碼", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showChangePasswordDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("取消", color = TextGray) }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = DarkSurface,
            modifier = Modifier.border(1.dp, RedPrimary.copy(alpha = 0.5f), shape = RoundedCornerShape(20.dp))
        )
    }

    // ── 刪除成績 Dialog ──
    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            containerColor = DarkSurface,
            title = { Text("刪除成績紀錄", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("確認要刪除此筆測試紀錄（${sessionToDelete?.lapTimeDisplay} · ${sessionToDelete?.recordedAt}）嗎？此操作無法復原。", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        val id = sessionToDelete?.sessionId ?: ""
                        if (id.isNotBlank()) {
                            TrackSessionManager.deleteSession(context, id)
                            allSessions = TrackSessionManager.getAllSessions(context)
                        }
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) { Text("確認刪除", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { sessionToDelete = null }) { Text("取消", color = TextGray) } }
        )
    }

    // ── 閃退與系統日誌查看與複製 Dialog ──
    if (showCrashLogDialog) {
        Dialog(onDismissRequest = { showCrashLogDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1C1C1E),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BugReport, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("系統偵錯與閃退日誌", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { showCrashLogDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "關閉", tint = TextGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tab 切換按鈕 (閃退 StackTrace vs 即時 Logcat)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isCrashTab = logTabMode == "CRASH"
                        Surface(
                            modifier = Modifier.weight(1f).clickable { logTabMode = "CRASH" },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCrashTab) RedPrimary else Color(0xFF2C2C2E),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isCrashTab) RedPrimary else Color.DarkGray)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text("閃退 StackTrace", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        val isLogcatTab = logTabMode == "LOGCAT"
                        Surface(
                            modifier = Modifier.weight(1f).clickable {
                                logTabMode = "LOGCAT"
                                systemLogcatText = CrashLogger.getSystemLogcat(150)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isLogcatTab) Color(0xFF00E5FF).copy(alpha = 0.3f) else Color(0xFF2C2C2E),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isLogcatTab) Color(0xFF00E5FF) else Color.DarkGray)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text("即時 Logcat 日誌", color = if (isLogcatTab) Color(0xFF00E5FF) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val activeLogText = if (logTabMode == "CRASH") crashLogText else systemLogcatText
                    if (activeLogText.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(DarkBackground, RoundedCornerShape(8.dp))
                                .border(1.dp, DividerColor, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (logTabMode == "CRASH") "目前尚未記錄到任何系統閃退紀錄" else "無即時 Logcat 系統日誌",
                                color = TextGray,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF101014),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                        ) {
                            LazyColumn(modifier = Modifier.padding(12.dp)) {
                                item {
                                    Text(
                                        text = activeLogText,
                                        color = if (logTabMode == "CRASH") Color(0xFF00FF66) else Color(0xFF00E5FF),
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (logTabMode == "CRASH") {
                                        CrashLogger.copyCrashLogToClipboard(context, crashLogText)
                                    } else {
                                        CrashLogger.copyCrashLogToClipboard(context, systemLogcatText)
                                    }
                                },
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("複製當前頁", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (logTabMode == "CRASH") {
                                        CrashLogger.clearCrashLog(context)
                                        crashLogText = null
                                        crashLogTime = null
                                        RevonToastManager.info("已清除閃退紀錄")
                                    } else {
                                        CrashLogger.clearSystemLogcat()
                                        systemLogcatText = ""
                                        RevonToastManager.info("已清除系統 Logcat 緩衝")
                                    }
                                },
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("清除當前頁", color = RedPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    CrashLogger.copyFullLogsToClipboard(context)
                                },
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF).copy(alpha = 0.18f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("複製全部 Log", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    CrashLogger.clearAllLogs(context)
                                    crashLogText = null
                                    crashLogTime = null
                                    systemLogcatText = ""
                                    RevonToastManager.info("已清空所有閃退與系統日誌")
                                },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RedPrimary.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("一鍵清空所有日誌", color = RedPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { showCrashLogDialog = false },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("關閉", color = TextGray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // 8. 隱私權保護政策 彈出卡片視窗
    if (showPrivacyPolicyDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPrivacyPolicyDialog = false },
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
                    Text("隱私權保護政策", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)

                    IconButton(onClick = { showPrivacyPolicyDialog = false }) {
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
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = """
親愛的使用者/客戶，您的個人資料及隱私權益，璟韻行銷有限公司(以下簡稱「本公司」)所經營之REV-ON(以下簡稱「我們」)絕對尊重及保護。為了幫助您瞭解我們如何蒐集、處理、利用及保護您的個人資料，請您詳閱下列隱私權保護政策(以下簡稱本政策)內容：

一、適用範圍：
(一). 本政策內容包括我們如何蒐集、處理及利用使用者/客戶因使用我們的網站/應用程式(以下簡稱「APP」)、服務、參加活動、加入會員、申辦服務及線上購物等所提供的個人資料。
(二). 我們的所有分公司、營運據點、子公司、供應商或合作伙伴(包括但不限於商業組織、政府機關等)或關係企業受我們委託或與我們合作時，如涉及蒐集、處理或利用個人資料者，我們亦將依本政策規定辦理。
(三). 除前項情形外，本政策不適用於我們以外之機構，也不適用於非我們所僱用或管理的人員。

二、資料之蒐集：
我們在您使用我們網站/APP、加入會員、申辦各項服務、線上購物、瀏覽網頁、參加宣傳活動或訂購產品/服務時，依實際情況將請您提供個人相關資料（包括姓名、行動電話、電子郵件、位置資訊、使用紀錄等）。

三、資料之利用：
我們對於您的個人資料，將依我們蒐集時所闡述之特定目的及相關法令規定之範圍內使用。除非取得您的同意或依其他法令特別規定，我們絕不會將您的個人資料揭露予第三人。

四、資料之保護：
我們將以嚴密的保護措施保護您的個人資料，資通安全防護符合相關主管機關要求。請妥善保管您的密碼與個人資料，避免外洩。

五、Cookie之運用：
我們將使用Cookie以紀錄及分析使用者行為，優化您的使用體驗。

六、與第三者共用個人資料之情形：
我們絕對不會任意交換、出租或以其他變相之方式將您的個人資料揭露予第三人，除非配合司法單位或政府機關依法調查。

七、隱私權保護政策之修正及諮詢：
客服LINE: @800rmqxy
客服信箱: revon7788@gmail.com
                            """.trimIndent(),
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { showPrivacyPolicyDialog = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("我 已 瞭 解", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // 9. 服務使用條款 彈出卡片視窗
    if (showTermsOfServiceDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showTermsOfServiceDialog = false },
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
                    Text("服務使用條款", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)

                    IconButton(onClick = { showTermsOfServiceDialog = false }) {
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
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = """
REV-ON網站及APP是由璟韻行銷股份有限公司（下稱本公司）所建置。本公司係依照本網站服務使用條款（以下簡稱「本條款」），提供本公司用戶及一般公眾使用各項網路資訊服務。當您已開始使用本服務時，視為已確實閱讀、瞭解並同意遵守本條款。

一、服務內容及基本規範
(一)本公司係透過網際網路提供各項網站/APP服務，您得於本網站/APP瀏覽、查詢、申請或使用各項服務。
(二)商業廣告及促銷資訊均由廣告商直接或間接提供，本公司不擔負交易與產生的消費爭議責任。

二、服務使用責任與義務
(一)提供個人資料應真實完整。
(二)不得有侵害他人權利、散佈病毒、冒用他人名義註冊等不法行為。
(三)應妥善保管帳號與密碼，並對該帳號登入後所進行之一切活動負責。

三、服務之停止或終止
本公司有權於發生緊急維修或不可抗力事件時逕行停止或中斷服務。
                            """.trimIndent(),
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { showTermsOfServiceDialog = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("同 意 條 款", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ── 子 Composable：頁首 ──
@Composable
private fun ProfileHeaderSection(
    avatarUrl: String?,
    avatarInitials: String,
    nickname: String,
    email: String,
    points: Int = 500,
    clubName: String?,
    isUploading: Boolean = false,
    onAvatarClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatarRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF16161B))
                    .graphicsLayer {
                        if (isUploading) rotationZ = rotationAngle
                    }
                    .border(
                        width = if (isUploading) 3.dp else 2.dp,
                        color = if (isUploading) NeonGreen else RedPrimary,
                        shape = CircleShape
                    )
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    val formattedPath = if (avatarUrl.startsWith("/")) avatarUrl else "/$avatarUrl"
                    val fullUrl = if (avatarUrl.startsWith("http")) avatarUrl else "https://revon88.synology.me/revon_android${formattedPath}"
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(fullUrl)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .crossfade(true)
                            .build(),
                        contentDescription = "用戶頭像",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(
                        text = avatarInitials,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
                if (isUploading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = NeonGreen,
                            strokeWidth = 3.dp
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.profile_welcome_back), color = RedPrimary, fontSize = 11.sp, letterSpacing = 0.5.sp)
                Text(
                    text = nickname,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                if (clubName != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = RedPrimary.copy(alpha = 0.18f)) {
                        Text(
                            text = clubName,
                            color = RedPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(text = email, color = TextGray, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "point $points",
                    color = NeonGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.offset(y = (-30).dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .size(38.dp)
                        .background(RedPrimary.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "編輯", tint = RedPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
    HorizontalDivider(color = DividerColor)
}

// ── 子 Composable：統計卡 ──
@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    shape: androidx.compose.ui.graphics.Shape = CircleShape
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(DarkBackground)
            .border(1.dp, DividerColor, shape),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(label, color = TextGray, fontSize = 9.sp, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

// ── 子 Composable：區塊容器 ──
@Composable
private fun SectionBlock(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = RedPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
    HorizontalDivider(color = DividerColor)
}

// ── 子 Composable：選單列 ──
@Composable
private fun MenuRow(
    label: String,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(label, color = Color.White, fontSize = 14.sp)
        }
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = TextGray,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ── 子 Composable：偵錯選項 Switch 開關列 ──
@Composable
private fun DebugSwitchRow(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = TextGray, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RedPrimary,
                uncheckedThumbColor = TextGray,
                uncheckedTrackColor = Color(0xFF2C2C2E)
            )
        )
    }
}

// ── 子 Composable：偵錯選項 下拉選單列 ──
@Composable
private fun DebugDropdownRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))

        Box {
            Surface(
                modifier = Modifier.clickable { expanded = true },
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF2C2C2E),
                border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(options.getOrElse(selectedIndex) { "" }, color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { index, opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun sanitizeCountry(country: String?): String {
    if (country.isNullOrBlank()) return "Taiwan (台灣)"
    return when {
        country.contains("Taiwan") || country.contains("台灣") -> "Taiwan (台灣)"
        country.contains("Japan") || country.contains("日本") -> "Japan (日本)"
        country.contains("United States") || country.contains("美國") -> "United States (美國)"
        country.contains("Hong Kong") || country.contains("香港") -> "Hong Kong (香港)"
        country.contains("Singapore") || country.contains("新加坡") -> "Singapore (新加坡)"
        country.contains("Malaysia") || country.contains("馬來西亞") -> "Malaysia (馬來西亞)"
        country.contains("Australia") || country.contains("澳洲") -> "Australia (澳洲)"
        country.contains("United Kingdom") || country.contains("英國") -> "United Kingdom (英國)"
        country.contains("Canada") || country.contains("加拿大") -> "Canada (加拿大)"
        country.contains("Germany") || country.contains("德國") -> "Germany (德國)"
        country.contains("France") || country.contains("法國") -> "France (法國)"
        else -> country
    }
}

private fun sanitizeCity(city: String?): String {
    if (city.isNullOrBlank()) return "Taichung (台中市)"
    return when {
        city.contains("Taichung") || city.contains("台中") -> "Taichung (台中市)"
        city.contains("Taipei") || city.contains("台北") -> "Taipei (台北市)"
        city.contains("New Taipei") || city.contains("新北") -> "New Taipei (新北市)"
        city.contains("Taoyuan") || city.contains("桃園") -> "Taoyuan (桃園市)"
        city.contains("Tainan") || city.contains("台南") -> "Tainan (台南市)"
        city.contains("Kaohsiung") || city.contains("高雄") -> "Kaohsiung (高雄市)"
        city.contains("Hsinchu") || city.contains("新竹") -> "Hsinchu (新竹市)"
        city.contains("Keelung") || city.contains("基隆") -> "Keelung (基隆市)"
        city.contains("Chiayi") || city.contains("嘉義") -> "Chiayi (嘉義市)"
        city.contains("Changhua") || city.contains("彰化") -> "Changhua (彰化縣)"
        city.contains("Nantou") || city.contains("南投") -> "Nantou (南投縣)"
        city.contains("Yunlin") || city.contains("雲林") -> "Yunlin (雲林縣)"
        city.contains("Pingtung") || city.contains("屏東") -> "Pingtung (屏東縣)"
        city.contains("Yilan") || city.contains("宜蘭") -> "Yilan (宜蘭縣)"
        city.contains("Hualien") || city.contains("花蓮") -> "Hualien (花蓮縣)"
        city.contains("Taitung") || city.contains("台東") -> "Taitung (台東縣)"
        city.contains("Kinmen") || city.contains("金門") -> "Kinmen (金門縣)"
        city.contains("Penghu") || city.contains("澎湖") -> "Penghu (澎湖縣)"
        city.contains("Matsu") || city.contains("馬祖") || city.contains("Lienchiang") -> "Lienchiang (連江縣/馬祖)"
        else -> city
    }
}

private fun format3Decimals(display: String, lapTimeMs: Long): String {
    if (lapTimeMs > 0) {
        val minutes = (lapTimeMs / (1000 * 60)) % 60
        val seconds = (lapTimeMs / 1000) % 60
        val millis = lapTimeMs % 1000
        return String.format(Locale.getDefault(), "%02d:%02d.%03d", minutes, seconds, millis)
    }
    if (display.contains(".")) {
        val parts = display.split(".")
        if (parts.size == 2 && parts[1].length < 3) {
            return "${parts[0]}.${parts[1].padEnd(3, '0')}"
        }
    }
    return display
}

private fun shareSessionJson(context: Context, rec: RaceSessionRecord) {
    try {
        val file = TrackSessionManager.exportSessionToJsonFile(context, rec)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "REV-ON 賽道 JSON 紀錄檔 - ${rec.trackName}")
            putExtra(Intent.EXTRA_TEXT, "這是我在 REV-ON 上跑 ${rec.trackName} 的完整軌跡 JSON 紀錄檔（成績 ${rec.lapTimeDisplay}）。")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享/導出賽道 JSON 數據檔"))
    } catch (e: Exception) {
        RevonToastManager.error("導出失敗: ${e.message}")
    }
}

@Composable
private fun AvatarCropPreviewDialog(
    imageUri: android.net.Uri,
    onDismiss: () -> Unit,
    onConfirm: (android.net.Uri) -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 1. 最下層：圖片展示區（支援多點觸控縮放與位移）
            coil.compose.AsyncImage(
                model = imageUri,
                contentDescription = "圖片縮放預覽",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.8f, 4.0f)
                            offset += pan
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )

            // 2. 中層：遮罩蒙版 (非選擇區域反灰半透明，中間圓形挖空透明並帶白色輪廓框)
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.99f } // 建立獨立混合圖層，防止 BlendMode.Clear 貫穿清空底層 Dialog 視窗透出背後頁面
            ) {
                val circleRadius = 150.dp.toPx()
                val centerPt = center
                // 畫全螢幕半透明灰黑色背景 (65% 黑色)
                drawRect(color = Color.Black.copy(alpha = 0.65f))
                // 將中間圓形區域挖空 (BlendMode.Clear)
                drawCircle(
                    color = Color.Transparent,
                    radius = circleRadius,
                    center = centerPt,
                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                )
                // 畫中間圓形預覽框白線輪廓
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = circleRadius,
                    center = centerPt,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }

            // 3. 上層：頂部列 (左箭頭返回與右側「完成」)，確保永遠在最上層不被圖片覆蓋
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                TextButton(
                    onClick = {
                        // 轉為 Temp 檔 (JPEG)
                        try {
                            val contentResolver = context.contentResolver
                            val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                val source = android.graphics.ImageDecoder.createSource(contentResolver, imageUri)
                                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                    decoder.isMutableRequired = true
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                android.provider.MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                            }

                            val tmpFile = java.io.File(context.cacheDir, "crop_avatar_${System.currentTimeMillis()}.jpg")

                            // 依據使用者在預覽畫面的 scale 與 offset 計算精準裁切領域
                            val screenWidth = context.resources.displayMetrics.widthPixels.toFloat()
                            val screenHeight = context.resources.displayMetrics.heightPixels.toFloat()
                            
                            // 原始圖片尺寸
                            val srcW = bitmap.width.toFloat()
                            val srcH = bitmap.height.toFloat()

                            // ContentScale.Fit 基礎繪製區域
                            val fitScale = Math.min(screenWidth / srcW, screenHeight / srcH)
                            val baseW = srcW * fitScale
                            val baseH = srcH * fitScale
                            val baseLeft = (screenWidth - baseW) / 2f
                            val baseTop = (screenHeight - baseH) / 2f

                            // 在螢幕上的圓形遮罩中心 (螢幕中心) 與半徑 150dp
                            val density = context.resources.displayMetrics.density
                            val cropRadiusPx = 150f * density
                            val cropCenterX = screenWidth / 2f
                            val cropCenterY = screenHeight / 2f
                            val cropRectLeft = cropCenterX - cropRadiusPx
                            val cropRectTop = cropCenterY - cropRadiusPx
                            val cropRectSize = cropRadiusPx * 2f

                            // 逆向計算圓形框對應到原始 Bitmap 上的座標 Rect
                            val totalScale = fitScale * scale
                            val currentImgLeft = baseLeft + (baseW / 2f) * (1f - scale) + offset.x
                            val currentImgTop = baseTop + (baseH / 2f) * (1f - scale) + offset.y

                            val bitmapCropLeft = ((cropRectLeft - currentImgLeft) / totalScale).coerceIn(0f, srcW - 10f)
                            val bitmapCropTop = ((cropRectTop - currentImgTop) / totalScale).coerceIn(0f, srcH - 10f)
                            val bitmapCropWidth = (cropRectSize / totalScale).coerceIn(10f, srcW - bitmapCropLeft)
                            val bitmapCropHeight = (cropRectSize / totalScale).coerceIn(10f, srcH - bitmapCropTop)

                            // 矩形框精準切下
                            val croppedBitmap = android.graphics.Bitmap.createBitmap(
                                bitmap,
                                bitmapCropLeft.toInt(),
                                bitmapCropTop.toInt(),
                                bitmapCropWidth.toInt(),
                                bitmapCropHeight.toInt()
                            )

                            java.io.FileOutputStream(tmpFile).use { out ->
                                croppedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                            }
                            val resultUri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                tmpFile
                            )
                            onConfirm(resultUri)
                        } catch (e: Exception) {
                            onConfirm(imageUri)
                        }
                    }
                ) {
                    Text(
                        text = "完成",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 4. 底部縮放比例提示
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${(scale * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}

