package io.revon.app.ui.screens

import android.widget.Toast
import io.revon.app.ui.components.RevonToastManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.revon.app.ui.theme.DarkBackground
import io.revon.app.ui.theme.DarkSurface
import io.revon.app.ui.theme.RedPrimary
import io.revon.app.ui.viewmodel.AuthState
import io.revon.app.ui.viewmodel.AuthViewModel

import androidx.compose.ui.res.stringResource
import io.revon.app.R

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var forgotPasswordEmail by remember { mutableStateOf("") }
    var resetCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var showResetCodeDialog by remember { mutableStateOf(false) }

    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        // 背景影片循環無縫播放
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
                            // 裁切滿版 (Crop Fill) 效果
                            mp.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                            start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 半透明暗色遮罩以提昇前景文字對比度
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .background(DarkSurface.copy(alpha = 0.88f), shape = RoundedCornerShape(20.dp))
                .border(1.dp, RedPrimary.copy(alpha = 0.5f), shape = RoundedCornerShape(20.dp))
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Rev-On",
                color = RedPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Performance Racing Platform",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Text(
                text = stringResource(R.string.auth_login_subtitle),
                color = Color.LightGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (authState is AuthState.Error) {
                Text(
                    text = (authState as AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Email Input
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.auth_email), color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = RedPrimary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RedPrimary,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Password Input
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.auth_password), color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = RedPrimary) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RedPrimary,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            // 忘記密碼？ 連結
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        forgotPasswordEmail = email
                        showForgotPasswordDialog = true
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.auth_forgot_password),
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Login Button
            Button(
                onClick = { viewModel.login(email, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                enabled = authState !is AuthState.Loading
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.auth_login),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Register Link
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.auth_no_account) + " ",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                TextButton(onClick = onNavigateToRegister) {
                    Text(
                        text = stringResource(R.string.auth_register_now),
                        color = RedPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    // 忘記密碼彈窗 Dialog
    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "忘記密碼？",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "請輸入您的註冊電子信箱，系統將發送重設密碼驗證信給您：",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = forgotPasswordEmail,
                        onValueChange = { forgotPasswordEmail = it },
                        label = { Text("電子信箱 (Email)", color = Color.Gray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RedPrimary,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val emailToSend = forgotPasswordEmail.trim()
                        if (emailToSend.isNotEmpty()) {
                            showForgotPasswordDialog = false
                            showResetCodeDialog = true
                            RevonToastManager.info("正在寄送驗證碼信件...")
                            viewModel.sendPasswordReset(emailToSend) { success, msg ->
                                if (success) RevonToastManager.success(msg) else RevonToastManager.error(msg)
                            }
                        } else {
                            RevonToastManager.error("請輸入有效的電子信箱")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("發送重設信", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("取消", color = Color.Gray)
                }
            }
        )
    }

    // 驗證碼與新密碼對話框 (保有 Rev-On 經典紅黑賽車風格，加入綠色打勾即時校驗)
    if (showResetCodeDialog) {
        var confirmNewPassword by remember { mutableStateOf("") }
        var newPasswordVisible by remember { mutableStateOf(false) }
        var confirmPasswordVisible by remember { mutableStateOf(false) }

        val isCodeValid = resetCode.trim().length == 6
        val isNewPasswordValid = newPassword.length >= 6
        val isPasswordMatch = isNewPasswordValid && confirmNewPassword.isNotEmpty() && newPassword == confirmNewPassword

        AlertDialog(
            onDismissRequest = { showResetCodeDialog = false },
            containerColor = DarkSurface,
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Rev-On",
                        color = RedPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.auth_enter_code),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "請輸入您在信箱收到的 6 位數驗證碼，以及您的新密碼：",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )

                    // 6 位數驗證碼 (不顯示打勾，僅一般紅黑邊框輸入)
                    Column {
                        OutlinedTextField(
                            value = resetCode,
                            onValueChange = { resetCode = it.take(6) },
                            label = { Text("6 位數驗證碼", color = Color.Gray) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = RedPrimary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RedPrimary,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    // 新密碼 (打了先不打勾，當確認新密碼也輸入且兩者一致時同時打勾)
                    Column {
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text(stringResource(R.string.profile_new_password_hint), color = Color.Gray) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = RedPrimary) },
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (confirmNewPassword.isNotEmpty() && newPassword.isNotEmpty()) {
                                    if (isPasswordMatch) Color(0xFF00C853) else MaterialTheme.colorScheme.error
                                } else RedPrimary,
                                unfocusedBorderColor = if (confirmNewPassword.isNotEmpty() && newPassword.isNotEmpty()) {
                                    if (isPasswordMatch) Color(0xFF00C853) else MaterialTheme.colorScheme.error
                                } else Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
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
                            label = { Text(stringResource(R.string.profile_confirm_password_hint), color = Color.Gray) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = RedPrimary) },
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (confirmNewPassword.isNotEmpty() && newPassword.isNotEmpty()) {
                                    if (isPasswordMatch) Color(0xFF00C853) else MaterialTheme.colorScheme.error
                                } else RedPrimary,
                                unfocusedBorderColor = if (confirmNewPassword.isNotEmpty() && newPassword.isNotEmpty()) {
                                    if (isPasswordMatch) Color(0xFF00C853) else MaterialTheme.colorScheme.error
                                } else Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
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
                    onClick = {
                        viewModel.resetPasswordWithCode(forgotPasswordEmail, resetCode, newPassword) { success, msg ->
                            if (success) RevonToastManager.success(msg) else RevonToastManager.error(msg)
                            if (success) {
                                showResetCodeDialog = false
                                resetCode = ""
                                newPassword = ""
                                confirmNewPassword = ""
                                forgotPasswordEmail = ""
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    enabled = isCodeValid && isPasswordMatch
                ) {
                    Text(stringResource(R.string.common_confirm), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetCodeDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.common_cancel), color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(1.dp, RedPrimary.copy(alpha = 0.5f), shape = RoundedCornerShape(20.dp))
        )
    }
}
