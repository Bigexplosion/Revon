<?php
/**
 * 重置密碼 API
 * 路徑: /api/auth/reset_password.php
 * 方法: POST
 */

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendResponse(405, 'error', '不支援的請求方法');
}

try {
    $pdo = getDB();
    enforceRateLimit($pdo, 'reset_pw', 5);

    $contentType = isset($_SERVER["CONTENT_TYPE"]) ? trim($_SERVER["CONTENT_TYPE"]) : '';
    if (strpos($contentType, 'application/json') !== false) {
        $input = json_decode(file_get_contents('php://input'), true);
    } else {
        $input = $_POST;
    }

    validateInput($input, [
        'email'        => 'required',
        'code'         => 'required',
        'new_password' => 'required|min:6'
    ]);

    $email = trim($input['email']);
    $code = trim($input['code']);
    $newPassword = trim($input['new_password']);

    // Check if the user exists
    $stmt = $pdo->prepare("SELECT id FROM users WHERE email = ?");
    $stmt->execute([$email]);
    $user = $stmt->fetch();

    if (!$user) {
        sendResponse(404, 'error', '找不到此信箱');
    }

    // 自動建立 password_resets 資料表 (若不存在)
    $pdo->exec("CREATE TABLE IF NOT EXISTS password_resets (
        email VARCHAR(255) PRIMARY KEY,
        code VARCHAR(6) NOT NULL,
        expires_at DATETIME NOT NULL
    )");

    // Verify the code
    $stmt = $pdo->prepare("SELECT * FROM password_resets WHERE email = ? AND code = ? AND expires_at > NOW()");
    $stmt->execute([$email, $code]);
    $reset = $stmt->fetch();

    if (!$reset) {
        sendResponse(400, 'error', '驗證碼錯誤或已過期');
    }

    $hash = password_hash($newPassword, PASSWORD_BCRYPT);
    $upd = $pdo->prepare("UPDATE users SET password = ?, remember_token = NULL WHERE id = ?");
    $upd->execute([$hash, $user['id']]);

    // Delete the used token
    $del = $pdo->prepare("DELETE FROM password_resets WHERE email = ?");
    $del->execute([$email]);

    sendResponse(200, 'success', '密碼重置成功，請使用新密碼重新登入');

} catch (Throwable $e) {
    sendError($e, 'auth/reset_password');
}
