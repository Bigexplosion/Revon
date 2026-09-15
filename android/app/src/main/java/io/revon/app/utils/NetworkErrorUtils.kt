package io.revon.app.utils

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

object NetworkErrorUtils {

    /**
     * 將技術性 Throwable / 網路 Exception 轉譯為使用者友善的中文提示訊息
     */
    fun getFriendlyErrorMessage(throwable: Throwable?, defaultMessage: String = "連線異常，請稍後再試"): String {
        if (throwable == null) return defaultMessage

        val rawMsg = throwable.message ?: throwable.localizedMessage ?: throwable.toString()
        val causeMsg = throwable.cause?.message ?: ""

        val detailedLog = buildString {
            if (rawMsg.isNotBlank()) append(rawMsg)
            if (causeMsg.isNotBlank() && causeMsg != rawMsg) append(" | Cause: ").append(causeMsg)
            append(" [").append(throwable.javaClass.simpleName).append("]")
        }

        return when {
            throwable is UnknownHostException || throwable is ConnectException || rawMsg.contains("Unable to resolve host", ignoreCase = true) -> {
                "網絡未正確連接 (${throwable.javaClass.simpleName}: $rawMsg)"
            }
            throwable is SocketTimeoutException || rawMsg.contains("timeout", ignoreCase = true) -> {
                "伺服器連線逾時 ($rawMsg)"
            }
            throwable is HttpException -> {
                "伺服器 HTTP 錯誤 ${throwable.code()}: ${throwable.message()}"
            }
            rawMsg.isNotBlank() -> detailedLog
            else -> "$defaultMessage ($detailedLog)"
        }
    }
}
