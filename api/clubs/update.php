<?php
/**
 * 修改車隊設定 API
 * 路徑: /api/clubs/update.php
 * 方法: POST
 */
require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendResponse(405, 'error', '不支援的請求方法');
}

try {
    $pdo = getDB();
    
    $json = file_get_contents('php://input');
    $data = json_decode($json, true) ?: $_POST;
    
    $club_id = (int)($data['club_id'] ?? 0);
    $name = trim($data['name'] ?? '');
    $badge_letters = trim($data['badge_letters'] ?? $data['badge'] ?? '');
    $motto = trim($data['motto'] ?? $data['description'] ?? '');
    $accent_color = trim($data['accent_color'] ?? '#E10600');

    if ($club_id <= 0) {
        sendResponse(400, 'error', '必須提供有效的 club_id');
    }
    if (empty($name)) {
        sendResponse(400, 'error', '車隊名稱不可為空');
    }

    // 更新資料庫
    $stmt = $pdo->prepare("
        UPDATE clubs 
        SET name = ?, badge_letters = ?, motto = ?, accent_color = ? 
        WHERE id = ?
    ");
    $stmt->execute([$name, $badge_letters, $motto, $accent_color, $club_id]);

    // 取得更新後的完整車隊資料
    $stmtSelect = $pdo->prepare("SELECT * FROM clubs WHERE id = ?");
    $stmtSelect->execute([$club_id]);
    $updatedClub = $stmtSelect->fetch(PDO::FETCH_ASSOC);

    if ($updatedClub) {
        $updatedClub['id'] = (int)$updatedClub['id'];
        $updatedClub['badge_letters'] = $updatedClub['badge_letters'] ?? strtoupper(mb_substr($updatedClub['name'] ?? 'RV', 0, 2));
        $updatedClub['motto'] = $updatedClub['motto'] ?? 'REV-ON 熱血車隊';
        $updatedClub['member_count'] = (int)($updatedClub['member_count'] ?? 1);
        $updatedClub['max_members'] = (int)($updatedClub['max_members'] ?? 50);
        $updatedClub['total_points'] = (int)($updatedClub['total_points'] ?? 0);
        $updatedClub['level'] = (int)($updatedClub['level'] ?? 1);
        $updatedClub['accent_color'] = $updatedClub['accent_color'] ?? '#E10600';
    }

    sendResponse(200, 'success', '車隊設定已成功儲存並同步全站', $updatedClub);

} catch (Throwable $e) {
    sendError($e, 'clubs/update');
}
