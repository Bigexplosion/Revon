<?php
/**
 * 山路/賽道即時 GPS 廣播遙測 API
 * 路徑: /api/race/live.php
 * 方法: POST
 * Header: Authorization: Bearer <token>
 */

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendResponse(405, 'error', '不支援的請求方法');
}

try {
    $pdo = getDB();
    $user = verifyTokenAndGetUser($pdo);

    $contentType = isset($_SERVER["CONTENT_TYPE"]) ? trim($_SERVER["CONTENT_TYPE"]) : '';
    if (strpos($contentType, 'application/json') !== false) {
        $input = json_decode(file_get_contents('php://input'), true);
    } else {
        $input = $_POST;
    }

    validateInput($input, [
        'lat' => 'required|numeric',
        'lng' => 'required|numeric'
    ]);

    $track_id = intval($input['track_id'] ?? 1);
    $lat = (float)$input['lat'];
    $lng = (float)$input['lng'];
    $state = trim($input['state'] ?? 'racing'); // armed / racing / stopped
    $vehicle_type = strtolower(trim($input['vehicle_type'] ?? 'car'));

    // 更新或寫入 live_status 表
    try {
        $stmt = $pdo->prepare("INSERT INTO live_status (user_id, track_id, state, lat, lng, vehicle_type, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW())
            ON DUPLICATE KEY UPDATE state = ?, lat = ?, lng = ?, updated_at = NOW()");
        $stmt->execute([$user['id'], $track_id, $state, $lat, $lng, $vehicle_type, $state, $lat, $lng]);
    } catch (PDOException $pe) {
        // 表若尚未建立不影響回應
    }

    sendResponse(200, 'success', 'GPS 廣播上傳成功', [
        'user_id' => $user['id'],
        'state' => $state,
        'lat' => $lat,
        'lng' => $lng
    ]);

} catch (Throwable $e) {
    sendError($e, 'race/live');
}
