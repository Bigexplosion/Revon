package com.example.qstart.revon

import android.location.Location
import org.json.JSONObject

/**
 * 【起終點判定與虛擬閘門引擎】
 * 抽取自 CircuitFragment.kt 的核心邏輯，提供獨立無 UI 依賴的賽道觸發判斷系統。
 * 包含：
 * 1. 虛擬閘門 (Vector Dot Product) 穿越檢測
 * 2. 傳統圓形半徑 (Radius Trigger) 觸發檢測
 * 3. 環狀賽道 (Circuit Loop)Lap Lap 遞增與 Lap Lap無縫過線結算
 * 4. Pit 區多邊形判定 (Ray-Casting Algorithm) 與暖胎進場鎖定
 * 5. 逆向行駛 (Wrong Way) 檢測
 */
class VirtualGateEngine {

    data class GeoPoint2D(val latitude: Double, val longitude: Double, val altitude: Double = 0.0) {
        fun distanceToMeters(other: GeoPoint2D): Double {
            val results = FloatArray(1)
            Location.distanceBetween(this.latitude, this.longitude, other.latitude, other.longitude, results)
            return results[0].toDouble()
        }
    }

    data class TrackDefinition(
        val name: String,
        val points: List<GeoPoint2D>,
        val startPoint: GeoPoint2D,
        val endPoint: GeoPoint2D,
        val isLoop: Boolean = false,
        val pitPolygon: List<GeoPoint2D>? = null,
        val pitEntrance: GeoPoint2D? = null,
        val pitEntranceRadius: Double = 15.0,
        val pitExit: GeoPoint2D? = null,
        val pitExitRadius: Double = 15.0
    )

    enum class TriggerMode { VECTOR, RADIUS }

    private var lastStartDotProduct = 0.0
    private var lastFinishDotProduct = 0.0
    private var lastTrackIndex = -1
    private var wrongWayStartTime = 0L
    private var isEntranceLocked = false

    /**
     * 起點虛擬閘門判定
     * @param currentPt 當前車輛 GPS 座標
     * @param location Android Location 物件
     * @param track 賽道定義
     * @param triggerMode 觸發模式 (VECTOR 向量點積或 RADIUS 半徑觸發)
     * @param triggerRadius 觸發半徑 (公尺)
     * @return Boolean 是否觸發起點衝刺
     */
    fun checkStartTrigger(
        currentPt: GeoPoint2D,
        location: Location,
        track: TrackDefinition,
        triggerMode: TriggerMode = TriggerMode.VECTOR,
        triggerRadius: Double = 35.0
    ): Boolean {
        val distToStart = currentPt.distanceToMeters(track.startPoint)
        val closestIdx = findClosestPointIndex(currentPt, track.points)
        if (closestIdx == -1) return false
        val progress = closestIdx.toDouble() / track.points.size

        // 1. 中途進場鎖定 (暖胎圈機制)
        if (!isEntranceLocked && progress > 0.10 && progress < 0.90 && distToStart < 100.0) {
            isEntranceLocked = true
        }

        // 2. 解鎖機制：進入起點區域解鎖
        if (isEntranceLocked && progress > 0.95) {
            isEntranceLocked = false
        }

        // 3. 起點過線觸發判定
        if (!isEntranceLocked && distToStart < triggerRadius) {
            val isCorrectDir = isCorrectDirection(location, track)

            if (triggerMode == TriggerMode.VECTOR) {
                if (track.points.isEmpty()) return false
                // 向量 A: 起點 -> 當前位置
                val vectorA = doubleArrayOf(currentPt.latitude - track.startPoint.latitude, currentPt.longitude - track.startPoint.longitude)
                // 向量 B: 賽道初始前進方向 (取 startPoint 到第 5 個點)
                val targetIdx = if (track.points.size > 5) 5 else track.points.size - 1
                val vectorB = doubleArrayOf(track.points[targetIdx].latitude - track.startPoint.latitude, track.points[targetIdx].longitude - track.startPoint.longitude)

                // 計算點積
                val currentDot = vectorA[0] * vectorB[0] + vectorA[1] * vectorB[1]

                // 觸發條件：點積由負(閘門後方)轉為正(閘門前方) 且 車頭方向正確
                val triggered = (lastStartDotProduct < 0 && currentDot >= 0 && isCorrectDir && progress < 0.15)
                lastStartDotProduct = currentDot
                if (triggered) return true
            } else {
                // 傳統半徑範圍觸發模式
                if (isCorrectDir && progress < 0.10) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * 終點與連刷過線檢測
     * @param currentPt 當前車輛 GPS 座標
     * @param elapsedTimeMs 計時經歷時間 (毫秒)
     * @param track 賽道定義
     * @param triggerMode 觸發模式
     * @param triggerRadius 觸發半徑 (公尺)
     * @return Boolean 是否完成一圈
     */
    fun checkFinishTrigger(
        currentPt: GeoPoint2D,
        elapsedTimeMs: Long,
        track: TrackDefinition,
        triggerMode: TriggerMode = TriggerMode.VECTOR,
        triggerRadius: Double = 35.0
    ): Boolean {
        if (elapsedTimeMs < 10000) return false // 10秒防誤觸保護

        val closestIdx = findClosestPointIndex(currentPt, track.points)
        if (closestIdx == -1) return false
        val lateralDist = currentPt.distanceToMeters(track.points[closestIdx])

        if (track.isLoop) {
            // 環狀賽道 Lap 無縫結算邏輯
            val totalNodes = track.points.size
            if (lateralDist < 15.0) {
                // 偵測從 90% 跳回 10% 的過線瞬間
                if (lastTrackIndex >= totalNodes * 0.90 && closestIdx <= totalNodes * 0.10) {
                    lastTrackIndex = closestIdx
                    return true
                }
            }
            lastTrackIndex = closestIdx
        } else {
            // 點對點賽道終點檢測
            val distToEnd = currentPt.distanceToMeters(track.endPoint)
            if (distToEnd < triggerRadius) {
                if (triggerMode == TriggerMode.VECTOR) {
                    val vectorA = doubleArrayOf(currentPt.latitude - track.endPoint.latitude, currentPt.longitude - track.endPoint.longitude)
                    val startIdx = if (track.points.size > 5) track.points.size - 6 else 0
                    val vectorB = doubleArrayOf(track.endPoint.latitude - track.points[startIdx].latitude, track.endPoint.longitude - track.points[startIdx].longitude)

                    val currentDot = vectorA[0] * vectorB[0] + vectorA[1] * vectorB[1]
                    val triggered = (lastFinishDotProduct < 0 && currentDot >= 0)
                    lastFinishDotProduct = currentDot
                    if (triggered) return true
                } else {
                    return true
                }
            }
        }
        return false
    }

    /**
     * 點對多邊形內部檢測 (Ray-Casting Algorithm 射線法)
     * 用於 Pit 區範圍檢測
     */
    fun isPointInPolygon(point: GeoPoint2D, polygon: List<GeoPoint2D>): Boolean {
        var intersectCount = 0
        val x = point.longitude
        val y = point.latitude
        for (i in polygon.indices) {
            val p1 = polygon[i]
            val p2 = polygon[(i + 1) % polygon.size]
            val x1 = p1.longitude; val y1 = p1.latitude
            val x2 = p2.longitude; val y2 = p2.latitude

            if (((y1 > y) != (y2 > y)) && (x < (x2 - x1) * (y - y1) / (y2 - y1) + x1)) {
                intersectCount++
            }
        }
        return intersectCount % 2 != 0
    }

    /**
     * 方位角比對 (車頭方向 vs 賽道起始向量)
     */
    private fun isCorrectDirection(location: Location, track: TrackDefinition): Boolean {
        if (location.speed < 1.0) return false
        val userBearing = location.bearing

        if (track.points.isEmpty()) return true
        val targetIdx = if (track.points.size > 10) 10 else track.points.size - 1
        val res = FloatArray(2)
        Location.distanceBetween(
            track.points[0].latitude, track.points[0].longitude,
            track.points[targetIdx].latitude, track.points[targetIdx].longitude, res
        )
        val trackBearing = if (res[1] < 0) res[1] + 360f else res[1]
        var diff = Math.abs(userBearing - trackBearing)
        if (diff > 180) diff = 360 - diff
        return diff < 45.0
    }

    private fun findClosestPointIndex(point: GeoPoint2D, trackPoints: List<GeoPoint2D>): Int {
        var minD = Double.MAX_VALUE
        var idx = -1
        for (i in trackPoints.indices) {
            val d = point.distanceToMeters(trackPoints[i])
            if (d < minD) { minD = d; idx = i }
        }
        return idx
    }
}
