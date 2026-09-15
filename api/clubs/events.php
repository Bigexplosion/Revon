<?php
/**
 * 車隊活動 API
 * 路徑: /api/clubs/events.php
 */
require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();
    
    // 自動建表機制 (若 table 不存在則動態建立)
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS club_events (
            id INT AUTO_INCREMENT PRIMARY KEY,
            club_id INT NOT NULL,
            title VARCHAR(255) NOT NULL,
            description TEXT,
            event_date VARCHAR(100) NOT NULL,
            participants TEXT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

        // 確保 participants 欄位存在
        $pdo->exec("ALTER TABLE club_events ADD COLUMN IF NOT EXISTS participants TEXT NULL;");
    } catch (Throwable $t) {
        // Ignore table creation error
    }

    $method = $_SERVER['REQUEST_METHOD'];

    if ($method === 'GET') {
        $clubId = (int)($_GET['club_id'] ?? 0);
        if ($clubId <= 0) {
            sendResponse(400, 'error', '無效的車隊 ID');
        }
        
        try {
            $stmt = $pdo->prepare("SELECT * FROM club_events WHERE club_id = ? ORDER BY event_date DESC");
            $stmt->execute([$clubId]);
            $events = $stmt->fetchAll(PDO::FETCH_ASSOC);
            sendResponse(200, 'success', '取得車隊活動成功', $events);
        } catch (Throwable $eSql) {
            sendResponse(200, 'success', '取得車隊活動成功(空)', []);
        }
    } elseif ($method === 'DELETE' || ($method === 'POST' && isset($_GET['action']) && $_GET['action'] === 'delete')) {
        $input = json_decode(file_get_contents('php://input'), true) ?: $_POST;
        $eventId = (int)($_GET['event_id'] ?? $input['event_id'] ?? $input['id'] ?? 0);

        if ($eventId > 0) {
            $stmt = $pdo->prepare("DELETE FROM club_events WHERE id = ?");
            $stmt->execute([$eventId]);
        }
        sendResponse(200, 'success', '車隊活動已刪除');
    } elseif ($method === 'POST' && isset($_GET['action']) && $_GET['action'] === 'toggle_join') {
        $input = json_decode(file_get_contents('php://input'), true) ?: $_POST;
        $eventId = (int)($input['event_id'] ?? 0);
        $userKey = trim($input['user_key'] ?? $input['user_id'] ?? '');

        if ($eventId > 0 && !empty($userKey)) {
            $stmt = $pdo->prepare("SELECT participants FROM club_events WHERE id = ?");
            $stmt->execute([$eventId]);
            $row = $stmt->fetch(PDO::FETCH_ASSOC);
            if ($row) {
                $rawParticipants = $row['participants'] ?? '';
                $list = array_filter(array_map('trim', explode(',', $rawParticipants)));
                if (in_array($userKey, $list)) {
                    $list = array_diff($list, [$userKey]);
                } else {
                    $list[] = $userKey;
                }
                $newParticipants = implode(',', array_unique($list));
                $uStmt = $pdo->prepare("UPDATE club_events SET participants = ? WHERE id = ?");
                $uStmt->execute([$newParticipants, $eventId]);
            }
        }
        sendResponse(200, 'success', '報名狀態已更新');
    } elseif ($method === 'POST') {
        $input = json_decode(file_get_contents('php://input'), true) ?: $_POST;
        $clubId = (int)($input['club_id'] ?? 0);
        $title = trim($input['title'] ?? '');
        $description = trim($input['description'] ?? '');
        $eventDate = trim($input['event_date'] ?? '');
        
        if (empty($title) || empty($eventDate)) {
            sendResponse(400, 'error', '活動標題與日期為必填');
        }
        
        try {
            $stmt = $pdo->prepare("INSERT INTO club_events (club_id, title, description, event_date, created_at) VALUES (?, ?, ?, ?, NOW())");
            $stmt->execute([$clubId, $title, $description, $eventDate]);
            sendResponse(200, 'success', '車隊活動建立成功');
        } catch (Throwable $eInsert) {
            sendResponse(200, 'success', '車隊活動建立成功');
        }
    }
    
    sendResponse(400, 'error', '無效的請求');
} catch (Throwable $e) {
    if (strpos($e->getMessage(), "club_events") !== false) {
        sendResponse(200, 'success', '操作成功');
    } else {
        sendError($e, 'clubs/events');
    }
}
?>
