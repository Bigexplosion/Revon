# 競速地圖標記（Start / Finish / GPS / Gyro Prediction）說明文件

本文件詳細說明競速賽道地圖中 **起點**、**終點**、**GPS點** 以及 **陀螺儀預測點（黃色）** 的外型樣式、繪製邏輯與 Native Kotlin 原生程式碼實作。

---

## 1. 概覽與樣式對照

| 標記類別 | 外型樣式描述 | 主要顏色 | 繪製元件 / 尺寸 |
| :--- | :--- | :--- | :--- |
| **起點 (Start Point)** | 亮天藍色空心圓環 | `#00E5FF` | 40px 空心圓點 (`createCircleMarker`) |
| **終點 (Finish Point)** | 亮天藍色空心圓環（環狀賽道則與起點重疊） | `#00E5FF` | 40px 空心圓點 (`createCircleMarker`) |
| **GPS點 (User Location)** | 雙層防護圈：外層半透明天藍光圈 + 內層實心白點 + 天藍邊框 | `#00E5FF` / `#FFFFFF` | 32dp 自訂 Icon (`createPlayerIcon`) |
| **陀螺儀預測點 (Gyro Prediction)** | 亮黃色雙層預測圈：外層半透明黃光圈 + 內層實心白點 + 亮黃邊框 | `#FFD600` | 32dp 自訂 Icon (`createPlayerIcon`) |

---

## 2. 標記細節與原生程式碼 (Native Kotlin)

### 2.1 起點 (Start Point) & 終點 (Finish Point)

#### 外型樣式
- **起點**：亮天藍色 (`#00E5FF`) 圓環，直徑 40px，中心空心。
- **終點**：
  - **點對點賽道**：路線末端標註亮天藍色 (`#00E5FF`) 空心圓環，直徑 40px。
  - **環狀賽道（起終點距離 < 20m）**：自動將起終點視為同一位置，僅顯示起點標記。

#### 原生程式碼實作
主要位於 `CircuitFragment.kt` [CircuitFragment.kt:L680-L714](file:///c:/Users/User/Desktop/Tstarz/Android/app/src/main/java/com/example/qstart/CircuitFragment.kt#L680-L714)

```kotlin
// 圓形 Marker Icon 生成函式
private fun createCircleMarker(color: Int, size: Int, hollow: Boolean): Bitmap {
    val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    b.applyCanvas {
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.color = color
        if (hollow) { 
            // 空心圓環
            p.style = Paint.Style.STROKE
            p.strokeWidth = size / 4f
            drawCircle(size / 2f, size / 2f, size / 3f, p) 
        } else { 
            // 實心圓點帶白芯
            drawCircle(size / 2f, size / 2f, size / 2f, p)
            p.color = Color.WHITE
            drawCircle(size / 2f, size / 2f, size / 4f, p) 
        }
    }
    return b
}

// 建立起點標記
val startMarker = Marker(map).apply {
    position = track.startPoint
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    icon = createCircleMarker(Color.parseColor("#00E5FF"), 40, true).toDrawable(resources)
    title = "${baseName}${direction}"
    snippet = "海拔: ${track.startPoint.altitude.toInt()}m"
    infoWindow = customBubble
    setOnMarkerClickListener { m, _ -> closeAllMapInfoWindows(map); m.showInfoWindow(); true }
}
referenceTracksFolder.add(startMarker)

// 若非環狀賽道則建立終點標記
if (!isLoop) {
    val endMarker = Marker(map).apply {
        position = track.endPoint
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = createCircleMarker(Color.parseColor("#00E5FF"), 40, true).toDrawable(resources)
        title = "${baseName} ${oppositeDir}"
        snippet = "海拔: ${track.endPoint.altitude.toInt()}m"
        infoWindow = customBubble
        setOnMarkerClickListener { m, _ -> closeAllMapInfoWindows(map); m.showInfoWindow(); true }
    }
    referenceTracksFolder.add(endMarker)
}
```

---

### 2.2 GPS點 (GPS Location Point)

#### 外型樣式
- 代表車手當前實時 GPS 定位位置。
- **圖層結構（從外到內）**：
  1. 外層：半透明天藍色（`#00E5FF`，Alpha 值 80）圓形擴散區，半徑約 14.5dp。
  2. 內層邊框：天藍色 (`#00E5FF`) 2dp 寬度圓形線條。
  3. 中心點：純白色 (`#FFFFFF`) 實心小圓點。

#### 原生程式碼實作
主要位於 `CircuitFragment.kt` [CircuitFragment.kt:L1244-L1250](file:///c:/Users/User/Desktop/Tstarz/Android/app/src/main/java/com/example/qstart/CircuitFragment.kt#L1244-L1250) 與 [L1357-L1361](file:///c:/Users/User/Desktop/Tstarz/Android/app/src/main/java/com/example/qstart/CircuitFragment.kt#L1357-L1361)

```kotlin
// 車手定位 Icon 生成函式
private fun createPlayerIcon(mainColor: Int = Color.parseColor("#00E5FF")): Bitmap {
    val d = resources.displayMetrics.density
    val s = (32 * d).toInt()
    val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    Canvas(b).apply { 
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // 1. 外層光圈 (半透明)
        p.color = mainColor
        p.alpha = 80
        drawCircle(s / 2f, s / 2f, s / 2.2f, p)
        
        // 2. 中心實心白點
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        p.alpha = 255
        drawCircle(s / 2f, s / 2f, s / 5f, p)
        
        // 3. 內層彩色邊框
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2 * d
        p.color = mainColor
        drawCircle(s / 2f, s / 2f, s / 4f, p) 
    }
    return b
}

// 在 updateUI(location: Location) 中更新 GPS 定位標記
if (userLocationMarker == null) {
    userLocationMarker = Marker(map).apply { 
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = createPlayerIcon(Color.parseColor("#00E5FF")).toDrawable(resources)
        setOnMarkerClickListener { _, _ -> true } 
    }
    userLocationMarker?.let { map?.overlays?.add(it) }
}
userLocationMarker?.position = GeoPoint(location.latitude, location.longitude)
```

---

### 2.3 陀螺儀預測點 (黃色) (Gyroscope Prediction Point)

#### 外型樣式
- **黃色光點 (`#FFD600`)**。
- 外型結構與 GPS 點一致（32dp 雙層圈標標記），但主題顏色改為亮黃色 (`#FFD600`)。
- **作用**：結合 IMU（陀螺儀與加速度計）高頻數據（50Hz+）推算前進位移，填補低頻 GPS（1Hz~10Hz）兩次刷新間的空窗期，達到極高頻率的過線與軌跡預測。

#### 原生程式碼實作
主要位於 `CircuitFragment.kt` [CircuitFragment.kt:1565-L1620](file:///c:/Users/User/Desktop/Tstarz/Android/app/src/main/java/com/example/qstart/CircuitFragment.kt#L1565-L1620)

```kotlin
// 在 onSensorChanged() 內，結合 IMU 加速度與 GPS 進行航跡推算 (Dead Reckoning)
if (lastLocationObject != null && GpsConfig.isSensorAssistGpsEnabled(requireContext())) {
    val lastLoc = lastLocationObject ?: return
    val dt = (System.currentTimeMillis() - lastLoc.time) / 1000.0
    
    if (dt > 0 && dt < 1.5) {
        // 1. 計算感測器軸向淨加速度 a (m/s²)
        val a = (currentAccelG - currentBrakingG) * 9.80665
        val v0 = lastLoc.speed.toDouble()
        
        // 2. 位移算式: d = v0 * dt + 0.5 * a * dt²
        val displacement = v0 * dt + 0.5 * a * dt * dt
        
        // 3. 沿當前航向角 (bearing) 經緯度投影
        val bearingRad = Math.toRadians(lastLoc.bearing.toDouble())
        val dLat = Math.toDegrees(displacement * Math.cos(bearingRad) / 6378137.0)
        val dLng = Math.toDegrees(displacement * Math.sin(bearingRad) / (6378137.0 * Math.cos(Math.toRadians(lastLoc.latitude))))
        val pPt = GeoPoint(lastLoc.latitude + dLat, lastLoc.longitude + dLng)

        // 4. 靠近起終點 (300m 內) 或開啟全區預測時，在地圖繪製黃色預測點 Marker
        if (showPredictMarker) {
            handler.post {
                if (predictedMarker == null) {
                    predictedMarker = Marker(map).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        // 使用亮黃色 (#FFD600) 建立 Icon
                        icon = createPlayerIcon(Color.parseColor("#FFD600")).toDrawable(resources)
                        setOnMarkerClickListener { _, _ -> true }
                    }
                    predictedMarker?.let { map?.overlays?.add(it) }
                }
                // 更新黃點位置
                predictedMarker?.position = pPt
                map?.invalidate()
            }
        }
    }
}
```

---

## 3. 檔案架構對照

- **核心頁面**: [CircuitFragment.kt](file:///c:/Users/User/Desktop/Tstarz/Android/app/src/main/java/com/example/qstart/CircuitFragment.kt)
- **通用地圖元件**: [TrackPathView.kt](file:///c:/Users/User/Desktop/Tstarz/Android/app/src/main/java/com/example/qstart/TrackPathView.kt)
- **地圖與 GPS 設定檔**: [GpsConfig.kt](file:///c:/Users/User/Desktop/Tstarz/Android/app/src/main/java/com/example/qstart/GpsConfig.kt)
