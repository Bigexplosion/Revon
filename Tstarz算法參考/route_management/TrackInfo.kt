package com.example.qstart.revon

import org.json.JSONObject
import java.io.File

/**
 * 賽道資訊與 GeoJSON 圖資解析類別
 */
data class TrackInfo(
    val name: String,
    val points: List<VirtualGateEngine.GeoPoint2D>,
    val startPoint: VirtualGateEngine.GeoPoint2D,
    val endPoint: VirtualGateEngine.GeoPoint2D,
    val diagonalMeters: Double = 0.0,
    val isLoop: Boolean = false,
    val pitPolygon: List<VirtualGateEngine.GeoPoint2D>? = null,
    val pitEntrance: VirtualGateEngine.GeoPoint2D? = null,
    val pitEntranceRadius: Double = 15.0,
    val pitExit: VirtualGateEngine.GeoPoint2D? = null,
    val pitExitRadius: Double = 15.0
) {
    companion object {
        /**
         * 從 GeoJSON 字串解析賽道資訊
         */
        fun parseGeoJson(jsonString: String, trackName: String): List<TrackInfo> {
            val resultTracks = mutableListOf<TrackInfo>()
            val points = mutableListOf<VirtualGateEngine.GeoPoint2D>()
            var pitPoly: List<VirtualGateEngine.GeoPoint2D>? = null
            var pitEntrancePt: VirtualGateEngine.GeoPoint2D? = null
            var pitEntranceRad = 15.0
            var pitExitPt: VirtualGateEngine.GeoPoint2D? = null
            var pitExitRad = 15.0

            try {
                if (jsonString.trim().startsWith("{")) {
                    val geoJson = JSONObject(jsonString)
                    val features = geoJson.optJSONArray("features") ?: return emptyList()
                    for (f in 0 until features.length()) {
                        val feature = features.getJSONObject(f)
                        val geometry = feature.getJSONObject("geometry")
                        val properties = feature.optJSONObject("properties") ?: JSONObject()
                        val typeProp = properties.optString("type", "")
                        val geomType = geometry.getString("type")

                        if (geomType == "LineString") {
                            val coords = geometry.getJSONArray("coordinates")
                            for (i in 0 until coords.length()) {
                                val c = coords.getJSONArray(i)
                                points.add(VirtualGateEngine.GeoPoint2D(c.getDouble(1), c.getDouble(0), if (c.length() > 2) c.getDouble(2) else 0.0))
                            }
                        } else if (geomType == "Polygon" && typeProp == "pit_zone") {
                            val coordsOuter = geometry.getJSONArray("coordinates")
                            if (coordsOuter.length() > 0) {
                                val coords = coordsOuter.getJSONArray(0)
                                val polyPoints = mutableListOf<VirtualGateEngine.GeoPoint2D>()
                                for (i in 0 until coords.length()) {
                                    val c = coords.getJSONArray(i)
                                    polyPoints.add(VirtualGateEngine.GeoPoint2D(c.getDouble(1), c.getDouble(0)))
                                }
                                pitPoly = polyPoints
                            }
                        } else if (geomType == "Point" && typeProp == "pit_entrance") {
                            val c = geometry.getJSONArray("coordinates")
                            pitEntrancePt = VirtualGateEngine.GeoPoint2D(c.getDouble(1), c.getDouble(0))
                            pitEntranceRad = properties.optDouble("radius", 15.0)
                        } else if (geomType == "Point" && typeProp == "pit_exit") {
                            val c = geometry.getJSONArray("coordinates")
                            pitExitPt = VirtualGateEngine.GeoPoint2D(c.getDouble(1), c.getDouble(0))
                            pitExitRad = properties.optDouble("radius", 15.0)
                        }
                    }
                }

                if (points.isNotEmpty()) {
                    val pStart = points.first()
                    val pEnd = points.last()
                    val isLoop = pStart.distanceToMeters(pEnd) < 20.0

                    if (isLoop) {
                        resultTracks.add(TrackInfo(trackName, points, pStart, pEnd, 0.0, true, pitPoly, pitEntrancePt, pitEntranceRad, pitExitPt, pitExitRad))
                    } else {
                        val (n1, n2) = if (pStart.altitude < pEnd.altitude)
                            Pair("${trackName}_上山", "${trackName}_下山")
                        else
                            Pair("${trackName}_下山", "${trackName}_上山")

                        resultTracks.add(TrackInfo(n1, points, pStart, pEnd, 0.0, false, pitPoly, pitEntrancePt, pitEntranceRad, pitExitPt, pitExitRad))
                        resultTracks.add(TrackInfo(n2, points.reversed(), pEnd, pStart, 0.0, false, pitPoly, pitEntrancePt, pitEntranceRad, pitExitPt, pitExitRad))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return resultTracks
        }
    }
}
