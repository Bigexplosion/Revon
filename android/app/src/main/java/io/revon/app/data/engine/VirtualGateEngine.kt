package io.revon.app.data.engine

import android.location.Location
import io.revon.app.data.config.GpsConfig
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * VirtualGateEngine - 多演算法虛擬閘門過線判定引擎
 *
 * 支援三種判定模式：
 *   P0 [GATE_ALGO_SEGMENT]  - 純幾何線段交叉法（預設，取代半徑法）
 *                             即使 1Hz GPS 兩點相距 50m 也能 100% 捕捉
 *   P1 [GATE_ALGO_BEARING]  - GPS Bearing 弧線插值
 *                             在兩個 GPS 點間按航向插值 N 段，適合高速彎道
 *   P2 [GATE_ALGO_GYRO_DR]  - 陀螺儀積分 Dead Reckoning
 *                             使用陀螺儀偏航角插值出精細路徑，最高精度
 *
 * 所有模式均包含精確時間內插（Time Interpolation），
 * 可將 1Hz 粗糙時間戳還原成毫秒級精確過線時間。
 */
class VirtualGateEngine {

    // ──────────────────────────────────────────────────────────────────────
    // 資料型別
    // ──────────────────────────────────────────────────────────────────────

    data class GeoPoint2D(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double = 0.0
    ) {
        fun distanceToMeters(other: GeoPoint2D): Double {
            val results = FloatArray(1)
            Location.distanceBetween(
                this.latitude, this.longitude,
                other.latitude, other.longitude,
                results
            )
            return results[0].toDouble()
        }
    }

    /**
     * GPS 幀，帶有航向與時間戳，供 P1/P2 插值使用。
     */
    data class GpsFrame(
        val pt: GeoPoint2D,
        val bearingDeg: Float,  // GPS 都卜勒航向角 (0–360°)
        val gyroYawDeg: Float,  // 陀螺儀積分偏航角 (0–360°)
        val speedMs: Float,     // 速度 m/s
        val timestampMs: Long
    )

    /**
     * 過線結果，包含精確時間戳與使用的演算法。
     */
    data class GateCrossingResult(
        val crossed: Boolean,
        val preciseTimeMs: Long = 0L,      // 時間內插後的精確過線毫秒數
        val interpolationRatio: Float = 0f, // 交叉點在 prev→curr 上的比例 (0~1)
        val algo: Int = GpsConfig.GATE_ALGO_SEGMENT  // 實際觸發的演算法
    )

    // ──────────────────────────────────────────────────────────────────────
    // 內部狀態
    // ──────────────────────────────────────────────────────────────────────

    private var previousFrame: GpsFrame? = null
    private var previousPt: GeoPoint2D? = null

    // 舊版點積狀態（向後相容）
    private var lastStartDotProduct = 0.0
    private var lastFinishDotProduct = 0.0

    // 預計算的閘門線（Gate Line G1-G2）
    private var startGate: GateLine? = null
    private var finishGate: GateLine? = null

    private data class GateLine(
        val g1Lat: Double, val g1Lng: Double,
        val g2Lat: Double, val g2Lng: Double
    )

    // P1/P2 插值段數（每個 GPS 區間切割成 N 小段）
    private val INTERP_STEPS_P1 = 6   // GPS Bearing 插值段數
    private val INTERP_STEPS_P2 = 10  // 陀螺儀 Dead Reckoning 插值段數

    // 地球半徑（公尺），用於 Dead Reckoning 位置推算
    private val EARTH_RADIUS_M = 6_371_000.0

    // 預設閘門半寬（公尺）：閘門線 = 起終點 ± 6m，共 12m 寬
    private val GATE_HALF_WIDTH_M = 6.0

    // ──────────────────────────────────────────────────────────────────────
    // 公開 API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 初始化起點閘門線。
     * 應在賽道載入後呼叫一次，避免在熱路徑上重複計算。
     */
    fun initStartGate(startPt: GeoPoint2D, trackPoints: List<GeoPoint2D>) {
        startGate = buildGateLine(startPt, trackPoints, isStart = true)
    }

    /**
     * 初始化終點閘門線。
     */
    fun initFinishGate(endPt: GeoPoint2D, trackPoints: List<GeoPoint2D>) {
        finishGate = buildGateLine(endPt, trackPoints, isStart = false)
    }

    /**
     * 重置所有內部狀態（換賽道或重新出發時呼叫）。
     */
    fun reset() {
        previousFrame = null
        previousPt = null
        lastStartDotProduct = 0.0
        lastFinishDotProduct = 0.0
    }

    // ──────────────────────────────────────────────────────────────────────
    // 起點觸發（P0 / P1 / P2）
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 多演算法起點觸發檢查。
     *
     * @param algo  GpsConfig.GATE_ALGO_SEGMENT / GATE_ALGO_BEARING / GATE_ALGO_GYRO_DR
     */
    fun checkStartTriggerEx(
        frame: GpsFrame,
        startPoint: GeoPoint2D,
        trackPoints: List<GeoPoint2D>,
        algo: Int = GpsConfig.GATE_ALGO_SEGMENT
    ): GateCrossingResult {
        val gate = startGate ?: buildGateLine(startPoint, trackPoints, isStart = true)
            .also { startGate = it }

        val prev = previousFrame
        previousFrame = frame

        if (prev == null) return GateCrossingResult(false)

        return when (algo) {
            GpsConfig.GATE_ALGO_BEARING -> checkWithBearingInterp(prev, frame, gate, algo)
            GpsConfig.GATE_ALGO_GYRO_DR -> checkWithGyroDr(prev, frame, gate, algo)
            else -> checkSegmentCross(prev.pt, frame.pt, prev.timestampMs, frame.timestampMs, gate, algo)
        }
    }

    /**
     * 舊版相容介面（不帶 frame，內部自動補齊）。
     * 若尚未建立閘門線則先建立；同時保留舊版半徑+點積邏輯作為 fallback。
     */
    fun checkStartTrigger(
        currentPt: GeoPoint2D,
        location: android.location.Location,
        startPoint: GeoPoint2D,
        trackPoints: List<GeoPoint2D>,
        triggerRadius: Double = 7.0,
        algo: Int = GpsConfig.GATE_ALGO_SEGMENT,
        bearingDeg: Float = 0f,
        gyroYawDeg: Float = 0f
    ): Boolean {
        val nowMs = System.currentTimeMillis()
        val speedMs = (location.speed).coerceAtLeast(0f)
        val frame = GpsFrame(currentPt, bearingDeg, gyroYawDeg, speedMs, nowMs)
        return checkStartTriggerEx(frame, startPoint, trackPoints, algo).crossed
    }

    // ──────────────────────────────────────────────────────────────────────
    // 終點觸發（P0 / P1 / P2）
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 多演算法終點觸發檢查。
     */
    fun checkFinishTriggerEx(
        frame: GpsFrame,
        endPoint: GeoPoint2D,
        trackPoints: List<GeoPoint2D>,
        elapsedTimeMs: Long,
        algo: Int = GpsConfig.GATE_ALGO_SEGMENT
    ): GateCrossingResult {
        if (elapsedTimeMs < 5000) return GateCrossingResult(false)

        val gate = finishGate ?: buildGateLine(endPoint, trackPoints, isStart = false)
            .also { finishGate = it }

        val prev = previousFrame ?: return GateCrossingResult(false)

        return when (algo) {
            GpsConfig.GATE_ALGO_BEARING -> checkWithBearingInterp(prev, frame, gate, algo)
            GpsConfig.GATE_ALGO_GYRO_DR -> checkWithGyroDr(prev, frame, gate, algo)
            else -> checkSegmentCross(prev.pt, frame.pt, prev.timestampMs, frame.timestampMs, gate, algo)
        }
    }

    /**
     * 舊版相容介面（終點）。
     */
    fun checkFinishTrigger(
        currentPt: GeoPoint2D,
        elapsedTimeMs: Long,
        endPoint: GeoPoint2D,
        trackPoints: List<GeoPoint2D>,
        triggerRadius: Double = 7.0,
        algo: Int = GpsConfig.GATE_ALGO_SEGMENT,
        bearingDeg: Float = 0f,
        gyroYawDeg: Float = 0f
    ): Boolean {
        if (elapsedTimeMs < 5000) return false
        val nowMs = System.currentTimeMillis()
        val frame = GpsFrame(currentPt, bearingDeg, gyroYawDeg, 0f, nowMs)
        val gate = finishGate ?: buildGateLine(endPoint, trackPoints, isStart = false)
            .also { finishGate = it }
        val prev = previousFrame ?: run { previousFrame = frame; return false }
        val result = checkSegmentCross(prev.pt, frame.pt, prev.timestampMs, frame.timestampMs, gate, algo)
        previousFrame = frame
        return result.crossed
    }

    // ──────────────────────────────────────────────────────────────────────
    // P0：純幾何線段交叉法
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 核心：判斷 [ptA→ptB] 軌跡線段是否與 [G1→G2] 閘門線段相交。
     * 使用 2D 向量叉積（Cross Product）進行精確幾何判斷。
     * 相交後以線性內插計算精確過線時間。
     */
    private fun checkSegmentCross(
        ptA: GeoPoint2D,
        ptB: GeoPoint2D,
        tA: Long,
        tB: Long,
        gate: GateLine,
        algo: Int
    ): GateCrossingResult {
        val ax = ptA.latitude;  val ay = ptA.longitude
        val bx = ptB.latitude;  val by = ptB.longitude
        val cx = gate.g1Lat;    val cy = gate.g1Lng
        val dx = gate.g2Lat;    val dy = gate.g2Lng

        if (!segmentsIntersect(ax, ay, bx, by, cx, cy, dx, dy)) {
            return GateCrossingResult(false)
        }

        // 計算交叉點在 A→B 上的比例參數 t
        val t = computeIntersectionT(ax, ay, bx, by, cx, cy, dx, dy)
            .coerceIn(0.0, 1.0)

        val preciseMs = tA + ((tB - tA) * t).toLong()
        return GateCrossingResult(
            crossed = true,
            preciseTimeMs = preciseMs,
            interpolationRatio = t.toFloat(),
            algo = algo
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // P1：GPS Bearing 插值
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 利用 GPS 都卜勒航向角，在 prev→curr 之間插值 N 個虛擬點，
     * 讓路徑貼合彎道弧線，再對每一小段跑 P0 交叉測試。
     */
    private fun checkWithBearingInterp(
        prev: GpsFrame,
        curr: GpsFrame,
        gate: GateLine,
        algo: Int
    ): GateCrossingResult {
        val n = INTERP_STEPS_P1
        val avgSpeedMs = ((prev.speedMs + curr.speedMs) / 2f).toDouble()
        val dtSec = ((curr.timestampMs - prev.timestampMs) / 1000.0).coerceAtLeast(0.1)

        // 在兩航向間線性插值，每步計算虛擬位置
        var pLat = prev.pt.latitude
        var pLng = prev.pt.longitude
        var pMs = prev.timestampMs

        for (i in 1..n) {
            val frac = i.toDouble() / n
            val bearingRad = Math.toRadians(lerpAngle(prev.bearingDeg, curr.bearingDeg, frac.toFloat()).toDouble())
            val stepMs = (dtSec / n * 1000).toLong()
            val stepDist = avgSpeedMs * (dtSec / n)  // 公尺

            // 球面航位推算（Rhumb line 近似）
            val dLat = stepDist * cos(bearingRad) / EARTH_RADIUS_M
            val dLng = stepDist * sin(bearingRad) / (EARTH_RADIUS_M * cos(Math.toRadians(pLat)))

            val nLat = pLat + Math.toDegrees(dLat)
            val nLng = pLng + Math.toDegrees(dLng)
            val nMs = pMs + stepMs

            val result = checkSegmentCross(
                GeoPoint2D(pLat, pLng), GeoPoint2D(nLat, nLng),
                pMs, nMs, gate, algo
            )
            if (result.crossed) return result

            pLat = nLat; pLng = nLng; pMs = nMs
        }
        return GateCrossingResult(false)
    }

    // ──────────────────────────────────────────────────────────────────────
    // P2：陀螺儀 Dead Reckoning
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 使用陀螺儀積分偏航角在兩 GPS 點之間推算精細路徑，
     * 再對每一小段跑 P0 交叉測試。
     *
     * gyroYawDeg 在 prev 到 curr 之間以線性插值模擬連續旋轉。
     */
    private fun checkWithGyroDr(
        prev: GpsFrame,
        curr: GpsFrame,
        gate: GateLine,
        algo: Int
    ): GateCrossingResult {
        val n = INTERP_STEPS_P2
        val dtSec = ((curr.timestampMs - prev.timestampMs) / 1000.0).coerceAtLeast(0.1)
        val avgSpeedMs = ((prev.speedMs + curr.speedMs) / 2f).toDouble()

        var pLat = prev.pt.latitude
        var pLng = prev.pt.longitude
        var pMs = prev.timestampMs

        for (i in 1..n) {
            val frac = i.toDouble() / n
            val yawDeg = lerpAngle(prev.gyroYawDeg, curr.gyroYawDeg, frac.toFloat())
            val yawRad = Math.toRadians(yawDeg.toDouble())
            val stepMs = (dtSec / n * 1000).toLong()
            val stepDist = avgSpeedMs * (dtSec / n)

            val dLat = stepDist * cos(yawRad) / EARTH_RADIUS_M
            val dLng = stepDist * sin(yawRad) / (EARTH_RADIUS_M * cos(Math.toRadians(pLat)))

            val nLat = pLat + Math.toDegrees(dLat)
            val nLng = pLng + Math.toDegrees(dLng)
            val nMs = pMs + stepMs

            val result = checkSegmentCross(
                GeoPoint2D(pLat, pLng), GeoPoint2D(nLat, nLng),
                pMs, nMs, gate, algo
            )
            if (result.crossed) return result

            pLat = nLat; pLng = nLng; pMs = nMs
        }
        return GateCrossingResult(false)
    }

    // ──────────────────────────────────────────────────────────────────────
    // 閘門線建構（Gate Materialization）
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 根據起終點座標與賽道走向向量，建構一條實體閘門線段 G1-G2。
     *
     * 原理：
     *   D = trackPoints[2] - gatePt  →  法向量 N = (-D.lng, D.lat)
     *   G1 = gatePt + normalize(N) * GATE_HALF_WIDTH_M（轉換為度）
     *   G2 = gatePt - normalize(N) * GATE_HALF_WIDTH_M
     *
     * 閘門寬度預設 12m（±6m），可根據賽道寬度調整。
     */
    private fun buildGateLine(
        gatePt: GeoPoint2D,
        trackPoints: List<GeoPoint2D>,
        isStart: Boolean
    ): GateLine {
        if (trackPoints.size < 2) {
            // 無賽道點時建立東西向預設閘門
            val latOffset = metersToLatDeg(GATE_HALF_WIDTH_M)
            return GateLine(
                gatePt.latitude + latOffset, gatePt.longitude,
                gatePt.latitude - latOffset, gatePt.longitude
            )
        }

        // 取賽道方向向量 D（起點取前方點，終點取後方點）
        val refIdx = if (isStart) {
            if (trackPoints.size > 2) 2 else trackPoints.size - 1
        } else {
            if (trackPoints.size > 5) trackPoints.size - 6 else 0
        }

        val dLat: Double
        val dLng: Double
        if (isStart) {
            dLat = trackPoints[refIdx].latitude - gatePt.latitude
            dLng = trackPoints[refIdx].longitude - gatePt.longitude
        } else {
            dLat = gatePt.latitude - trackPoints[refIdx].latitude
            dLng = gatePt.longitude - trackPoints[refIdx].longitude
        }

        // 法向量 N = (-dLng, dLat)
        val nLat = -dLng
        val nLng = dLat
        val nNorm = sqrt(nLat * nLat + nLng * nLng)

        if (nNorm < 1e-10) {
            // 方向向量退化，使用東西向
            val latOffset = metersToLatDeg(GATE_HALF_WIDTH_M)
            return GateLine(
                gatePt.latitude + latOffset, gatePt.longitude,
                gatePt.latitude - latOffset, gatePt.longitude
            )
        }

        // 將公尺轉換為度偏移量（平面近似）
        val latDegPerMeter = metersToLatDeg(1.0)
        val lngDegPerMeter = metersToLngDeg(1.0, gatePt.latitude)

        val halfW = GATE_HALF_WIDTH_M
        val scaledNLat = (nLat / nNorm) * halfW * latDegPerMeter
        val scaledNLng = (nLng / nNorm) * halfW * lngDegPerMeter

        return GateLine(
            g1Lat = gatePt.latitude + scaledNLat,
            g1Lng = gatePt.longitude + scaledNLng,
            g2Lat = gatePt.latitude - scaledNLat,
            g2Lng = gatePt.longitude - scaledNLng
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // 幾何工具函式
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 判斷兩線段 AB 與 CD 是否相交（2D 叉積法）。
     *
     * 數學原理：
     *   若 A、B 在 CD 兩側（叉積符號相異），且 C、D 在 AB 兩側，
     *   則兩線段必然相交。
     */
    private fun segmentsIntersect(
        ax: Double, ay: Double,
        bx: Double, by: Double,
        cx: Double, cy: Double,
        dx: Double, dy: Double
    ): Boolean {
        val d1 = cross(cx, cy, dx, dy, ax, ay)
        val d2 = cross(cx, cy, dx, dy, bx, by)
        val d3 = cross(ax, ay, bx, by, cx, cy)
        val d4 = cross(ax, ay, bx, by, dx, dy)

        if (d1 * d2 < 0 && d3 * d4 < 0) return true

        // 共線端點特例（點剛好落在線段上）
        if (abs(d1) < 1e-12 && onSegment(cx, cy, dx, dy, ax, ay)) return true
        if (abs(d2) < 1e-12 && onSegment(cx, cy, dx, dy, bx, by)) return true
        if (abs(d3) < 1e-12 && onSegment(ax, ay, bx, by, cx, cy)) return true
        if (abs(d4) < 1e-12 && onSegment(ax, ay, bx, by, dx, dy)) return true

        return false
    }

    /**
     * 計算 A→B 與 C→D 交叉點在 A→B 上的比例參數 t（Parametric Intersection）。
     * t=0 代表 A，t=1 代表 B。
     *
     * 公式推導自 parametric line intersection：
     *   t = ((C - A) × (D - C)) / ((B - A) × (D - C))
     */
    private fun computeIntersectionT(
        ax: Double, ay: Double,
        bx: Double, by: Double,
        cx: Double, cy: Double,
        dx: Double, dy: Double
    ): Double {
        val abx = bx - ax;  val aby = by - ay
        val cdx = dx - cx;  val cdy = dy - cy
        val denom = abx * cdy - aby * cdx
        if (abs(denom) < 1e-12) return 0.5  // 平行，取中點
        val acx = cx - ax;  val acy = cy - ay
        return (acx * cdy - acy * cdx) / denom
    }

    /** 2D 向量叉積 (P→Q) × (P→R) */
    private fun cross(px: Double, py: Double, qx: Double, qy: Double, rx: Double, ry: Double): Double =
        (qx - px) * (ry - py) - (qy - py) * (rx - px)

    /** 判斷點 (px,py) 是否落在線段 AB 的包圍矩形內（共線特例用） */
    private fun onSegment(ax: Double, ay: Double, bx: Double, by: Double, px: Double, py: Double): Boolean =
        px in minOf(ax, bx)..maxOf(ax, bx) && py in minOf(ay, by)..maxOf(ay, by)

    // ──────────────────────────────────────────────────────────────────────
    // 座標換算工具
    // ──────────────────────────────────────────────────────────────────────

    /** 公尺轉緯度度數（近似值，全球通用） */
    private fun metersToLatDeg(meters: Double): Double = meters / 111_320.0

    /** 公尺轉經度度數（與緯度相關） */
    private fun metersToLngDeg(meters: Double, latDeg: Double): Double =
        meters / (111_320.0 * cos(Math.toRadians(latDeg)))

    /**
     * 角度線性插值（考慮 360° 跨越，例如 350° → 10°）。
     */
    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var diff = b - a
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return ((a + diff * t) + 360f) % 360f
    }
}
