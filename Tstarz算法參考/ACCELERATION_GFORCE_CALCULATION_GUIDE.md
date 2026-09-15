# 獨立加、減速 G 值（Accel G / Braking G）判斷與計算完整說明文件

本文件詳細整理 **Tstarz Android 車輛測速與賽道分析系統** 中，關於獨立縱向加速度（加速 G 值 `accelG`）、獨立縱向減速度（煞車/減速 G 值 `brakingG`）、側向 G 力（`latG`）與總 G 力（`gForce`）的判斷、計算、感測器融合演算法、資料儲存結構以及前端分析應用的完整技術原理、數學算法與真實 Kotlin 原始碼。

---

## 1. 架構總覽與計算流程 (Architecture & Flow)

系統結合手機內建的 **三軸加速度計 / 線性加速度計 (Accelerometer / Linear Acceleration)**、**重力感測器 (Gravity Sensor)**、**陀螺儀 (Gyroscope)** 以及 **GPS 定位模組**，透過姿態校正、向量投影與慢速低通濾波 (Sensor Fusion) 來計算車輛當下的即時動態 G 力。

```
[三軸感測器 event.values] ──> [低通濾波 LPF (alpha=0.08)] ──> [基於校正 baseline 的車身座標軸投影 (Gram-Schmidt)]
                                                                           │
[GPS 速度差分 Acceleration] ──> [低頻融合坡度/姿態偏置 (Slope Bias)] <───┴───> [淨縱向 G 力 (fusedLongG)]
                                                                                       │
                                                 ┌─────────────────────────────────────┴─────────────────────────────────────┐
                                                 ▼                                                                           ▼
                               [正值: 加速狀態 fusedLongG > 0]                                             [負值: 煞車狀態 fusedLongG < 0]
                                     accelG = fusedLongG                                                         accelG = 0.0
                                     brakingG = 0.0                                                            brakingG = |fusedLongG|
```

---

## 2. 核心資料結構與儲存規格 (Data Models & Storage)

### 2.1 `TrackPoint` 資料模型 (`TrackManager.kt`)
系統採樣的每個軌跡點皆包含以下 G 力相關欄位：

```kotlin
data class TrackPoint(
    val lat: Double,
    val lng: Double,
    val speed: Double,          // 時速 km/h
    val timestamp: Long,        // 時間戳記 (ms)
    val leanAngle: Double = 0.0,// 傾角 (度)
    val latG: Double = 0.0,     // 側向 G 力
    val gForce: Double = 0.0,   // 全軸向合力總 G 值
    val accelG: Double = 0.0,   // 獨立縱向加速 G 值
    val brakingG: Double = 0.0, // 獨立縱向減速/煞車 G 值
    val distance: Double = 0.0  // 累積里程 (m)
)
```

### 2.2 JSON 序列化規格 (儲存與雲端同步)
在 JSON 軌跡檔與雲端備份中，為了縮減傳輸體積採用短 Key：
- `"g"` : `gForce` (總 G 力)
- `"ga"` : `accelG` (縱向加速 G 力)
- `"gb"` : `brakingG` (縱向煞車 G 力)
- `"gl"` : `latG` (側向 G 力)

**`TrackManager.kt` 序列化真實程式碼片段：**
```kotlin
// 寫入點資料至 JSON
val obj = JSONObject()
obj.put("s", round(it.speed, 2))
obj.put("g", round(it.gForce, 2))
obj.put("ga", round(it.accelG, 2))
obj.put("gb", round(it.brakingG, 2))
obj.put("gl", round(it.latG, 2))
```

---

## 3. 感測器數據擷取與預處理 (Sensor Acquisition & Preprocessing)

### 3.1 感測器類型選擇 (`SensorManager`)
系統優先使用 `Sensor.TYPE_LINEAR_ACCELERATION`（硬體/系統層級已分離重力的線性加速度計），若硬體不支援則退回使用 `Sensor.TYPE_ACCELEROMETER`（基礎三軸加速度計，需人工扣除 $1\text{G}$ 重力）。

### 3.2 低通濾波 (Low-Pass Filter, LPF) 原理與程式碼
為了消除車輛震動、引擎高頻微震與感測器雜訊，對感測器三軸原始值實施指數加權移動平均低通濾波：

$$\text{filteredG}[i] = \text{filteredG}[i] + \alpha \times (\text{event.values}[i] - \text{filteredG}[i])$$

**系統濾波係數 $\alpha$ 配置：**
- **單圈/賽道模式 (`CircuitFragment.kt`)**：$\alpha = 0.08$
- **直線加速模式 (`DragRaceFragment.kt`)**：$\alpha = 0.10$
- **即時調校頁面 (`SettingsFragment.kt`)**：$\alpha = 0.08$

```kotlin
// 指數加權移動平均 LPF
for (i in 0..2) {
    filteredG[i] = filteredG[i] + sensorAlpha * (event.values[i] - filteredG[i])
}
```

---

## 4. 手機姿態校正與車身座標軸投影 (Pose Calibration & Gram-Schmidt Projection)

由於手機固定在車輛/手機架上的角度各不相同（直放、斜放、橫放），直接取感測器 $Z$ 軸或 $Y$ 軸會產生嚴重的姿態傾斜誤差。系統採用 **Gram-Schmidt 正交化演算法** 將手機三軸動態投影至車輛真實的前進軸與橫向軸。

### 4.1 靜態重力基準校正 (`GpsConfig.saveGravityBaseline`)
1. 使用者將手機固定於車上靜止時點擊「姿態校正」。
2. 系統擷取 2 秒內 `Sensor.TYPE_GRAVITY` 的樣本平均值，取得基準重力向量 $\vec{B} = [b_x, b_y, b_z]$。
3. 計算向下單位向量 $\vec{D}$ (Down Vector)：
   $$\vec{D} = \frac{\vec{B}}{\|\vec{B}\|}$$

### 4.2 虛擬車身三軸投影演算法與公式
在每次感測器觸發 `onSensorChanged` 時執行以下步驟：

1. **向下軸 ($\vec{D}$ / `downW`)**：取校正存檔的 $\vec{B}$ 正規化向量。
   $$\vec{D} = \left[ \frac{b_x}{\sqrt{b_x^2+b_y^2+b_z^2}}, \frac{b_y}{\sqrt{b_x^2+b_y^2+b_z^2}}, \frac{b_z}{\sqrt{b_x^2+b_y^2+b_z^2}} \right]$$

2. **前進軸 ($\vec{F}$ / `forwardW`)**：將手機 $Y$ 軸向量 $[0, 1, 0]$ 投影至垂直於 $\vec{D}$ 的水平平面（Gram-Schmidt 正交化）：
   $$\vec{F}_{\text{raw}} = [0, 1, 0] - ([0, 1, 0] \cdot \vec{D}) \vec{D}$$
   正規化後取得車身前進方向單位向量：
   $$\vec{F} = \frac{\vec{F}_{\text{raw}}}{\|\vec{F}_{\text{raw}}\|}$$

3. **橫向軸 ($\vec{R}$ / `rightW`)**：利用前進軸與向下軸的叉積 (Cross Product) 取得橫向軸單位向量：
   $$\vec{R} = \vec{F} \times \vec{D} = [F_y D_z - F_z D_y, \; F_z D_x - F_x D_z, \; F_x D_y - F_y D_x]$$

**`CircuitFragment.kt` 正交投影真實程式碼：**
```kotlin
val baseline = GpsConfig.gravityBaseline ?: floatArrayOf(0f, 0f, 9.80665f)

// 1. 定位向下軸 downW
val baseNorm = Math.sqrt((baseline[0]*baseline[0] + baseline[1]*baseline[1] + baseline[2]*baseline[2]).toDouble())
val downW = if (baseNorm > 0.1) {
    doubleArrayOf(baseline[0]/baseNorm, baseline[1]/baseNorm, baseline[2]/baseNorm)
} else {
    doubleArrayOf(0.0, 0.0, 1.0)
}

// 2. 定位前進軸 forwardW (Gram-Schmidt: 從手機 Y 軸投影至水平面)
val yDotDown = 0 * downW[0] + 1 * downW[1] + 0 * downW[2]
var forwardW = doubleArrayOf(0 - yDotDown * downW[0], 1 - yDotDown * downW[1], 0 - yDotDown * downW[2])
val fNorm = Math.sqrt(forwardW[0]*forwardW[0] + forwardW[1]*forwardW[1] + forwardW[2]*forwardW[2])
if (fNorm > 1e-6) {
    forwardW = doubleArrayOf(forwardW[0]/fNorm, forwardW[1]/fNorm, forwardW[2]/fNorm)
}

// 3. 定位橫向軸 rightW (叉積: forward × down)
val rightW = doubleArrayOf(
    forwardW[1]*downW[2] - forwardW[2]*downW[1],
    forwardW[2]*downW[0] - forwardW[0]*downW[2],
    forwardW[0]*downW[1] - forwardW[1]*downW[0]
)
currentRightW = rightW
```

---

## 5. 獨立加、減速 G 值判斷與計算演算法 (Core Calculation & Code)

### 5.1 縱向加速度投影與 G 值換算
將低通濾波後的加速度向量投影至前進軸 $\vec{F}$：

$$a_{\text{long}} = \text{filteredG}[0] \cdot F_x + \text{filteredG}[1] \cdot F_y + \text{filteredG}[2] \cdot F_z$$

換算為以重力加速度 $g$ ($9.80665 \text{ m/s}^2$) 為單位的原始 IMU 縱向 G 值：

$$\text{currentImuG} = \frac{a_{\text{long}}}{9.80665}$$

### 5.2 GPS 與 IMU 慢速低頻融合（坡度與姿態偏置補償）
當車輛上坡或下坡時，車身角度變化會導致重力分量滲透進縱向加速度計。系統利用 GPS 速度差分導出的真實加速度進行極慢速修正：

1. **計算 GPS 速度差分加速度** (`CircuitFragment.kt` 的 `handleNewLocation`)：
   $$a_{\text{gps}} = \frac{(v_{\text{current}} - v_{\text{last}}) / 3.6}{\Delta t_{\text{gps}}}, \quad \text{gpsG} = \frac{a_{\text{gps}}}{9.80665}$$
2. **極慢速 LPF 適應坡度偏置 (`currentSlopeBiasG`)**：
   $$\text{error} = \text{fusedLongG}_{\text{prev}} - \text{gpsG}$$
   $$\text{currentSlopeBiasG} = 0.9 \times \text{currentSlopeBiasG} + 0.1 \times \text{error}$$
3. **取得融合後的淨縱向 G 值 (`fusedLongG`)**：
   $$\text{fusedLongG} = \text{currentImuG} - \text{currentSlopeBiasG}$$

**`CircuitFragment.kt` 中的 GPS 差分融合程式碼：**
```kotlin
// handleNewLocation 內：GPS G-force 差分計算
val currentGpsSpeedKmh = location.speed * 3.6
val currentGpsTime = location.time
if (lastGpsTime > 0L && currentGpsTime > lastGpsTime) {
    val dtGps = (currentGpsTime - lastGpsTime) / 1000.0
    if (dtGps > 0) {
        val gpsAccel = ((currentGpsSpeedKmh - lastGpsSpeedKmh) / 3.6) / dtGps
        lastGpsAccel = gpsAccel
        
        // GPS 與 IMU 低頻融合 (坡度估計)
        val gpsG = gpsAccel / 9.80665
        val error = fusedLongG - gpsG
        currentSlopeBiasG = 0.9 * currentSlopeBiasG + 0.1 * error
    }
}
```

### 5.3 獨立加、減速 G 值拆解與邏輯判斷
車輛加速時，慣性推力使加速度向量朝向前方（$\text{fusedLongG} > 0$）；煞車減速時，慣性力朝向後方（$\text{fusedLongG} < 0$）。

系統根據正負號將其嚴格拆解為兩個獨立的非負數指標：

**`CircuitFragment.kt` 完整拆解程式碼：**
```kotlin
// 4. 投影加速度向量
val longAccel = filteredG[0]*forwardW[0] + filteredG[1]*forwardW[1] + filteredG[2]*forwardW[2]
val latAccel = filteredG[0]*rightW[0] + filteredG[1]*rightW[1] + filteredG[2]*rightW[2]

currentLatG = Math.abs(latAccel / 9.80665)
currentGForce = Math.sqrt((filteredG[0]*filteredG[0] + filteredG[1]*filteredG[1] + filteredG[2]*filteredG[2]).toDouble()) / 9.80665

// 換算為 G 值
currentImuG = longAccel / 9.80665

// 高頻動態補償：扣除坡度與姿態誤差
fusedLongG = currentImuG - currentSlopeBiasG

// 加速時 fusedLongG 為正 (車架向前推設備)，煞車時為負
if (fusedLongG > 0) {
    currentAccelG = fusedLongG
    currentBrakingG = 0.0
} else {
    currentAccelG = 0.0
    currentBrakingG = Math.abs(fusedLongG)
}
```

---

## 6. 直線加速與即時儀表板差異處理 (Drag Race & Settings Variants)

### 6.1 直線加速模式 (`DragRaceFragment.kt`)
直線加速測速需依賴起步瞬間的靜態基準，並扣除靜態重力偏置：

```kotlin
val linAccX = filteredG[0] - baseline[0]
val linAccY = filteredG[1] - baseline[1]
val linAccZ = filteredG[2] - baseline[2]

val longAccel = linAccX*forwardW[0] + linAccY*forwardW[1] + linAccZ*forwardW[2]

currentImuG = longAccel / 9.80665
fusedLongG = currentImuG

// 物理判定：正值為加速，負值為煞車
currentAccelG = if (fusedLongG > 0) fusedLongG else 0.0
currentBrakingG = if (fusedLongG < 0) Math.abs(fusedLongG) else 0.0
currentGForce = Math.sqrt((filteredG[0]*filteredG[0] + filteredG[1]*filteredG[1] + filteredG[2]*filteredG[2]).toDouble()) / 9.80665
```

### 6.2 即時測試頁面 (`SettingsFragment.kt`)
在未進行車身正交投影的情況下，簡化版的縱向加減速判定如下：

```kotlin
val dX = if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && baseline != null) filteredG[0] - baseline[0] else filteredG[0]
val dZ = if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && baseline != null) filteredG[2] - baseline[2] else filteredG[2]

val longG = dZ / 9.80665f
val latG = dX / 9.80665f

// 加速為負 (向後慣性)，煞車為正 (向前慣性)
if (longG < 0 && Math.abs(longG) > GpsConfig.maxAccelG) GpsConfig.maxAccelG = Math.abs(longG).toDouble()
if (longG > 0 && longG > GpsConfig.maxBrakingG) GpsConfig.maxBrakingG = longG.toDouble()

val totalG = sqrt((longG * longG + latG * latG).toDouble())
if (totalG > GpsConfig.sessionMaxG) GpsConfig.sessionMaxG = totalG
```

---

## 7. 前端呈現、煞車點分析與高頻預測 (Frontend & Applications)

### 7.1 軌跡與圖表顯示 (`TrackDetailFragment.kt`)
```kotlin
// --- 嚴格區分新舊格式顯示 ---
if (maxA > 0.01 || maxB > 0.01) {
    // 新版格式：直接顯示加速與煞車分量
    txtMaxG.text = String.format("↑%.2fG ↓%.2fG", maxA, maxB)
} else {
    // 舊版格式：僅顯示總 G 力
    txtMaxG.text = String.format("%.2f G", maxG)
}

/**
 * 判斷某個點是否處於煞車狀態
 * - 新版紀錄 (有 ga/gb)：只看 brakingG >= 0.3
 * - 舊版紀錄 (只有 g)：不預測
 */
private fun checkBraking(p: TrackPoint, hasDetailedG: Boolean): Boolean {
    return if (hasDetailedG) {
        p.brakingG >= 0.3
    } else {
        false
    }
}
```

### 7.2 高頻感測器輔助衝線預測 (Sensor-Assisted Crossing Prediction)
利用當前加減速淨加速度 $a = (\text{accelG} - \text{brakingG}) \times 9.80665$ 計算高頻補償位移 $S = v_0 dt + \frac{1}{2} a dt^2$：

```kotlin
val a = (currentAccelG - currentBrakingG) * 9.80665
val v0 = lastLoc.speed.toDouble()
val displacement = v0 * dt + 0.5 * a * dt * dt

val bearingRad = Math.toRadians(lastLoc.bearing.toDouble())
val dLat = Math.toDegrees(displacement * Math.cos(bearingRad) / 6378137.0)
val dLng = Math.toDegrees(displacement * Math.sin(bearingRad) / (6378137.0 * Math.cos(Math.toRadians(lastLoc.latitude))))
val pPt = GeoPoint(lastLoc.latitude + dLat, lastLoc.longitude + dLng)
```

---

## 8. 原始碼檔案對照表 (Code Mapping Reference)

| 檔案名稱 (`com.example.qstart`) | 核心職責與關鍵程式碼位置 |
| :--- | :--- |
| [`GpsConfig.kt`](file:///c:/Users/User/Desktop/Tstarz/Android/app/src/main/java/com/example/qstart/GpsConfig.kt#L57-L59) | 儲存/載入 `gravityBaseline`，管理全域 Session `sessionMaxG` / `maxAccelG` / `maxBrakingG` |
| [`TrackManager.kt`](file:///c:/Users/User/Desktop/Tstarz/Android/app/src/main/java/com/example/qstart/TrackManager.kt#L17-L19) | `TrackPoint` 定義，JSON 序列化中的 `"g"`, `"ga"`, `"gb"`, `"gl"` 寫入 |
| [`CircuitFragment.kt`](file:///c:/Users/User/Desktop/Tstarz/Android/app/src/main/java/com/example/qstart/CircuitFragment.kt#L1491-L1551) | 實作 `onSensorChanged` 的 Gram-Schmidt 車身軸投影、IMU+GPS 慢速融合與獨立 `currentAccelG`/`currentBrakingG` 拆解 |
| [`DragRaceFragment.kt`](file:///c:/Users/User/Desktop/Tstarz/Android/app/src/main/java/com/example/qstart/DragRaceFragment.kt#L507-L551) | 直線加速模式的獨立 G 力計算與高頻衝線預測邏輯 |
| [`SettingsFragment.kt`](file:///c:/Users/User/Desktop/Tstarz/Android/app/src/main/java/com/example/qstart/SettingsFragment.kt#L731-L752) | 即時 G 力監控與儀表板顯示，縱向與側向 G 力判斷 |
| [`TrackDetailFragment.kt`](file:///c:/Users/User/Desktop/Tstarz/Android/app/src/main/java/com/example/qstart/TrackDetailFragment.kt#L457-L487) | 軌跡詳情頁面 `↑maxA ↓maxB` 格式化、煞車點判斷 (`checkBraking`) 與折線圖繪製 |
