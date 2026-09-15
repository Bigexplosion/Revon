# REV-ON 資料庫架構說明書

> **資料庫版本：** MariaDB 10.11  
> **匯出工具：** phpMyAdmin 5.2.2  
> **字元集：** `utf8mb4_unicode_ci` / `utf8mb4_general_ci`  
> **最後更新：** 2026-09-08  

---

## 目錄

1. [概覽](#概覽)
2. [資料表分組](#資料表分組)
3. [核心資料表（用戶）](#一核心資料表用戶)
4. [核心資料表（賽道與成績）](#二核心資料表賽道與成績)
5. [賽道計時紀錄](#三賽道計時紀錄)
6. [賽道圈速系統（Circuit Mode）](#四賽道圈速系統circuit-mode)
7. [即時競速狀態](#五即時競速狀態)
8. [排行榜系統](#六排行榜系統)
9. [VIP 與訂閱系統](#七vip-與訂閱系統)
10. [點數與優惠系統](#八點數與優惠系統)
11. [公告與社群系統](#九公告與社群系統)
12. [電商系統](#十電商系統)
13. [管理後台系統](#十一管理後台系統)
14. [AI 巡邏反作弊系統](#十二ai-巡邏反作弊系統)
15. [噪音大賽系統](#十三噪音大賽系統)
16. [其他輔助資料表](#十四其他輔助資料表)
17. [**PHP API 整合設計指引**](#php-api-整合設計指引)
18. [**Android App JSON 格式與 SQL 欄位映射**](#android-app-json-格式與-sql-欄位映射) ← 新增
19. [關聯圖（ER 概覽）](#er-關聯概覽)
20. [建議指引（上架前）](#建議指引上架前)


---

## 概覽

REV-ON 是一款台灣的山路計時 APP，支援汽車（car）、機車（motor）等車種，  
玩家可在地圖上選擇路線、自行建立路線、進行 GPS 計時比賽，並查看排行榜。  

資料庫分為兩類路線模式：
- **山路模式（Touge/Mountain Mode）**：一般山路計時，使用 `tracks`、`track_results`、`track_log` 等表。
- **賽道模式（Circuit Mode）**：封閉賽道圈速計時，使用 `circuits`、`circuit_laps`、`circuit_live_status` 等表，並支援即時遙測上傳。

---

## 資料表分組

| 群組 | 資料表名稱 |
|------|-----------|
| 用戶 | `users`, `user_logs`, `user_reports` |
| 賽道 | `tracks`, `custom_tracks`, `circuits` |
| 成績 | `track_results`, `track_log`, `circuit_laps` |
| 即時狀態 | `live_status`, `circuit_live_status`, `active_sessions` |
| 排行榜 | `rank_snapshots`, `vip_leaderboard`, `player_sessions` |
| VIP 訂閱 | `vip_subscriptions`, `subscriptions` |
| 點數 | `points_logs`, `coupons`, `coupon_usage` |
| 公告社群 | `announcements`, `posts`, `post_comments`, `post_likes`, `banners`, `infos` |
| 電商 | `products`, `orders`, `order_items`, `cart`, `payment_callbacks` |
| 管理 | `admin_users`, `admin_logs`, `error_logs` |
| AI 反作弊 | `ai_police_alerts`, `ai_police_scan_log` |
| 噪音大賽 | `noise_contest_sessions`, `noise_contest_attempts`, `noise_contest_results`, `noise_contest_audit_log`, `decibel_tests` |
| 其他 | `countries`, `bookings`, `push_subscribers`, `rooms`, `lottery_entries`, `post_backgrounds`, `platforms` |

---

## 一、核心資料表（用戶）

### `users` — 用戶帳號主表

> 所有 APP 使用者的基本資料、VIP 狀態、角色。

| 欄位 | 類型 | 說明 | 範例值 |
|------|------|------|--------|
| `id` | INT PK AUTO | 用戶唯一 ID | `19` |
| `account` | VARCHAR(50) | 帳號（登入用） | `Revon` |
| `password` | VARCHAR(255) | bcrypt 雜湊密碼 | `$2y$10$eKRge9...` |
| `real_name` | VARCHAR(50) | 真實姓名 | `廖郁鈞` |
| `nickname` | VARCHAR(50) | 顯示暱稱 | `鈞` |
| `gender` | ENUM | 性別 `male/female/other` | `male` |
| `phone` | VARCHAR(30) | 手機號碼 | `0906776965` |
| `email` | VARCHAR(100) | 電子郵件（忘記密碼、VIP 通知用） | `qaz911028@gmail.com` |
| `birthday` | DATE | 生日 | `2002-10-28` |
| `city` | VARCHAR(50) | 所在城市 | `台灣` |
| `points` | INT | 可用點數 | `0` |
| `created_at` | DATETIME | 註冊時間 | `2026-01-26 16:13:48` |
| `vip_level` | INT | VIP 等級（1=一般，2+=進階） | `1` |
| `vip_expire` | DATETIME | VIP 到期時間 | `2099-12-31 23:59:59` |
| `remember_token` | VARCHAR(64) | 登入保持 Token（自動登入用） | `abc123...` |
| `vip_whitelist` | TINYINT | 是否列入 VIP 白名單（免費 VIP） | `1` |
| `used_coupon` | TINYINT | 是否已使用過優惠券（舊欄位） | `0` |
| `reset_token` | VARCHAR(255) | 密碼重設 Token | `null` |
| `token_expire` | DATETIME | 重設 Token 有效期限 | `null` |
| `free_plays` | TINYINT UNSIGNED | 免費遊玩次數（新玩家預設 5 次） | `5` |
| `role` | VARCHAR(20) | 角色 `user / admin` | `admin` |

> **注意：** `email` 為新用戶強制必填，舊用戶可自行填寫。若未填寫，忘記密碼功能將失效。

---

### `user_logs` — 用戶交易紀錄

> 記錄每筆點數變動與 VIP 訂閱事件，用於對帳與稽核。

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `user_id` | 對應 `users.id` | `19` |
| `order_no` | 訂單編號（藍新金流格式） | `REVON177493629419` |
| `change_amount` | 變動金額或點數（TWD 或 pts） | `200` |
| `reason` | 變動原因文字說明 | `VIP 訂閱成功` |
| `created_at` | 交易時間 | `2026-03-31 15:10:27` |

---

### `user_reports` — 玩家問題回報

> 玩家透過 APP 提交的問題單，由管理員處理。

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `ticket_no` | 表單編號（格式：RV+日期+流水號） | `RV202607030001` |
| `user_id` | 提交者 ID | `19` |
| `user_email` | 提交者信箱（快速聯絡用） | `qaz911028@gmail.com` |
| `category` | 問題類型 `bug/suggestion/account/payment/other` | `bug` |
| `subject` | 問題標題 | `沒辦法測時間` |
| `description` | 詳細描述 | `GPS 跑不出來` |
| `status` | 處理狀態 `pending/processing/resolved/closed` | `resolved` |
| `admin_reply` | 管理員回覆內容 | `目前已完成更新，請再使用看看` |
| `admin_id` | 處理的管理員 ID | `19` |
| `replied_at` | 回覆時間 | `2026-07-12 01:07:52` |

---

## 二、核心資料表（賽道與成績）

### `tracks` — 官方賽道（山路模式）

> 由管理員建立的官方計時路線，為主要計分對象。

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `id` | 賽道 ID | `1` |
| `name` | 賽道名稱 | `紅86` |
| `country` | 國家 | `Taiwan` |
| `city` | 城市 | `Tainan` |
| `start_lat` / `start_lng` | 起點 GPS 座標（緯度/經度） | `22.931538, 120.224336` |
| `end_lat` / `end_lng` | 終點 GPS 座標 | `22.933874, 120.351600` |
| `img` | 賽道預覽圖路徑 | `track1.jpg` |
| `is_hidden` | 是否隱藏（管理員測試用） | `0` |
| `is_vip_only` | 是否 VIP 限定賽道 | `0` |
| `vehicle_type` | 可使用車輛類型 `car/motor` | `car` |
| `mid1_lat` / `mid1_lng` | 中繼點 1 GPS 座標（防作弊用） | `24.146509, 120.876594` |
| `mid2_lat` / `mid2_lng` | 中繼點 2 GPS 座標 | `24.120250, 120.887690` |
| `created_at` | 建立時間 | `2026-04-02 14:22:54` |

---

### `custom_tracks` — 玩家自訂路線

> 玩家提交的自訂計時路線，需經管理員審核後才會出現在 APP 中。

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `id` | 自訂路線 ID | `1005` |
| `name` | 路線名稱 | `義大-三角` |
| `country` / `city` | 國家 / 城市 | `Taiwan / Kaohsiung` |
| `start_lat` / `start_lng` | 起點座標 | `22.7311492, 120.3994408` |
| `end_lat` / `end_lng` | 終點座標 | `22.7297252, 120.3694168` |
| `is_hidden` | 是否隱藏 | `0` |
| `is_vip_only` | 是否 VIP 專屬 | `0` |
| `vehicle_type` | 車輛類型 | `car` |
| `mid1_lat` / `mid1_lng` | 中繼點 1 座標（防跳點） | `null` |
| `mid2_lat` / `mid2_lng` | 中繼點 2 座標 | `null` |
| `creator_id` | 建立者用戶 ID | `2630` |
| `creator_email` | 建立者信箱 | `a93781922@gmail.com` |
| `creator_account` | 建立者帳號 | `gy_1627` |
| `status` | 審核狀態 `pending/approved/rejected` | `approved` |
| `reject_reason` | 駁回原因 | `null` |
| `approved_at` | 審核通過時間 | `2026-07-03 20:24:02` |
| `path` | 預生成路線 JSON（地圖繪製用） | `null` |
| `is_deleted` | 軟刪除旗標 | `0` |

> **`tracks` vs `custom_tracks` 差異：** `tracks` 為管理員直接建立的官方路線；`custom_tracks` 為玩家投稿並需審核的路線，兩者欄位幾乎相同，但 `custom_tracks` 多了審核流程欄位（`creator_id`, `status`, `reject_reason`, `approved_at`）。

---

### `circuits` — 賽道場地（Circuit Mode 封閉賽道）

> 支援圈速計時的封閉賽道場地定義，與山路模式的 `tracks` 不同。

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `id` | 賽道 ID | `1` |
| `name` | 賽道名稱 | `麗寶賽車場` |
| `country` / `city` | 國家 / 城市 | `台灣 / 台中` |
| `line_p1_lat` / `line_p1_lng` | 起終點線端點 1 GPS | `24.3189656, 120.6866367` |
| `line_p2_lat` / `line_p2_lng` | 起終點線端點 2 GPS | `24.3188630, 120.6867238` |
| `creator_id` | 建立者用戶 ID | `19` |
| `status` | 狀態 `active/hidden` | `active` |

> **Circuit Mode 的起終點線** 是一條虛擬線段（P1→P2），玩家 GPS 穿越該線時觸發圈速計時。

---

## 三、賽道計時紀錄

### `track_results` — 山路最佳成績表

> 每位用戶在每條賽道的每一筆計時成績（含重複紀錄，用於取最佳值）。

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `user_id` | 用戶 ID（→ `users.id`） | `19` |
| `track_id` | 賽道 ID（→ `tracks.id`） | `1` |
| `time_ms` | 計時結果（**毫秒**） | `231012`（約 3 分 51 秒） |
| `vehicle_type` | 車種 `car/motor/other` | `car` |
| `created_at` | 完成時間 | `2026-01-28 21:16:21` |

---

### `track_log` — 山路完整計時日誌（含賽季）

> 與 `track_results` 類似，但多了賽季月份欄位，適合用於月賽排行。

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `user_id` | 用戶 ID | `19` |
| `track_id` | 賽道 ID | `1` |
| `season_id` | 賽季月份（格式：`YYYY-MM`），空字串表示不分賽季 | `2026-09` |
| `time_ms` | 計時結果（毫秒） | `231012` |
| `vehicle_type` | 車種 | `car` |
| `created_at` | 完成時間 | `2026-01-28 21:16:21` |

> **注意：** `track_results` 與 `track_log` 目前存在數據重疊，建議日後整合為單一表。

---

### `circuit_laps` — 圈速計時紀錄

> 玩家在封閉賽道（Circuit Mode）的每一圈成績，按 `session_id` 分組。

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `circuit_id` | 賽道場地 ID（→ `circuits.id`） | `2` |
| `user_id` | 用戶 ID | `19` |
| `session_id` | 場次 ID（格式：`sess_{timestamp}_{random}`） | `sess_1788589352321_pu4kf2` |
| `lap_number` | 第幾圈 | `1` |
| `lap_time_ms` | 該圈成績（毫秒） | `528967` |
| `vehicle_type` | 車種 | `car` |
| `created_at` | 完成時間 | `2026-09-05 14:42:11` |

---

### `results` — 通用成績表（舊版/備用）

> 結構較簡單的成績表，可能為早期版本的遺留，或作為備用。

| 欄位 | 說明 |
|------|------|
| `user_id` | 用戶 ID |
| `track_id` | 賽道 ID |
| `time` | 計時結果（毫秒） |
| `created_at` | 完成時間 |

---

## 四、賽道圈速系統（Circuit Mode）

### `circuit_live_status` — 即時圈速競速狀態

> 玩家進入 Circuit Mode 後的即時狀態，每次更新覆蓋同一 `session_id` 的紀錄。  
> Android App 僅在 **Circuit Mode** 才會上傳遙測，Mountain Mode 不會寫入此表。

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `session_id` | 場次 ID（主鍵） | `sess_1788589352321_pu4kf2` |
| `circuit_id` | 賽道場地 ID | `2` |
| `user_id` | 用戶 ID | `19` |
| `state` | 當前狀態 `armed/racing/stopped` | `armed` |
| `lap_number` | 當前圈數 | `0` |
| `lap_start_at_ms` | 本圈開始時刻（Unix 毫秒） | `1788589352321` |
| `last_lap_time_ms` | 上一圈完成時間（毫秒） | `18429` |
| `personal_best_ms` | 個人最佳圈速（毫秒） | `16003` |
| `gps_accuracy` | 當前 GPS 精確度（公尺） | `36.7` |
| `lat` / `lng` | 當前 GPS 位置 | `24.3188053, 120.6854972` |
| `vehicle_type` | 車種 | `car` |
| `updated_at` | 最後更新時間 | `2026-09-05 14:06:09` |

> **狀態說明：**
> - `armed`：已進入賽道區域，等待穿越起終點線
> - `racing`：計時進行中
> - `stopped`：已離開賽道或主動停止

### `active_sessions` — 玩家山路/賽道即時動態與進度條 (Heartbeat)

> 用於管理者後台「即時儀表版」監控。APP 競速中每 3 秒自動發送 Heartbeat 寫入，離線超過 2 分鐘或比賽完成會自動清理。

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `id` | 唯一 Session 紀錄 ID | `1` |
| `user_id` | 用戶 ID | `19` |
| `user_name` | 用戶顯示暱稱 / 帳號 | `極速車手` |
| `track_id` | 賽道 / 路線 ID | `101` |
| `track_name` | 賽道顯示名稱 | `136線道 (太平-國姓)` |
| `start_time` | 開始競速時間 | `2026-09-14 10:14:00` |
| `last_heartbeat` | 最後心跳時間 (超過 120s 清除) | `2026-09-14 10:15:10` |
| `progress_pct` | 路線完跑進度百分比 (%) | `68.5` |
| `current_speed` | 當前即時時速 (km/h) | `85.2` |
| `vehicle_type` | 車種 `CAR/MOTOR` | `CAR` |
| `status` | 比賽狀態 `RACING/FINISHED` | `RACING` |

---

### `error_logs` — 全站 API 與系統錯誤日誌表

> 存放後台與前台 API 拋出之 `Throwable` 例外、SQL 錯誤、驗證失敗 (400) 及 Auth 失敗 (401) 紀錄。

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `id` | 日誌唯一 ID | `102` |
| `context` | API 模組或請求路徑 | `admin/records` / `validateInput` |
| `error_type` | 錯誤分類/例外名稱 | `PDOException` / `ValidationError` |
| `message` | 完整錯誤訊息本文 | `找不到該單圈的 Telemetry Log 數據` |
| `file` | 發生錯誤的檔案路徑 | `api/admin/records.php` |
| `line` | 發生錯誤的程式碼行號 | `54` |
| `sql_state` | SQLSTATE 錯誤碼 (PDO 專用) | `42S02` |
| `trace` | JSON 格式的 Context Stack Trace | `["records.php(54): sendResponse(...)"]` |
| `created_at` | 發生時間 | `2026-09-14 10:11:52` |

---

## 五、即時競速狀態

### `live_status` — 山路即時競速狀態

> 山路計時比賽的即時狀態，用於顯示其他玩家的位置和成績。

| 欄位 | 說明 |
|------|------|
| `user_id` | 用戶 ID |
| `track_id` | 賽道 ID |
| `state` | 狀態 `armed/racing/stopped` |
| `start_at_ms` | 計時開始毫秒時刻 |
| `elapsed_ms` | 已經過毫秒數（供客戶端插值計算） |
| `gps_accuracy` | GPS 精確度 |
| `lat` / `lng` | 當前位置 |
| `vehicle_type` | 車種 |
| `updated_at` | 最後更新 |

---

### `player_sessions` — 玩家場次紀錄

> 記錄每次進入賽道的場次，用於歷史回顧和防刷榜稽核。

| 欄位 | 說明 |
|------|------|
| `session_id` | 唯一場次 ID |
| `user_id` | 用戶 ID |
| `track_id` | 賽道 ID |
| `vehicle_type` | 車種 |
| `start_at` | 開始時間 |
| `end_at` | 結束時間 |
| `result_ms` | 最終成績（毫秒，NULL 表示未完成） |

---

## 六、排行榜系統

### `rank_snapshots` — 排行榜快照

> 定時生成的排行榜快照，避免每次即時計算大量成績。  
> 按 `(track_id, vehicle_type, snapshot_date)` 索引查詢。

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `track_id` | 賽道 ID | `1` |
| `vehicle_type` | 車種（`car/motor/all`） | `car` |
| `user_id` | 用戶 ID | `19` |
| `rank_position` | 排名（第幾名） | `2` |
| `snapshot_date` | 快照日期 | `2026-08-18` |
| `updated_at` | 快照寫入時間 | `2026-08-05 21:58:59` |

> **`vehicle_type = 'all'`** 表示汽車+機車合算的綜合排行榜。

---

### `vip_leaderboard` — VIP 專屬排行榜

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `track_id` | 賽道 ID | `27` |
| `user_id` | 用戶 ID | `19` |
| `nickname` | 顯示暱稱（快取） | `null` |
| `time_ms` | 成績（毫秒） | `18012` |

---

## 七、VIP 與訂閱系統

### `vip_subscriptions` — VIP 訂閱明細

> 每筆 VIP 訂閱的完整資訊，對應藍新金流付款紀錄。

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `user_id` | 用戶 ID | `19` |
| `order_no` | 訂單編號 | `REVON177493629419` |
| `plan` | 訂閱方案 | `monthly` |
| `start_date` | 訂閱開始時間 | `2026-03-31 15:10:00` |
| `expire_date` | 訂閱到期時間 | `2026-04-30 23:59:59` |
| `status` | 訂閱狀態 `active/expired/cancelled` | `active` |
| `auto_renew` | 是否自動續訂 | `1` |

---

### `subscriptions` — 訂閱概況（簡化版）

| 欄位 | 說明 |
|------|------|
| `user_id` | 用戶 ID |
| `plan_name` | 方案名稱 |
| `start_date` / `end_date` | 開始/結束日期 |
| `status` | `active/expired/cancelled` |

---

## 八、點數與優惠系統

### `points_logs` — 點數變動日誌

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `user_id` | 用戶 ID | `19` |
| `change_amount` | 點數變動量（正=增加，負=扣除） | `+500` |
| `reason` | 變動原因 | `兌換優惠券 VIP500` |
| `created_at` | 時間 | `2026-04-10 11:03:22` |

---

### `coupons` — 優惠券定義

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `code` | 優惠碼（不分大小寫） | `VIP500` |
| `points` | 兌換點數 | `500` |
| `vip_days` | 贈送 VIP 天數 | `30` |

> **範例優惠碼：**
> - `VIP500` → 500 點 + 30 天 VIP
> - `FREEMONTH` → 0 點 + 30 天 VIP
> - `revon60` → 60 天 VIP（已大量發送給早期用戶）

---

### `coupon_usage` — 優惠碼使用紀錄

| 欄位 | 說明 |
|------|------|
| `user_id` | 使用者 ID |
| `coupon_code` | 使用的優惠碼 |
| `created_at` | 使用時間 |

---

## 九、公告與社群系統

### `announcements` — 系統公告

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `title` | 公告標題 | `🏁 REV-ON 系統更新公告` |
| `content` | 公告內容（支援換行） | `🔥 全新功能搶先公開...` |
| `is_active` | 是否啟用顯示（1=啟用） | `1` |
| `created_by` | 發布者用戶 ID | `2112` |

---

### `banners` — APP 首頁橫幅廣告

| 欄位 | 說明 |
|------|------|
| `img_path` | 圖片路徑 |
| `created_at` | 建立時間 |

---

### `infos` — 資訊文章/新聞

| 欄位 | 說明 |
|------|------|
| `title` | 標題 |
| `content` | 內容 |
| `img` | 封面圖片 |
| `is_active` | 是否顯示 |
| `created_by` | 建立者 ID |

---

### `posts` — 社群貼文

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `title` | 貼文標題 | `revon-01` |
| `content` | 內容 | `一群熱愛跑山...` |
| `main_img` | 封面圖 | `uploads/cover_...jpg` |
| `img` | 附圖 | `uploads/p_...jpg` |
| `user_id` | 發文者 ID | `19` |

---

### `post_comments` — 貼文留言

| 欄位 | 說明 |
|------|------|
| `post_id` | 對應貼文 ID |
| `user_id` | 留言者 ID |
| `content` | 留言內容 |

---

### `post_likes` — 貼文按讚

| 欄位 | 說明 |
|------|------|
| `post_id` | 對應貼文 ID |
| `user_id` | 按讚者 ID |

---

## 十、電商系統

### `products` — 商品

| 欄位 | 說明 |
|------|------|
| `name` | 商品名稱 |
| `price` | 售價（TWD） |
| `description` | 商品描述 |
| `img` ~ `img4` | 商品圖片（最多 4 張） |
| `stock` | 庫存數量 |

---

### `orders` — 訂單

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `user_id` | 購買者 ID | `19` |
| `order_no` | 訂單編號 | `REVON202604292013032026` |
| `total_amount` | 總金額（TWD） | `190` |
| `status` | 狀態 `pending/paid/cancelled/refunded` | `paid` |
| `payment_method` | 付款方式 | `credit_card` |

---

### `order_items` — 訂單明細

| 欄位 | 說明 |
|------|------|
| `order_id` | 對應訂單 ID |
| `product_id` | 商品 ID |
| `quantity` | 數量 |
| `unit_price` | 單價 |

---

### `cart` — 購物車

| 欄位 | 說明 |
|------|------|
| `user_id` | 用戶 ID |
| `product_id` | 商品 ID |
| `qty` | 數量 |
| `created_at` | 加入時間 |

---

### `payment_callbacks` — 金流回呼紀錄

> 藍新金流通知到達後的原始參數儲存，用於對帳和重跑補單。

---

## 十一、管理後台系統

### `admin_users` — 管理員帳號

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `user_id` | 對應 `users.id` | `19` |
| `username` | 管理員帳號 | `Revon` |
| `role` | 角色（`admin/super_admin`） | `admin` |
| `is_active` | 是否啟用 | `1` |

---

### `admin_logs` — 管理員操作日誌

> 所有後台操作的稽核紀錄，上架後必須保留。

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `admin_id` | 管理員 ID | `19` |
| `action` | 操作類型 | `add_info / del_info / add_banner / del_banner` |
| `target_type` | 操作對象類型 `info/banner/post` | `banner` |
| `target_id` | 對象 ID | `3` |
| `details` | 操作詳情 | `新增橫幅廣告：夏季活動` |
| `ip_address` | 操作者 IP | `61.230.xxx.xxx` |

---

## 十二、AI 巡邏反作弊系統

### `ai_police_alerts` — 異常成績警報

> 系統定期掃描所有成績，若某用戶在某賽道的成績明顯快於平均值，自動產生警報。

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `type` | 警報類型 `score_anomaly/low_activity_map` | `score_anomaly` |
| `track_id` | 賽道 ID | `1` |
| `user_id` | 疑似作弊的用戶 ID | `19` |
| `vehicle_type` | 車種 | `car` |
| `detail` | 詳細描述 | `賽道平均 423798ms，此成績 231012ms，快了約 45%（樣本數 5）` |
| `status` | 處理狀態 `pending/emailed/ignored/resolved` | `emailed` |

> **警報類型說明：**
> - `score_anomaly`：成績異常快，可能使用 GPS 模擬或飛人模式
> - `low_activity_map`：某賽道近 30 天遊玩次數極少，考慮下架

---

### `ai_police_scan_log` — AI 掃描日誌

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `scan_date` | 掃描日期 | `2026-09-04` |
| `run_at` | 執行時間 | `2026-09-04 15:09:55` |
| `alerts_found` | 本次發現警報數 | `35` |

---

## 十三、噪音大賽系統

### `noise_contest_sessions` — 噪音大賽場次

| 欄位 | 說明 |
|------|------|
| `session_id` | 唯一場次 ID |
| `user_id` | 用戶 ID |
| `vehicle_type` | 車種 |
| `status` | `active/completed/cancelled` |

---

### `noise_contest_attempts` — 噪音測量嘗試

| 欄位 | 說明 |
|------|------|
| `session_id` | 場次 ID |
| `attempt_no` | 第幾次嘗試 |
| `db_value` | 分貝值 |
| `recorded_at` | 紀錄時間 |

---

### `noise_contest_results` — 噪音大賽最終成績

| 欄位 | 說明 |
|------|------|
| `user_id` | 用戶 ID |
| `max_db` | 最高分貝值 |
| `vehicle_type` | 車種 |
| `created_at` | 完成時間 |

---

### `noise_contest_audit_log` — 噪音大賽稽核日誌

> 防止作弊與異常分貝值的稽核紀錄。

---

### `decibel_tests` — 分貝測試

> 獨立的分貝測試（不在大賽體系內），供單純錄音測試用。

---

## 十四、其他輔助資料表

### `countries` — 國家清單

| 欄位 | 說明 |
|------|------|
| `alpha2` | ISO 2 碼（如 `TW`） |
| `alpha3` | ISO 3 碼（如 `TWN`） |
| `name_zh` | 中文名稱 |
| `name_en` | 英文名稱 |
| `is_active` | 是否啟用 |

> 目前已收錄：台灣、美國、日本、韓國、新加坡、英國、德國、法國、加拿大、澳洲、印度、巴西。

---

### `bookings` — 活動預約

> 線下活動（如夜跑、鑑賞會）的預約表單，非 APP 核心功能。

---

### `push_subscribers` — 推播訂閱者

| 欄位 | 說明 |
|------|------|
| `endpoint` | Web Push 端點 URL |
| `p256dh` | ECDH 公鑰 |
| `auth` | 認證密鑰 |

---

### `rooms` — 虛擬競賽房間

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| `name` | 房間名稱 | `新手競速賽` |
| `prize` | 獎金（點數） | `5000` |
| `max_players` | 最大玩家數 | `10` |
| `entry_fee` | 報名費（點數） | `100` |
| `status` | 房間狀態（1=開放） | `1` |

---

### `lottery_entries` — 抽獎活動紀錄

---

### `platforms` — 應用平台版本控制

| 欄位 | 說明 |
|------|------|
| `platform` | `android/ios` |
| `version` | 版本號 |
| `force_update` | 是否強制更新 |

---

## PHP API 整合設計指引

> 本專案後端以 **PHP + MariaDB 10（Synology NAS）** 為核心，Android App 透過 HTTP(S) 呼叫 PHP API，絕對禁止 App 直連資料庫。

---

### API 通訊規範

#### 基本原則
- 所有 API 回應統一使用 **JSON** 格式，禁止回傳 HTML 錯誤頁面（會造成 App Kotlin 解析崩潰）
- 統一回傳結構：
```json
// 成功
{ "status": "success", "data": { ... } }

// 失敗
{ "status": "error", "message": "說明文字", "code": 4001 }
```
- HTTP 狀態碼對應：
  - `200` — 成功
  - `400` — 請求格式錯誤（缺少必填欄位）
  - `401` — 未認證（Token 無效或過期）
  - `403` — 無權限（已認證但不能執行此操作）
  - `429` — 請求過於頻繁（Rate Limit）
  - `500` — 伺服器內部錯誤（PHP 例外，記得 catch）

#### 身份驗證機制（Token-Based Auth）
- **不使用 PHP Session / Cookie**，App 環境不適合 Session 機制
- 登入後由 PHP 生成一組 `remember_token`（64 位隨機字串），存入 `users.remember_token`
- App 後續所有請求需在 Header 帶上：
```
Authorization: Bearer <remember_token>
```
- PHP 端驗證邏輯（每支 API 最前面加）：
```php
$token = str_replace('Bearer ', '', $_SERVER['HTTP_AUTHORIZATION'] ?? '');
$user = getUserByToken($pdo, $token); // SELECT * FROM users WHERE remember_token = ?
if (!$user) { respond(401, 'error', '未授權，請重新登入'); exit; }
```

---

### 安全性強化（PHP API 層）

#### 1. Prepared Statements — 防 SQL 注入（最優先！）
所有含用戶輸入的 SQL 查詢必須使用 PDO Prepared Statements，絕對禁止字串拼接 SQL：

```php
// ❌ 危險：字串拼接
$sql = "SELECT * FROM users WHERE account = '" . $_POST['account'] . "'";

// ✅ 正確：Prepared Statement
$stmt = $pdo->prepare("SELECT * FROM users WHERE account = ?");
$stmt->execute([$_POST['account']]);
```

#### 2. Rate Limiting — 防刷 API / 暴力破解
在登入、成績上傳等敏感端點加入頻率限制，防止惡意刷榜或帳號暴力破解：

```php
// 建議在 MariaDB 建立 api_rate_limit 表
CREATE TABLE `api_rate_limit` (
  `ip`         VARCHAR(45)  NOT NULL,
  `endpoint`   VARCHAR(100) NOT NULL,
  `count`      INT          NOT NULL DEFAULT 1,
  `window_start` DATETIME   NOT NULL,
  PRIMARY KEY (`ip`, `endpoint`)
) ENGINE=InnoDB;

// PHP 範例：登入端點每 IP 每分鐘最多 10 次
function checkRateLimit(PDO $pdo, string $ip, string $endpoint, int $maxPerMinute = 10): bool {
    $now = date('Y-m-d H:i:s');
    $windowStart = date('Y-m-d H:i:00'); // 以分鐘為窗口
    $stmt = $pdo->prepare("INSERT INTO api_rate_limit (ip, endpoint, count, window_start)
        VALUES (?, ?, 1, ?)
        ON DUPLICATE KEY UPDATE
          count = IF(window_start = ?, count + 1, 1),
          window_start = IF(window_start = ?, window_start, ?)");
    $stmt->execute([$ip, $endpoint, $windowStart, $windowStart, $windowStart, $windowStart]);
    $stmt = $pdo->prepare("SELECT count FROM api_rate_limit WHERE ip=? AND endpoint=?");
    $stmt->execute([$ip, $endpoint]);
    $row = $stmt->fetch();
    return $row['count'] <= $maxPerMinute;
}
```

> **建議限制：**
> - `/api/login.php` → 每 IP 每分鐘最多 **5 次**
> - `/api/upload_result.php` → 每用戶每分鐘最多 **20 次**
> - `/api/submit_custom_track.php` → 每用戶每小時最多 **3 次**

#### 3. 輸入驗證 — 防注入與資料污染
```php
// 驗證整數
$track_id = filter_input(INPUT_POST, 'track_id', FILTER_VALIDATE_INT);
if ($track_id === false || $track_id <= 0) respond(400, 'error', 'track_id 格式錯誤');

// 驗證毫秒成績合理範圍（不能是負數或超過 24 小時）
$time_ms = (int)$_POST['time_ms'];
if ($time_ms <= 0 || $time_ms > 86400000) respond(400, 'error', '成績數值不合理');

// 驗證 vehicle_type 白名單
$allowed_vehicles = ['car', 'motor', 'other'];
if (!in_array($_POST['vehicle_type'], $allowed_vehicles)) respond(400, 'error', '車種無效');
```

#### 4. HTTPS 強制（上架前必須完成）
- Android 9.0 (API 28) 以上預設**封鎖明文 HTTP 請求**
- Synology NAS 必須設定 **Let's Encrypt SSL 憑證**（免費），並將 `http://` 強制轉址到 `https://`
- 確認 `AndroidManifest.xml` 中的 API base URL 為 `https://revon88.synology.me/...`

#### 5. 敏感資料保護
- **密碼**：使用 `password_hash($pw, PASSWORD_BCRYPT)` / `password_verify()` — **已正確實作**
- **手機 / Email / 生日**：`users` 表中含個資，若 NAS 遭入侵可直接外洩。建議：
  - 資料庫層：對 `phone`、`email`、`birthday` 使用 **AES-256 加密**儲存
  - 或最低限度：`phone` 在 API 回傳時遮蔽（如 `09xx-xxx-965`）
- **API 錯誤訊息**：生產環境禁止回傳 PHP 錯誤堆疊（`display_errors = Off`）

---

### 效能優化（多人同時使用情境）

#### 關鍵索引建議

以下資料表隨用戶增加會快速膨脹，**必須建立複合索引**：

```sql
-- track_results：排行榜查詢核心索引
ALTER TABLE `track_results`
  ADD INDEX `idx_leaderboard` (`track_id`, `vehicle_type`, `time_ms`),
  ADD INDEX `idx_user_history` (`user_id`, `created_at`);

-- track_log：月賽查詢
ALTER TABLE `track_log`
  ADD INDEX `idx_season` (`track_id`, `vehicle_type`, `season_id`, `time_ms`);

-- circuit_laps：賽道圈速查詢
ALTER TABLE `circuit_laps`
  ADD INDEX `idx_circuit_leaderboard` (`circuit_id`, `vehicle_type`, `lap_time_ms`),
  ADD INDEX `idx_session` (`session_id`);

-- circuit_live_status：即時狀態查詢（已是 PRIMARY KEY session_id，但建議加用戶索引）
ALTER TABLE `circuit_live_status`
  ADD INDEX `idx_user_active` (`user_id`, `updated_at`);

-- live_status：山路即時
ALTER TABLE `live_status`
  ADD INDEX `idx_track_active` (`track_id`, `updated_at`);

-- ai_police_alerts：管理員巡邏查詢
ALTER TABLE `ai_police_alerts`
  ADD INDEX `idx_status` (`status`, `created_at`);

-- users：登入查詢
ALTER TABLE `users`
  ADD UNIQUE INDEX `idx_account` (`account`),
  ADD INDEX `idx_token` (`remember_token`(16)); -- 只索引前 16 字元即可
```

#### 排行榜快取策略

```
[即時查詢方式（用戶少時）]
App 查詢 → PHP → SELECT + ORDER BY time_ms LIMIT 100 → 回傳

[快照方式（用戶多時，建議採用）]
Cron Job（每小時）→ 計算 rank_snapshots → 存入快照表
App 查詢 → PHP → 直接 SELECT rank_snapshots（極快）
```

- **`rank_snapshots`** 表已存在，應建立 PHP Cron Job（Synology 任務排程）定期更新：
```bash
# Synology 控制台 → 任務排程 → 新增腳本
# 每小時執行：
/usr/bin/php /volume1/web/revon_android/cron/update_rank_snapshots.php
```

#### 即時狀態表清理（防資料表無限膨脹）
```sql
-- 由 Cron Job 每天凌晨 3:00 執行
DELETE FROM `circuit_live_status` WHERE state = 'stopped' AND updated_at < NOW() - INTERVAL 7 DAY;
DELETE FROM `live_status` WHERE state = 'stopped' AND updated_at < NOW() - INTERVAL 1 DAY;
```

#### 資料庫連線設定（Synology MariaDB 10）
```php
// db_config.php — 統一使用此設定
define('DB_HOST', '127.0.0.1');
define('DB_PORT', 3307);        // Synology MariaDB 10 預設 Port
define('DB_NAME', 'revon_android');
define('DB_USER', 'yelux_user');
define('DB_PASS', 'Yelux_2026!App');
define('DB_CHARSET', 'utf8mb4');

function getDB(): PDO {
    static $pdo = null;
    if ($pdo === null) {
        $dsn = "mysql:host=" . DB_HOST . ";port=" . DB_PORT
             . ";dbname=" . DB_NAME . ";charset=" . DB_CHARSET;
        $pdo = new PDO($dsn, DB_USER, DB_PASS, [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false, // 使用原生 Prepared Statements
            PDO::ATTR_TIMEOUT            => 5,     // 連線逾時 5 秒
        ]);
    }
    return $pdo;
}
```

---

### 多人使用擴充規劃

| 用戶規模 | 現有 NAS 架構是否足夠 | 建議行動 |
|:--------:|:--------------------:|:---------|
| < 500 人 | ✅ 完全足夠 | 正常運作，加好索引即可 |
| 500 ~ 2000 人 | ⚠️ 需要調整 | 開啟 MariaDB Query Cache、定期清理即時表、啟用 rank_snapshots |
| 2000 ~ 10000 人 | ⚠️ NAS 壓力大 | 考慮將 PHP 與 DB 遷移到 VPS（DigitalOcean / AWS Lightsail） |
| > 10000 人 | ❌ NAS 無法承受 | 微服務架構 + 雲端 DB（RDS） + CDN + Load Balancer |

> **好消息**：PHP 程式碼與 MariaDB Schema 在遷移到 VPS 時完全不需要改寫，只需更換 `DB_HOST` / `DB_PORT` 即可。

---

## Android App JSON 格式與 SQL 欄位映射

針對 Android App (`io.revon.app`) 介面需求，PHP API 與資料庫回傳映射如下：

### 1. 賽道模型 (`Track.kt`) 映射表
- `code` (字串唯一識別碼) ↔ 對應 `tracks.id` (轉為 `track_1` 格式)
- `name_zh` (中文名稱) ↔ 對應 `tracks.name`
- `category` (`TOUGE` / `CIRCUIT`) ↔ 區分 `tracks` (山路) 或 `circuits` (場地)
- `difficulty` (`NORMAL` / `HARD` / `EXTREME`) ↔ 對應 `tracks.difficulty`
- `distance_km` (長度 km) ↔ 對應 `tracks.length_meters / 1000`
- `corners_count` (彎道數) ↔ 對應 `tracks.corners_count` (預設 12)
- `cover_image` (圖片) ↔ 對應 `tracks.img`

### 2. 排行榜模型 (`LeaderboardItem.kt`) 映射表
- `rank` (名次) ↔ API 計算序號 (1, 2, 3...)
- `playerNickname` (玩家暱稱) ↔ 對應 `users.nickname`
- `finishTimeMs` (毫秒) ↔ 對應 `track_results.time_ms`
- `timeDisplay` (格式化時間) ↔ API 計算 `mm:ss.sss` (如 `"02:05.400"`)

---

## 📌 未來 API 開發計畫 (待建置清單)

以下為 Android App 功能擴充所需之後續 API 預計開發項目：

1. **用戶個人資料 (`api/auth/me.php`)**
   - GET 取得玩家個人檔案、VIP 到期日與 `free_plays` 剩餘遊玩次數。
2. **車隊/俱樂部系統 (`api/clubs/`)**
   - `list.php`: 取得車隊列表。
   - `detail.php`: 取得車隊詳情與成員名單。
   - `chat.php`: 車隊即時聊天訊息。
3. **即時位置遙測 (`api/race/live.php`)**
   - POST 定期上傳 GPS 座標與 state (`armed/racing/stopped`)，供同路線車友在地圖上即時顯示。

---

## ER 關聯概覽

```
users (id)
 |-- user_logs (user_id)
 |-- user_reports (user_id)
 |-- vip_subscriptions (user_id)
 |-- points_logs (user_id)
 |-- coupon_usage (user_id)
 |-- posts (user_id)
 |-- post_comments (user_id)
 |-- post_likes (user_id)
 |
 |-- track_results (user_id) ---- tracks (id)
 |-- track_log (user_id)     ---- tracks (id)
 |-- live_status (user_id)   ---- tracks (id)
 |-- rank_snapshots (user_id)---- tracks (id)
 |
 |-- circuit_laps (user_id)       ---- circuits (id)
 |-- circuit_live_status (user_id)---- circuits (id)

custom_tracks (creator_id) ---- users (id)

admin_users (user_id) ---- users (id)
admin_logs  (admin_id)---- admin_users (user_id)

ai_police_alerts (user_id) ---- users (id)
ai_police_alerts (track_id)---- tracks (id)

orders (user_id)       ---- users (id)
order_items (order_id) ---- orders (id)
order_items (product_id)--- products (id)
```

---

## 建議指引（上架前）

### ✅ PHP API 上架前逐項確認清單

| 項目 | 說明 | 完成？ |
|:-----|:-----|:------:|
| **HTTPS 憑證** | Synology Let's Encrypt 已啟用，API URL 使用 `https://` | ☐ |
| **Prepared Statements** | 所有含用戶輸入的 SQL 已改為 PDO Prepared Statements | ☐ |
| **Token 驗證** | 所有需登入的 API 已驗證 `Authorization: Bearer` Header | ☐ |
| **Rate Limiting** | 登入、成績上傳端點已加入請求頻率限制 | ☐ |
| **輸入驗證** | 所有 POST 參數已通過白名單或範圍驗證 | ☐ |
| **錯誤回應** | 所有 API 在錯誤時回傳 JSON，而非 PHP HTML 錯誤頁面 | ☐ |
| **display_errors** | 伺服器 `php.ini` 中 `display_errors = Off` | ☐ |
| **資料庫索引** | 關鍵查詢索引已依上方 SQL 建立完成 | ☐ |
| **Cron Job** | rank_snapshots 與即時表清理排程已在 Synology 設定 | ☐ |
| **個資遮蔽** | `phone` 在 API 回傳時已遮蔽中段字元 | ☐ |
| **隱私政策** | App Store 頁面已聲明收集真實姓名、電話、Email、生日 | ☐ |
| **VIP 同步** | `users.vip_expire` 與 `vip_subscriptions.expire_date` 同步更新邏輯已完成 | ☐ |
| **free_plays 扣除** | 新玩家 5 次免費次數在 API 層正確扣除，且有防刷保護 | ☐ |
| **反作弊 Cron** | `ai_police_alerts` 掃描腳本已設定每日執行 | ☐ |

---

### 安全性

1. **`users.password`** 使用 bcrypt (`$2y$10$`)，請勿改用 MD5 或 SHA1。
2. **`users.reset_token`** 應設定有效期（`token_expire`），超時自動失效，且密碼重設後立即清除 Token。
3. **`admin_logs`** 應定期備份至獨立位置，並對管理後台 IP 設定存取白名單。
4. 建議對 `users.email`、`users.phone`、`users.birthday` 使用 AES-256 加密存儲，符合個資法（PDPA）要求。
5. **`api_rate_limit`**（建議新增的輔助表）：為所有敏感端點提供 IP 層的頻率限制防護，詳見「PHP API 整合設計指引」章節的實作範例。

### 效能

1. **`track_results`** 表隨玩家增加會快速膨脹，建議：
   - 對 `(track_id, vehicle_type, time_ms)` 建立複合索引（詳見 PHP API 章節中的完整索引 SQL）
   - 使用 `track_log` 作為完整流水帳，`track_results` 只保留最佳成績（UPSERT 模式）
2. **`rank_snapshots`** 快照機制已正確，應由 Synology 任務排程每小時更新一次。
3. **`circuit_live_status`** 為高頻寫入表，Cron Job 每天清除 7 天前 `stopped` 狀態的舊資料。
4. **`live_status`** 山路即時表，Cron Job 每天清除 24 小時前 `stopped` 狀態的舊資料。

### App Store 上架注意

1. **`free_plays`**：新玩家預設 5 次免費遊玩，API 端必須在每次計時完成後原子性扣除（使用 `UPDATE users SET free_plays = free_plays - 1 WHERE id = ? AND free_plays > 0`），防止並發條件下多扣。
2. **VIP 機制**：`users.vip_expire` 需與 `vip_subscriptions.expire_date` 保持同步，藍新金流回呼後同步更新兩個欄位。
3. **`custom_tracks.status`**：`pending` 狀態的路線不應在 APP 中顯示；`rejected` 路線應透過 Email 通知創建者（可串接 Synology SMTP 或 SendGrid）。
4. **反作弊**：`ai_police_alerts` 掃描需定期執行（每日），`emailed` 代表已通知管理員，`resolved` 代表已處理（刪除刷榜成績或停用帳號）。
5. **隱私政策**：`users` 表存有真實姓名、電話、生日、Email，App Store 審核需在隱私政策中明確聲明，並提供刪除帳號的管道。

### 資料歸檔建議

| 資料表 | 建議保留期限 | 備註 |
|--------|------------|------|
| `track_log` | 永久 | 成績稽核依據 |
| `track_results` | 永久 | 排行榜依據 |
| `circuit_laps` | 永久 | 圈速排行依據 |
| `circuit_live_status` | 7 天（stopped 可清除） | Cron Job 自動清理 |
| `live_status` | 24 小時（stopped 可清除） | Cron Job 自動清理 |
| `api_rate_limit` | 滾動 1 小時 | 定期 DELETE 舊窗口資料 |
| `admin_logs` | 3 年 | 稽核用途，建議異地備份 |
| `ai_police_scan_log` | 1 年 | 定期壓縮歸檔 |
| `coupon_usage` | 永久 | 防重複兌換依據 |
| `user_logs` | 永久 | 財務稽核，不可刪除 |
| `payment_callbacks` | 永久 | 金流對帳依據 |

---

*本文件根據 `revon.sql`（匯出日期：2026-09-08）整理，部分欄位說明為推測補充。若資料庫結構有更新，請同步修改此文件。*
