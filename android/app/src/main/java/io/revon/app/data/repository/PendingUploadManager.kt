package io.revon.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.revon.app.data.model.RaceSessionRecord
import io.revon.app.di.NetworkModule
import io.revon.app.utils.SyncNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object PendingUploadManager {

    private const val PREF_NAME = "revon_pending_uploads_pref"
    private const val KEY_PENDING = "pending_sessions_json"
    private val gson = Gson()
    private val mutex = Mutex()

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    private val _pendingList = MutableStateFlow<List<RaceSessionRecord>>(emptyList())
    val pendingList: StateFlow<List<RaceSessionRecord>> = _pendingList.asStateFlow()

    private val _currentlyUploadingId = MutableStateFlow<String?>(null)
    val currentlyUploadingId: StateFlow<String?> = _currentlyUploadingId.asStateFlow()

    private val _uploadProgressMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val uploadProgressMap: StateFlow<Map<String, Int>> = _uploadProgressMap.asStateFlow()

    private var isSyncing = false

    fun init(context: Context) {
        val list = getPendingUploads(context)
        _pendingList.value = list
        _pendingCount.value = list.size
    }

    /**
     * 將離線或上傳失敗的計時紀錄存入待補傳佇列
     */
    fun enqueuePendingUpload(context: Context, record: RaceSessionRecord) {
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                val current = getPendingUploads(context).toMutableList()
                if (current.none { it.sessionId == record.sessionId }) {
                    current.add(record)
                    savePendingList(context, current)
                    _pendingList.value = current
                    _pendingCount.value = current.size
                    SyncNotificationManager.showOfflinePendingNotification(context, current.size)
                }
            }
        }
    }

    /**
     * 從待補傳佇列中移除指定紀錄 (取消自動補傳)
     */
    fun removePendingUpload(context: Context, sessionId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                val current = getPendingUploads(context).toMutableList()
                current.removeAll { it.sessionId == sessionId }
                savePendingList(context, current)
                _pendingList.value = current
                _pendingCount.value = current.size

                val newMap = _uploadProgressMap.value.toMutableMap()
                newMap.remove(sessionId)
                _uploadProgressMap.value = newMap

                if (_currentlyUploadingId.value == sessionId) {
                    _currentlyUploadingId.value = null
                }

                if (current.isEmpty()) {
                    SyncNotificationManager.cancelSyncNotification(context)
                } else {
                    SyncNotificationManager.showOfflinePendingNotification(context, current.size)
                }
            }
        }
    }

    fun getPendingUploads(context: Context): List<RaceSessionRecord> {
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = pref.getString(KEY_PENDING, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<RaceSessionRecord>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun savePendingList(context: Context, list: List<RaceSessionRecord>) {
        val json = gson.toJson(list)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING, json)
            .apply()
    }

    /**
     * 連線恢復時，背景自動重試上傳佇列中所有紀錄
     */
    fun triggerAutoSync(context: Context) {
        if (isSyncing) return
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                if (isSyncing) return@launch
                val pending = getPendingUploads(context)
                _pendingList.value = pending
                _pendingCount.value = pending.size

                if (pending.isEmpty()) {
                    _pendingCount.value = 0
                    SyncNotificationManager.cancelSyncNotification(context)
                    return@launch
                }

                isSyncing = true
                SyncNotificationManager.showSyncingNotification(context, pending.size)

                val apiRepo = ApiRaceRepository(NetworkModule.apiService)
                val remainingList = mutableListOf<RaceSessionRecord>()

                val allTracks = TrackRepository.getTracks(context)
                for (record in pending) {
                    // 若該紀錄在同步過程中已被使用者取消，跳過處理
                    val currentFreshList = getPendingUploads(context)
                    if (currentFreshList.none { it.sessionId == record.sessionId }) {
                        continue
                    }

                    _currentlyUploadingId.value = record.sessionId
                    val progressMap = _uploadProgressMap.value.toMutableMap()
                    progressMap[record.sessionId] = 25
                    _uploadProgressMap.value = progressMap

                    try {
                        val matchedTrack = allTracks.find { it.code.equals(record.trackCode, ignoreCase = true) || it.name.equals(record.trackName, ignoreCase = true) }
                        val numericTrackId = matchedTrack?.id ?: record.trackCode.toIntOrNull() ?: 0
                        val isCustomTrack = matchedTrack?.isCustom == true || record.trackCode.startsWith("custom_") || record.trackCode.contains("錄製") || record.trackName.contains("錄製")

                        val vType = try {
                            io.revon.app.data.model.VehicleType.valueOf(record.vehicleType)
                        } catch (e: Exception) {
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

                        val rootObj = com.google.gson.JsonObject()
                        val headerObj = com.google.gson.JsonObject()
                        headerObj.addProperty("type", "session")
                        headerObj.addProperty("trackName", record.trackName)
                        headerObj.addProperty("recordId", record.sessionId)
                        headerObj.addProperty("recordedAt", record.recordedAt)
                        headerObj.addProperty("lapTimeMs", record.lapTimeMs)
                        rootObj.add("header", headerObj)
                        
                        val pointsArray = com.google.gson.JsonArray()
                        record.safePointsList.forEach { pt ->
                            val ptObj = com.google.gson.JsonObject()
                            ptObj.addProperty("lt", pt.latitude)
                            ptObj.addProperty("lg", pt.longitude)
                            ptObj.addProperty("s", pt.speedKmh)
                            ptObj.addProperty("t", pt.timestampMs)
                            ptObj.addProperty("l", pt.leanAngle)
                            ptObj.addProperty("g", pt.accelG)
                            ptObj.addProperty("ga", pt.latG)
                            ptObj.addProperty("gb", pt.longG)
                            pointsArray.add(ptObj)
                        }
                        rootObj.add("points", pointsArray)

                        progressMap[record.sessionId] = 65
                        _uploadProgressMap.value = progressMap

                        val result = apiRepo.uploadRaceResult(resultObj, trackId = numericTrackId, telemetryJson = gson.toJson(rootObj))
                        if (!result.isSuccess) {
                            remainingList.add(record)
                        } else {
                            progressMap[record.sessionId] = 100
                            _uploadProgressMap.value = progressMap
                        }
                    } catch (e: Exception) {
                        remainingList.add(record)
                    }
                }

                _currentlyUploadingId.value = null
                savePendingList(context, remainingList)
                _pendingList.value = remainingList
                _pendingCount.value = remainingList.size

                if (remainingList.isEmpty()) {
                    SyncNotificationManager.cancelSyncNotification(context)
                } else {
                    SyncNotificationManager.showOfflinePendingNotification(context, remainingList.size)
                }

                isSyncing = false
            }
        }
    }
}
