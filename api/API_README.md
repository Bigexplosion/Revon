# REV-ON 後端 API 使用與整合規格說明書

> **更新日期**：2026-09-11  
> **系統狀態**：正式上線 Production Ready  
> **版本**：v1.3.0

本文件詳細說明目前已開發完成的 **REV-ON PHP 後端 API**，提供 Android App (Retrofit/OkHttp) 以及網頁前端/管理後台開發存取。

---

## 📌 通用規範、驗證與錯誤檢測機制

### 1. HTTP 回應格式與 Nginx 代理防護
本系統所有 API 無論成功或失敗，HTTP Header 一律固定回傳 `200 OK`，避免 Nginx 或 Cloudflare 代理層自動截斷 JSON Body 並覆蓋為預設 HTML 錯誤頁。真正的執行狀態由 Body 內部的 JSON `status` 與 `code` 欄位決定。

```json
{
  "status": "success",
  "code": 200,
  "message": "請求成功",
  "data": { ... }
}
```

### 2. Bearer Token 驗證 Header
保護性 API（如上傳成績、個人資料修改）需要在 HTTP Header 中夾帶 Token：
```http
Authorization: Bearer <your_access_token>
```

### 3. 輸入參數自動檢測機制 (validateInput Engine)
API 入口均會進行必要欄位檢驗與 SQL Injection 防護。若缺少必要欄位或格式不符，會統一回覆 `status: "error"` 與對應錯誤訊息。

---

## 📜 已完成 API 清單與規格

| 功能分組 | API 路徑 | Method | 需要 Bearer Token | 說明 |
| :--- | :--- | :--- | :--- | :--- |
| **驗證** | `/api/auth/login.php` | `POST` | ❌ | 用戶登入，取得 Bearer Token |
| **驗證** | `/api/auth/register.php` | `POST` | ❌ | 用戶註冊（支援兩步驟完整註冊資料），自動取得初始 Token |
| **驗證** | `/api/auth/me.php` | `GET` | ✅ | 獲取當前登入者個人完整資料 |
| **賽道** | `/api/track/list.php` | `GET` | ❌ | 獲取所有官方與自訂賽道清單 |
| **賽道** | `/api/track/upload_custom.php` | `POST` | ✅ | 用戶建立並上傳自訂賽道 |
| **成績** | `/api/race/upload.php` | `POST` | ✅ | 上傳山路/自訂賽道計時成績與詳細軌跡 Telemetry Log (支援 body 防篡改簽章) |
| **成績** | `/api/race/upload_circuit.php` | `POST` | ✅ | 上傳 Circuit 圈速成績與軌跡 JSON |
| **排行** | `/api/rank/leaderboard.php` | `GET` | ❌ | 查詢賽道排行榜（支援 `is_custom` 自訂賽道） |
| **車隊** | `/api/club/list.php` | `GET` | ❌ | 獲取車隊與俱樂部列表 |
| **用戶** | `/api/user/update_profile.php` | `POST` | ✅ | 修改個人暱稱、頭像、真實姓名、性別、電話、生日等 |
| **忘記密碼** | `/api/auth/forgot_password.php` | `POST` | ❌ | 發送驗證碼郵件以重置密碼 |
| **管理員** | `/api/admin/users.php` | `GET` / `POST` | ✅ (Admin Token) | 後台用戶清單查詢與用戶資料編輯 |
| **管理員** | `/api/admin/records.php` | `GET` / `DELETE` / `POST` | ✅ (Admin Token) | 競速成績、軌跡 Log(`action=telemetry`) 讀取、複選批次刪除 |
| **管理員** | `/api/admin/monitor.php` | `GET` / `POST` | ✅ (Admin Token) | 即時監控中樞、心跳上報(`action=heartbeat`)與完成連線清除 |
| **管理員** | `/api/admin/logs.php` | `GET` / `POST` | ✅ (Admin Token) | 系統與 SQL 錯誤日誌監控中樞與自動報錯寫入 |

---

### 1️⃣ 用戶登入 API
- **路徑**：`/api/auth/login.php`
- **Method**：`POST`

#### 請求 Body (JSON):
```json
{
  "account": "user123 或 user@example.com",
  "password": "mypassword"
}
```
*註：`account` 欄位支援輸入「使用者帳號」或「註冊電子信箱 Email」擇一進行登入。*

#### 回應範例:
```json
{
  "status": "success",
  "code": 200,
  "message": "登入成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6...",
    "user": {
      "id": 1,
      "account": "user123",
      "username": "極速車手",
      "email": "user@example.com",
      "real_name": "王小明",
      "gender": "male",
      "phone": "0912345678",
      "birthday": "1995-08-15",
      "avatar_url": "http://.../avatar.jpg"
    }
  }
}
```

---

### 2️⃣ 用戶註冊 API
- **路徑**：`/api/auth/register.php`
- **Method**：`POST`

#### 請求 Body (JSON):
```json
{
  "account": "user123",
  "password": "mypassword",
  "email": "user@example.com",
  "real_name": "王小明",
  "gender": "male",
  "phone": "0912345678",
  "birthday": "1995-08-15"
}
```
*註：`real_name` (真實姓名)、`gender` (male/female/other)、`phone` (電話)、`birthday` (YYYY-MM-DD) 為個人詳細資料欄位。*

#### 回應範例:
```json
{
  "status": "success",
  "code": 200,
  "message": "註冊成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6...",
    "user": {
      "id": 1,
      "account": "user123",
      "username": "user123",
      "email": "user@example.com",
      "real_name": "王小明",
      "gender": "male",
      "phone": "0912345678",
      "birthday": "1995-08-15"
    }
  }
}
```

---

### 3️⃣ 當前用戶個人資料 API
- **路徑**：`/api/auth/me.php`
- **Method**：`GET`
- **Header**：`Authorization: Bearer <token>`

#### 回應範例:
```json
{
  "status": "success",
  "code": 200,
  "message": "獲取成功",
  "data": {
    "user": {
      "id": 1,
      "account": "user123",
      "username": "極速車手",
      "email": "user@example.com",
      "real_name": "王小明",
      "gender": "male",
      "phone": "0912345678",
      "birthday": "1995-08-15",
      "avatar_url": "http://.../avatar.jpg"
    }
  }
}
```

---

### 4️⃣ 上傳山路/自訂賽道計時成績 API
- **路徑**：`/api/race/upload.php`
- **Method**：`POST`
- **Header**：`Authorization: Bearer <token>`

#### 說明與資料庫架構變更：
1. **成績統一儲存**：官方賽道與自訂賽道成績統一寫入 `track_results` 資料表（透過 `is_custom` 標記是否為自訂路線）。
2. **詳細軌跡與 Telemetry Log**：詳細路線軌跡、G力、傾角、速度時間序列紀錄統一儲存於獨立資料表 `track_telemetry_logs`，並透過 `result_id` 關聯。

#### 請求 Body (JSON):
```json
{
  "track_id": 1,
  "is_custom": 0,
  "elapsed_time_ms": 145230,
  "top_speed": 118.5,
  "max_lean_angle": 38.2,
  "vehicle_name": "Yamaha R3",
  "vehicle_type": "motorcycle",
  "telemetry_json": "{ ... GPS 軌跡與感測器序列 ... }"
}
```

#### 回應範例:
```json
{
  "status": "success",
  "code": 200,
  "message": "成績上傳成功",
  "data": {
    "result_id": 88
  }
}
```

---

### 5️⃣ 賽道排行榜查詢 API
- **路徑**：`/api/rank/leaderboard.php`
- **Method**：`GET`
- **Query 參數**：`track_id` (必填), `is_custom` (預設 0), `limit` (預設 50)

#### 回應範例:
```json
{
  "status": "success",
  "code": 200,
  "message": "獲取成功",
  "data": [
    {
      "rank": 1,
      "user_name": "極速車手",
      "avatar_url": "http://.../avatar.jpg",
      "vehicle": "Yamaha R3",
      "elapsed_time": "02:25.230",
      "top_speed": 118.5,
      "max_lean_angle": 38.2,
      "created_at": "2026-09-11 16:15:00"
    }
  ]
}
```

---

### 6️⃣ 修改個人資料 API
- **路徑**：`/api/user/update_profile.php`
- **Method**：`POST`
- **Header**：`Authorization: Bearer <token>`

#### 請求 Body (JSON):
```json
{
  "username": "新車手名稱",
  "avatar_url": "http://.../new_avatar.jpg",
  "real_name": "王小明",
  "gender": "male",
  "phone": "0912345678",
  "birthday": "1995-08-15"
}
```

#### 回應範例:
```json
{
  "status": "success",
  "code": 200,
  "message": "個人資料已更新",
  "data": {
    "username": "新車手名稱",
    "real_name": "王小明",
    "gender": "male",
    "phone": "0912345678",
    "birthday": "1995-08-15"
  }
}
```

---

### 7️⃣ 後台管理員 API (Admin User Management)
- **路徑**：`/api/admin/users.php`
- **Method**：`GET`（取得用戶清單） / `POST`（編輯用戶資料）

#### POST 編輯用戶 Request Body (JSON):
```json
{
  "action": "update_user",
  "user_id": 1,
  "username": "極速車手",
  "email": "user@example.com",
  "real_name": "王小明",
  "gender": "male",
  "phone": "0912345678",
  "birthday": "1995-08-15"
}
```

---

## 🗄️ 資料庫雙軌儲存架構說明 (山路 vs 封閉賽車場)

系統將路線分為**「山路 (Touge/Road)」**與**「封閉賽車場 (Circuit/Track Day)」**兩套獨立且對應的三表儲存體系：

### 1. 🏔️ 山路體系 (Touge / 官方與玩家自訂)
- **賽道主表**：`tracks` (官方) / `custom_tracks` (玩家自訂)
- **1. 成績摘要表 (`track_results`)**：儲存個人最佳成績摘要，帶有 `is_custom` 標籤 (0: 官方, 1: 自訂)。
- **2. 歷程日誌表 (`track_log`)**：無條件記錄每一次刷圈與完賽歷史履歷。
- **3. 軌跡數據表 (`track_telemetry_logs`)**：儲存詳細 GPS 點位、速度與車況數據 (JSON)。

### 2. 🏁 封閉賽車場體系 (Circuits / 賽道日)
- **賽道主表**：`circuits`
- **1. 成績摘要表 (`circuit_results`)**：儲存玩家在各賽車場的最佳單圈 (Lap Time) 摘要。
- **2. 歷程日誌表 (`circuit_log`)**：記錄玩家每次場次 (Session) 與每單圈的歷程紀錄。
- **3. 軌跡數據表 (`circuit_telemetry_logs`)**：儲存賽車場單圈詳細 GPS 遙測軌跡 (JSON)。

