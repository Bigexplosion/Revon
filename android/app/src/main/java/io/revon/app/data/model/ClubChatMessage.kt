package io.revon.app.data.model

import com.google.gson.annotations.SerializedName

data class ClubChatMessage(
    @SerializedName("id") val id: String? = "",
    @SerializedName("club_id") val clubId: String? = "",
    @SerializedName("author_nickname") val authorNickname: String? = "車手",
    @SerializedName("author_uid") val authorUid: String? = "",
    @SerializedName("body") val body: String? = "",
    @SerializedName("message_type") val messageType: String? = "text"
)

