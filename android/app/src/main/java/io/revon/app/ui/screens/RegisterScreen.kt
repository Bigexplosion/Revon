package io.revon.app.ui.screens

import android.app.DatePickerDialog
import android.util.Patterns
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.revon.app.ui.components.RevonToastManager
import io.revon.app.ui.theme.DarkBackground
import io.revon.app.ui.theme.DarkSurface
import io.revon.app.ui.theme.RedPrimary
import io.revon.app.ui.viewmodel.AuthState
import io.revon.app.ui.viewmodel.AuthViewModel
import java.util.Calendar

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    val context = LocalContext.current

    // 第一頁：帳號安全
    var email by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    // 信箱與帳號與電話重複檢測狀態
    var isEmailChecking by remember { mutableStateOf(false) }
    var isEmailDuplicate by remember { mutableStateOf(false) }
    var isAccountChecking by remember { mutableStateOf(false) }
    var isAccountDuplicate by remember { mutableStateOf(false) }
    var isPhoneChecking by remember { mutableStateOf(false) }
    var isPhoneDuplicate by remember { mutableStateOf(false) }

    // Debounce 後台校驗 Email 是否重複
    val isEmailFormatValid = email.trim().isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    LaunchedEffect(email) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            isEmailDuplicate = false
            isEmailChecking = false
            return@LaunchedEffect
        }
        isEmailChecking = true
        kotlinx.coroutines.delay(400)
        val res = viewModel.checkAvailability(email = trimmedEmail)
        isEmailChecking = false
        if (res != null) {
            val exists = res["email_exists"] == true || res["email_available"] == false
            isEmailDuplicate = exists
        }
    }

    // Debounce 後台校驗 帳號 是否重複
    val isAccountFormatValid = account.trim().length >= 3
    LaunchedEffect(account) {
        val trimmedAccount = account.trim()
        if (trimmedAccount.length < 3) {
            isAccountDuplicate = false
            isAccountChecking = false
            return@LaunchedEffect
        }
        isAccountChecking = true
        kotlinx.coroutines.delay(400)
        val res = viewModel.checkAvailability(account = trimmedAccount)
        isAccountChecking = false
        if (res != null) {
            val exists = res["account_exists"] == true || res["account_available"] == false
            isAccountDuplicate = exists
        }
    }

    // Debounce 後台校驗 電話 是否重複
    LaunchedEffect(phone) {
        val trimmedPhone = phone.trim()
        if (trimmedPhone.isEmpty()) {
            isPhoneDuplicate = false
            isPhoneChecking = false
            return@LaunchedEffect
        }
        isPhoneChecking = true
        kotlinx.coroutines.delay(400)
        val res = viewModel.checkAvailability(phone = trimmedPhone)
        isPhoneChecking = false
        if (res != null) {
            val exists = res["phone_exists"] == true || res["phone_available"] == false
            isPhoneDuplicate = exists
        }
    }

    // 第二頁：個人詳細資料
    var realName by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("male") } // male, female, other
    var genderExpanded by remember { mutableStateOf(false) }
    var birthday by remember { mutableStateOf("") }

    // GPS 定位國家與城市/地區 (預設 Taiwan (台灣) / Taichung (台中市))
    var userGpsCountry by remember { mutableStateOf("Taiwan (台灣)") }
    var userGpsCity by remember { mutableStateOf("Taichung (台中市)") }

    var currentStep by remember { mutableIntStateOf(1) }

    val authState by viewModel.authState.collectAsState()

    // GPS 國家與城市自動抓取與格式化: 英文(中文)
    LaunchedEffect(Unit) {
        try {
            val geocoder = android.location.Geocoder(context, java.util.Locale.TAIWAN)
            val locManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
            val lastLoc = locManager?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: locManager?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            if (lastLoc != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lastLoc.latitude, lastLoc.longitude, 1) { addrs ->
                    val addr = addrs.firstOrNull()
                    if (addr != null) {
                        val countryStr = addr.countryName ?: "台灣"
                        val cName = addr.locality ?: addr.adminArea ?: "台中市"
                        val eCountry = when {
                            countryStr.contains("Japan") || countryStr.contains("日本") -> "Japan (日本)"
                            countryStr.contains("United States") || countryStr.contains("美國") -> "United States (美國)"
                            countryStr.contains("Hong Kong") || countryStr.contains("香港") -> "Hong Kong (香港)"
                            else -> "Taiwan (台灣)"
                        }
                        val eCity = when {
                            cName.contains("臺北") || cName.contains("台北") -> "Taipei (台北市)"
                            cName.contains("新北") -> "New Taipei (新北市)"
                            cName.contains("桃園") -> "Taoyuan (桃園市)"
                            cName.contains("臺中") || cName.contains("台中") -> "Taichung (台中市)"
                            cName.contains("臺南") || cName.contains("台南") -> "Tainan (台南市)"
                            cName.contains("高雄") -> "Kaohsiung (高雄市)"
                            cName.contains("新竹") -> "Hsinchu (新竹市)"
                            cName.contains("基隆") -> "Keelung (基隆市)"
                            cName.contains("嘉義") -> "Chiayi (嘉義市)"
                            cName.contains("彰化") -> "Changhua (彰化縣)"
                            cName.contains("南投") -> "Nantou (南投縣)"
                            cName.contains("雲林") -> "Yunlin (雲林縣)"
                            cName.contains("屏東") -> "Pingtung (屏東縣)"
                            cName.contains("宜蘭") -> "Yilan (宜蘭縣)"
                            cName.contains("花蓮") -> "Hualien (花蓮縣)"
                            cName.contains("台東") -> "Taitung (台東縣)"
                            else -> "Taichung (台中市)"
                        }
                        userGpsCountry = eCountry
                        userGpsCity = eCity
                    }
                }
            }
        } catch (e: Exception) {
            // 保留預設值 Taiwan (台灣) / Taichung (台中市)
        }
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onRegisterSuccess()
        }
    }

    // 本地與伺服器即時輸入檢查
    val isEmailValid = isEmailFormatValid && !isEmailDuplicate && !isEmailChecking
    val isAccountValid = isAccountFormatValid && !isAccountDuplicate && !isAccountChecking
    val isPasswordValid = password.length >= 6
    val isPasswordMatch = isPasswordValid && confirmPassword.isNotEmpty() && password == confirmPassword

    // 第一頁繼續按鈕啟用判斷 (全部符合規範且無重複)
    val isStep1Valid = isEmailValid && isAccountValid && isPasswordValid && isPasswordMatch

    // 第二頁創建帳號啟用判斷 (所有個人資訊皆填寫，電話不能重複)
    val isStep2Valid = realName.trim().isNotBlank() &&
            phone.trim().isNotBlank() &&
            !isPhoneDuplicate &&
            !isPhoneChecking &&
            birthday.trim().isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        // 背景影片 (bg.mp4) 滿版循環播放
        val rawResId = remember {
            context.resources.getIdentifier("bg", "raw", context.packageName)
        }
        if (rawResId != 0) {
            AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        setVideoURI(android.net.Uri.parse("android.resource://" + ctx.packageName + "/" + rawResId))
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            mp.setVolume(0f, 0f)
                            mp.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                            start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 暗色半透明遮罩
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(DarkSurface.copy(alpha = 0.90f), shape = RoundedCornerShape(20.dp))
                .border(1.dp, RedPrimary.copy(alpha = 0.5f), shape = RoundedCornerShape(20.dp))
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Rev-On",
                color = RedPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            Text(
                text = "建立帳號 (步驟 $currentStep / 2)",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            if (authState is AuthState.Error) {
                Text(
                    text = (authState as AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { width -> width } + fadeIn() with
                                slideOutHorizontally { width -> -width } + fadeOut()
                    } else {
                        slideInHorizontally { width -> -width } + fadeIn() with
                                slideOutHorizontally { width -> width } + fadeOut()
                    }
                }
            ) { step ->
                if (step == 1) {
                    // ── 第一頁：電子信箱、帳號、密碼、確認密碼 ──
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Email (重複或格式錯誤打紅色叉叉，點擊叉叉可直接清空欄位)
                        Column {
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("電子信箱 (Email)", color = Color.Gray) },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = RedPrimary) },
                                trailingIcon = {
                                    if (isEmailChecking) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = RedPrimary, strokeWidth = 2.dp)
                                    } else if (email.trim().isNotEmpty()) {
                                        if (!isEmailFormatValid || isEmailDuplicate) {
                                            Icon(
                                                Icons.Default.Cancel,
                                                contentDescription = "點擊清空電子信箱",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.clickable { email = ""; isEmailDuplicate = false }
                                            )
                                        } else if (isEmailValid) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Email 可使用", tint = Color(0xFF00C853))
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (email.trim().isEmpty()) RedPrimary
                                        else if (!isEmailFormatValid || isEmailDuplicate) MaterialTheme.colorScheme.error
                                        else if (isEmailValid) Color(0xFF00C853)
                                        else RedPrimary,
                                    unfocusedBorderColor = if (email.trim().isEmpty()) Color.DarkGray
                                        else if (!isEmailFormatValid || isEmailDuplicate) MaterialTheme.colorScheme.error
                                        else if (isEmailValid) Color(0xFF00C853)
                                        else Color.DarkGray,
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                                )
                            )
                            if (email.trim().isNotEmpty()) {
                                if (!isEmailFormatValid) {
                                    Text("電子信箱格式不正確", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                                } else if (isEmailDuplicate) {
                                    Text("此電子信箱已被註冊 (重複)", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                                }
                            }
                        }

                        // 帳號 (重複或長度不足打紅色叉叉，點擊叉叉可直接清空欄位)
                        Column {
                            OutlinedTextField(
                                value = account,
                                onValueChange = { account = it },
                                label = { Text("帳號 (Account - 至少 3 字元)", color = Color.Gray) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = RedPrimary) },
                                trailingIcon = {
                                    if (isAccountChecking) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = RedPrimary, strokeWidth = 2.dp)
                                    } else if (account.trim().isNotEmpty()) {
                                        if (!isAccountFormatValid || isAccountDuplicate) {
                                            Icon(
                                                Icons.Default.Cancel,
                                                contentDescription = "點擊清空帳號",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.clickable { account = ""; isAccountDuplicate = false }
                                            )
                                        } else if (isAccountValid) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "帳號可用", tint = Color(0xFF00C853))
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (account.trim().isEmpty()) RedPrimary
                                        else if (!isAccountFormatValid || isAccountDuplicate) MaterialTheme.colorScheme.error
                                        else if (isAccountValid) Color(0xFF00C853)
                                        else RedPrimary,
                                    unfocusedBorderColor = if (account.trim().isEmpty()) Color.DarkGray
                                        else if (!isAccountFormatValid || isAccountDuplicate) MaterialTheme.colorScheme.error
                                        else if (isAccountValid) Color(0xFF00C853)
                                        else Color.DarkGray,
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                                )
                            )
                            if (account.trim().isNotEmpty()) {
                                if (!isAccountFormatValid) {
                                    Text("帳號至少需要 3 個字元", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                                } else if (isAccountDuplicate) {
                                    Text("此帳號已被註冊 (重複)", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                                }
                            }
                        }

                        // 密碼 (長度滿足顯示綠色打勾，不足顯示紅色打叉，點擊叉叉可清空)
                        Column {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("密碼 (Password - 至少 6 字元)", color = Color.Gray) },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = RedPrimary) },
                                trailingIcon = {
                                    if (password.isNotEmpty()) {
                                        if (isPasswordValid) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "密碼長度符合", tint = Color(0xFF00C853))
                                        } else {
                                            Icon(
                                                Icons.Default.Cancel,
                                                contentDescription = "點擊清空密碼",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.clickable { password = "" }
                                            )
                                        }
                                    }
                                },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (password.isEmpty()) RedPrimary
                                        else if (!isPasswordValid) MaterialTheme.colorScheme.error
                                        else Color(0xFF00C853),
                                    unfocusedBorderColor = if (password.isEmpty()) Color.DarkGray
                                        else if (!isPasswordValid) MaterialTheme.colorScheme.error
                                        else Color(0xFF00C853),
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                                )
                            )
                            if (password.isNotEmpty() && !isPasswordValid) {
                                Text("密碼至少需要 6 個字元", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                            }
                        }

                        // 確認密碼 (自動檢查一致性，不一致顯示紅色打叉，點擊叉叉可清空)
                        Column {
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text("確認密碼", color = Color.Gray) },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = RedPrimary) },
                                trailingIcon = {
                                    if (confirmPassword.isNotEmpty()) {
                                        if (isPasswordMatch) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "密碼一致", tint = Color(0xFF00C853))
                                        } else {
                                            Icon(
                                                Icons.Default.Cancel,
                                                contentDescription = "點擊清空確認密碼",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.clickable { confirmPassword = "" }
                                            )
                                        }
                                    }
                                },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (confirmPassword.isEmpty()) RedPrimary
                                        else if (!isPasswordMatch) MaterialTheme.colorScheme.error
                                        else Color(0xFF00C853),
                                    unfocusedBorderColor = if (confirmPassword.isEmpty()) Color.DarkGray
                                        else if (!isPasswordMatch) MaterialTheme.colorScheme.error
                                        else Color(0xFF00C853),
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                                )
                            )
                            if (confirmPassword.isNotEmpty() && !isPasswordMatch) {
                                Text("兩次輸入的密碼不一致", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 第一頁「繼續」按鈕 (未打完前為灰色)
                        Button(
                            onClick = { currentStep = 2 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RedPrimary,
                                disabledContainerColor = Color(0xFF333340),
                                disabledContentColor = Color.Gray
                            ),
                            enabled = isStep1Valid
                        ) {
                            Text(text = "繼 續 ›", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                } else {
                    // ── 第二頁：真實姓名、性別、電話、出生年月日 ──
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 真實姓名
                        OutlinedTextField(
                            value = realName,
                            onValueChange = { realName = it },
                            label = { Text("真實姓名", color = Color.Gray) },
                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = RedPrimary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RedPrimary, unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            )
                        )

                        // 性別 (下拉選單 男 女 不透漏)
                        val genderLabel = when (gender) {
                            "male" -> "男 (Male)"
                            "female" -> "女 (Female)"
                            else -> "不透漏 (Other)"
                        }
                        Box(modifier = Modifier.fillMaxWidth().clickable { genderExpanded = true }) {
                            OutlinedTextField(
                                value = genderLabel,
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                label = { Text("性別", color = Color.Gray) },
                                leadingIcon = { Icon(Icons.Default.Wc, contentDescription = null, tint = RedPrimary) },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = Color.White, disabledBorderColor = Color.DarkGray,
                                    disabledLabelColor = Color.Gray, disabledLeadingIconColor = RedPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = genderExpanded,
                                onDismissRequest = { genderExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("男 (Male)") },
                                    onClick = { gender = "male"; genderExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("女 (Female)") },
                                    onClick = { gender = "female"; genderExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("不透漏 (Other)") },
                                    onClick = { gender = "other"; genderExpanded = false }
                                )
                            }
                        }

                        // 電話 (重複時打紅色叉叉，按叉叉清空；無錯誤時不打勾勾)
                        Column {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("聯絡電話", color = Color.Gray) },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = RedPrimary) },
                                trailingIcon = {
                                    if (isPhoneChecking) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = RedPrimary, strokeWidth = 2.dp)
                                    } else if (phone.trim().isNotEmpty() && isPhoneDuplicate) {
                                        Icon(
                                            Icons.Default.Cancel,
                                            contentDescription = "點擊清空聯絡電話",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.clickable {
                                                phone = ""
                                                isPhoneDuplicate = false
                                            }
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (isPhoneDuplicate) MaterialTheme.colorScheme.error else RedPrimary,
                                    unfocusedBorderColor = if (isPhoneDuplicate) MaterialTheme.colorScheme.error else Color.DarkGray,
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                                )
                            )
                            if (phone.trim().isNotEmpty() && isPhoneDuplicate) {
                                Text("此聯絡電話已被註冊 (重複)", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                            }
                        }

                        // 出生年月日 (調用 Android 原生 DatePickerDialog)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val calendar = Calendar.getInstance()
                                    val year = calendar.get(Calendar.YEAR) - 20
                                    val month = calendar.get(Calendar.MONTH)
                                    val day = calendar.get(Calendar.DAY_OF_MONTH)
                                    val datePicker = DatePickerDialog(context, { _, y, m, d ->
                                        birthday = String.format("%04d-%02d-%02d", y, m + 1, d)
                                    }, year, month, day)
                                    datePicker.show()
                                }
                        ) {
                            OutlinedTextField(
                                value = birthday,
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                label = { Text("出生年月日", color = Color.Gray) },
                                placeholder = { Text("點擊選擇日期 (YYYY-MM-DD)", color = Color.Gray) },
                                leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = RedPrimary) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = Color.White, disabledBorderColor = Color.DarkGray,
                                    disabledLabelColor = Color.Gray, disabledLeadingIconColor = RedPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { currentStep = 1 },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
                            ) {
                                Text("‹ 上一步", color = Color.White)
                            }

                            Button(
                                onClick = {
                                    viewModel.register(
                                        email = email.trim(),
                                        account = account.trim(),
                                        pass = password,
                                        realName = realName.trim(),
                                        gender = gender,
                                        phone = phone.trim(),
                                        birthday = birthday.trim(),
                                        city = userGpsCity,
                                        country = userGpsCountry
                                    )
                                },
                                modifier = Modifier.weight(1.8f).height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RedPrimary,
                                    disabledContainerColor = Color(0xFF333340),
                                    disabledContentColor = Color.Gray
                                ),
                                enabled = isStep2Valid && authState !is AuthState.Loading
                            ) {
                                if (authState is AuthState.Loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Text(text = "創 建 帳 號", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "已經有帳號了嗎？ ", color = Color.Gray, fontSize = 13.sp)
                TextButton(onClick = onNavigateToLogin) {
                    Text(text = "立即登入", color = RedPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
