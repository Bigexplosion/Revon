<?php
/**
 * 上傳自訂路線 API
 * 路徑: /api/tracks/custom.php
 * 方法: POST
 */

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendResponse(405, 'error', '不支援的請求方法');
}

try {
    $pdo = getDB();
    enforceRateLimit($pdo, 'custom_track', 10); // 頻率限制

    // 驗證登入 Token 並取得用戶
    $user = verifyTokenAndGetUser($pdo);

    // 解析 JSON
    $contentType = isset($_SERVER["CONTENT_TYPE"]) ? trim($_SERVER["CONTENT_TYPE"]) : '';
    if (strpos($contentType, 'application/json') !== false) {
        $input = json_decode(file_get_contents('php://input'), true);
    } else {
        $input = $_POST;
    }

    $name = trim($input['name'] ?? '');
    $vehicle_type = trim($input['vehicle_type'] ?? 'car');
    
    if (empty($name)) {
        sendResponse(400, 'error', '路線名稱不得為空');
    }

    $start_lat = $input['start_lat'] ?? null;
    $start_lng = $input['start_lng'] ?? null;
    $end_lat = $input['end_lat'] ?? null;
    $end_lng = $input['end_lng'] ?? null;
    
    if ($start_lat === null || $start_lng === null || $end_lat === null || $end_lng === null) {
        sendResponse(400, 'error', '起點與終點座標不完整');
    }

    $stmt = $pdo->prepare("INSERT INTO custom_tracks (
        name, country, city, start_lat, start_lng, end_lat, end_lng, 
        is_hidden, is_vip_only, vehicle_type, 
        mid1_lat, mid1_lng, mid2_lat, mid2_lng, 
        creator_id, creator_account, status, path, is_deleted
    ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, 0)");

    $stmt->execute([
        $name,
        $input['country'] ?? 'Taiwan',
        $input['city'] ?? '',
        $start_lat,
        $start_lng,
        $end_lat,
        $end_lng,
        $vehicle_type,
        $input['mid1_lat'] ?? null,
        $input['mid1_lng'] ?? null,
        $input['mid2_lat'] ?? null,
        $input['mid2_lng'] ?? null,
        $user['id'],
        $user['account'],
        $input['path'] ?? null
    ]);

    sendResponse(200, 'success', '自訂路線已提交，請等待管理員審核', ['track_id' => $pdo->lastInsertId()]);

} catch (Throwable $e) {
    sendError($e, 'tracks/custom');
}
