<?php
/**
 * 強制更新排名基準快照 API
 * 路徑: /api/admin/snapshots.php
 */
require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();

    $pdo->exec("CREATE TABLE IF NOT EXISTS rank_snapshots (
        id INT AUTO_INCREMENT PRIMARY KEY,
        track_id INT NOT NULL,
        vehicle_type VARCHAR(50) DEFAULT 'car',
        created_by VARCHAR(100) DEFAULT 'admin',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

    if ($_SERVER['REQUEST_METHOD'] === 'GET') {
        $stmt = $pdo->query("SELECT * FROM rank_snapshots ORDER BY id DESC LIMIT 20");
        sendResponse(200, 'success', '取得快照列表成功', $stmt->fetchAll(PDO::FETCH_ASSOC));

    } elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $input = json_decode(file_get_contents('php://input'), true) ?: $_POST;
        $trackId = (int)($input['track_id'] ?? 0);
        $vehicleType = $input['vehicle_type'] ?? 'car';

        if ($trackId <= 0) {
            sendResponse(400, 'error', '請選擇有效的賽道');
        }

        $stmt = $pdo->prepare("INSERT INTO rank_snapshots (track_id, vehicle_type, created_by) VALUES (?, ?, 'admin')");
        $stmt->execute([$trackId, $vehicleType]);

        sendResponse(200, 'success', "已成功人工建立賽道 (ID: {$trackId}) 於車種 {$vehicleType} 的今日排名基準快照！", [
            'snapshot_id' => $pdo->lastInsertId(),
            'timestamp' => date('Y-m-d H:i:s')
        ]);
    }
    sendResponse(400, 'error', '無效的請求');
} catch (Throwable $e) {
    sendError($e, 'admin/snapshots');
}
