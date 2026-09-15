package io.revon.app.data.model

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("status") val status: String,
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: T?
)

data class UserResponseData(
    @SerializedName("id") val id: String,
    @SerializedName("account") val account: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("real_name") val realName: String? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("birthday") val birthday: String? = null,
    @SerializedName("email") val email: String?,
    @SerializedName("city") val city: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("vip_level") val vipLevel: Int,
    @SerializedName("points", alternate = ["free_plays"]) val freePlays: Int = 0,
    @SerializedName("role") val role: String,
    @SerializedName("token") val token: String
)

data class TracksResponseData(
    @SerializedName("official") val official: List<Track>,
    @SerializedName("custom") val custom: List<Track>,
    @SerializedName("circuits") val circuits: List<Track>
)
