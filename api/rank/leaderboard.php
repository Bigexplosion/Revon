<?php
/**
 * 取得賽道排行榜 API
 * 路徑: /api/rank/leaderboard.php
 * 方法: GET 或 POST
 */

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();

    // 優先讀取 $_GET / $_REQUEST，相容 JSON Body
    $track_id = intval($_GET['track_id'] ?? $_REQUEST['track_id'] ?? 0);
    $is_custom = intval($_GET['is_custom'] ?? $_REQUEST['is_custom'] ?? 0) === 1;
    $vehicle_type = trim($_GET['vehicle_type'] ?? $_REQUEST['vehicle_type'] ?? '');
    $limit = intval($_GET['limit'] ?? $_REQUEST['limit'] ?? 50);

    if ($track_id <= 0) {
        $contentType = isset($_SERVER["CONTENT_TYPE"]) ? trim($_SERVER["CONTENT_TYPE"]) : '';
        if (strpos($contentType, 'application/json') !== false) {
            $input = json_decode(file_get_contents('php://input'), true) ?? [];
            $track_id = intval($input['track_id'] ?? 0);
            $is_custom = intval($input['is_custom'] ?? 0) === 1;
            $vehicle_type = trim($input['vehicle_type'] ?? '');
            $limit = intval($input['limit'] ?? 50);
        }
    }

    if ($track_id <= 0) {
        sendResponse(400, 'error', '缺少必要的賽道 ID');
    }

    if ($limit > 100) $limit = 100;

    $params = [$track_id];
    $vehicle_sql = "";
    if ($vehicle_type !== '') {
        $vehicle_sql = " AND r.vehicle_type = ? ";
        $params[] = $vehicle_type;
    }

    if ($is_custom) {
        // 自訂路線排行 (統一使用 track_results)
        $sql = "SELECT u.account, u.nickname, u.profile_image_url, u.avatar_url, u.avatar, r.time_ms, r.vehicle_type, r.created_at 
                FROM track_results r
                JOIN users u ON r.user_id = u.id
                WHERE r.track_id = ? AND r.is_custom = 1 $vehicle_sql
                ORDER BY r.time_ms ASC
                LIMIT $limit";
    } else {
        // 官方路線排行
        $sql = "SELECT u.account, u.nickname, u.profile_image_url, u.avatar_url, u.avatar, r.time_ms, r.vehicle_type, r.created_at 
                FROM track_results r
                JOIN users u ON r.user_id = u.id
                WHERE r.track_id = ? AND (r.is_custom IS NULL OR r.is_custom = 0) $vehicle_sql
                ORDER BY r.time_ms ASC
                LIMIT $limit";
    }

    $stmt = $pdo->prepare($sql);
    $stmt->execute($params);
    $leaderboard = $stmt->fetchAll();

    $formatted = [];
    $index = 1;
    foreach ($leaderboard as $row) {
        $ms = intval($row['time_ms']);
        $minutes = floor($ms / 60000);
        $seconds = floor(($ms % 60000) / 1000);
        $millis = $ms % 1000;
        $timeDisplay = sprintf('%02d:%02d.%03d', $minutes, $seconds, $millis);

        $avatarUrl = $row['avatar_url'] ?? $row['profile_image_url'] ?? $row['avatar'] ?? null;
        $formatted[] = [
            'rank' => $index++,
            'playerNickname' => $row['nickname'] ?? $row['account'] ?? '熱血車友',
            'account' => $row['account'],
            'nickname' => $row['nickname'],
            'avatar_url' => $avatarUrl,
            'profile_image_url' => $avatarUrl,
            'finishTimeMs' => $ms,
            'time_ms' => $ms,
            'timeDisplay' => $timeDisplay,
            'vehicle_type' => $row['vehicle_type'],
            'created_at' => $row['created_at']
        ];
    }

    sendResponse(200, 'success', '取得排行榜成功', $formatted);

} catch (Throwable $e) {
    sendError($e, 'rank/leaderboard');
}
