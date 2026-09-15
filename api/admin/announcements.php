<?php
/**
 * 跑馬燈與內部公告 API
 * 路徑: /api/admin/announcements.php
 */
require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();
    
    // 自動建表（若不存在）
    $pdo->exec("CREATE TABLE IF NOT EXISTS announcements (
        id INT AUTO_INCREMENT PRIMARY KEY,
        type VARCHAR(20) DEFAULT 'marquee', -- marquee 或 internal
        title VARCHAR(255) NOT NULL,
        content TEXT,
        is_active TINYINT DEFAULT 1,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

    if ($_SERVER['REQUEST_METHOD'] === 'GET') {
        $type = $_GET['type'] ?? '';
        if ($type) {
            $stmt = $pdo->prepare("SELECT * FROM announcements WHERE type = ? ORDER BY id DESC");
            $stmt->execute([$type]);
        } else {
            $stmt = $pdo->query("SELECT * FROM announcements ORDER BY id DESC");
        }
        $data = $stmt->fetchAll(PDO::FETCH_ASSOC);
        sendResponse(200, 'success', '取得公告成功', $data);

    } elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $input = json_decode(file_get_contents('php://input'), true) ?: $_POST;
        $action = $input['action'] ?? 'create';

        if ($action === 'create') {
            $type = $input['type'] ?? 'marquee';
            $title = trim($input['title'] ?? '');
            $content = trim($input['content'] ?? '');

            if (empty($title)) {
                sendResponse(400, 'error', '標題不得為空');
            }

            $stmt = $pdo->prepare("INSERT INTO announcements (type, title, content, is_active) VALUES (?, ?, ?, 1)");
            $stmt->execute([$type, $title, $content]);
            sendResponse(200, 'success', '公告建立成功', ['id' => $pdo->lastInsertId()]);

        } elseif ($action === 'toggle') {
            $id = (int)($input['id'] ?? 0);
            $isActive = (int)($input['is_active'] ?? 1);
            $stmt = $pdo->prepare("UPDATE announcements SET is_active = ? WHERE id = ?");
            $stmt->execute([$isActive, $id]);
            sendResponse(200, 'success', '狀態已更新');

        } elseif ($action === 'delete') {
            $id = (int)($input['id'] ?? 0);
            $stmt = $pdo->prepare("DELETE FROM announcements WHERE id = ?");
            $stmt->execute([$id]);
            sendResponse(200, 'success', '公告已刪除');
        }
    }
    sendResponse(400, 'error', '無效的請求');
} catch (Throwable $e) {
    sendError($e, 'admin/announcements');
}
