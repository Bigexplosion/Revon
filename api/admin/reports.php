<?php
/**
 * 客服問題回報 API
 * 路徑: /api/admin/reports.php
 */
require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();

    $pdo->exec("CREATE TABLE IF NOT EXISTS reports (
        id INT AUTO_INCREMENT PRIMARY KEY,
        ticket_no VARCHAR(50) NOT NULL UNIQUE,
        user_account VARCHAR(100) NOT NULL,
        user_email VARCHAR(150),
        category VARCHAR(50) DEFAULT 'general',
        title VARCHAR(255) NOT NULL,
        content TEXT NOT NULL,
        status VARCHAR(20) DEFAULT 'pending', -- pending, processing, resolved, closed
        admin_reply TEXT,
        reply_at DATETIME,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

    if ($_SERVER['REQUEST_METHOD'] === 'GET') {
        $status = $_GET['status'] ?? '';
        $category = $_GET['category'] ?? '';

        $sql = "SELECT * FROM reports WHERE 1=1";
        $params = [];

        if ($status) {
            $sql .= " AND status = ?";
            $params[] = $status;
        }
        if ($category) {
            $sql .= " AND category = ?";
            $params[] = $category;
        }
        $sql .= " ORDER BY id DESC";

        $stmt = $pdo->prepare($sql);
        $stmt->execute($params);
        $reports = $stmt->fetchAll(PDO::FETCH_ASSOC);

        // 統計各狀態數量
        $statsStmt = $pdo->query("SELECT 
            SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) as pending,
            SUM(CASE WHEN status = 'processing' THEN 1 ELSE 0 END) as processing,
            SUM(CASE WHEN status = 'resolved' THEN 1 ELSE 0 END) as resolved,
            SUM(CASE WHEN status = 'closed' THEN 1 ELSE 0 END) as closed
        FROM reports");
        $stats = $statsStmt->fetch(PDO::FETCH_ASSOC);

        sendResponse(200, 'success', '取得回報單列表成功', [
            'list' => $reports,
            'stats' => [
                'pending' => (int)($stats['pending'] ?? 0),
                'processing' => (int)($stats['processing'] ?? 0),
                'resolved' => (int)($stats['resolved'] ?? 0),
                'closed' => (int)($stats['closed'] ?? 0)
            ]
        ]);

    } elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $input = json_decode(file_get_contents('php://input'), true) ?: $_POST;
        $action = $input['action'] ?? 'reply';
        $reportId = (int)($input['report_id'] ?? 0);

        if ($action === 'reply' && $reportId > 0) {
            $reply = trim($input['reply'] ?? '');
            $newStatus = $input['status'] ?? 'resolved';

            $stmt = $pdo->prepare("UPDATE reports SET admin_reply = ?, status = ?, reply_at = NOW() WHERE id = ?");
            $stmt->execute([$reply, $newStatus, $reportId]);
            sendResponse(200, 'success', '工單回覆更新成功');
        }
    }
    sendResponse(400, 'error', '無效的請求');
} catch (Throwable $e) {
    sendError($e, 'admin/reports');
}
