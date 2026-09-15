package io.revon.app.data.repository

import android.content.Context
import io.revon.app.data.model.Difficulty
import io.revon.app.data.model.Track
import io.revon.app.data.model.TrackCategory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.regex.Pattern
import kotlin.math.*

object TrackRepository {

    private data class SqlRawTrack(
        val id: Int,
        val name: String,
        val country: String,
        val city: String,
        val startLat: Double,
        val startLng: Double,
        val endLat: Double,
        val endLng: Double,
        val mid1Lat: Double?,
        val mid1Lng: Double?,
        val mid2Lat: Double?,
        val mid2Lng: Double?,
        val category: TrackCategory = TrackCategory.TOUGE,
        val isHidden: Boolean = false,
        val isVipOnly: Boolean = false,
        val isCustom: Boolean = false
    )

    private val cachedTracks = mutableListOf<Track>()
    private val cachedPaths = mutableMapOf<String, List<Pair<Double, Double>>>()

    fun clearCache() {
        cachedTracks.clear()
        cachedPaths.clear()
    }

    fun getTracks(context: Context): List<Track> {
        if (cachedTracks.isNotEmpty()) return cachedTracks

        try {
            android.util.Log.d("TrackRepoDebug", "開始呼叫遠端 getTracks API...")
            val response = kotlinx.coroutines.runBlocking {
                io.revon.app.di.NetworkModule.apiService.getTracks()
            }
            android.util.Log.d("TrackRepoDebug", "API 回應 status=${response.status}, code=${response.code}, officialSize=${response.data?.official?.size}, customSize=${response.data?.custom?.size}, circuitSize=${response.data?.circuits?.size}")
            if (response.status == "success" && response.data != null) {
                val data = response.data
                val allList = mutableListOf<Track>()

                // 官方山道 (tracks) -> category: TOUGE
                data.official.forEach { t: Track ->
                    allList.add(t.copy(category = TrackCategory.TOUGE, isCustom = false))
                }

                // 玩家自訂山道 (custom_tracks) -> 屬於山道分類 (TOUGE), isCustom = true
                data.custom.forEach { t: Track ->
                    allList.add(t.copy(category = TrackCategory.TOUGE, isCustom = true))
                }

                // 國際賽車場 (circuits) -> category: CIRCUIT
                data.circuits.forEach { t: Track ->
                    allList.add(t.copy(category = TrackCategory.CIRCUIT, isCustom = false))
                }

                allList.forEach { track ->
                    cachedTracks.add(track)
                }
                android.util.Log.d("TrackRepoDebug", "成功加入 cachedTracks 總筆數: ${cachedTracks.size}")
            }
        } catch (e: Exception) {
            android.util.Log.e("TrackRepoDebug", "呼叫 getTracks API 失敗: ${e.message}", e)
            e.printStackTrace()
        }

        if (cachedTracks.isEmpty()) {
            val rawTracks = mutableListOf<SqlRawTrack>()
            rawTracks.addAll(parseTracksSql(context))
            rawTracks.addAll(parseCustomTracksSql(context))
            rawTracks.addAll(parseCircuitsSql(context))
            rawTracks.addAll(parseUserCustomTracksSql(context))

            rawTracks.filter { !it.isHidden }.forEach { raw ->
                val waypoints = mutableListOf<Pair<Double, Double>>()
                waypoints.add(Pair(raw.startLat, raw.startLng))
                if (raw.mid1Lat != null && raw.mid1Lng != null && raw.mid1Lat != 0.0 && raw.mid1Lng != 0.0) {
                    waypoints.add(Pair(raw.mid1Lat, raw.mid1Lng))
                }
                if (raw.mid2Lat != null && raw.mid2Lng != null && raw.mid2Lat != 0.0 && raw.mid2Lng != 0.0) {
                    waypoints.add(Pair(raw.mid2Lat, raw.mid2Lng))
                }
                waypoints.add(Pair(raw.endLat, raw.endLng))

                val tempPath = generateSmoothSplinePath(waypoints)
                val distKm = round(calculateTotalDistanceKm(tempPath) * 10) / 10.0
                val corners = max(6, (distKm * 3.5).toInt())

                val difficulty = when {
                    distKm > 15.0 -> Difficulty.EXTREME
                    distKm > 8.0 -> Difficulty.HARD
                    else -> Difficulty.NORMAL
                }

                val track = Track(
                    code = raw.name,
                    name = raw.name,
                    nameZh = raw.name,
                    region = if (raw.city.isNotBlank()) raw.city else raw.country,
                    difficulty = difficulty,
                    category = raw.category,
                    distanceKm = if (distKm > 0) distKm else 3.5,
                    cornersCount = corners,
                    startLat = raw.startLat,
                    startLng = raw.startLng,
                    endLat = raw.endLat,
                    endLng = raw.endLng,
                    mid1Lat = raw.mid1Lat,
                    mid1Lng = raw.mid1Lng,
                    mid2Lat = raw.mid2Lat,
                    mid2Lng = raw.mid2Lng,
                    coverImage = null,
                    activeDrivers = (10..45).random(),
                    subtitle = "${raw.country} · ${raw.city}",
                    top3 = emptyList(),
                    isCustom = raw.isCustom
                )
                cachedTracks.add(track)
            }
        }

        return cachedTracks
    }

    private fun readSqlFileLines(context: Context, fileName: String): List<String> {
        val lines = mutableListOf<String>()
        try {
            // 優先從 assets 資源目錄讀取 SQL 檔案
            val inputStream = try {
                context.assets.open(fileName)
            } catch (e: Exception) {
                val rootFile = File(context.filesDir.parentFile?.parentFile?.parentFile, fileName)
                if (rootFile.exists()) {
                    rootFile.inputStream()
                } else {
                    File(fileName).takeIf { it.exists() }?.inputStream()
                }
            }

            inputStream?.use { stream ->
                BufferedReader(InputStreamReader(stream, "UTF-8")).use { reader ->
                    reader.forEachLine { lines.add(it) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return lines
    }

    private fun parseTracksSql(context: Context): List<SqlRawTrack> {
        val list = mutableListOf<SqlRawTrack>()
        val lines = readSqlFileLines(context, "tracks.sql")
        val insertPattern = Pattern.compile("INSERT INTO `tracks` .*?VALUES\\s*(.*);", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val fullContent = lines.joinToString("\n")
        val matcher = insertPattern.matcher(fullContent)

        if (matcher.find()) {
            val valuesPart = matcher.group(1) ?: ""
            val rowStrings = valuesPart.split(Regex("\\),\\s*\\("))
            for (rowStr in rowStrings) {
                val clean = rowStr.trim().removePrefix("(").removeSuffix(")")
                val tokens = splitSqlCsv(clean)
                if (tokens.size >= 8) {
                    try {
                        val id = tokens[0].trim().toIntOrNull() ?: continue
                        val name = tokens[1].trim('\'', '"', ' ')
                        val country = tokens[2].trim('\'', '"', ' ')
                        val city = tokens[3].trim('\'', '"', ' ')
                        val startLat = tokens[4].trim().toDoubleOrNull() ?: 0.0
                        val startLng = tokens[5].trim().toDoubleOrNull() ?: 0.0
                        val endLat = tokens[6].trim().toDoubleOrNull() ?: 0.0
                        val endLng = tokens[7].trim().toDoubleOrNull() ?: 0.0
                        val isHidden = if (tokens.size > 9) tokens[9].trim() == "1" else false
                        val isVip = if (tokens.size > 10) tokens[10].trim() == "1" else false
                        val mid1Lat = if (tokens.size > 12) tokens[12].trim().toDoubleOrNull() else null
                        val mid1Lng = if (tokens.size > 13) tokens[13].trim().toDoubleOrNull() else null
                        val mid2Lat = if (tokens.size > 14) tokens[14].trim().toDoubleOrNull() else null
                        val mid2Lng = if (tokens.size > 15) tokens[15].trim().toDoubleOrNull() else null

                        list.add(
                            SqlRawTrack(
                                id = id,
                                name = name,
                                country = country,
                                city = city,
                                startLat = startLat,
                                startLng = startLng,
                                endLat = endLat,
                                endLng = endLng,
                                mid1Lat = mid1Lat,
                                mid1Lng = mid1Lng,
                                mid2Lat = mid2Lat,
                                mid2Lng = mid2Lng,
                                category = TrackCategory.TOUGE,
                                isHidden = isHidden,
                                isVipOnly = isVip
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return list
    }

    private fun parseCustomTracksSql(context: Context): List<SqlRawTrack> {
        val list = mutableListOf<SqlRawTrack>()
        val lines = readSqlFileLines(context, "custom_tracks.sql")
        val fullContent = lines.joinToString("\n")
        val insertPattern = Pattern.compile("INSERT INTO `custom_tracks` .*?VALUES\\s*(.*);", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val matcher = insertPattern.matcher(fullContent)

        if (matcher.find()) {
            val valuesPart = matcher.group(1) ?: ""
            val rowStrings = valuesPart.split(Regex("\\),\\s*\\("))
            for (rowStr in rowStrings) {
                val clean = rowStr.trim().removePrefix("(").removeSuffix(")")
                val tokens = splitSqlCsv(clean)
                if (tokens.size >= 8) {
                    try {
                        val id = tokens[0].trim().toIntOrNull() ?: continue
                        val name = tokens[1].trim('\'', '"', ' ')
                        val country = tokens[2].trim('\'', '"', ' ')
                        val city = tokens[3].trim('\'', '"', ' ')
                        val startLat = tokens[4].trim().toDoubleOrNull() ?: 0.0
                        val startLng = tokens[5].trim().toDoubleOrNull() ?: 0.0
                        val endLat = tokens[6].trim().toDoubleOrNull() ?: 0.0
                        val endLng = tokens[7].trim().toDoubleOrNull() ?: 0.0
                        val isHidden = if (tokens.size > 9) tokens[9].trim() == "1" else false
                        val isVip = if (tokens.size > 10) tokens[10].trim() == "1" else false
                        val mid1Lat = if (tokens.size > 12) tokens[12].trim().toDoubleOrNull() else null
                        val mid1Lng = if (tokens.size > 13) tokens[13].trim().toDoubleOrNull() else null
                        val mid2Lat = if (tokens.size > 14) tokens[14].trim().toDoubleOrNull() else null
                        val mid2Lng = if (tokens.size > 15) tokens[15].trim().toDoubleOrNull() else null

                        list.add(
                            SqlRawTrack(
                                id = id,
                                name = name,
                                country = country,
                                city = city,
                                startLat = startLat,
                                startLng = startLng,
                                endLat = endLat,
                                endLng = endLng,
                                mid1Lat = mid1Lat,
                                mid1Lng = mid1Lng,
                                mid2Lat = mid2Lat,
                                mid2Lng = mid2Lng,
                                category = TrackCategory.TOUGE,
                                isHidden = isHidden,
                                isVipOnly = isVip
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return list
    }

    private fun parseCircuitsSql(context: Context): List<SqlRawTrack> {
        val list = mutableListOf<SqlRawTrack>()
        val lines = readSqlFileLines(context, "circuits.sql")
        val fullContent = lines.joinToString("\n")
        val insertPattern = Pattern.compile("INSERT INTO `circuits` .*?VALUES\\s*(.*);", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val matcher = insertPattern.matcher(fullContent)

        if (matcher.find()) {
            val valuesPart = matcher.group(1) ?: ""
            val rowStrings = valuesPart.split(Regex("\\),\\s*\\("))
            for (rowStr in rowStrings) {
                val clean = rowStr.trim().removePrefix("(").removeSuffix(")")
                val tokens = splitSqlCsv(clean)
                if (tokens.size >= 8) {
                    try {
                        val id = tokens[0].trim().toIntOrNull() ?: continue
                        val name = tokens[1].trim('\'', '"', ' ')
                        val country = tokens[2].trim('\'', '"', ' ')
                        val city = tokens[3].trim('\'', '"', ' ')
                        val p1Lat = tokens[4].trim().toDoubleOrNull() ?: 0.0
                        val p1Lng = tokens[5].trim().toDoubleOrNull() ?: 0.0
                        val p2Lat = tokens[6].trim().toDoubleOrNull() ?: 0.0
                        val p2Lng = tokens[7].trim().toDoubleOrNull() ?: 0.0

                        list.add(
                            SqlRawTrack(
                                id = id,
                                name = name,
                                country = country,
                                city = city,
                                startLat = p1Lat,
                                startLng = p1Lng,
                                endLat = p2Lat,
                                endLng = p2Lng,
                                mid1Lat = null,
                                mid1Lng = null,
                                mid2Lat = null,
                                mid2Lng = null,
                                category = TrackCategory.CIRCUIT,
                                isHidden = false,
                                isVipOnly = false
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return list
    }

    private fun parseUserCustomTracksSql(context: Context): List<SqlRawTrack> {
        val list = mutableListOf<SqlRawTrack>()
        val file = File(context.filesDir, "user_custom_tracks.sql")
        if (!file.exists()) return list
        try {
            val fullContent = file.readText(Charsets.UTF_8)
            val insertPattern = Pattern.compile("INSERT INTO `custom_tracks` .*?VALUES\\s*(.*);", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val matcher = insertPattern.matcher(fullContent)
            while (matcher.find()) {
                val valuesPart = matcher.group(1) ?: ""
                val rowStrings = valuesPart.split(Regex("\\),\\s*\\("))
                for (rowStr in rowStrings) {
                    val clean = rowStr.trim().removePrefix("(").removeSuffix(")")
                    val tokens = splitSqlCsv(clean)
                    if (tokens.size >= 8) {
                        val id = tokens[0].trim().toIntOrNull() ?: System.currentTimeMillis().toInt()
                        val name = tokens[1].trim('\'', '"', ' ')
                        val country = tokens[2].trim('\'', '"', ' ')
                        val city = tokens[3].trim('\'', '"', ' ')
                        val startLat = tokens[4].trim().toDoubleOrNull() ?: 0.0
                        val startLng = tokens[5].trim().toDoubleOrNull() ?: 0.0
                        val endLat = tokens[6].trim().toDoubleOrNull() ?: 0.0
                        val endLng = tokens[7].trim().toDoubleOrNull() ?: 0.0
                        val isCircuit = if (tokens.size > 8) tokens[8].trim() == "1" else true
                        val isHidden = if (tokens.size > 9) tokens[9].trim() == "1" else false
                        val isVip = if (tokens.size > 10) tokens[10].trim() == "1" else false
                        val mid1Lat = if (tokens.size > 12) tokens[12].trim().toDoubleOrNull() else null
                        val mid1Lng = if (tokens.size > 13) tokens[13].trim().toDoubleOrNull() else null
                        val mid2Lat = if (tokens.size > 14) tokens[14].trim().toDoubleOrNull() else null
                        val mid2Lng = if (tokens.size > 15) tokens[15].trim().toDoubleOrNull() else null

                        list.add(
                            SqlRawTrack(
                                id = id,
                                name = name,
                                country = country,
                                city = city,
                                startLat = startLat,
                                startLng = startLng,
                                endLat = endLat,
                                endLng = endLng,
                                mid1Lat = mid1Lat,
                                mid1Lng = mid1Lng,
                                mid2Lat = mid2Lat,
                                mid2Lng = mid2Lng,
                                category = if (isCircuit) TrackCategory.CIRCUIT else TrackCategory.TOUGE,
                                isHidden = isHidden,
                                isVipOnly = isVip,
                                isCustom = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveCustomTrack(context: Context, track: Track) {
        val existingTracks = getTracks(context).filter { it.isCustom && it.code != track.code }
        val allCustom = existingTracks.toMutableList()
        allCustom.add(track)

        val file = File(context.filesDir, "user_custom_tracks.sql")
        val sb = StringBuilder()
        sb.append("-- REVON User Custom Tracks Export\n")

        allCustom.forEachIndexed { idx, t ->
            val id = (1000 + idx)
            val isCircuitVal = if (t.category == TrackCategory.CIRCUIT) 1 else 0
            val mid1LatStr = t.mid1Lat?.toString() ?: "NULL"
            val mid1LngStr = t.mid1Lng?.toString() ?: "NULL"
            val mid2LatStr = t.mid2Lat?.toString() ?: "NULL"
            val mid2LngStr = t.mid2Lng?.toString() ?: "NULL"

            sb.append("INSERT INTO `custom_tracks` (`id`, `name`, `country`, `city`, `start_lat`, `start_lng`, `end_lat`, `end_lng`, `is_circuit`, `is_hidden`, `is_vip_only`, `created_at`, `mid1_lat`, `mid1_lng`, `mid2_lat`, `mid2_lng`) VALUES ")
            sb.append("($id, '${t.code}', '${t.subtitle?.substringBefore("·")?.trim() ?: "台灣"}', '${t.region}', ${t.startLat}, ${t.startLng}, ${t.endLat}, ${t.endLng}, $isCircuitVal, 0, 0, '2026-09-05 00:00:00', $mid1LatStr, $mid1LngStr, $mid2LatStr, $mid2LngStr);\n")
        }

        file.writeText(sb.toString(), Charsets.UTF_8)
        clearCache()
    }

    fun deleteCustomTrack(context: Context, trackCode: String) {
        val existingTracks = getTracks(context).filter { it.isCustom && it.code != trackCode }
        val file = File(context.filesDir, "user_custom_tracks.sql")

        if (existingTracks.isEmpty()) {
            if (file.exists()) file.delete()
        } else {
            val sb = StringBuilder()
            sb.append("-- REVON User Custom Tracks Export\n")
            existingTracks.forEachIndexed { idx, t ->
                val id = (1000 + idx)
                val isCircuitVal = if (t.category == TrackCategory.CIRCUIT) 1 else 0
                val mid1LatStr = t.mid1Lat?.toString() ?: "NULL"
                val mid1LngStr = t.mid1Lng?.toString() ?: "NULL"
                val mid2LatStr = t.mid2Lat?.toString() ?: "NULL"
                val mid2LngStr = t.mid2Lng?.toString() ?: "NULL"

                sb.append("INSERT INTO `custom_tracks` (`id`, `name`, `country`, `city`, `start_lat`, `start_lng`, `end_lat`, `end_lng`, `is_circuit`, `is_hidden`, `is_vip_only`, `created_at`, `mid1_lat`, `mid1_lng`, `mid2_lat`, `mid2_lng`) VALUES ")
                sb.append("($id, '${t.code}', '${t.subtitle?.substringBefore("·")?.trim() ?: "台灣"}', '${t.region}', ${t.startLat}, ${t.startLng}, ${t.endLat}, ${t.endLng}, $isCircuitVal, 0, 0, '2026-09-05 00:00:00', $mid1LatStr, $mid1LngStr, $mid2LatStr, $mid2LngStr);\n")
            }
            file.writeText(sb.toString(), Charsets.UTF_8)
        }
        clearCache()
    }

    private fun splitSqlCsv(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var quoteChar = ' '

        for (ch in input) {
            if ((ch == '\'' || ch == '"') && (quoteChar == ' ' || quoteChar == ch)) {
                inQuotes = !inQuotes
                quoteChar = if (inQuotes) ch else ' '
                sb.append(ch)
            } else if (ch == ',' && !inQuotes) {
                tokens.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) {
            tokens.add(sb.toString().trim())
        }
        return tokens
    }

    /**
     * 依 Waypoints (起點 -> 中繼點1 -> 中繼點2 -> 終點) 進行點位內插，生成連貫導航路徑線段
     */
    private fun generateInterpolatedPath(waypoints: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (waypoints.size < 2) return waypoints
        val result = mutableListOf<Pair<Double, Double>>()

        for (i in 0 until waypoints.size - 1) {
            val p1 = waypoints[i]
            val p2 = waypoints[i + 1]
            val steps = 25
            for (step in 0..steps) {
                val ratio = step.toDouble() / steps
                val lat = p1.first + (p2.first - p1.first) * ratio
                val lng = p1.second + (p2.second - p1.second) * ratio
                // 加一點微小地型波幅讓線條在畫面上自然流暢
                val wave = sin(ratio * Math.PI) * 0.0003
                result.add(Pair(lat + wave, lng + wave))
            }
        }
        return result
    }

    fun getTracksSortedByGpsDistance(context: Context, userLat: Double, userLng: Double): List<Pair<Track, Double>> {
        val tracks = getTracks(context).distinctBy { it.code }
        if (userLat == 0.0 && userLng == 0.0) {
            return tracks.map { Pair(it, 0.0) }
        }

        return tracks.map { track ->
            val sLat = track.startLat
            val sLng = track.startLng
            val distKm = if (sLat != null && sLng != null && sLat != 0.0 && sLng != 0.0) {
                haversineDistance(userLat, userLng, sLat, sLng)
            } else {
                9999.0
            }
            Pair(track, (round(distKm * 10) / 10.0))
        }.sortedBy { it.second }
    }

    fun getTrackPath(context: Context, trackCode: String): List<Pair<Double, Double>> {
        val cached = cachedPaths[trackCode]
        if (!cached.isNullOrEmpty()) return cached

        val tracks = getTracks(context)
        val track = tracks.find { it.code.equals(trackCode, ignoreCase = true) || it.name.equals(trackCode, ignoreCase = true) } 
            ?: tracks.firstOrNull { it.code.contains(trackCode, ignoreCase = true) || it.name.contains(trackCode, ignoreCase = true) }

        val queryName = track?.name ?: trackCode

        // 1. 優先從 assets/tracks/ 讀取真實的高精細 GPS 賽道點位 (如 華山路.json, 136.json, 麗寶賽車場.json 等)
        val assetPath = loadTrackPathFromAssets(context, queryName) ?: loadTrackPathFromAssets(context, trackCode)
        if (!assetPath.isNullOrEmpty()) {
            cachedPaths[trackCode] = assetPath
            return assetPath
        }

        // 2. 若無資產檔，嘗試從使用者已儲存的實際錄製 TrackSession 中讀取動態 GPS 點位
        val matchingSession = TrackSessionManager.getAllSessions(context).find {
            it.trackCode.equals(trackCode, ignoreCase = true) || it.trackName.contains(trackCode, ignoreCase = true) || it.trackName.contains(queryName, ignoreCase = true)
        }
        if (matchingSession != null && matchingSession.safePointsList.size >= 2) {
            val pts = matchingSession.safePointsList.map { Pair(it.latitude, it.longitude) }
            cachedPaths[trackCode] = pts
            return pts
        }

        // 3. 自訂/純點位賽道：依起點、檢查點1、檢查點2、終點生成自然平滑的彎道曲線 (Catmull-Rom Spline)
        if (track != null) {
            val waypoints = mutableListOf<Pair<Double, Double>>()
            track.startLat?.let { lat -> track.startLng?.let { lng -> waypoints.add(Pair(lat, lng)) } }
            if (track.mid1Lat != null && track.mid1Lng != null && track.mid1Lat != 0.0) waypoints.add(Pair(track.mid1Lat, track.mid1Lng))
            if (track.mid2Lat != null && track.mid2Lng != null && track.mid2Lat != 0.0) waypoints.add(Pair(track.mid2Lat, track.mid2Lng))
            track.endLat?.let { lat -> track.endLng?.let { lng -> waypoints.add(Pair(lat, lng)) } }

            if (waypoints.size >= 2) {
                val path = generateSmoothSplinePath(waypoints)
                cachedPaths[trackCode] = path
                return path
            }
        }

        return emptyList()
    }

    private fun loadTrackPathFromAssets(context: Context, nameOrCode: String): List<Pair<Double, Double>>? {
        val cleanQuery = nameOrCode.lowercase()
            .replace("台中華山", "華山")
            .replace("台中136", "136")
            .replace("彰化139", "139")
            .replace("賽道_", "")
            .trim()

        val folders = listOf("tracks/touge", "tracks/circuit")
        for (folder in folders) {
            try {
                val files = context.assets.list(folder) ?: continue
                val matchedFile = files.find { fileName ->
                    val nameWithoutExt = fileName.replace(".json", "").lowercase()
                    cleanQuery.contains(nameWithoutExt) || nameWithoutExt.contains(cleanQuery) ||
                            (cleanQuery.contains("華山") && nameWithoutExt.contains("華山")) ||
                            (cleanQuery.contains("136") && nameWithoutExt.contains("136")) ||
                            (cleanQuery.contains("139") && nameWithoutExt.contains("139")) ||
                            (cleanQuery.contains("麗寶") && nameWithoutExt.contains("麗寶")) ||
                            (cleanQuery.contains("溪湖") && nameWithoutExt.contains("溪湖")) ||
                            (cleanQuery.contains("谷關") && nameWithoutExt.contains("谷關")) ||
                            (cleanQuery.contains("公老坪") && nameWithoutExt.contains("公老坪")) ||
                            (cleanQuery.contains("天冷") && nameWithoutExt.contains("天冷")) ||
                            (cleanQuery.contains("日月潭") && nameWithoutExt.contains("日月潭")) ||
                            (cleanQuery.contains("華南") && nameWithoutExt.contains("華南"))
                } ?: continue

                val jsonStr = context.assets.open("$folder/$matchedFile").bufferedReader().use { it.readText() }
                val jsonArray = com.google.gson.JsonParser.parseString(jsonStr).asJsonArray
                val list = mutableListOf<Pair<Double, Double>>()
                for (elem in jsonArray) {
                    val obj = elem.asJsonObject
                    val lat = obj.get("lat")?.asDouble ?: 0.0
                    val lng = obj.get("lng")?.asDouble ?: 0.0
                    if (lat != 0.0 && lng != 0.0) {
                        list.add(Pair(lat, lng))
                    }
                }
                if (list.size >= 2) return list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    private fun generateSmoothSplinePath(waypoints: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (waypoints.size < 2) return waypoints

        // 若僅有起點與終點 (2個點位)，自動合成 2 個中繼彎點，形成連貫多彎山道路形
        val fullWaypoints = if (waypoints.size == 2) {
            val (p1, p2) = waypoints
            val dLat = p2.first - p1.first
            val dLng = p2.second - p1.second
            val m1 = Pair(p1.first + dLat * 0.35 + dLng * 0.18, p1.second + dLng * 0.35 - dLat * 0.18)
            val m2 = Pair(p1.first + dLat * 0.65 - dLng * 0.18, p1.second + dLng * 0.65 + dLat * 0.18)
            listOf(p1, m1, m2, p2)
        } else {
            waypoints
        }

        val extendedPts = mutableListOf<Pair<Double, Double>>()
        extendedPts.add(fullWaypoints.first())
        extendedPts.addAll(fullWaypoints)
        extendedPts.add(fullWaypoints.last())

        val result = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until extendedPts.size - 3) {
            val p0 = extendedPts[i]
            val p1 = extendedPts[i + 1]
            val p2 = extendedPts[i + 2]
            val p3 = extendedPts[i + 3]

            val steps = 40
            for (step in 0..steps) {
                val t = step.toDouble() / steps
                val t2 = t * t
                val t3 = t2 * t

                val lat = 0.5 * ((2 * p1.first) +
                        (-p0.first + p2.first) * t +
                        (2 * p0.first - 5 * p1.first + 4 * p2.first - p3.first) * t2 +
                        (-p0.first + 3 * p1.first - 3 * p2.first + p3.first) * t3)

                val lng = 0.5 * ((2 * p1.second) +
                        (-p0.second + p2.second) * t +
                        (2 * p0.second - 5 * p1.second + 4 * p2.second - p3.second) * t2 +
                        (-p0.second + 3 * p1.second - 3 * p2.second + p3.second) * t3)

                val sBend = sin(t * Math.PI * 4) * 0.0008 * (if (i % 2 == 0) 1.0 else -1.0)
                val cBend = cos(t * Math.PI * 4) * 0.0008 * (if (i % 2 == 0) -1.0 else 1.0)
                result.add(Pair(lat + sBend, lng + cBend))
            }
        }
        return result
    }

    private fun calculateTotalDistanceKm(path: List<Pair<Double, Double>>): Double {
        if (path.size < 2) return 3.5
        var total = 0.0
        for (i in 0 until path.size - 1) {
            val (lat1, lon1) = path[i]
            val (lat2, lon2) = path[i + 1]
            total += haversineDistance(lat1, lon1, lat2, lon2)
        }
        return total
    }

    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}

