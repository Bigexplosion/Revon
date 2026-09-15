<?php
/**
 * 取得車隊/俱樂部列表 API (100% SQL 查詢，純動態數據)
 * 路徑: /api/clubs/list.php
 * 方法: GET 或 POST
 */

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();

    // 100% 直連 SQL 數據庫讀取，刪除所有硬編碼 fallback
    $stmt = $pdo->query("SELECT * FROM clubs ORDER BY total_points DESC, id ASC");
    $clubs = $stmt->fetchAll(PDO::FETCH_ASSOC);

    foreach ($clubs as &$c) {
        $c['id'] = (int)$c['id'];
        $c['badge_letters'] = $c['badge_letters'] ?? strtoupper(mb_substr($c['name'] ?? 'RV', 0, 2));
        $c['motto'] = $c['motto'] ?? ($c['description'] ?? 'REV-ON 熱血車隊');
        $c['member_count'] = (int)($c['member_count'] ?? $c['members_count'] ?? 1);
        $c['max_members'] = (int)($c['max_members'] ?? 50);
        $c['total_points'] = (int)($c['total_points'] ?? 0);
        $c['level'] = (int)($c['level'] ?? 1);
        $c['accent_color'] = $c['accent_color'] ?? '#E10600';
    }

    sendResponse(200, 'success', '取得車隊列表成功', $clubs);

} catch (Throwable $e) {
    sendError($e, 'clubs/list');
}
