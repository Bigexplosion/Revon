package io.revon.app.data.model

import com.google.gson.annotations.SerializedName

data class LeaderboardItem(
    val rank: Int,
    val playerNickname: String,
    val timeDisplay: String,
    val finishTimeMs: Long,
    @SerializedName("avatar_url") val avatarUrl: String? = null
)

enum class TrackCategory {
    @SerializedName("ALL") ALL,
    @SerializedName("TOUGE") TOUGE,
    @SerializedName("CIRCUIT") CIRCUIT
}

data class Track(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("name_zh") val nameZh: String?,
    @SerializedName("region") val region: String,
    @SerializedName("difficulty") val difficulty: Difficulty,
    @SerializedName("category") val category: TrackCategory = TrackCategory.TOUGE,
    @SerializedName("distance_km") val distanceKm: Double?,
    @SerializedName("corners_count") val cornersCount: Int?,
    @SerializedName("start_lat") val startLat: Double?,
    @SerializedName("start_lng") val startLng: Double?,
    @SerializedName("end_lat") val endLat: Double?,
    @SerializedName("end_lng") val endLng: Double?,
    @SerializedName("mid1_lat") val mid1Lat: Double? = null,
    @SerializedName("mid1_lng") val mid1Lng: Double? = null,
    @SerializedName("mid2_lat") val mid2Lat: Double? = null,
    @SerializedName("mid2_lng") val mid2Lng: Double? = null,
    @SerializedName("cover_image") val coverImage: String?,
    @SerializedName("active_drivers") val activeDrivers: Int = 0,
    val subtitle: String? = null,
    val top3: List<LeaderboardItem>? = emptyList(),
    val isCustom: Boolean = false
) {
    val safeTop3: List<LeaderboardItem>
        get() = top3 ?: emptyList()
}

enum class Difficulty {
    @SerializedName("NORMAL") NORMAL,
    @SerializedName("HARD") HARD,
    @SerializedName("EXTREME") EXTREME
}

