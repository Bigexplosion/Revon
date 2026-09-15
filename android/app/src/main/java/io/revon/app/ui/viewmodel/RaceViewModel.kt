package io.revon.app.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.revon.app.data.model.Difficulty
import io.revon.app.data.model.RaceResult
import io.revon.app.data.model.RaceSessionRecord
import io.revon.app.data.model.Track
import io.revon.app.data.model.TrackPoint
import io.revon.app.data.model.TrackCategory
import io.revon.app.data.model.VehicleType
import io.revon.app.data.repository.TrackSessionManager
import io.revon.app.data.service.GPSData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class RaceStep {
    VEHICLE_SELECT,
    NOTICE_COUNTDOWN,
    NAVIGATING,
    RACING,
    FINISHED
}

class RaceViewModel(application: Application) : AndroidViewModel(application) {

    private val _step = MutableStateFlow(RaceStep.VEHICLE_SELECT)
    val step: StateFlow<RaceStep> = _step.asStateFlow()

    private val _selectedVehicle = MutableStateFlow("CAR")
    val selectedVehicle: StateFlow<String> = _selectedVehicle.asStateFlow()

    private val _countdown = MutableStateFlow(3)
    val countdown: StateFlow<Int> = _countdown.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _selectedTrack = MutableStateFlow(
        Track(
            code = "136",
            name = "136 Pass",
            nameZh = "136 縣道經典山道",
            region = "TAICHUNG",
            difficulty = Difficulty.HARD,
            distanceKm = 12.5,
            cornersCount = 48,
            startLat = 24.108,
            startLng = 120.780,
            endLat = 24.085,
            endLng = 120.818,
            coverImage = null,
            activeDrivers = 28
        )
    )
    val selectedTrack: StateFlow<Track> = _selectedTrack.asStateFlow()

    private val _savedCategory = MutableStateFlow(TrackCategory.ALL)
    val savedCategory: StateFlow<TrackCategory> = _savedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _lastResult = MutableStateFlow<RaceResult?>(null)
    val lastResult: StateFlow<RaceResult?> = _lastResult.asStateFlow()

    private val _lastSessionRecord = MutableStateFlow<RaceSessionRecord?>(null)
    val lastSessionRecord: StateFlow<RaceSessionRecord?> = _lastSessionRecord.asStateFlow()

    private val recordedPoints = mutableListOf<TrackPoint>()

    private var timerJob: Job? = null
    private var startTimeMs = 0L

    init {
        val pref = getApplication<Application>().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _selectedVehicle.value = pref.getString(KEY_VEHICLE, "CAR") ?: "CAR"
        val savedCategoryName = pref.getString(KEY_CATEGORY, TrackCategory.ALL.name) ?: TrackCategory.ALL.name
        _savedCategory.value = try {
            TrackCategory.valueOf(savedCategoryName)
        } catch (e: Exception) {
            TrackCategory.ALL
        }
    }

    fun setCategory(category: TrackCategory) {
        _savedCategory.value = category
        getApplication<Application>().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CATEGORY, category.name)
            .apply()
    }

    fun setVehicle(vehicle: String) {
        _selectedVehicle.value = vehicle
        getApplication<Application>().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VEHICLE, vehicle)
            .apply()
    }

    fun selectTrack(track: Track) {
        _selectedTrack.value = track
    }

    fun startCountdown() {
        _step.value = RaceStep.NOTICE_COUNTDOWN
        val trackObj = _selectedTrack.value
        val userSharedPref = getApplication<Application>().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val userId = userSharedPref.getInt("user_id", 1)
        val userName = userSharedPref.getString("nickname", "車手") ?: "車手"

        // 發送「正在準備中」至後台
        viewModelScope.launch {
            try {
                val api = io.revon.app.di.NetworkModule.apiService
                api.sendHeartbeat(mapOf(
                    "action" to "start_prepare",
                    "user_id" to userId,
                    "user_name" to userName,
                    "track_id" to trackObj.id,
                    "track_name" to (trackObj.nameZh ?: trackObj.name),
                    "status" to "PREPARING",
                    "vehicle_type" to _selectedVehicle.value
                ))
            } catch (_: Exception) {}
        }

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            _countdown.value = 3
            delay(1000)
            _countdown.value = 2
            delay(1000)
            _countdown.value = 1
            delay(1000)
            _step.value = RaceStep.NAVIGATING
        }
    }

    fun skipCountdown() {
        timerJob?.cancel()
        _step.value = RaceStep.NAVIGATING
    }

    fun startRaceTimer() {
        _step.value = RaceStep.RACING
        startTimeMs = System.currentTimeMillis()
        recordedPoints.clear()
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val startLat = _selectedTrack.value.startLat ?: 24.108
            val startLng = _selectedTrack.value.startLng ?: 120.780
            var i = 0
            val trackObj = _selectedTrack.value
            val userSharedPref = getApplication<Application>().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val userId = userSharedPref.getInt("user_id", 1)
            val userName = userSharedPref.getString("nickname", "車手") ?: "車手"

            while (_step.value == RaceStep.RACING) {
                val curMs = System.currentTimeMillis() - startTimeMs
                _elapsedMs.value = curMs

                val progress = ((curMs % 60000) / 60000f).coerceIn(0f, 1f)
                val spd = (50f + 50f * Math.sin(progress * Math.PI * 2).toFloat()).coerceAtLeast(20f)
                val lean = (35f * Math.sin(progress * Math.PI * 4).toFloat())
                val accel = (0.6f * Math.cos(progress * Math.PI * 2).toFloat())

                // 每 200ms (~5Hz) 收集一次軌跡 telemetry 點位
                if (i % 12 == 0) {
                    val lat = startLat + 0.0002 * (i / 12) * Math.cos(progress * Math.PI)
                    val lng = startLng + 0.0003 * (i / 12) * Math.sin(progress * Math.PI)

                    recordedPoints.add(
                        TrackPoint(
                            latitude = lat,
                            longitude = lng,
                            altitude = 200.0 + (i / 12),
                            speedKmh = spd,
                            timestampMs = System.currentTimeMillis(),
                            leanAngle = Math.abs(lean),
                            accelG = accel,
                            latG = Math.abs(lean) / 20f
                        )
                    )
                }

                // 每 3 秒 (~180 次 16ms 循環) 向後台發送一筆 Realtime Heartbeat
                if (i % 180 == 0) {
                    try {
                        val api = io.revon.app.di.NetworkModule.apiService
                        val heartbeatBody = mapOf(
                            "action" to "heartbeat",
                            "user_id" to userId,
                            "user_name" to userName,
                            "track_id" to trackObj.id,
                            "track_name" to (trackObj.nameZh ?: trackObj.name),
                            "progress_pct" to (progress * 100f),
                            "speed" to spd,
                            "vehicle_type" to _selectedVehicle.value
                        )
                        launch {
                            try { api.sendHeartbeat(heartbeatBody) } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                }

                i++
                delay(16) // 16ms 極速流暢刷新
            }
        }
    }

    fun finishRace(
        driverNickname: String,
        realGpsPoints: List<TrackPoint> = emptyList(),
        laps: List<io.revon.app.data.model.LapInfo> = emptyList(),
        bestLapMs: Long? = null,
        sessionId: String? = null
    ): RaceSessionRecord {
        timerJob?.cancel()
        val finalTimeMs = bestLapMs ?: _elapsedMs.value.coerceAtLeast(1000L)
        val track = _selectedTrack.value

        val displayTime = formatLapTime(finalTimeMs)
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        val vehicleEnum = when (_selectedVehicle.value) {
            "MOTOR" -> VehicleType.MOTOR
            "OTHER" -> VehicleType.OTHER
            else -> VehicleType.CAR
        }

        val ptsList = if (realGpsPoints.isNotEmpty()) realGpsPoints else recordedPoints.toList()

        val maxSpeed = ptsList.maxOfOrNull { it.speedKmh } ?: 108.5f
        val avgSpeed = if (ptsList.isNotEmpty()) ptsList.map { it.speedKmh }.average().toFloat() else 62.0f
        val maxLean = ptsList.maxOfOrNull { it.leanAngle } ?: 0.0f
        val maxBrakingG = ptsList.map { if (it.accelG < 0f) kotlin.math.abs(it.accelG) else 0f }
            .maxOrNull()?.takeIf { it > 0.05f } ?: 0.65f

        val result = RaceResult(
            trackCode = track.code,
            trackName = track.nameZh ?: track.name,
            playerNickname = driverNickname.ifBlank { "車手" },
            vehicleType = vehicleEnum,
            finishTimeMs = finalTimeMs,
            finishTimeDisplay = displayTime,
            recordedAt = dateStr,
            region = track.region,
            clubId = null,
            clubName = null,
            isCustomRoute = false,
            customRouteId = null,
            customRouteName = null
        )

        val targetSessionId = sessionId.takeIf { !it.isNullOrBlank() } ?: ("sess_" + System.currentTimeMillis())

        val sessionRecord = RaceSessionRecord(
            sessionId = targetSessionId,
            trackCode = track.code,
            trackName = track.nameZh ?: track.name,
            playerNickname = driverNickname.ifBlank { "車手" },
            vehicleType = _selectedVehicle.value,
            lapTimeMs = finalTimeMs,
            lapTimeDisplay = displayTime,
            maxSpeedKmh = maxSpeed,
            avgSpeedKmh = avgSpeed,
            maxLeanAngle = maxLean,
            maxBrakingG = maxBrakingG,
            pointsEarned = 350,
            recordedAt = dateStr,
            pointsList = ptsList,
            laps = laps
        )

        TrackSessionManager.saveSession(getApplication(), sessionRecord)

        // 通知後端將即時競速 active_sessions 標記為 FINISHED 並保留顯示完跑時間
        try {
            val userSharedPref = getApplication<Application>().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val userId = userSharedPref.getInt("user_id", 1)
            viewModelScope.launch {
                try {
                    val api = io.revon.app.di.NetworkModule.apiService
                    api.finishSession(mapOf(
                        "action" to "finish_session",
                        "user_id" to userId,
                        "time_display" to displayTime
                    ))
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        _lastResult.value = result
        _lastSessionRecord.value = sessionRecord
        _step.value = RaceStep.FINISHED
        return sessionRecord
    }

    fun resetToVehicleSelect() {
        timerJob?.cancel()
        _elapsedMs.value = 0L

        // 中途按下取消 / 停止或跳出時，自動發送 cancel_session 清除數據
        try {
            val userSharedPref = getApplication<Application>().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val userId = userSharedPref.getInt("user_id", 1)
            viewModelScope.launch {
                try {
                    val api = io.revon.app.di.NetworkModule.apiService
                    api.finishSession(mapOf("action" to "cancel_session", "user_id" to userId))
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        _step.value = RaceStep.VEHICLE_SELECT
    }

    fun formatLapTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val millis = ms % 1000
        return String.format(Locale.getDefault(), "%02d:%02d.%03d", minutes, seconds, millis)
    }

    companion object {
        private const val PREF_NAME = "race_vm_pref"
        private const val KEY_VEHICLE = "last_selected_vehicle"
        private const val KEY_CATEGORY = "last_selected_category"
    }
}
