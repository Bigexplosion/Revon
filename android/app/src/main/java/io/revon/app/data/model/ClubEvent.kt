package io.revon.app.data.model

import com.google.gson.annotations.SerializedName

data class ClubEvent(
    @SerializedName("id") val id: String,
    @SerializedName("club_id") val clubId: String,
    @SerializedName("title") val title: String,
    @SerializedName("track_code") val trackCode: String,
    @SerializedName("date_time_display") val dateTimeDisplay: String,
    @SerializedName("description") val description: String,
    @SerializedName("creator_nickname") val creatorNickname: String,
    @SerializedName("creator_uid") val creatorUid: String = "",
    @SerializedName("participants") val participants: List<String> = emptyList(),
    @SerializedName("participant_count") val participantCount: Int = 1,
    @SerializedName("is_joined") val isJoined: Boolean = false
)
