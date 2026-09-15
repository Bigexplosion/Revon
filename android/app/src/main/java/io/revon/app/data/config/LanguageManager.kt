package io.revon.app.data.config

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage {
    ZH, // 繁體中文
    EN  // English
}

object LanguageManager {
    private const val PREF_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "KEY_APP_LANGUAGE"

    private val _currentLanguage = MutableStateFlow(AppLanguage.ZH)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val langStr = prefs.getString(KEY_LANGUAGE, "ZH") ?: "ZH"
        _currentLanguage.value = if (langStr == "EN") AppLanguage.EN else AppLanguage.ZH
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        _currentLanguage.value = language
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
    }
}
