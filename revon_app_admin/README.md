# REV-ON Admin Dashboard

這是一個專為 REV-ON App 開發的管理後台，使用純粹的 HTML/JS/CSS 打造，方便直接部署於 Nginx / Apache 伺服器，並直接對接 REV-ON PHP API。

## 檔案結構
- `index.html` - 後台入口頁面與主框架
- `css/style.css` - 後台樣式
- `js/api.js` - API 請求封裝 (使用 Fetch API 呼叫 `../api/...`)
- `js/app.js` - 頁面邏輯與表格渲染
- `views/` - 存放各種管理頁面的 HTML 區塊 (如 `users.html`, `tracks.html`)

## 開發與部署說明
此資料夾可與 `api` 資料夾放置於同一個網頁根目錄中，無需額外架設 Node.js 伺服器，透過瀏覽器直接開啟或經由網址 (如 `https://example.com/revon_app_admin/`) 進入。

## 主要功能
- 會員管理 (查詢、修改)
- 賽道管理 (審核自訂賽道、新增官方賽道)
- 成績管理 (刪除作弊成績)
- 優惠券管理 (新增/刪除兌換碼)
