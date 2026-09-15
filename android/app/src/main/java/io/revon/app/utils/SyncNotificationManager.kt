package io.revon.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object SyncNotificationManager {

    private const val CHANNEL_ID = "revon_sync_channel"
    private const val CHANNEL_NAME = "REV-ON 雲端同步與離線補傳"
    private const val NOTIFICATION_ID = 8801

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "通知使用者網路斷線與離線計時紀錄自動補傳進度"
            }
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 當網路斷線且有本地待補傳紀錄時，發送常駐系統通知
     */
    fun showOfflinePendingNotification(context: Context, pendingCount: Int) {
        createNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val title = "⚡ REV-ON 網路已中斷"
        val content = if (pendingCount > 0) {
            "有 $pendingCount 筆計時紀錄已離線保存至本地，連線後將自動補傳"
        } else {
            "網路暫時連線失敗，App 將在連線恢復後自動重試"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(pendingCount > 0)
            .setAutoCancel(false)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 當網路恢復、正在自動補傳離線紀錄時更新通知
     */
    fun showSyncingNotification(context: Context, pendingCount: Int) {
        createNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val title = "↻ 正在自動補傳離線紀錄"
        val content = "網路連線已恢復，正在將 $pendingCount 筆計時紀錄同步至雲端資料庫..."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 當所有離線紀錄均補傳成功時，清除通知
     */
    fun cancelSyncNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
