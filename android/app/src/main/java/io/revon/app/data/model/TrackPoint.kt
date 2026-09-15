package io.revon.app.data.model

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val speedKmh: Float = 0f,
    val timestampMs: Long = System.currentTimeMillis(),
    val leanAngle: Float = 0f,
    val accelG: Float = 0f, // 縱向加速(+)與煞車(-) G 力 (相容舊版)
    val latG: Float = 0f,   // 側向 G 力 (+右離心力 / -左離心力)
    val longG: Float = 0f   // 前後 G 力 (+加速 / -煞車，用於汽車 2D G-Ball 圓球渲染)
)

