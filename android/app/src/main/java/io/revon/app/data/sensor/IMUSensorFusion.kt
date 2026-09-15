package io.revon.app.data.sensor

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * IMUSensorFusion - 姿態投影與物理量計算模組 (從 Tstar 模組移植)
 */
class IMUSensorFusion {

    data class FusionOutput(
        val accelG: Double,     // 縱向加速 G 力
        val brakingG: Double,   // 縱向煞車 G 力
        val latG: Double,       // 側向 G 力 (絕對值)
        val signedLatG: Double, // 帶正負號側向 G 力 (+右 / -左)
        val imuLongG: Double,   // 帶正負號縱向 G 力 (+加速 / -煞車)
        val gForce: Double,     // 總 G 力合向量
        val leanAngle: Double   // 機車壓車傾角 (度)
    )

    private val alpha = 0.08f
    private val filteredG = FloatArray(3)

    fun processAccelerometerSample(
        rawAccel: FloatArray,
        gravityBaseline: FloatArray?,
        isCarMode: Boolean = false
    ): FusionOutput {
        for (i in 0..2) {
            filteredG[i] = filteredG[i] + alpha * (rawAccel[i] - filteredG[i])
        }

        val baseline = gravityBaseline ?: floatArrayOf(0f, 0f, 9.80665f)
        val baseNorm = sqrt((baseline[0] * baseline[0] + baseline[1] * baseline[1] + baseline[2] * baseline[2]).toDouble())

        val downW = if (baseNorm > 0.1) {
            doubleArrayOf(baseline[0] / baseNorm, baseline[1] / baseNorm, baseline[2] / baseNorm)
        } else {
            doubleArrayOf(0.0, 0.0, 1.0)
        }

        val yDotDown = 0 * downW[0] + 1 * downW[1] + 0 * downW[2]
        var forwardW = doubleArrayOf(0 - yDotDown * downW[0], 1 - yDotDown * downW[1], 0 - yDotDown * downW[2])
        val fNorm = sqrt(forwardW[0] * forwardW[0] + forwardW[1] * forwardW[1] + forwardW[2] * forwardW[2])
        if (fNorm > 1e-6) {
            forwardW = doubleArrayOf(forwardW[0] / fNorm, forwardW[1] / fNorm, forwardW[2] / fNorm)
        }

        val rightW = doubleArrayOf(
            forwardW[1] * downW[2] - forwardW[2] * downW[1],
            forwardW[2] * downW[0] - forwardW[0] * downW[2],
            forwardW[0] * downW[1] - forwardW[1] * downW[0]
        )

        val longAccel = filteredG[0] * forwardW[0] + filteredG[1] * forwardW[1] + filteredG[2] * forwardW[2]
        val latAccel = filteredG[0] * rightW[0] + filteredG[1] * rightW[1] + filteredG[2] * rightW[2]

        val signedLatG = latAccel / 9.80665
        val currentLatG = abs(signedLatG)
        val gForce = sqrt((filteredG[0] * filteredG[0] + filteredG[1] * filteredG[1] + filteredG[2] * filteredG[2]).toDouble()) / 9.80665
        val imuLongG = longAccel / 9.80665

        val accelG = if (imuLongG > 0) imuLongG else 0.0
        val brakingG = if (imuLongG < 0) abs(imuLongG) else 0.0

        val leanAngle = if (isCarMode) {
            0.0
        } else {
            val cRoll = atan2(filteredG[0].toDouble(), sqrt((filteredG[1] * filteredG[1] + filteredG[2] * filteredG[2]).toDouble()))
            val bRoll = atan2(baseline[0].toDouble(), sqrt((baseline[1] * baseline[1] + baseline[2] * baseline[2]).toDouble()))
            abs(Math.toDegrees(cRoll - bRoll))
        }

        return FusionOutput(accelG, brakingG, currentLatG, signedLatG, imuLongG, gForce, leanAngle)
    }

}
