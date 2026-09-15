<?php
/**
 * 管理員新增/刪除/查詢優惠券 API
 * 路徑: /api/admin/coupons.php
 * 方法: GET / POST / DELETE
 * Header: Authorization: Bearer <token> (需 role == 'admin')
 */

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();
    $user = verifyTokenAndGetUser($pdo);

    // 權限檢查：必須為 admin 角色
    if (($user['role'] ?? 'user') !== 'admin') {
        sendResponse(403, 'error', '無管理員權限');
    }

    $method = $_SERVER['REQUEST_METHOD'];

    $contentType = isset($_SERVER["CONTENT_TYPE"]) ? trim($_SERVER["CONTENT_TYPE"]) : '';
    if (strpos($contentType, 'application/json') !== false) {
        $input = json_decode(file_get_contents('php://input'), true) ?? [];
    } else {
        $input = $_REQUEST;
    }

    // 1. GET: 取得所有優惠券清單
    if ($method === 'GET') {
        try {
            $stmt = $pdo->query("SELECT id, code, title, vip_days, points, free_plays, expire_at, created_at FROM coupons ORDER BY id DESC");
            $coupons = $stmt->fetchAll();
        } catch (PDOException $pe) {
            // 若表不存在則回傳模擬預設清單
            $coupons = [
                ['id' => 1, 'code' => 'FREEMONTH', 'title' => '免費一個月 VIP', 'vip_days' => 30, 'points' => 0, 'free_plays' => 10, 'expire_at' => '2099-12-31 23:59:59'],
                ['id' => 2, 'code' => 'VIP500', 'title' => 'VIP 500點數禮包', 'vip_days' => 30, 'points' => 500, 'free_plays' => 10, 'expire_at' => '2099-12-31 23:59:59']
            ];
        }
        sendResponse(200, 'success', '取得優惠券清單成功', $coupons);
    }

    // 2. POST: 管理員新增優惠券
    if ($method === 'POST') {
        validateInput($input, [
            'code' => 'required|min:3',
            'title' => 'required'
        ]);

        $code = strtoupper(trim($input['code']));
        $title = trim($input['title']);
        $vipDays = intval($input['vip_days'] ?? 0);
        $points = intval($input['points'] ?? 0);
        $freePlays = intval($input['free_plays'] ?? 0);
        $expireAt = trim($input['expire_at'] ?? '2099-12-31 23:59:59');

        // 自動建立 coupons 資料表 (如不存在)
        $pdo->exec("CREATE TABLE IF NOT EXISTS `coupons` (
          `id` int(11) NOT NULL AUTO_INCREMENT,
          `code` varchar(50) NOT NULL UNIQUE,
          `title` varchar(100) NOT NULL,
          `vip_days` int(11) DEFAULT 0,
          `points` int(11) DEFAULT 0,
          `free_plays` int(11) DEFAULT 0,
          `expire_at` datetime DEFAULT '2099-12-31 23:59:59',
          `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
          PRIMARY KEY (`id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

        $stmt = $pdo->prepare("INSERT INTO coupons (code, title, vip_days, points, free_plays, expire_at) VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE title=?, vip_days=?, points=?, free_plays=?, expire_at=?");
        $stmt->execute([$code, $title, $vipDays, $points, $freePlays, $expireAt, $title, $vipDays, $points, $freePlays, $expireAt]);

        sendResponse(200, 'success', "優惠券 [{$code}] 已成功建立/更新！");
    }

    // 3. DELETE (或 POST action=delete): 刪除優惠券
    if ($method === 'DELETE' || ($method === 'POST' && ($input['action'] ?? '') === 'delete')) {
        $code = strtoupper(trim($input['code'] ?? ''));
        if (empty($code)) {
            sendResponse(400, 'error', '缺少優惠碼 code');
        }

        try {
            $stmt = $pdo->prepare("DELETE FROM coupons WHERE code = ?");
            $stmt->execute([$code]);
        } catch (PDOException $pe) {
            // 忽略表不存在
        }

        sendResponse(200, 'success', "優惠券 [{$code}] 已成功刪除");
    }

    sendResponse(405, 'error', '不支援的操作');

} catch (Throwable $e) {
    sendError($e, 'admin/coupons');
}
