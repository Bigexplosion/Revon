package io.revon.app.data.repository

import io.revon.app.data.api.ApiService
import io.revon.app.data.model.ClubChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApiChatRepository(private val apiService: ApiService) {

    suspend fun getMessages(clubId: String): Result<List<ClubChatMessage>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getChatMessages(clubId)
                if (response.status == "success" && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.failure(Exception(response.message ?: "Failed to get messages"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun sendMessage(clubId: String, msg: ClubChatMessage): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val params: Map<String, Any> = mapOf(
                    "club_id" to clubId,
                    "author_nickname" to (msg.authorNickname ?: "車手"),
                    "author_uid" to (msg.authorUid ?: ""),
                    "body" to (msg.body ?: ""),
                    "message_type" to (msg.messageType ?: "text")
                )
                val response = apiService.sendChatMessage(params)
                if (response.status == "success") {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.message ?: "Failed to send message"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteMessage(clubId: String, msg: ClubChatMessage): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val params: Map<String, String> = mutableMapOf<String, String>().apply {
                    put("club_id", clubId)
                    if (!msg.id.isNullOrBlank()) put("id", msg.id)
                    if (!msg.body.isNullOrBlank()) put("body", msg.body)
                    if (!msg.authorNickname.isNullOrBlank()) put("author_nickname", msg.authorNickname)
                }
                val response = apiService.deleteChatMessage(params)
                if (response.status == "success") {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.message ?: "Failed to delete message"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
