package io.revon.app.data.model

import com.google.gson.annotations.SerializedName

data class ClubMember(
    @SerializedName("club_id") val clubId: String,
    @SerializedName("club_name") val clubName: String?,
    @SerializedName("status") val status: MemberStatus = MemberStatus.PENDING,
    @SerializedName("member_nickname") val memberNickname: String?,
    @SerializedName("role") val role: MemberRole = MemberRole.MEMBER,
    @SerializedName("joined_at") val joinedAt: String? = null
)

enum class MemberStatus {
    @SerializedName("joined") JOINED,
    @SerializedName("pending") PENDING
}

enum class MemberRole {
    @SerializedName("captain") CAPTAIN,
    @SerializedName("admin") ADMIN,
    @SerializedName("member") MEMBER
}
