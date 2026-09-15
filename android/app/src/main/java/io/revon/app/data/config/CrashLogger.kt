package io.revon.app.data.config

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private const val TAG = "CrashLogger"
    private const val PREF_NAME = "crash_logger_prefs"
    private const val KEY_LAST_CRASH_LOG = "last_crash_log"
    private const val KEY_CRASH_TIME = "crash_timestamp"

    fun init(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrash(appContext, thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save crash log", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun saveCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val appVersionName = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "Unknown"
        }

        val logBuilder = StringBuilder()
        logBuilder.append("================ REVON CRASH LOG ================\n")
        logBuilder.append("時間 (Time): ").append(timeStr).append("\n")
        logBuilder.append("App 版本 (Version): ").append(appVersionName).append("\n")
        logBuilder.append("裝置型號 (Device): ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
        logBuilder.append("Android SDK: ").append(Build.VERSION.SDK_INT).append(" (").append(Build.VERSION.RELEASE).append(")\n")
        logBuilder.append("線程 (Thread): ").append(thread.name).append("\n")
        logBuilder.append("異常名稱 (Exception): ").append(throwable.javaClass.name).append("\n")
        logBuilder.append("閃退訊息 (Message): ").append(throwable.localizedMessage ?: throwable.message ?: "N/A").append("\n")
        logBuilder.append("---------------- StackTrace ----------------\n")
        logBuilder.append(stackTrace)
        logBuilder.append("=================================================")

        val crashContent = logBuilder.toString()

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_CRASH_LOG, crashContent)
            .putString(KEY_CRASH_TIME, timeStr)
            .commit()

        Log.e(TAG, "Uncaught Exception Saved: $crashContent")
    }

    fun getLastCrashLog(context: Context): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_CRASH_LOG, null)
    }

    fun getLastCrashTime(context: Context): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CRASH_TIME, null)
    }

    fun clearCrashLog(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_CRASH_LOG)
            .remove(KEY_CRASH_TIME)
            .apply()
    }

    fun clearSystemLogcat(): Boolean {
        return try {
            Runtime.getRuntime().exec("logcat -c")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clearAllLogs(context: Context) {
        clearCrashLog(context)
        clearSystemLogcat()
    }

    fun getSystemLogcat(maxLines: Int = 150): String {
        return try {
            val process = Runtime.getRuntime().exec("logcat -d -v time -t $maxLines")
            val bufferedReader = process.inputStream.bufferedReader()
            val lines = bufferedReader.readLines()
            if (lines.isEmpty()) "無即時 Logcat 紀錄" else lines.joinToString("\n")
        } catch (e: Exception) {
            "讀取 Logcat 失敗: ${e.message}"
        }
    }

    fun getCombinedLogReport(context: Context): String {
        val sb = StringBuilder()
        val crashLog = getLastCrashLog(context)
        if (!crashLog.isNullOrEmpty()) {
            sb.append(crashLog).append("\n\n")
        } else {
            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            sb.append("================ REVON DEBUG SYSTEM LOG ================\n")
            sb.append("時間 (Time): ").append(timeStr).append("\n")
            sb.append("裝置型號 (Device): ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
            sb.append("Android SDK: ").append(Build.VERSION.SDK_INT).append(" (").append(Build.VERSION.RELEASE).append(")\n")
            sb.append("無系統崩潰 StackTrace (No Uncaught Exception Recorded)\n")
            sb.append("========================================================\n\n")
        }
        sb.append("================ REALTIME LOGCAT (Last 150 lines) ================\n")
        sb.append(getSystemLogcat(150)).append("\n")
        sb.append("==================================================================")
        return sb.toString()
    }

    fun copyCrashLogToClipboard(context: Context, logText: String? = null): Boolean {
        val contentToCopy = logText ?: getLastCrashLog(context)
        if (contentToCopy.isNullOrEmpty()) {
            io.revon.app.ui.components.RevonToastManager.info("目前尚無崩潰紀錄可供複製")
            return false
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            val clip = ClipData.newPlainText("Revon Crash Log", contentToCopy)
            clipboard.setPrimaryClip(clip)
            io.revon.app.ui.components.RevonToastManager.info("已成功複製崩潰訊息至剪貼簿")
            return true
        }
        return false
    }

    fun copyFullLogsToClipboard(context: Context): Boolean {
        val combinedLog = getCombinedLogReport(context)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            val clip = ClipData.newPlainText("Revon Full System Log", combinedLog)
            clipboard.setPrimaryClip(clip)
            io.revon.app.ui.components.RevonToastManager.info("已成功複製崩潰與系統 Logcat 紀錄")
            return true
        }
        return false
    }

    fun generateTestCrash(context: Context) {
        val testException = NullPointerException("測試模組 Developer Debug Test Crash: NullPointerException at line 88 in TestModule")
        val thread = Thread.currentThread()
        saveCrash(context, thread, testException)
        io.revon.app.ui.components.RevonToastManager.info("已成功模擬產生一筆測試紀錄")
    }
}
