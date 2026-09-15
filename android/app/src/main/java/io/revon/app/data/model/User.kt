package io.revon.app.data.model

import com.google.gson.annotations.SerializedName

data class User(
    @SerializedName("id") val id: String,
    @SerializedName("account") val account: String? = null,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("real_name") val realName: String? = null,
    @SerializedName("gender") val gender: String? = "other",
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("birthday") val birthday: String? = null,
    @SerializedName("email") val email: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("role") val role: String = "user",
    @SerializedName("points", alternate = ["free_plays", "r_points"]) val rPoints: Int = 0,
    @SerializedName("region") val region: String?,
    @SerializedName("city") val city: String?,
    @SerializedName("country") val country: String?
)
