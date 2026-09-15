package io.revon.app.data.model

import com.google.gson.annotations.SerializedName

data class RaceResult(
    @SerializedName("track_code") val trackCode: String,
    @SerializedName("track_name") val trackName: String?,
    @SerializedName("player_nickname") val playerNickname: String,
    @SerializedName("vehicle_type") val vehicleType: VehicleType,
    @SerializedName("finish_time_ms") val finishTimeMs: Long,
    @SerializedName("finish_time_display") val finishTimeDisplay: String,
    @SerializedName("recorded_at") val recordedAt: String?,
    @SerializedName("region") val region: String?,
    @SerializedName("club_id") val clubId: String?,
    @SerializedName("club_name") val clubName: String?,
    @SerializedName("is_custom_route") val isCustomRoute: Boolean = false,
    @SerializedName("custom_route_id") val customRouteId: String?,
    @SerializedName("custom_route_name") val customRouteName: String?,
    @SerializedName("avatar_url") val avatarUrl: String? = null
)

enum class VehicleType {
    @SerializedName("CAR") CAR,
    @SerializedName("MOTOR") MOTOR,
    @SerializedName("OTHER") OTHER
}
