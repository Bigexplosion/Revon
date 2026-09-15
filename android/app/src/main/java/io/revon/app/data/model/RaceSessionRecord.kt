package io.revon.app.data.model

data class LapInfo(
    val lapNumber: Int,
    val lapTimeMs: Long,
    val lapTimeDisplay: String,
    val diffToBestMs: Long = 0L,
    val pointsList: List<TrackPoint>? = emptyList()
) {
    val safePointsList: List<TrackPoint>
        get() = pointsList ?: emptyList()
}

data class RaceSessionRecord(
    val sessionId: String,
    val trackCode: String,
    val trackName: String,
    val playerNickname: String,
    val vehicleType: String,
    val lapTimeMs: Long,
    val lapTimeDisplay: String,
    val maxSpeedKmh: Float,
    val avgSpeedKmh: Float,
    val maxLeanAngle: Float,
    val maxBrakingG: Float,
    val pointsEarned: Int,
    val recordedAt: String,
    val pointsList: List<TrackPoint>? = emptyList(),
    val laps: List<LapInfo>? = emptyList()
) {
    val safeLaps: List<LapInfo>
        get() = laps ?: emptyList()

    val safePointsList: List<TrackPoint>
        get() = pointsList ?: emptyList()
}
