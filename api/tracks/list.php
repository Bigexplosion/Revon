<?php
/**
 * 取得賽道與路線清單 API
 * 路徑: /api/tracks/list.php
 * 方法: GET 或 POST
 */

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();

    // 取得官方賽道 (tracks)
    $stmt = $pdo->query("SELECT * FROM tracks WHERE is_hidden = 0 ORDER BY id ASC");
    $official_tracks = $stmt->fetchAll();

    foreach ($official_tracks as &$t) {
        $t['id'] = (int)$t['id'];
        $t['code'] = $t['name'];
        $t['name_zh'] = $t['name'];
        $t['category'] = 'TOUGE';
        $t['difficulty'] = strtoupper($t['difficulty'] ?? 'NORMAL');
        $t['distance_km'] = isset($t['distance_km']) ? (float)$t['distance_km'] : (isset($t['length_meters']) ? round($t['length_meters'] / 1000, 2) : 1.5);
        $t['corners_count'] = isset($t['corners_count']) ? (int)$t['corners_count'] : 12;
        $t['cover_image'] = $t['img'] ?? 'track1.jpg';
        $t['region'] = !empty($t['city']) ? $t['city'] : ($t['country'] ?? 'Taiwan');
        $t['subtitle'] = ($t['country'] ?? 'Taiwan') . ' · ' . ($t['city'] ?? '');
        $t['start_lat'] = (float)$t['start_lat'];
        $t['start_lng'] = (float)$t['start_lng'];
        $t['end_lat'] = (float)$t['end_lat'];
        $t['end_lng'] = (float)$t['end_lng'];
        $t['mid1_lat'] = isset($t['mid1_lat']) ? (float)$t['mid1_lat'] : null;
        $t['mid1_lng'] = isset($t['mid1_lng']) ? (float)$t['mid1_lng'] : null;
        $t['mid2_lat'] = isset($t['mid2_lat']) ? (float)$t['mid2_lat'] : null;
        $t['mid2_lng'] = isset($t['mid2_lng']) ? (float)$t['mid2_lng'] : null;
        $t['isCustom'] = false;
    }

    // 取得玩家自訂路線 (custom_tracks)
    $stmtCustom = $pdo->query("SELECT * FROM custom_tracks WHERE status = 'approved' AND is_hidden = 0 ORDER BY id DESC");
    $custom_tracks = $stmtCustom->fetchAll();

    foreach ($custom_tracks as &$ct) {
        $ct['id'] = (int)$ct['id'];
        $ct['code'] = $ct['name'];
        $ct['name_zh'] = $ct['name'];
        $ct['category'] = 'TOUGE';
        $ct['difficulty'] = 'NORMAL';
        $ct['distance_km'] = 2.0;
        $ct['corners_count'] = 10;
        $ct['cover_image'] = 'custom_track.jpg';
        $ct['region'] = !empty($ct['city']) ? $ct['city'] : ($ct['country'] ?? 'Kaohsiung');
        $ct['subtitle'] = ($ct['country'] ?? 'Taiwan') . ' · ' . ($ct['city'] ?? '');
        $ct['start_lat'] = (float)$ct['start_lat'];
        $ct['start_lng'] = (float)$ct['start_lng'];
        $ct['end_lat'] = (float)$ct['end_lat'];
        $ct['end_lng'] = (float)$ct['end_lng'];
        $ct['mid1_lat'] = isset($ct['mid1_lat']) ? (float)$ct['mid1_lat'] : null;
        $ct['mid1_lng'] = isset($ct['mid1_lng']) ? (float)$ct['mid1_lng'] : null;
        $ct['mid2_lat'] = isset($ct['mid2_lat']) ? (float)$ct['mid2_lat'] : null;
        $ct['mid2_lng'] = isset($ct['mid2_lng']) ? (float)$ct['mid2_lng'] : null;
        $ct['isCustom'] = true;
    }

    // 取得封閉賽道 (circuits)
    $stmtCircuit = $pdo->query("SELECT *, line_p1_lat as start_lat, line_p1_lng as start_lng, line_p2_lat as end_lat, line_p2_lng as end_lng FROM circuits WHERE status = 'active' ORDER BY id ASC");
    $circuits = $stmtCircuit->fetchAll();

    foreach ($circuits as &$c) {
        $c['id'] = (int)$c['id'];
        $c['code'] = $c['name'];
        $c['name_zh'] = $c['name'];
        $c['category'] = 'CIRCUIT';
        $c['difficulty'] = 'HARD';
        $c['region'] = !empty($c['city']) ? $c['city'] : ($c['country'] ?? 'Taichung');
        $c['subtitle'] = ($c['country'] ?? 'Taiwan') . ' · ' . ($c['city'] ?? '');
        $c['start_lat'] = !empty($c['line_p1_lat']) ? (float)$c['line_p1_lat'] : (!empty($c['start_lat']) ? (float)$c['start_lat'] : 0.0);
        $c['start_lng'] = !empty($c['line_p1_lng']) ? (float)$c['line_p1_lng'] : (!empty($c['start_lng']) ? (float)$c['start_lng'] : 0.0);
        $c['end_lat'] = !empty($c['line_p2_lat']) ? (float)$c['line_p2_lat'] : (!empty($c['end_lat']) ? (float)$c['end_lat'] : 0.0);
        $c['end_lng'] = !empty($c['line_p2_lng']) ? (float)$c['line_p2_lng'] : (!empty($c['end_lng']) ? (float)$c['end_lng'] : 0.0);
        $c['isCustom'] = false;
    }

    sendResponse(200, 'success', '取得賽道列表成功', [
        'official' => $official_tracks,
        'custom'   => $custom_tracks,
        'circuits' => $circuits
    ]);

} catch (Throwable $e) {
    sendError($e, 'tracks/list');
}
