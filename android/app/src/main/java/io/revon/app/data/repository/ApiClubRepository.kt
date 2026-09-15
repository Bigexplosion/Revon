package io.revon.app.data.repository

import io.revon.app.data.api.ApiService
import io.revon.app.data.model.Club
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApiClubRepository(private val apiService: ApiService) {

    suspend fun getClubs(): Result<List<Club>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getClubs()
                if (response.status == "success" && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.failure(Exception(response.message ?: "取得車隊列表失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun createClub(name: String, description: String): Result<Map<String, Any>> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf("name" to name, "description" to description)
                val response = apiService.createClub(params)
                if (response.status == "success" && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.failure(Exception(response.message ?: "建立車隊失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateClub(clubId: Int, name: String, badgeLetters: String, motto: String, accentColor: String): Result<Club> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf(
                    "club_id" to clubId,
                    "name" to name,
                    "badge_letters" to badgeLetters,
                    "motto" to motto,
                    "accent_color" to accentColor
                )
                val response = apiService.updateClub(params)
                if (response.status == "success" && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.failure(Exception(response.message ?: "更新車隊設定失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getClubMembers(clubId: Int): Result<List<Map<String, Any>>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getClubMembers(clubId)
                if (response.status == "success" && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.failure(Exception(response.message ?: "獲取成員失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun manageClubMember(action: String, clubId: Int, userId: Int, inviteCode: String = ""): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf(
                    "action" to action,
                    "club_id" to clubId,
                    "user_id" to userId,
                    "invite_code" to inviteCode
                )
                val response = apiService.manageClubMember(params)
                if (response.status == "success") {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.message ?: "成員管理操作失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getClubEvents(clubId: Int): Result<List<Map<String, Any>>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getClubEvents(clubId)
                if (response.status == "success" && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.failure(Exception(response.message ?: "獲取活動失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun createClubEvent(clubId: Int, title: String, description: String, eventDate: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf(
                    "club_id" to clubId,
                    "title" to title,
                    "description" to description,
                    "event_date" to eventDate
                )
                val response = apiService.createClubEvent(params)
                if (response.status == "success") {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.message ?: "活動發布失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteClubEvent(eventId: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf("event_id" to eventId)
                val response = apiService.deleteClubEvent(params)
                if (response.status == "success") {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.message ?: "刪除活動失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun toggleEventJoin(eventId: Int, userKey: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf(
                    "event_id" to eventId,
                    "user_key" to userKey
                )
                val response = apiService.toggleEventJoin(params)
                if (response.status == "success") {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.message ?: "更新報名狀態失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
