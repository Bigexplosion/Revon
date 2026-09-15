package com.example.qstart.revon

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 【IMUSensorFusion - 姿勢投影與物理量計算模組】
 * 抽取自 CircuitFragment / DragRaceFragment
 *
 * 核心解決問題：
 * 1. 手機隨意固定在車架上（直放/橫放/傾斜）導致的加減速三軸分量錯亂。
 * 2. 透過 Gram-Schmidt 正交化建立動態車輛坐標系 (前進軸 forwardW、橫向軸 rightW、下方軸 downW)。
 * 3. 即時剝離重力分量，計算絕無動態坡度與夾角誤差的縱向加速 G、縱向煞車 G、側向 Lat G 及機車壓車傾角 (Lean Angle)。
 */
class IMUSensorFusion {

    data class FusionOutput(
        val accelG: Double,     // 縱向加速 G 力 (向前推進為正)
        val brakingG: Double,   // 縱向煞車 G 力 (減速為正)
        val latG: Double,       // 側向 G 力 (絕對值)
        val gForce: Double,     // 總 G 力合向量
        val leanAngle: Double,  // 機車壓車傾角 (度)
        val rightW: DoubleArray // 車身橫向軸向量
    )

    private val alpha = 0.08f
    private val filteredG = FloatArray(3)
    private var slopeBiasG = 0.0

    /**
     * 處理單幀加速度感測器數值
     * @param rawAccel 三軸原始加速度計數值 (m/s^2)
     * @param gravityBaseline 校準時儲存的重力基準向量 FloatArray(3) (若無則使用 [0, 0, 9.80665])
     * @param isCarMode 是否為汽車模式 (汽車模式傾角強制為 0)
     */
    fun processAccelerometerSample(
        rawAccel: FloatArray,
        gravityBaseline: FloatArray?,
        isCarMode: Boolean = false
    ): FusionOutput {
        // 低通通濾波 (Low Pass Filter)
        for (i in 0..2) {
            filteredG[i] = filteredG[i] + alpha * (rawAccel[i] - filteredG[i])
        }

        val baseline = gravityBaseline ?: floatArrayOf(0f, 0f, 9.80665f)
        val baseNorm = sqrt((baseline[0] * baseline[0] + baseline[1] * baseline[1] + baseline[2] * baseline[2]).toDouble())

        // 1. 定位下方軸 downW
        val downW = if (baseNorm > 0.1) {
            doubleArrayOf(baseline[0] / baseNorm, baseline[1] / baseNorm, baseline[2] / baseNorm)
        } else {
            doubleArrayOf(0.0, 0.0, 1.0)
        }

        // 2. Gram-Schmidt 正交化：從手機 Y 軸投影至水平面，獲得前進軸 forwardW
        val yDotDown = 0 * downW[0] + 1 * downW[1] + 0 * downW[2]
        var forwardW = doubleArrayOf(0 - yDotDown * downW[0], 1 - yDotDown * downW[1], 0 - yDotDown * downW[2])
        val fNorm = sqrt(forwardW[0] * forwardW[0] + forwardW[1] * forwardW[1] + forwardW[2] * forwardW[2])
        if (fNorm > 1e-6) {
            forwardW = doubleArrayOf(forwardW[0] / fNorm, forwardW[1] / fNorm, forwardW[2] / fNorm)
        }

        // 3. 叉積計算橫向軸 rightW (forward × down)
        val rightW = doubleArrayOf(
            forwardW[1] * downW[2] - forwardW[2] * downW[1],
            forwardW[2] * downW[0] - forwardW[0] * downW[2],
            forwardW[0] * downW[1] - forwardW[1] * downW[0]
        )

        // 4. 將當前加速度點積投影至前進軸與橫向軸
        val longAccel = filteredG[0] * forwardW[0] + filteredG[1] * forwardW[1] + filteredG[2] * forwardW[2]
        val latAccel = filteredG[0] * rightW[0] + filteredG[1] * rightW[1] + filteredG[2] * rightW[2]

        val currentLatG = abs(latAccel / 9.80665)
        val gForce = sqrt((filteredG[0] * filteredG[0] + filteredG[1] * filteredG[1] + filteredG[2] * filteredG[2]).toDouble()) / 9.80665
        val imuLongG = longAccel / 9.80665

        // 扣除長期坡度誤差
        val fusedLongG = imuLongG - slopeBiasG

        val accelG = if (fusedLongG > 0) fusedLongG else 0.0
        val brakingG = if (fusedLongG < 0) abs(fusedLongG) else 0.0

        // 計算傾角
        val leanAngle = if (isCarMode) {
            0.0
        } else {
            val cRoll = atan2(filteredG[0].toDouble(), sqrt((filteredG[1] * filteredG[1] + filteredG[2] * filteredG[2]).toDouble()))
            val bRoll = atan2(baseline[0].toDouble(), sqrt((baseline[1] * baseline[1] + baseline[2] * baseline[2]).toDouble()))
            abs(Math.toDegrees(cRoll - bRoll))
        }

        return FusionOutput(accelG, brakingG, currentLatG, gForce, leanAngle, rightW)
    }

    /**
     * GPS 與 IMU 低頻坡度估計補償 (Low-Pass Error Correction)
     */
    fun updateSlopeBias(gpsSpeedKmh: Double, lastGpsSpeedKmh: Double, dtGpsSec: Double, fusedLongG: Double) {
        if (dtGpsSec <= 0) return
        val gpsAccel = ((gpsSpeedKmh - lastGpsSpeedKmh) / 3.6) / dtGpsSec
        val gpsG = gpsAccel / 9.80665
        val error = fusedLongG - gpsG
        // 極慢速平滑適應坡度
        slopeBiasG = 0.9 * slopeBiasG + 0.1 * error
    }
}
