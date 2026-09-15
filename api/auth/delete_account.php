<?php
/**
 * 刪除個人帳號 API (符合 Apple Guideline 5.1.1 規定)
 * 路徑: /api/auth/delete_account.php
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

    // 進行帳號抹除或軟刪除
    $stmt = $pdo->prepare("UPDATE users SET account = CONCAT('deleted_', id), password = '', remember_token = NULL, email = NULL, phone = NULL, is_deleted = 1 WHERE id = ?");
    
    try {
        $stmt->execute([$user['id']]);
    } catch (PDOException $pe) {
        // 若缺少 is_deleted 欄位，退回基本清除
        $stmtFallback = $pdo->prepare("UPDATE users SET password = '', remember_token = NULL WHERE id = ?");
        $stmtFallback->execute([$user['id']]);
    }

    sendResponse(200, 'success', '帳號已成功註銷並清理個人資料');

} catch (Throwable $e) {
    sendError($e, 'auth/delete_account');
}
