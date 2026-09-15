<?php
require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

// 取得 HTTP 請求方法
$method = $_SERVER['REQUEST_METHOD'];

try {
    $pdo = getDB();
    if ($method === 'GET') {
        $club_id = $_GET['club_id'] ?? null;
        if (!$club_id) {
            sendResponse(400, "error", "必須提供 club_id");
        }

        // 確認資料表是否存在與字元集設定 (使用 utf8mb4 支援 Emoji)
        $pdo->exec("CREATE TABLE IF NOT EXISTS club_messages (
            id INT AUTO_INCREMENT PRIMARY KEY,
            club_id VARCHAR(255) NOT NULL,
            author_nickname VARCHAR(255) NOT NULL,
            author_uid VARCHAR(255) NOT NULL,
            body TEXT NOT NULL,
            message_type VARCHAR(50) DEFAULT 'text',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

        // 自動升級現有資料表字元集 (解決表情符號 \xF0\x9F 報錯 1366 錯誤)
        try {
            $pdo->exec("ALTER TABLE club_messages CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;");
        } catch (Throwable $tConvert) {}

        // 查詢對應車隊名稱與 ID，兩者皆比對 (同時相容 ID 或 Name 發送的訊息)
        $cStmt = $pdo->prepare("SELECT id, name FROM clubs WHERE id = ? OR name = ? LIMIT 1");
        $cStmt->execute([$club_id, $club_id]);
        $clubRow = $cStmt->fetch(PDO::FETCH_ASSOC);

        if ($clubRow) {
            $stmt = $pdo->prepare("
                SELECT m.*, COALESCE(NULLIF(u.nickname, ''), m.author_nickname) as live_nickname
                FROM club_messages m
                LEFT JOIN users u ON (m.author_uid = CAST(u.id AS CHAR) OR m.author_uid = u.account OR m.author_nickname = u.nickname)
                WHERE m.club_id = ? OR m.club_id = ?
                ORDER BY m.created_at ASC
                LIMIT 100
            ");
            $stmt->execute([(string)$clubRow['id'], (string)$clubRow['name']]);
        } else {
            $stmt = $pdo->prepare("
                SELECT m.*, COALESCE(NULLIF(u.nickname, ''), m.author_nickname) as live_nickname
                FROM club_messages m
                LEFT JOIN users u ON (m.author_uid = CAST(u.id AS CHAR) OR m.author_uid = u.account OR m.author_nickname = u.nickname)
                WHERE m.club_id = ?
                ORDER BY m.created_at ASC
                LIMIT 100
            ");
            $stmt->execute([(string)$club_id]);
        }
        $messages = $stmt->fetchAll(PDO::FETCH_ASSOC);

        // 轉換為 App 的格式
        $formatted = array_map(function($msg) {
            $displayName = !empty($msg['live_nickname']) && $msg['live_nickname'] !== '車手' ? $msg['live_nickname'] : $msg['author_nickname'];
            return [
                'id' => (string)$msg['id'],
                'club_id' => (string)$msg['club_id'],
                'author_nickname' => $displayName,
                'author_uid' => (string)$msg['author_uid'],
                'body' => htmlspecialchars_decode($msg['body'], ENT_QUOTES),
                'timestamp' => strtotime($msg['created_at']) * 1000,
                'message_type' => $msg['message_type']
            ];
        }, $messages);

        sendResponse(200, "success", "取得訊息成功", $formatted);
    } elseif (($method === 'POST' && isset($_GET['action']) && $_GET['action'] === 'delete') || $method === 'DELETE') {
        $json = file_get_contents('php://input');
        $data = json_decode($json, true) ?? [];

        $id = $_GET['id'] ?? $data['id'] ?? null;
        $club_id = $_GET['club_id'] ?? $data['club_id'] ?? null;
        $body = $data['body'] ?? $_GET['body'] ?? null;
        $author_nickname = $data['author_nickname'] ?? $_GET['author_nickname'] ?? null;

        if ($id && is_numeric($id) && (int)$id > 0) {
            $stmt = $pdo->prepare("DELETE FROM club_messages WHERE id = ?");
            $stmt->execute([(int)$id]);
        } elseif (!empty($club_id) && !empty($body)) {
            if (!empty($author_nickname)) {
                $stmt = $pdo->prepare("DELETE FROM club_messages WHERE (club_id = ? OR club_id = ?) AND body = ? AND author_nickname = ? ORDER BY id DESC LIMIT 1");
                $stmt->execute([$club_id, $club_id, $body, $author_nickname]);
            } else {
                $stmt = $pdo->prepare("DELETE FROM club_messages WHERE (club_id = ? OR club_id = ?) AND body = ? ORDER BY id DESC LIMIT 1");
                $stmt->execute([$club_id, $club_id, $body]);
            }
        } elseif (!empty($body)) {
            $stmt = $pdo->prepare("DELETE FROM club_messages WHERE body = ? ORDER BY id DESC LIMIT 1");
            $stmt->execute([$body]);
        } else {
            sendResponse(400, "error", "刪除訊息失敗：必須提供訊息 ID 或內容");
        }

        sendResponse(200, "success", "訊息已成功從資料庫刪除");
    } elseif ($method === 'POST') {
        enforceRateLimit($pdo, 'club_chat', 15);

        $json = file_get_contents('php://input');
        $data = json_decode($json, true);
        
        if (json_last_error() !== JSON_ERROR_NONE) {
            sendResponse(400, "error", "無效的 JSON 格式");
        }

        $club_id = $data['club_id'] ?? null;
        $author_nickname = $data['author_nickname'] ?? '';
        $author_uid = $data['author_uid'] ?? '';
        $rawBody = $data['body'] ?? '';
        $message_type = $data['message_type'] ?? 'text';

        if (empty($club_id) || empty($rawBody)) {
            sendResponse(400, "error", "必須提供 club_id 與 body");
        }

        // 安全淨化處理：防護 HTML / XSS / Script 語法注入（保留所有 Emoji 與標準文字）
        $cleanBody = htmlspecialchars(trim($rawBody), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');

        // 若 author_uid 存在，先從 users 表查詢最新正確暱稱
        if (!empty($author_uid)) {
            $uStmt = $pdo->prepare("SELECT nickname FROM users WHERE id = ? OR account = ? LIMIT 1");
            $uStmt->execute([$author_uid, $author_uid]);
            $userRow = $uStmt->fetch(PDO::FETCH_ASSOC);
            if ($userRow && !empty($userRow['nickname'])) {
                $author_nickname = $userRow['nickname'];
            }
        }

        if (empty($author_nickname) || $author_nickname === '車手') {
            $author_nickname = '賽車手';
        }

        // 確認資料表是否存在與字元集
        $pdo->exec("CREATE TABLE IF NOT EXISTS club_messages (
            id INT AUTO_INCREMENT PRIMARY KEY,
            club_id VARCHAR(255) NOT NULL,
            author_nickname VARCHAR(255) NOT NULL,
            author_uid VARCHAR(255) NOT NULL,
            body TEXT NOT NULL,
            message_type VARCHAR(50) DEFAULT 'text',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

        // 使用 PDO Prepared Statement (帶參數的預編譯語法) 防禦 SQL 注入攻擊，寫入淨化後的訊息
        $stmt = $pdo->prepare("INSERT INTO club_messages (club_id, author_nickname, author_uid, body, message_type) VALUES (?, ?, ?, ?, ?)");
        $stmt->execute([(string)$club_id, $author_nickname, $author_uid, $cleanBody, $message_type]);

        sendResponse(200, "success", "訊息發送成功");
    } else {
        sendResponse(405, "error", "僅支援 GET、POST 與 DELETE 方法");
    }
} catch (Throwable $e) {
    sendError($e, 'clubs/chat');
}
?>
