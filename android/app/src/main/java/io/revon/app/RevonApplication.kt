package io.revon.app

import android.app.Application
import com.google.android.gms.maps.MapsInitializer
import io.revon.app.data.config.CrashLogger
import io.revon.app.data.local.TokenManager
import io.revon.app.di.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RevonApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.init(this)
        
        val tokenManager = TokenManager(this)
        NetworkModule.setToken(tokenManager.getToken())

        try {
            MapsInitializer.initialize(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 啟動背景預先讀取/快取賽道與資料庫，避免切換頁面 Lag
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                io.revon.app.data.repository.TrackRepository.getTracks(this@RevonApplication)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
