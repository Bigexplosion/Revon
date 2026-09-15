package io.revon.app.data.repository

import io.revon.app.data.api.ApiService
import io.revon.app.data.local.TokenManager
import io.revon.app.data.model.User
import io.revon.app.di.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class AuthRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun login(account: String, pass: String): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf("account" to account, "password" to pass)
                val response = apiService.login(params)
                if (response.status == "success" && response.data != null) {
                    val userData = response.data
                    tokenManager.saveToken(userData.token)
                    NetworkModule.setToken(userData.token)
                    val fullUser = checkSession() ?: User(
                        id = userData.id,
                        account = userData.account,
                        nickname = userData.nickname.ifBlank { "Revon" + String.format("%05d", (1..99999).random()) },
                        realName = userData.realName,
                        gender = userData.gender ?: "other",
                        phone = userData.phone,
                        birthday = userData.birthday,
                        email = userData.email ?: account,
                        avatarUrl = null,
                        role = userData.role,
                        rPoints = userData.freePlays,
                        region = "Asia",
                        city = userData.city ?: "Taipei",
                        country = userData.country ?: "Taiwan"
                    )
                    Result.success(fullUser)
                } else {
                    Result.failure(Exception(response.message ?: "登入失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun checkAvailability(account: String? = null, email: String? = null, phone: String? = null): Result<Map<String, Boolean>> {
        return withContext(Dispatchers.IO) {
            try {
                val res = apiService.checkAvailability(account, email, phone)
                if (res.status == "success" && res.data != null) {
                    Result.success(res.data)
                } else {
                    Result.failure(Exception(res.message ?: "檢查失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun register(
        email: String,
        account: String,
        pass: String,
        realName: String,
        gender: String,
        phone: String,
        birthday: String,
        city: String = "Taichung (台中市)",
        country: String = "Taiwan (台灣)"
    ): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf(
                    "email" to email,
                    "account" to account,
                    "password" to pass,
                    "real_name" to realName,
                    "gender" to gender,
                    "phone" to phone,
                    "birthday" to birthday,
                    "city" to city,
                    "country" to country
                )
                val response = apiService.register(params)
                if (response.status == "success" && response.data != null) {
                    val userData = response.data
                    val user = User(
                        id = userData.id,
                        account = userData.account,
                        nickname = userData.nickname.ifBlank { "Revon" + String.format("%05d", (1..99999).random()) },
                        realName = userData.realName ?: realName,
                        gender = userData.gender ?: gender,
                        phone = userData.phone ?: phone,
                        birthday = userData.birthday ?: birthday,
                        email = if (userData.email.isNullOrBlank()) email else userData.email,
                        avatarUrl = null,
                        role = userData.role ?: "racer",
                        rPoints = userData.freePlays,
                        region = "Asia",
                        city = userData.city ?: city,
                        country = userData.country ?: country
                    )
                    tokenManager.saveToken(userData.token)
                    tokenManager.saveUser(user)
                    Result.success(user)
                } else {
                    Result.failure(Exception(response.message ?: "註冊失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun checkSession(): User? {
        if (!tokenManager.isLoggedIn()) return null
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getCurrentUser()
                if (response.status == "success" && response.data != null) {
                    tokenManager.saveUser(response.data)
                    response.data
                } else if (response.code == 401 || response.status == "error") {
                    tokenManager.clearToken()
                    null
                } else {
                    tokenManager.getUser() // Fallback to local on network connection error
                }
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) {
                    tokenManager.clearToken()
                    null
                } else {
                    tokenManager.getUser()
                }
            } catch (e: Exception) {
                tokenManager.getUser() // Fallback to local on offline
            }
        }
    }

    fun logout() {
        tokenManager.clearToken()
    }

    suspend fun updateProfile(
        nickname: String,
        realName: String,
        gender: String,
        phone: String,
        birthday: String,
        email: String,
        city: String,
        country: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf(
                    "nickname" to nickname,
                    "real_name" to realName,
                    "gender" to gender,
                    "phone" to phone,
                    "birthday" to birthday,
                    "email" to email,
                    "city" to city,
                    "country" to country
                )
                val response = apiService.updateProfile(params)
                if (response.status == "success") {
                    val user = tokenManager.getUser()
                    if (user != null) {
                        tokenManager.saveUser(
                            user.copy(
                                nickname = nickname,
                                realName = realName,
                                gender = gender,
                                phone = phone,
                                birthday = birthday,
                                email = email,
                                city = city,
                                country = country
                            )
                        )
                    }
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.message ?: "更新失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun uploadAvatar(context: android.content.Context, imageUri: android.net.Uri): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                
                // 解碼任何格式（包含 HEIC, PNG, WEBP, JPG）為 Bitmap
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(contentResolver, imageUri)
                    android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                } ?: return@withContext Result.failure(Exception("無法解析圖片數據"))

                // 轉碼壓縮為 JPEG 格式
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                val bytes = stream.toByteArray()
                stream.close()

                val mediaType = "image/jpeg".toMediaTypeOrNull()
                val requestFile = okhttp3.RequestBody.create(mediaType, bytes)
                val body = okhttp3.MultipartBody.Part.createFormData("avatar", "avatar.jpg", requestFile)

                val response = apiService.uploadAvatar(body)
                if (response.status == "success" && response.data != null) {
                    val avatarUrl = response.data["avatar_url"] ?: response.data["profile_image_url"] ?: ""
                    val user = tokenManager.getUser()
                    if (user != null && avatarUrl.isNotBlank()) {
                        tokenManager.saveUser(user.copy(avatarUrl = avatarUrl))
                    }
                    Result.success(avatarUrl)
                } else {
                    Result.failure(Exception(response.message ?: "頭像上傳失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun requestPasswordReset(email: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf("email" to email)
                val response = apiService.requestPasswordReset(params)
                if (response.status == "success") {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.message ?: "發送失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun resetPassword(email: String, code: String, newPass: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val params = mapOf(
                    "email" to email,
                    "code" to code,
                    "new_password" to newPass
                )
                val response = apiService.resetPassword(params)
                if (response.status == "success") {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.message ?: "重置失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
