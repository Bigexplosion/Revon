# REV-ON 系統文檔與技術規格庫 (REV-ON System Documentation)

本資料夾存放 REV-ON Android 競速應用程式的系統架構規格、資料庫結構與開發者說明文檔。

---

## 📚 文檔索引 (Documentation Index)

| 文檔名稱 | 說明內容 | 適用對象 / 用途 |
| :--- | :--- | :--- |
| 📄 [`STORAGE_ARCHITECTURE.md`](STORAGE_ARCHITECTURE.md) | **三層式賽道紀錄儲存與同步機制規格書**<br>詳細比較 SharedPreferences 快取、本機 Tstarz JSON 檔案與 Firebase 雲端廣播差異，包含完整 Data Schemas 與代碼指引。 | 開發者、數據分析、未來功能擴充 |

---

## 🛠️ 技術組件簡介
- **前端介面**：Android Jetpack Compose (Material3 + Custom Canvas Graphics)
- **競速引擎**：IMU Sensor Fusion + GPS Virtual Gate 虛擬計時閘門檢測
- **雲端後端**：Firebase Auth + Firestore Real-time Database + Firebase Storage
- **地圖系統**：OsmDroid 暗黑極簡化客製濾鏡地圖
