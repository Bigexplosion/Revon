package io.revon.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.revon.app.data.model.User

class TokenManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREF_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun clearToken() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_ACCOUNT)
            .remove(KEY_USER_NICKNAME)
            .remove(KEY_USER_REAL_NAME)
            .remove(KEY_USER_GENDER)
            .remove(KEY_USER_PHONE)
            .remove(KEY_USER_BIRTHDAY)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_AVATAR)
            .remove(KEY_USER_ROLE)
            .remove(KEY_USER_RPOINTS)
            .remove(KEY_USER_REGION)
            .remove(KEY_USER_CITY)
            .remove(KEY_USER_COUNTRY)
            .apply()
    }

    fun isLoggedIn(): Boolean {
        return !getToken().isNullOrEmpty()
    }

    fun saveUser(user: User) {
        prefs.edit()
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_USER_ACCOUNT, user.account)
            .putString(KEY_USER_NICKNAME, user.nickname)
            .putString(KEY_USER_REAL_NAME, user.realName)
            .putString(KEY_USER_GENDER, user.gender)
            .putString(KEY_USER_PHONE, user.phone)
            .putString(KEY_USER_BIRTHDAY, user.birthday)
            .putString(KEY_USER_EMAIL, user.email)
            .putString(KEY_USER_AVATAR, user.avatarUrl)
            .putString(KEY_USER_ROLE, user.role)
            .putInt(KEY_USER_RPOINTS, user.rPoints)
            .putString(KEY_USER_REGION, user.region)
            .putString(KEY_USER_CITY, user.city)
            .putString(KEY_USER_COUNTRY, user.country)
            .apply()
    }

    fun getUser(): User? {
        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        val account = prefs.getString(KEY_USER_ACCOUNT, null)
        val nickname = prefs.getString(KEY_USER_NICKNAME, "車手") ?: "車手"
        val realName = prefs.getString(KEY_USER_REAL_NAME, null)
        val gender = prefs.getString(KEY_USER_GENDER, "other") ?: "other"
        val phone = prefs.getString(KEY_USER_PHONE, null)
        val birthday = prefs.getString(KEY_USER_BIRTHDAY, null)
        val email = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        val avatarUrl = prefs.getString(KEY_USER_AVATAR, null)
        val role = prefs.getString(KEY_USER_ROLE, "racer") ?: "racer"
        val rPoints = prefs.getInt(KEY_USER_RPOINTS, 500)
        val region = prefs.getString(KEY_USER_REGION, "Asia") ?: "Asia"
        val city = prefs.getString(KEY_USER_CITY, "Taipei") ?: "Taipei"
        val country = prefs.getString(KEY_USER_COUNTRY, "Taiwan") ?: "Taiwan"
        return User(
            id = id,
            account = account,
            nickname = nickname,
            realName = realName,
            gender = gender,
            phone = phone,
            birthday = birthday,
            email = email,
            avatarUrl = avatarUrl,
            role = role,
            rPoints = rPoints,
            region = region,
            city = city,
            country = country
        )
    }

    companion object {
        private const val PREF_NAME = "revon_auth_prefs"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_ACCOUNT = "user_account"
        private const val KEY_USER_NICKNAME = "user_nickname"
        private const val KEY_USER_REAL_NAME = "user_real_name"
        private const val KEY_USER_GENDER = "user_gender"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_BIRTHDAY = "user_birthday"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_AVATAR = "user_avatar"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_USER_RPOINTS = "user_rpoints"
        private const val KEY_USER_REGION = "user_region"
        private const val KEY_USER_CITY = "user_city"
        private const val KEY_USER_COUNTRY = "user_country"
    }
}
