package io.revon.app.data.model

import com.google.gson.annotations.SerializedName

data class Club(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val rawName: String? = null,
    @SerializedName("badge_letters") val rawBadgeLetters: String? = null,
    @SerializedName("motto") val rawMotto: String? = null,
    @SerializedName("member_count") val memberCount: Int = 0,
    @SerializedName("max_members") val maxMembers: Int = 50,
    @SerializedName("club_record_track_code") val clubRecordTrackCode: String? = null,
    @SerializedName("club_record_time_display") val clubRecordTimeDisplay: String? = null,
    @SerializedName("region") val region: String? = "Taiwan",
    @SerializedName("captain_nickname") val captainNickname: String? = null,
    @SerializedName("total_points") val totalPoints: Int = 0,
    @SerializedName("level") val level: Int = 1,
    @SerializedName("accent_color") val accentColor: String? = "#E10600"
) {
    val name: String
        get() = rawName?.takeIf { it.isNotBlank() } ?: "熱血車隊 #$id"

    val badgeLetters: String
        get() = rawBadgeLetters?.takeIf { it.isNotBlank() }
            ?: name.take(2).uppercase().ifEmpty { "RV" }

    val motto: String
        get() = rawMotto?.takeIf { it.isNotBlank() } ?: "REV-ON 山路競技，極速狂飆！"
}
