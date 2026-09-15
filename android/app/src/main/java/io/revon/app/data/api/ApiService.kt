package io.revon.app.data.api

import com.google.gson.JsonElement
import io.revon.app.data.model.ApiResponse
import io.revon.app.data.model.Club
import io.revon.app.data.model.LeaderboardItem
import io.revon.app.data.model.RaceResult
import io.revon.app.data.model.TracksResponseData
import io.revon.app.data.model.User
import io.revon.app.data.model.UserResponseData
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import kotlin.jvm.JvmSuppressWildcards

@JvmSuppressWildcards
interface ApiService {
    @POST("auth/login.php")
    suspend fun login(@Body credentials: Map<String, String>): ApiResponse<UserResponseData>

    @POST("auth/register.php")
    suspend fun register(@Body credentials: Map<String, String>): ApiResponse<UserResponseData>

    @GET("auth/check_availability.php")
    suspend fun checkAvailability(
        @Query("account") account: String? = null,
        @Query("email") email: String? = null,
        @Query("phone") phone: String? = null
    ): ApiResponse<Map<String, Boolean>>

    @GET("auth/me.php")
    suspend fun getCurrentUser(): ApiResponse<User>

    @GET("tracks/list.php")
    suspend fun getTracks(): ApiResponse<TracksResponseData>

    @POST("tracks/custom.php")
    suspend fun uploadCustomTrack(@Body trackData: Map<String, Any>): ApiResponse<Map<String, String>>

    @POST("race/upload.php")
    suspend fun uploadRaceResult(@Body data: Map<String, Any>): ApiResponse<Map<String, Any>>

    @POST("race/circuit.php")
    suspend fun uploadCircuitResult(@Body data: Map<String, Any>): ApiResponse<JsonElement>

    @GET("rank/leaderboard.php")
    suspend fun getLeaderboard(
        @Query("track_id") trackId: Int,
        @Query("vehicle_type") vehicleType: String? = null,
        @Query("is_custom") isCustom: Int = 0
    ): ApiResponse<List<LeaderboardItem>>

    @GET("clubs/list.php")
    suspend fun getClubs(): ApiResponse<List<Club>>

    @GET("clubs/chat.php")
    suspend fun getChatMessages(@Query("club_id") clubId: String): ApiResponse<List<io.revon.app.data.model.ClubChatMessage>>

    @POST("clubs/chat.php")
    suspend fun sendChatMessage(@Body msg: Map<String, Any>): ApiResponse<JsonElement>

    @POST("clubs/chat.php?action=delete")
    suspend fun deleteChatMessage(@Body data: Map<String, String>): ApiResponse<JsonElement>

    @POST("user/update_profile.php")
    suspend fun updateProfile(@Body profileData: Map<String, String>): ApiResponse<JsonElement>

    @retrofit2.http.Multipart
    @POST("user/upload_avatar.php")
    suspend fun uploadAvatar(@retrofit2.http.Part avatar: okhttp3.MultipartBody.Part): ApiResponse<Map<String, String>>

    @POST("auth/request_reset.php")
    suspend fun requestPasswordReset(@Body data: Map<String, String>): ApiResponse<JsonElement>

    @POST("auth/reset_password.php")
    suspend fun resetPassword(@Body data: Map<String, String>): ApiResponse<JsonElement>

    @POST("clubs/create.php")
    suspend fun createClub(@Body data: Map<String, String>): ApiResponse<Map<String, Any>>

    @POST("clubs/update.php")
    suspend fun updateClub(@Body data: Map<String, Any>): ApiResponse<Club>

    @GET("clubs/members.php")
    suspend fun getClubMembers(@Query("club_id") clubId: Int): ApiResponse<List<Map<String, Any>>>

    @POST("clubs/members.php")
    suspend fun manageClubMember(@Body data: Map<String, Any>): ApiResponse<JsonElement>

    @GET("clubs/events.php")
    suspend fun getClubEvents(@Query("club_id") clubId: Int): ApiResponse<List<Map<String, Any>>>

    @POST("clubs/events.php")
    suspend fun createClubEvent(@Body data: Map<String, Any>): ApiResponse<JsonElement>

    @POST("clubs/events.php?action=delete")
    suspend fun deleteClubEvent(@Body data: Map<String, Any>): ApiResponse<JsonElement>

    @POST("clubs/events.php?action=toggle_join")
    suspend fun toggleEventJoin(@Body data: Map<String, Any>): ApiResponse<JsonElement>

    @POST("admin/monitor.php")
    suspend fun sendHeartbeat(@Body data: Map<String, Any>): ApiResponse<JsonElement>

    @POST("admin/monitor.php")
    suspend fun finishSession(@Body data: Map<String, Any>): ApiResponse<JsonElement>
}
