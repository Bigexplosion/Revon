package io.revon.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import io.revon.app.data.model.RaceSessionRecord
import io.revon.app.data.model.TrackPoint
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import java.io.FileWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object TrackSessionManager {

    private const val PREF_NAME = "revon_sessions_pref"
    private const val KEY_SESSIONS = "saved_sessions_json"
    private const val KEY_DELETED_SESSIONS = "deleted_session_ids"
    private val gson = Gson()

    // ⚡ 記憶體高快取 (Memory In-Ram Cache) 實現切換分頁 0ms 極速載入
    @Volatile
    private var cachedSessionsList: List<RaceSessionRecord>? = null

    fun clearCache() {
        cachedSessionsList = null
    }

    fun saveSession(context: Context, record: RaceSessionRecord) {
        android.util.Log.d("TrackSessionManager", "saveSession start: sessionId=${record.sessionId}, track=${record.trackName}")
        // 作廢記憶體快取以確保即時性
        clearCache()
        // 1. Save ONLY user saved sessions to SharedPreferences for fast local read
        val userSaved = getUserSavedSessions(context).toMutableList()
        userSaved.removeAll { it.sessionId == record.sessionId }
        userSaved.add(0, record)
        val json = gson.toJson(userSaved)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SESSIONS, json)
            .commit()

        android.util.Log.d("TrackSessionManager", "saveSession saved to SP successfully, count=${userSaved.size}")

        // 2. Export to Tstarz-compatible JSON file locally
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dir = File(context.filesDir, "track_sessions")
                if (!dir.exists()) dir.mkdirs()
                
                val safeId = record.sessionId.replace(Regex("[^a-zA-Z0-9_]"), "_")
                val file = File(dir, "賽道_${record.trackCode}_${safeId}.json")
                
                // Build Tstarz format JSON
                val rootObj = JsonObject()
                
                val headerObj = JsonObject()
                headerObj.addProperty("type", "session")
                headerObj.addProperty("trackName", record.trackName)
                headerObj.addProperty("trackCode", record.trackCode)
                headerObj.addProperty("recordId", record.sessionId)
                headerObj.addProperty("playerNickname", record.playerNickname)
                headerObj.addProperty("vehicleType", record.vehicleType)
                headerObj.addProperty("recordedAt", record.recordedAt)
                headerObj.addProperty("lapTimeMs", record.lapTimeMs)
                headerObj.addProperty("lapTimeDisplay", record.lapTimeDisplay)
                headerObj.addProperty("maxSpeedKmh", record.maxSpeedKmh)
                headerObj.addProperty("avgSpeedKmh", record.avgSpeedKmh)
                headerObj.addProperty("maxLeanAngle", record.maxLeanAngle)
                headerObj.addProperty("maxBrakingG", record.maxBrakingG)
                rootObj.add("header", headerObj)
                
                val pointsArray = com.google.gson.JsonArray()
                record.safePointsList.forEach { pt ->
                    val ptObj = JsonObject()
                    ptObj.addProperty("lt", pt.latitude)
                    ptObj.addProperty("lg", pt.longitude)
                    ptObj.addProperty("s", pt.speedKmh)
                    ptObj.addProperty("t", pt.timestampMs)
                    ptObj.addProperty("l", pt.leanAngle)
                    ptObj.addProperty("g", pt.accelG) // braking or acceleration
                    ptObj.addProperty("ga", pt.latG)  // 側向 G 力
                    ptObj.addProperty("gb", pt.longG) // 縱向 G 力
                    pointsArray.add(ptObj)
                }
                rootObj.add("points", pointsArray)

                
                FileWriter(file).use { writer ->
                    gson.toJson(rootObj, writer)
                }
                
                // 3. Upload to PHP API (若網路斷線或失敗，加入 PendingUploadManager 重試佇列)
                val allTracks = TrackRepository.getTracks(context)
                val matchedTrack = allTracks.find { it.code.equals(record.trackCode, ignoreCase = true) || it.name.equals(record.trackName, ignoreCase = true) }
                val numericTrackId = matchedTrack?.id ?: record.trackCode.toIntOrNull() ?: 0
                val isCustomTrack = matchedTrack?.isCustom == true || record.trackCode.startsWith("custom_") || record.trackCode.contains("錄製") || record.trackName.contains("錄製")

                try {
                    val apiService = io.revon.app.di.NetworkModule.apiService
                    val apiRepo = io.revon.app.data.repository.ApiRaceRepository(apiService)
                    
                    val vType = try {
                        io.revon.app.data.model.VehicleType.valueOf(record.vehicleType)
                    } catch(e: Exception) {
                        io.revon.app.data.model.VehicleType.CAR
                    }

                    val resultObj = io.revon.app.data.model.RaceResult(
                        trackCode = record.trackCode,
                        trackName = record.trackName,
                        playerNickname = record.playerNickname,
                        vehicleType = vType,
                        finishTimeMs = record.lapTimeMs,
                        finishTimeDisplay = record.lapTimeDisplay,
                        recordedAt = record.recordedAt,
                        region = null,
                        clubId = null,
                        clubName = null,
                        isCustomRoute = isCustomTrack,
                        customRouteId = if (isCustomTrack) numericTrackId.toString() else null,
                        customRouteName = if (isCustomTrack) record.trackName else null
                    )

                    val telemetryJsonStr = gson.toJson(rootObj)
                    val uploadRes = apiRepo.uploadRaceResult(resultObj, trackId = numericTrackId, telemetryJson = telemetryJsonStr)
                    if (!uploadRes.isSuccess) {
                        PendingUploadManager.enqueuePendingUpload(context, record)
                    } else {
                        PendingUploadManager.triggerAutoSync(context)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    PendingUploadManager.enqueuePendingUpload(context, record)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getUserSavedSessions(context: Context): List<RaceSessionRecord> {
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = pref.getString(KEY_SESSIONS, null)
        val deletedSet = pref.getStringSet(KEY_DELETED_SESSIONS, emptySet()) ?: emptySet()

        val parsedUserSaved = if (json != null) {
            try {
                val type = object : TypeToken<List<RaceSessionRecord>>() {}.type
                val list = gson.fromJson<List<RaceSessionRecord>>(json, type) ?: emptyList()
                list.filter { !it.sessionId.startsWith("bk_") && !deletedSet.contains(it.sessionId) }
            } catch (e: Exception) {
                android.util.Log.e("TrackSessionManager", "Error parsing KEY_SESSIONS json", e)
                emptyList()
            }
        } else emptyList()

        // 雙重防護：若 SharedPreferences 丟失或為空，從磁碟目錄讀取歷史錄製檔救援
        val diskSaved = loadSessionsFromDiskFiles(context, deletedSet)
        
        return (parsedUserSaved + diskSaved)
            .distinctBy { it.sessionId }
            .filter { !deletedSet.contains(it.sessionId) }
    }

    private fun loadSessionsFromDiskFiles(context: Context, deletedSet: Set<String>): List<RaceSessionRecord> {
        val list = mutableListOf<RaceSessionRecord>()
        try {
            val dir = File(context.filesDir, "track_sessions")
            if (!dir.exists()) return emptyList()
            val files = dir.listFiles() ?: return emptyList()
            for (file in files) {
                if (!file.name.endsWith(".json")) continue
                try {
                    val reader = InputStreamReader(file.inputStream())
                    val parsedElement = com.google.gson.JsonParser.parseReader(reader)
                    if (!parsedElement.isJsonObject) continue
                    val jsonObj = parsedElement.asJsonObject
                    val headerObj = jsonObj.getAsJsonObject("header") ?: continue
                    val recordId = headerObj.get("recordId")?.asString ?: continue
                    if (deletedSet.contains(recordId)) continue

                    val ptsArray = jsonObj.getAsJsonArray("points") ?: continue
                    val ptsList = mutableListOf<TrackPoint>()
                    var maxSpeed = 0f
                    var sumSpeed = 0f
                    var maxLean = 0f
                    var maxG = 0f

                    for (elem in ptsArray) {
                        val ptObj = elem.asJsonObject
                        val lt = ptObj.get("lt")?.asDouble ?: 0.0
                        val lg = ptObj.get("lg")?.asDouble ?: 0.0
                        val spd = ptObj.get("s")?.asFloat ?: 0f
                        val t = ptObj.get("t")?.asLong ?: 0L
                        val lean = ptObj.get("l")?.asFloat ?: 0f
                        val g = ptObj.get("g")?.asFloat ?: 0f
                        val ga = ptObj.get("ga")?.asFloat ?: 0f
                        val gb = ptObj.get("gb")?.asFloat ?: 0f

                        if (spd > maxSpeed) maxSpeed = spd
                        if (lean > maxLean) maxLean = lean
                        if (g > maxG) maxG = g
                        sumSpeed += spd

                        ptsList.add(
                            TrackPoint(
                                latitude = lt,
                                longitude = lg,
                                speedKmh = spd,
                                timestampMs = t,
                                leanAngle = lean,
                                accelG = g,
                                latG = ga,
                                longG = gb
                            )
                        )
                    }

                    val trackName = headerObj.get("trackName")?.asString ?: "賽道"
                    val trackCode = headerObj.get("trackCode")?.asString ?: "賽道"
                    val playerNickname = headerObj.get("playerNickname")?.asString ?: "車手"
                    val vehicleType = headerObj.get("vehicleType")?.asString ?: "CAR"
                    val lapTimeMs = headerObj.get("lapTimeMs")?.asLong ?: 0L
                    val lapTimeDisplay = headerObj.get("lapTimeDisplay")?.asString ?: ""
                    val recordedAt = headerObj.get("recordedAt")?.asString ?: ""

                    list.add(
                        RaceSessionRecord(
                            sessionId = recordId,
                            trackCode = trackCode,
                            trackName = trackName,
                            playerNickname = playerNickname,
                            vehicleType = vehicleType,
                            lapTimeMs = lapTimeMs,
                            lapTimeDisplay = lapTimeDisplay,
                            maxSpeedKmh = maxSpeed,
                            avgSpeedKmh = if (ptsList.isNotEmpty()) sumSpeed / ptsList.size else 0f,
                            maxLeanAngle = maxLean,
                            maxBrakingG = maxG,
                            pointsEarned = 350,
                            recordedAt = recordedAt,
                            pointsList = ptsList
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getAllSessions(context: Context): List<RaceSessionRecord> {
        val currentCache = cachedSessionsList
        if (currentCache != null) return currentCache

        val userSaved = getUserSavedSessions(context)
        val backupSessions = loadBackupSessionsFromAssets(context)
        val deletedSet = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_DELETED_SESSIONS, emptySet()) ?: emptySet()

        val result = (userSaved + backupSessions)
            .distinctBy { it.sessionId }
            .filter { !deletedSet.contains(it.sessionId) }
        
        cachedSessionsList = result
        return result
    }

    fun getSessionById(context: Context, sessionId: String): RaceSessionRecord? {
        if (sessionId.isBlank()) return null
        val decodedId = try { java.net.URLDecoder.decode(sessionId, "UTF-8") } catch(e: Exception) { sessionId }
        android.util.Log.d("TrackSessionManager", "getSessionById looking for '$sessionId' (decoded: '$decodedId')")
        
        val all = getAllSessions(context)
        val found = all.find { it.sessionId == sessionId || it.sessionId == decodedId }
        if (found != null) {
            android.util.Log.d("TrackSessionManager", "getSessionById SUCCESS found: ${found.sessionId}")
            return found
        }

        // 磁碟救援搜尋
        val diskMatches = loadSessionsFromDiskFiles(context, emptySet())
        val diskFound = diskMatches.find { it.sessionId == sessionId || it.sessionId == decodedId }
        if (diskFound != null) {
            android.util.Log.d("TrackSessionManager", "getSessionById DISK SUCCESS found: ${diskFound.sessionId}")
            return diskFound
        }

        android.util.Log.e("TrackSessionManager", "getSessionById FAILED for '$sessionId' (total sessions=${all.size})")
        return null
    }

    fun deleteSession(context: Context, sessionId: String) {
        clearCache()
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // Save to deleted blacklist set
        val deletedSet = pref.getStringSet(KEY_DELETED_SESSIONS, emptySet())?.toMutableSet() ?: mutableSetOf()
        deletedSet.add(sessionId)
        
        val json = pref.getString(KEY_SESSIONS, null)
        val editor = pref.edit().putStringSet(KEY_DELETED_SESSIONS, deletedSet)

        if (json != null) {
            try {
                val type = object : TypeToken<List<RaceSessionRecord>>() {}.type
                val userSaved = gson.fromJson<List<RaceSessionRecord>>(json, type)?.toMutableList() ?: mutableListOf()
                userSaved.removeAll { it.sessionId == sessionId }
                editor.putString(KEY_SESSIONS, gson.toJson(userSaved))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        editor.commit()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dir = File(context.filesDir, "track_sessions")
                if (dir.exists()) {
                    dir.listFiles()?.forEach { f ->
                        if (f.name.contains(sessionId)) {
                            f.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 從 assets/backup_sessions/ 自動讀取 35 筆 Tstarz 真實錄製 JSON
     */
    private fun loadBackupSessionsFromAssets(context: Context): List<RaceSessionRecord> {
        val list = mutableListOf<RaceSessionRecord>()
        try {
            val files = context.assets.list("backup_sessions") ?: return emptyList()
            for (filename in files) {
                if (!filename.endsWith(".json")) continue
                try {
                    val stream = context.assets.open("backup_sessions/$filename")
                    val reader = InputStreamReader(stream)
                    val parsedElement = com.google.gson.JsonParser.parseReader(reader)
                    if (!parsedElement.isJsonObject) continue
                    val jsonObj = parsedElement.asJsonObject

                    val ptsArray = jsonObj.getAsJsonArray("points") ?: continue
                    if (ptsArray.size() < 2) continue

                    val ptsList = mutableListOf<TrackPoint>()
                    var maxSpeed = 0f
                    var sumSpeed = 0f
                    var maxLean = 0f
                    var maxG = 0f

                    for (elem in ptsArray) {
                        val ptObj = elem.asJsonObject
                        val lt = ptObj.get("lt")?.asDouble ?: 0.0
                        val lg = ptObj.get("lg")?.asDouble ?: 0.0
                        val spd = ptObj.get("s")?.asFloat ?: 0f
                        val t = ptObj.get("t")?.asLong ?: 0L
                        val lean = ptObj.get("l")?.asFloat ?: 0f
                        val g = ptObj.get("g")?.asFloat ?: 0f

                        val ga = ptObj.get("ga")?.asFloat ?: 0f
                        val gb = ptObj.get("gb")?.asFloat ?: 0f

                        if (spd > maxSpeed) maxSpeed = spd
                        if (lean > maxLean) maxLean = lean
                        if (g > maxG) maxG = g
                        sumSpeed += spd

                        // 歷史錄製檔容錯：若舊紀錄 JSON 的 l (傾角) 為 0.0，依據 G 力與前後轉向角自動回補合理壓車傾角
                        val effectiveLean = if (lean > 0.1f) {
                            lean
                        } else {
                            val computedFromG = (kotlin.math.abs(g) * 14.5f).coerceIn(0.0f, 48.0f)
                            computedFromG
                        }
                        if (effectiveLean > maxLean) maxLean = effectiveLean

                        ptsList.add(
                            TrackPoint(
                                latitude = lt,
                                longitude = lg,
                                speedKmh = spd,
                                timestampMs = t,
                                leanAngle = effectiveLean,
                                accelG = g,
                                latG = ga,
                                longG = gb
                            )
                        )
                    }

                    val headerObj = jsonObj.getAsJsonObject("header")
                    val headerLapTimeMs = headerObj?.get("lapTimeMs")?.asLong ?: 0L

                    val startT = ptsList.first().timestampMs
                    val endT = ptsList.last().timestampMs
                    val calcTimeMs = (endT - startT).coerceAtLeast(0L)
                    val lapTimeMs = if (headerLapTimeMs > 0L && headerLapTimeMs < 86400000L) {
                        headerLapTimeMs
                    } else if (calcTimeMs > 0L) {
                        calcTimeMs
                    } else {
                        60000L
                    }

                    val cleanTrackName = filename
                        .replace(".json", "")
                        .replace("賽道_", "")
                        .replace("_Session", "")
                        .replace("手動紀錄_自錄賽道_", "")

                    val parts = cleanTrackName.split("_")
                    val trackCode = if (parts.isNotEmpty()) parts[0] else "賽道"

                    val headerRecordedAt = headerObj?.get("recordedAt")?.asString
                    val dateStr = when {
                        !headerRecordedAt.isNullOrBlank() -> headerRecordedAt
                        parts.size >= 3 -> "${parts[parts.size - 2]} ${parts[parts.size - 1]}"
                        else -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(endT))
                    }



                    val seconds = (lapTimeMs / 1000) % 60
                    val minutes = (lapTimeMs / (1000 * 60)) % 60
                    val millis = lapTimeMs % 1000
                    val timeDisplay = String.format(Locale.getDefault(), "%02d:%02d.%03d", minutes, seconds, millis)

                    list.add(
                        RaceSessionRecord(
                            sessionId = "bk_$filename",
                            trackCode = trackCode,
                            trackName = cleanTrackName,
                            playerNickname = "車手",
                            vehicleType = if (filename.contains("STR") || filename.contains("fit")) "CAR" else "MOTOR",
                            lapTimeMs = lapTimeMs,
                            lapTimeDisplay = timeDisplay,
                            maxSpeedKmh = maxSpeed,
                            avgSpeedKmh = if (ptsList.isNotEmpty()) sumSpeed / ptsList.size else 0f,
                            maxLeanAngle = maxLean,
                            maxBrakingG = maxG,
                            pointsEarned = 350,
                            recordedAt = dateStr,
                            pointsList = ptsList
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list.sortedBy { it.lapTimeMs }
    }

    fun exportSessionToJsonFile(context: Context, record: RaceSessionRecord): File {
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val safeId = record.sessionId.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val fileName = "賽道_${record.trackCode}_${safeId}.json"
        val file = File(exportDir, fileName)

        val rootObj = JsonObject()
        val headerObj = JsonObject()
        headerObj.addProperty("type", "session")
        headerObj.addProperty("trackName", record.trackName)
        headerObj.addProperty("trackCode", record.trackCode)
        headerObj.addProperty("recordId", record.sessionId)
        headerObj.addProperty("playerNickname", record.playerNickname)
        headerObj.addProperty("recordedAt", record.recordedAt)
        headerObj.addProperty("lapTimeMs", record.lapTimeMs)
        headerObj.addProperty("lapTimeDisplay", record.lapTimeDisplay)
        headerObj.addProperty("maxSpeedKmh", record.maxSpeedKmh)
        headerObj.addProperty("avgSpeedKmh", record.avgSpeedKmh)
        headerObj.addProperty("maxLeanAngle", record.maxLeanAngle)
        headerObj.addProperty("maxBrakingG", record.maxBrakingG)
        rootObj.add("header", headerObj)

        val pointsArray = com.google.gson.JsonArray()
        record.safePointsList.forEach { pt ->
            val ptObj = JsonObject()
            ptObj.addProperty("lt", pt.latitude)
            ptObj.addProperty("lg", pt.longitude)
            ptObj.addProperty("s", pt.speedKmh)
            ptObj.addProperty("t", pt.timestampMs)
            ptObj.addProperty("l", pt.leanAngle)
            ptObj.addProperty("g", pt.accelG)
            pointsArray.add(ptObj)
        }
        rootObj.add("points", pointsArray)

        FileWriter(file).use { writer ->
            gson.toJson(rootObj, writer)
        }
        return file
    }
}
