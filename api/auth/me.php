<?php
/**
 * 取得當前登入者資訊 API
 * 路徑: /api/auth/me.php
 * 方法: GET 或 POST
 * Header: Authorization: Bearer <token>
 */

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();
    $user = verifyTokenAndGetUser($pdo);

    $avatarUrl = $user['avatar_url'] ?? $user['profile_image_url'] ?? $user['avatar'] ?? null;

    // 若資料庫欄位尚未或無法寫入值，則自動從 profile_image_url/ 目錄比對 {id}_*.* 檔案
    if (empty($avatarUrl)) {
        $rootDir = dirname(dirname(__DIR__));
        $targetDir = $rootDir . '/profile_image_url';
        $pattern = $targetDir . '/' . $user['id'] . '_*.*';
        $found = glob($pattern);
        if (!empty($found)) {
            $matchedFile = basename($found[0]);
            $avatarUrl = '/profile_image_url/' . $matchedFile;
        }
    }

    // 格式化輸出用戶個人資訊
    $userData = [
        'id'          => (int)$user['id'],
        'account'     => $user['account'],
        'nickname'    => $user['nickname'] ?? $user['account'],
        'real_name'   => $user['real_name'] ?? '',
        'gender'      => $user['gender'] ?? 'other',
        'email'       => $user['email'] ?? '',
        'phone'       => $user['phone'] ?? '',
        'birthday'    => $user['birthday'] ?? '',
        'vip_level'   => (int)($user['vip_level'] ?? 1),
        'vip_expire'  => $user['vip_expire'] ?? null,
        'free_plays'  => (int)($user['free_plays'] ?? 5),
        'points'      => (int)($user['points'] ?? 0),
        'avatar_url'  => $avatarUrl,
        'profile_image_url' => $avatarUrl,
        'city'        => $user['city'] ?? 'Taipei',
        'country'     => $user['country'] ?? 'Taiwan',
        'role'        => $user['role'] ?? 'user',
        'created_at'  => $user['created_at'] ?? null
    ];

    sendResponse(200, 'success', '取得用戶個人資訊成功', $userData);

} catch (Throwable $e) {
    sendError($e, 'auth/me');
}
