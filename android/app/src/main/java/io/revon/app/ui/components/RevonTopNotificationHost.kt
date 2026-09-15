package io.revon.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.revon.app.ui.theme.DarkSurface
import io.revon.app.ui.theme.NeonGreen
import io.revon.app.ui.theme.RedPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.roundToInt

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

enum class NotificationType {
    INFO, SUCCESS, WARNING, ERROR
}

data class RevonNotification(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val type: NotificationType = NotificationType.INFO,
    val durationMs: Long = 3500L
)

object RevonToastManager {
    private val _notifications = MutableSharedFlow<RevonNotification>(extraBufferCapacity = 64)
    val notifications = _notifications.asSharedFlow()

    fun show(message: String, type: NotificationType = NotificationType.INFO, durationMs: Long = 3500L) {
        if (message.isBlank()) return
        _notifications.tryEmit(RevonNotification(message = message, type = type, durationMs = durationMs))
    }

    fun success(message: String) = show(message, NotificationType.SUCCESS)
    fun info(message: String) = show(message, NotificationType.INFO)
    fun warning(message: String) = show(message, NotificationType.WARNING)
    fun error(message: String) = show(message, NotificationType.ERROR)
}

@Composable
fun RevonTopNotificationHost() {
    var currentNotification by remember { mutableStateOf<RevonNotification?>(null) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        RevonToastManager.notifications.collect { notification ->
            currentNotification = notification
            offsetX = 0f
            offsetY = 0f
            delay(notification.durationMs)
            if (currentNotification?.id == notification.id) {
                currentNotification = null
            }
        }
    }

    val density = LocalDensity.current

    if (currentNotification != null) {
        Popup(
            alignment = Alignment.TopCenter,
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            AnimatedVisibility(
                visible = currentNotification != null,
                enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(300)) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(250)) + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 10.dp, start = 14.dp, end = 14.dp)
            ) {
                currentNotification?.let { notif ->
                    val (bgColor, borderColor, icon, iconColor) = when (notif.type) {
                        NotificationType.SUCCESS -> Quadruple(
                            Color(0xFF0D1E16),
                            NeonGreen,
                            Icons.Default.CheckCircle,
                            NeonGreen
                        )
                        NotificationType.WARNING -> Quadruple(
                            Color(0xFF261D0C),
                            Color(0xFFFFB300),
                            Icons.Default.Warning,
                            Color(0xFFFFB300)
                        )
                        NotificationType.ERROR -> Quadruple(
                            Color(0xFF260D0E),
                            RedPrimary,
                            Icons.Default.Warning,
                            RedPrimary
                        )
                        else -> Quadruple(
                            Color(0xFF12141C),
                            Color(0xFF00E5FF),
                            Icons.Default.Info,
                            Color(0xFF00E5FF)
                        )
                    }

                    val thresholdX = with(density) { 150.dp.toPx() }
                    val dragXState = rememberDraggableState { delta ->
                        offsetX += delta
                        if (kotlin.math.abs(offsetX) > thresholdX) {
                            currentNotification = null
                        }
                    }

                    val thresholdY = with(density) { -40.dp.toPx() }
                    val dragYState = rememberDraggableState { delta ->
                        offsetY += delta
                        if (offsetY < thresholdY) {
                            currentNotification = null
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt().coerceAtMost(0)) }
                            .draggable(state = dragXState, orientation = Orientation.Horizontal)
                            .draggable(state = dragYState, orientation = Orientation.Vertical)
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(borderColor, borderColor.copy(alpha = 0.3f), borderColor)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        color = bgColor,
                        shadowElevation = 12.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(iconColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = notif.message,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 18.sp,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { currentNotification = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "關閉",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
