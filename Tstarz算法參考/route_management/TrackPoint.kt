package com.example.qstart.revon

/**
 * 軌跡點與傳感器數據結構
 * @param lat 緯度 (保留 7 位小數，精度達 1cm)
 * @param lng 經度 (保留 7 位小數)
 * @param speed 車速 (km/h)
 * @param timestamp 時間戳記 (毫秒)
 * @param leanAngle 機車車身傾角 (度)
 * @param latG 側向 G 力
 * @param gForce 總加速度 (G)
 * @param accelG 縱向加速 G 力
 * @param brakingG 縱向減速 G 力
 * @param distance 移動累積距離 (公尺)
 * @param fx 三軸加速計 X 軸 (低通濾波)
 * @param fy 三軸加速計 Y 軸
 * @param fz 三軸加速計 Z 軸
 * @param rx 三軸加速計 X 軸 (原始)
 * @param ry 三軸加速計 Y 軸
 * @param rz 三軸加速計 Z 軸
 * @param gx 重力基準 X 軸
 * @param gy 重力基準 Y 軸
 * @param gz 重力基準 Z 軸
 */
data class TrackPoint(
    val lat: Double,
    val lng: Double,
    val speed: Double,
    val timestamp: Long,
    val leanAngle: Double = 0.0,
    val latG: Double = 0.0,
    val gForce: Double = 0.0,
    val accelG: Double = 0.0,
    val brakingG: Double = 0.0,
    val distance: Double = 0.0,
    val fx: Float? = null, val fy: Float? = null, val fz: Float? = null,
    val rx: Float? = null, val ry: Float? = null, val rz: Float? = null,
    val gx: Float? = null, val gy: Float? = null, val gz: Float? = null
)
