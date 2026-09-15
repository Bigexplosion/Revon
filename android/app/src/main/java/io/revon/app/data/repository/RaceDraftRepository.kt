package io.revon.app.data.repository

import android.content.Context
import android.util.Log
import io.revon.app.data.model.TrackPoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object RaceDraftRepository {
    private const val TAG = "RaceDraftRepository"
    private const val DRAFT_DIR_NAME = "race_drafts"
    private const val ABORTED_DIR_NAME = "aborted_sessions"

    private fun getDraftDir(context: Context): File {
        val dir = File(context.filesDir, DRAFT_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getAbortedDir(context: Context): File {
        val dir = File(context.filesDir, ABORTED_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 計時過程分流寫入本地 JSON 草稿檔 (防止 RAM 溢出與異常丟失)
     */
    fun saveDraftPoints(
        context: Context,
        sessionId: String,
        trackCode: String,
        trackName: String,
        driverNickname: String,
        vehicleType: String,
        points: List<TrackPoint>,
        isRacing: Boolean
    ): File? {
        return try {
            val file = File(getDraftDir(context), "draft_${sessionId}.json")
            val root = JSONObject().apply {
                put("sessionId", sessionId)
                put("trackCode", trackCode)
                put("trackName", trackName)
                put("driverNickname", driverNickname)
                put("vehicleType", vehicleType)
                put("isRacing", isRacing)
                put("updatedAt", System.currentTimeMillis())
                put("pointsCount", points.size)

                val ptsArray = JSONArray()
                points.forEach { pt ->
                    ptsArray.put(JSONObject().apply {
                        put("latitude", pt.latitude)
                        put("longitude", pt.longitude)
                        put("speedKmh", pt.speedKmh)
                        put("accelG", pt.accelG)
                        put("timestampMs", pt.timestampMs)
                    })
                }
                put("points", ptsArray)
            }

            file.writeText(root.toString(2))
            file
        } catch (e: Exception) {
            Log.e(TAG, "Save draft failed: ${e.message}", e)
            null
        }
    }

    /**
     * 開發者偵錯：意外結束存檔 (保存中途中斷的完整數據 JSON)
     */
    fun saveAbortedSession(
        context: Context,
        sessionId: String,
        trackCode: String,
        trackName: String,
        driverNickname: String,
        vehicleType: String,
        points: List<TrackPoint>,
        elapsedMs: Long,
        reason: String = "EMERGENCY_ABORT"
    ): File? {
        return try {
            val timestamp = System.currentTimeMillis()
            val fileName = "aborted_${sessionId}_${timestamp}.json"
            val file = File(getAbortedDir(context), fileName)

            val root = JSONObject().apply {
                put("sessionId", sessionId)
                put("trackCode", trackCode)
                put("trackName", trackName)
                put("driverNickname", driverNickname)
                put("vehicleType", vehicleType)
                put("elapsedMs", elapsedMs)
                put("pointsCount", points.size)
                put("abortedAt", timestamp)
                put("abortReason", reason)

                val ptsArray = JSONArray()
                points.forEach { pt ->
                    ptsArray.put(JSONObject().apply {
                        put("latitude", pt.latitude)
                        put("longitude", pt.longitude)
                        put("speedKmh", pt.speedKmh)
                        put("accelG", pt.accelG)
                        put("timestampMs", pt.timestampMs)
                    })
                }
                put("points", ptsArray)
            }

            file.writeText(root.toString(2))
            Log.i(TAG, "Aborted session saved to: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Save aborted session failed: ${e.message}", e)
            null
        }
    }

    /**
     * 讀取所有意外結束存檔列表
     */
    fun getAbortedSessions(context: Context): List<File> {
        val dir = getAbortedDir(context)
        return dir.listFiles { _, name -> name.endsWith(".json") }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * 清理完賽草稿檔
     */
    fun clearDraft(context: Context, sessionId: String) {
        try {
            val file = File(getDraftDir(context), "draft_${sessionId}.json")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Clear draft failed: ${e.message}")
        }
    }
}
