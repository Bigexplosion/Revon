package com.example.qstart

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class TrackPoint(
    val lat: Double,
    val lng: Double,
    val speed: Double,
    val timestamp: Long,
    val leanAngle: Double = 0.0,
    val latG: Double = 0.0, // 新增：側向G力 (主要給汽車模式用)
    val gForce: Double = 0.0,
    val accelG: Double = 0.0,
    val brakingG: Double = 0.0,
    val distance: Double = 0.0,
    // 三軸數據欄位 (僅在開發模式啟用時填入)
    val fx: Float? = null, val fy: Float? = null, val fz: Float? = null,
    val rx: Float? = null, val ry: Float? = null, val rz: Float? = null,
    val gx: Float? = null, val gy: Float? = null, val gz: Float? = null
)

class TrackManager(val context: Context) {

    /**
     * 數據瘦身輔助函數：將 Double 數值裁切至指定小數點位數
     * 這能顯著減少 JSON 檔案體積，同時不影響分析精準度
     */
    private fun round(value: Double, decimals: Int): Double {
        val multiplier = Math.pow(10.0, decimals.toDouble())
        return kotlin.math.round(value * multiplier) / multiplier
    }

    fun getAllTrackFiles(): Array<File>? {
        return context.filesDir.listFiles { _, name -> name.endsWith(".json") }
    }

    fun saveTrack(points: List<TrackPoint>, prefix: String = "TRACK") {
        if (points.isEmpty()) return

        val safePrefix = prefix.replace(":", "").replace("/", "").replace(" ", "_")
        val fileName = "${safePrefix}_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".json"
        val file = File(context.filesDir, fileName)

        val rootObj = JSONObject()
        
        // 寫入 Header 元數據
        val header = JSONObject()
        header.put("run_id", points.firstOrNull()?.timestamp ?: System.currentTimeMillis())
        header.put("v_model", GpsConfig.getVehicleModel(context))
        header.put("t_front", GpsConfig.getTireFront(context))
        header.put("t_rear", GpsConfig.getTireRear(context))
        header.put("app", "Tstarz")
        rootObj.put("header", header)

        val jsonArray = JSONArray()
        points.forEach {
            val obj = JSONObject()
            // 實施數據瘦身：座標保留 7 位 (1公分精度)，其餘物理量保留 2 位
            obj.put("lt", round(it.lat, 7))
            obj.put("lg", round(it.lng, 7))
            obj.put("s", round(it.speed, 2))
            obj.put("t", it.timestamp)
            obj.put("l", round(it.leanAngle, 2))
            obj.put("gl", round(it.latG, 2)) // 寫入側向G值
            obj.put("g", round(it.gForce, 2))
            obj.put("ga", round(it.accelG, 2))
            obj.put("gb", round(it.brakingG, 2))
            obj.put("d", round(it.distance, 2))
            
            // 若有感測器原始數據，則寫入 JSON
            it.fx?.let { v -> obj.put("fx", round(v.toDouble(), 3)) }
            it.fy?.let { v -> obj.put("fy", round(v.toDouble(), 3)) }
            it.fz?.let { v -> obj.put("fz", round(v.toDouble(), 3)) }
            it.rx?.let { v -> obj.put("rx", round(v.toDouble(), 3)) }
            it.ry?.let { v -> obj.put("ry", round(v.toDouble(), 3)) }
            it.rz?.let { v -> obj.put("rz", round(v.toDouble(), 3)) }

            jsonArray.put(obj)
        }
        rootObj.put("points", jsonArray)

        // 使用 no-indent 的 toString() 達到最大壓縮
        file.writeText(rootObj.toString())
    }

    /**
     * Session 模式專用：包含點位與完賽狀態
     */
    data class SessionLap(val points: List<TrackPoint>, val isComplete: Boolean)

    fun saveSession(laps: List<SessionLap>, trackName: String) {
        if (laps.isEmpty()) return

        // 統一命名規則：賽道_賽道名_日期_時間
        val fileName = "賽道_${trackName}_Session_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".json"
        val file = File(context.filesDir, fileName)

        val rootObj = JSONObject()
        val header = JSONObject()
        header.put("type", "session")
        header.put("track", trackName)
        header.put("v_model", GpsConfig.getVehicleModel(context))
        header.put("app", "Tstarz")
        rootObj.put("header", header)

        val lapsArray = JSONArray()
        laps.forEachIndexed { index, lap ->
            val lapObj = JSONObject()
            lapObj.put("lap_num", index + 1)
            lapObj.put("c", lap.isComplete) // 寫入完賽狀態
            
            val pointsArray = JSONArray()
            lap.points.forEach {
                val p = JSONObject()
                // 嚴格對齊普通紀錄的命名與瘦身規則
                p.put("lt", round(it.lat, 7))
                p.put("lg", round(it.lng, 7))
                p.put("s", round(it.speed, 2))
                p.put("t", it.timestamp)
                p.put("l", round(it.leanAngle, 2))
                p.put("gl", round(it.latG, 2)) // 寫入側向G值
                p.put("g", round(it.gForce, 2))
                p.put("ga", round(it.accelG, 2))
                p.put("gb", round(it.brakingG, 2))
                p.put("d", round(it.distance, 2))

                // 同樣適用於 Session 模式
                it.fx?.let { v -> p.put("fx", round(v.toDouble(), 3)) }
                it.fy?.let { v -> p.put("fy", round(v.toDouble(), 3)) }
                it.fz?.let { v -> p.put("fz", round(v.toDouble(), 3)) }
                it.rx?.let { v -> p.put("rx", round(v.toDouble(), 3)) }
                it.ry?.let { v -> p.put("ry", round(v.toDouble(), 3)) }
                it.rz?.let { v -> p.put("rz", round(v.toDouble(), 3)) }

                pointsArray.put(p)
            }
            lapObj.put("points", pointsArray)
            lapsArray.put(lapObj)
        }
        rootObj.put("laps", lapsArray)
        file.writeText(rootObj.toString())
    }

    fun exportBackup(context: Context, outputUri: android.net.Uri, callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val files = context.filesDir.listFiles { _, name -> name.endsWith(".json") }
                if (files == null || files.isEmpty()) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post { callback(false, "沒有可匯出的紀錄檔") }
                    return@Thread
                }
                
                context.contentResolver.openOutputStream(outputUri)?.use { fos ->
                    java.util.zip.ZipOutputStream(fos).use { zos ->
                        for (file in files) {
                            val entry = java.util.zip.ZipEntry(file.name)
                            zos.putNextEntry(entry)
                            file.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(true, "匯出成功，共匯出 ${files.size} 筆紀錄") }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(false, "匯出失敗: ${e.message}") }
            }
        }.start()
    }

    fun importBackup(context: Context, inputUri: android.net.Uri, callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                var count = 0
                context.contentResolver.openInputStream(inputUri)?.use { fis ->
                    java.util.zip.ZipInputStream(fis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.endsWith(".json")) {
                                val outFile = File(context.filesDir, entry.name)
                                outFile.outputStream().use { fos ->
                                    zis.copyTo(fos)
                                }
                                count++
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(true, "匯入成功，共還原 $count 筆紀錄") }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(false, "匯入失敗: ${e.message}") }
            }
        }.start()
    }

    fun saveTempRecovery(points: List<TrackPoint>, type: String, extraInfo: String) {
        if (points.isEmpty()) return
        Thread {
            try {
                val tempFile = File(context.filesDir, "temp_recovery_run.json")
                val rootObj = JSONObject()
                rootObj.put("type", type)
                rootObj.put("extraInfo", extraInfo)
                
                val jsonArray = JSONArray()
                points.forEach {
                    val obj = JSONObject()
                    obj.put("lt", round(it.lat, 7))
                    obj.put("lg", round(it.lng, 7))
                    obj.put("s", round(it.speed, 2))
                    obj.put("t", it.timestamp)
                    obj.put("l", round(it.leanAngle, 2))
                    obj.put("gl", round(it.latG, 2))
                    obj.put("g", round(it.gForce, 2))
                    obj.put("ga", round(it.accelG, 2))
                    obj.put("gb", round(it.brakingG, 2))
                    obj.put("d", round(it.distance, 2))
                    
                    it.fx?.let { v -> obj.put("fx", round(v.toDouble(), 3)) }
                    it.fy?.let { v -> obj.put("fy", round(v.toDouble(), 3)) }
                    it.fz?.let { v -> obj.put("fz", round(v.toDouble(), 3)) }
                    it.rx?.let { v -> obj.put("rx", round(v.toDouble(), 3)) }
                    it.ry?.let { v -> obj.put("ry", round(v.toDouble(), 3)) }
                    it.rz?.let { v -> obj.put("rz", round(v.toDouble(), 3)) }
                    
                    jsonArray.put(obj)
                }
                rootObj.put("points", jsonArray)
                tempFile.writeText(rootObj.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun getTempRecovery(): JSONObject? {
        val tempFile = File(context.filesDir, "temp_recovery_run.json")
        if (!tempFile.exists()) return null
        return try {
            JSONObject(tempFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun clearTempRecovery() {
        try {
            val tempFile = File(context.filesDir, "temp_recovery_run.json")
            if (tempFile.exists()) {
                tempFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
