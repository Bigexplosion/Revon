<?php
/**
 * 檢查帳號或 Email 是否衝突 / 已被註冊
 * 路徑: /api/auth/check_availability.php
 * 方法: GET / POST
 */

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();

    $contentType = isset($_SERVER["CONTENT_TYPE"]) ? trim($_SERVER["CONTENT_TYPE"]) : '';
    if (strpos($contentType, 'application/json') !== false) {
        $input = json_decode(file_get_contents('php://input'), true);
    } else {
        $input = array_merge($_GET, $_POST);
    }

    $account = trim($input['account'] ?? '');
    $email   = trim($input['email'] ?? '');
    $phone   = trim($input['phone'] ?? '');

    $accountExists = false;
    $emailExists   = false;
    $phoneExists   = false;

    if (!empty($account)) {
        $stmt = $pdo->prepare("SELECT id FROM users WHERE account = ? LIMIT 1");
        $stmt->execute([$account]);
        if ($stmt->fetch()) {
            $accountExists = true;
        }
    }

    if (!empty($email)) {
        $stmt = $pdo->prepare("SELECT id FROM users WHERE email = ? LIMIT 1");
        $stmt->execute([$email]);
        if ($stmt->fetch()) {
            $emailExists = true;
        }
    }

    if (!empty($phone)) {
        $stmt = $pdo->prepare("SELECT id FROM users WHERE phone = ? LIMIT 1");
        $stmt->execute([$phone]);
        if ($stmt->fetch()) {
            $phoneExists = true;
        }
    }

    sendResponse(200, 'success', '檢查完成', [
        'account_exists'    => $accountExists,
        'account_available' => !$accountExists,
        'email_exists'      => $emailExists,
        'email_available'   => !$emailExists,
        'phone_exists'      => $phoneExists,
        'phone_available'   => !$phoneExists
    ]);

} catch (Throwable $e) {
    sendError($e, 'auth/check_availability');
}
