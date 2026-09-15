package io.revon.app.data.repository

import io.revon.app.data.api.ApiService
import io.revon.app.data.model.RaceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApiRaceRepository(private val apiService: ApiService) {

    suspend fun uploadRaceResult(result: RaceResult, trackId: Int, telemetryJson: String? = null): Result<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mutableMapOf<String, Any>(
                    "track_id" to trackId,
                    "track_code" to result.trackCode,
                    "track_name" to (result.trackName ?: result.trackCode),
                    "time_ms" to result.finishTimeMs,
                    "vehicle_type" to result.vehicleType.name,
                    "is_custom" to if (result.isCustomRoute) 1 else 0
                )
                if (!telemetryJson.isNullOrBlank()) {
                    params["telemetry_json"] = telemetryJson
                }

                // 計算防篡改簽章
                val timeMs = result.finishTimeMs
                val secretKey = io.revon.app.BuildConfig.API_SECRET_KEY
                val mac = javax.crypto.Mac.getInstance("HmacSHA256")
                val secretKeySpec = javax.crypto.spec.SecretKeySpec(secretKey.toByteArray(), "HmacSHA256")
                mac.init(secretKeySpec)
                val hashBytes = mac.doFinal("time_ms=$timeMs".toByteArray())
                val signature = hashBytes.joinToString("") { "%02x".format(it) }

                params["signature"] = signature

                val response = apiService.uploadRaceResult(params)
                if (response.status == "success" && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.failure(Exception(response.message ?: "成績上傳失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // --- Mock Firebase functions for compilation ---
    fun listenToOnlineCount(onUpdate: (Int) -> Unit): Any? {
        onUpdate(1)
        return null
    }

    fun listenToTrackOnlineCounts(onUpdate: (Map<String, Int>) -> Unit): Any? {
        onUpdate(emptyMap())
        return null
    }

    fun listenToLatestRecords(onUpdate: (List<Map<String, Any>>) -> Unit): Any? {
        onUpdate(emptyList())
        return null
    }

    suspend fun getLeaderboard(trackCode: String): Result<List<Map<String, Any>>> {
        // Fallback for compilation, later replaced with actual API call
        return Result.success(emptyList())
    }

    fun updateOnlinePresence(uid: String, nickname: String, nearbyTracks: List<String>) {
        // No-op
    }

    fun updateLiveTelemetry(uid: String, nickname: String, vehicleType: String, trackCode: String, trackName: String, speedKmh: Float, accelG: Float, lat: Double, lng: Double, isSos: Boolean, status: String = "RACING") {
        // Real HTTP call to /api/admin/monitor.php
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val params = mapOf<String, Any>(
                    "action" to "heartbeat",
                    "user_id" to (uid.hashCode() and 0x7FFFFFFF),
                    "user_name" to nickname.ifBlank { "車手" },
                    "track_id" to (trackCode.hashCode() and 0x7FFFFFFF),
                    "track_name" to trackName.ifBlank { trackCode },
                    "vehicle_type" to vehicleType,
                    "progress_pct" to 0.0f,
                    "speed" to speedKmh,
                    "status" to status
                )
                apiService.sendHeartbeat(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopLiveTelemetry(uid: String) {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val params = mapOf<String, Any>(
                    "action" to "cancel_session",
                    "user_id" to (uid.hashCode() and 0x7FFFFFFF)
                )
                apiService.sendHeartbeat(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
