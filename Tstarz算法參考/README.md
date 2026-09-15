# revon - Tstarz 核心功能模組匯出專案 (供 AI / 開發者參考)

本資料夾包含了從 Tstarz 車速與賽道分析 App 中抽離出的 4 大核心功能模組源碼與說明，供其他 AI 代理或開發團隊參考與移植。

---

## 📁 檔案與目錄結構

```text
revon/
├── README.md                          # 本說明文件
├── map_browsing/                      # 【1. 地圖瀏覽功能】
│   ├── CircuitFragment.kt             # 地圖主介面 (OsmDroid、LOD動態圖層、跟隨模式、主題)
│   ├── TrackPathView.kt               # 自訂 Canvas 軌跡畫布 (熱力圖、雙指縮放、點擊拖曳)
│   ├── TrackPreviewView.kt            # 軌跡預覽與點選互動 View (GPS與螢幕矩陣轉換)
│   └── TrackDetailFragment.kt         # 賽道詳細紀錄視圖 (軌跡與圖表聯動)
├── trigger_detection/                # 【2. 起終點判定功能】
│   └── VirtualGateEngine.kt           # 虛擬閘門判定引擎 (向量點積、起終點/環狀賽道過線、Pit區、逆向偵測)
├── route_management/                  # 【3. 路線與圖資處理】
│   ├── TrackInfo.kt                   # 賽道與圖資資料結構 (GeoJSON 解析、Pit區 Polygon 邊界)
│   └── TrackPoint.kt                  # GPS 軌跡點與物理傳感器點位資料結構
├── data_recording/                    # 【4. 數據紀錄與感測器融合】
│   ├── TelemetryService.kt            # 前台服務 (維持背景 GPS 與 IMU 傳感器持續採集)
│   ├── TrackManager.kt                # 數據持久化與瘦身 (JSON 序列化、Session 連刷、崩潰復原)
│   ├── GpsConfig.kt                   # 全局配置管理 (觸發模式、車輛類型、姿態校正基準)
│   ├── DragRaceFragment.kt            # 直線加速測試模組 (0-100km/h, 400m 衝線高頻插值)
│   └── IMUSensorFusion.kt             # IMU 感測器融合與姿態投影 (Gram-Schmidt 虛擬軸投影、傾角、G力)
```

---

## 🎯 四大核心功能說明

### 1. 地圖瀏覽功能 (Map Browsing)
* **圖層管理與 LOD 效能優化 (`CircuitFragment.kt`)**: 採用 `OsmDroid` 實現離線/線上地圖渲染。針對多賽道同時顯示的效能瓶頸，實現動態 LOD (Level of Detail) 算法，根據目前視角比例尺 (Meters Per MPP) 自動切換「單點摘要 Marker」與「完整多邊形 Polyline」。
* **速度熱力圖 (`TrackPathView.kt`)**: 使用 HSV 色彩空間 (藍 240° → 綠 120° → 紅 0°) 自動將車速（固定速域 0-120 km/h 或動態速域 Min-Max）轉為彩色繪製軌跡。
* **經緯度至螢幕座標轉換 (`TrackPreviewView.kt`)**: 使用 Android `Matrix` 實現包含緯度修正 (`Math.cos(Math.toRadians(avgLat))`) 的精確座標映射。

---

### 2. 起終點判定功能 (Start/End Gate Determination)
* **虛擬閘門向量點積算法 (`VirtualGateEngine.kt`)**:
  * 不僅依賴半徑觸發（易誤觸），引入**向量點積 (Dot Product)** 穿越判定。
  * 設定起點與衝刺方向向量 $\vec{B}$，車輛相對於起點的位移向量為 $\vec{A}$。
  * 計算點積 $Dot = \vec{A} \cdot \vec{B}$。當點積從**負值 (閘門後方)** 轉變為**正值 (閘門前方)** 的瞬間，判定為**精確過線**。
* **環狀賽道 (Circuit Loop) Lap 自動連刷**:
  * 偵測路徑進度 (Progress Percent)。當索引從末端 95% 跨越至前端 5% 且橫向距離 < 15m 時，自動結算上一圈並 Seamless 無縫展開新一圈紀錄。
* **Pit 區與暖胎區判定**:
  * 支援多邊形內部檢測 (`isPointInPolygon` 射線法) 及進出場點門檻，確保在 Pit 區內不會誤觸發計時。

---

### 3. 路線與圖資處理 (Route Management)
* **GeoJSON 解析 (`TrackInfo.kt`)**:
  * 支援 `LineString` (賽道主線) 與 `Polygon` / `Point` (Pit 區與進出點) 解析。
  * 自動判斷賽道類型：點對點 (Point-to-Point) 或環狀賽道 (Loop, 起終點距離 < 20m)。
  * 點對點賽道根據海拔變化自動拆分上山/下山或順/逆時針方向。
* **數據瘦身與精確度 (`TrackPoint.kt`)**:
  * 座標保留 7 位小數 (達 1 cm 精度)，速度與 G 力保留 2 位小數，顯著降低 JSON 檔案體積。

---

### 4. 數據紀錄與感測器融合 (Data Recording & Sensor Fusion)
* **前台服務保活 (`TelemetryService.kt`)**:
  * 啟動 Foreground Service 與 Ongoing Notification，防止 Android 系統在背景釋放 GPS 與 Sensor 監聽器。
* ** Gram-Schmidt 姿態投影與重力剝離 (`IMUSensorFusion.kt`)**:
  * 解決手機任意擺放（直放/橫放/傾斜）造成的加減速誤差。
  * 使用姿態校準基準 $\vec{G}_{down}$，透過 Gram-Schmidt 正交化建立車輛**前進軸 ($\vec{W}_{forward}$)** 與**橫向軸 ($\vec{W}_{right}$)**。
  * 將即時三軸加速度計數據投影至前進軸與橫向軸，精確分離出**縱向 G 力 (Longitudinal G)**、**側向 G 力 (Lateral G)** 與**機車壓車傾角 (Lean Angle)**。
* **崩潰與異常中斷復原機制 (`TrackManager.kt`)**:
  * 每 10 秒將快取數據寫入 `temp_recovery_run.json`，App 意外閃退或斷電重啟後自動跳出復原對話框。

---

## 💡 如何供其他 AI 參考與調用

其他 AI 代理在設計相關功能時，可依需求直接參考：
1. **需要實現 GPS 地圖熱力圖與軌跡渲染** ➔ 參考 `map_browsing/TrackPathView.kt` 與 `TrackPreviewView.kt`
2. **需要實現自動過線計時 / 賽道觸發閘門** ➔ 參考 `trigger_detection/VirtualGateEngine.kt`
3. **需要實現手機 IMU 側傾角與 G 力計算** ➔ 參考 `data_recording/IMUSensorFusion.kt`
4. **需要實現賽道 GeoJSON 解析與點位瘦身** ➔ 參考 `route_management/TrackInfo.kt` 與 `data_recording/TrackManager.kt`
