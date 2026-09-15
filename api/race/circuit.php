<?php
/**
 * 上傳賽道圈速成績 API
 * 路徑: /api/race/circuit.php
 * 方法: POST
 */

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendResponse(405, 'error', '不支援的請求方法');
}

try {
    $pdo = getDB();
    enforceRateLimit($pdo, 'upload_circuit', 10);

    $user = verifyTokenAndGetUser($pdo);

    $contentType = isset($_SERVER["CONTENT_TYPE"]) ? trim($_SERVER["CONTENT_TYPE"]) : '';
    if (strpos($contentType, 'application/json') !== false) {
        $input = json_decode(file_get_contents('php://input'), true);
    } else {
        $input = $_POST;
    }

    $circuit_id = isset($input['circuit_id']) ? (int)$input['circuit_id'] : 0;
    $session_id = trim($input['session_id'] ?? '');
    $laps = $input['laps'] ?? [];
    $vehicle_type = trim($input['vehicle_type'] ?? 'car');

    if ($circuit_id <= 0 || empty($session_id) || empty($laps) || !is_array($laps)) {
        sendResponse(400, 'error', '無效的賽道資料或圈速資訊');
    }

    // 寫入 circuit_laps
    $stmt = $pdo->prepare("INSERT INTO circuit_laps (circuit_id, user_id, session_id, lap_number, lap_time_ms, vehicle_type, created_at) VALUES (?, ?, ?, ?, ?, ?, NOW())");
    
    $pdo->beginTransaction();
    foreach ($laps as $lap) {
        $lap_number = intval($lap['lap_number'] ?? 0);
        $lap_time_ms = intval($lap['lap_time_ms'] ?? 0);
        if ($lap_number > 0 && $lap_time_ms > 0) {
            $stmt->execute([$circuit_id, $user['id'], $session_id, $lap_number, $lap_time_ms, $vehicle_type]);
        }
    }
    $pdo->commit();

    sendResponse(200, 'success', '圈速成績上傳成功');

} catch (Throwable $e) {
    if (isset($pdo) && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    sendError($e, 'race/circuit');
}
