<?php
/**
 * 優惠券/兌換碼兌換 API
 * 路徑: /api/user/redeem_coupon.php
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
    enforceRateLimit($pdo, 'redeem_coupon', 5);

    $user = verifyTokenAndGetUser($pdo);

    $contentType = isset($_SERVER["CONTENT_TYPE"]) ? trim($_SERVER["CONTENT_TYPE"]) : '';
    if (strpos($contentType, 'application/json') !== false) {
        $input = json_decode(file_get_contents('php://input'), true);
    } else {
        $input = $_POST;
    }

    validateInput($input, [
        'code' => 'required|min:3'
    ]);

    $code = strtoupper(trim($input['code']));

    // 優先查詢資料庫 coupons 表
    $coupon = null;
    try {
        $stmt = $pdo->prepare("SELECT * FROM coupons WHERE code = ? AND expire_at >= NOW()");
        $stmt->execute([$code]);
        $coupon = $stmt->fetch();
    } catch (PDOException $pe) {}

    if ($coupon) {
        $title = $coupon['title'];
        $addVipDays = intval($coupon['vip_days'] ?? 0);
        $addPoints = intval($coupon['points'] ?? 0);
        $addFreePlays = intval($coupon['free_plays'] ?? 0);
    } else if ($code === 'VIP500' || $code === 'FREEMONTH' || $code === 'REVON60') {
        $title = '預設推廣優惠碼';
        $addVipDays = ($code === 'REVON60') ? 60 : 30;
        $addPoints = ($code === 'VIP500') ? 500 : 0;
        $addFreePlays = 10;
    } else {
        sendResponse(400, 'error', '無效或已過期的優惠兌換碼');
    }

    // 更新玩家 VIP, 點數, 免費次數
    $upd = $pdo->prepare("UPDATE users SET vip_level = IF(? > 0, 2, vip_level), points = points + ?, free_plays = free_plays + ? WHERE id = ?");
    $upd->execute([$addVipDays, $addPoints, $addFreePlays, $user['id']]);

    sendResponse(200, 'success', "兌換成功！[{$title}] 已套用", [
        'added_vip_days' => $addVipDays,
        'added_points' => $addPoints,
        'added_free_plays' => $addFreePlays
    ]);

} catch (Throwable $e) {
    sendError($e, 'user/redeem_coupon');
}
