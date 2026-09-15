<?php
/**
 * 請求重置密碼 (發送驗證碼)
 * 路徑: /api/auth/request_reset.php
 * 方法: POST
 */
require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendResponse(405, 'error', '不支援的請求方法');
}

try {
    $pdo = getDB();
    enforceRateLimit($pdo, 'request_reset', 3);

    $input = json_decode(file_get_contents('php://input'), true) ?: $_POST;
    $email = trim($input['email'] ?? '');
    
    if (empty($email)) {
        sendResponse(400, 'error', 'Email 為必填');
    }
    
    $stmt = $pdo->prepare("SELECT id FROM users WHERE email = ?");
    $stmt->execute([$email]);
    $user = $stmt->fetch();
    
    if (!$user) {
        // 為安全起見，不透露信箱是否存在
        sendResponse(200, 'success', '如果信箱存在，驗證碼已寄出');
    }
    
    $code = str_pad(rand(0, 999999), 6, '0', STR_PAD_LEFT);
    $expiresAt = date('Y-m-d H:i:s', time() + 15 * 60); // 15分鐘後過期
    
    // 自動建立 password_resets 資料表 (若不存在)
    $pdo->exec("CREATE TABLE IF NOT EXISTS password_resets (
        email VARCHAR(255) PRIMARY KEY,
        code VARCHAR(6) NOT NULL,
        expires_at DATETIME NOT NULL
    )");

    $stmt = $pdo->prepare("INSERT INTO password_resets (email, code, expires_at) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE code = ?, expires_at = ?");
    $stmt->execute([$email, $code, $expiresAt, $code, $expiresAt]);
    
    // 發送 Email 驗證碼
    $subject = "=?UTF-8?B?" . base64_encode("REV-ON 密碼重置驗證碼") . "?=";
    $message = "親愛的 REV-ON 車手您好：\n\n您的密碼重置驗證碼為：【 $code 】\n\n該驗證碼有效時間為 15 分鐘，請盡速於 App 輸入完成重置。\n若非本人操作，請忽略此信件。";
    $headers = "From: REV-ON <noreply@revon.tw>\r\n" .
               "Reply-To: noreply@revon.tw\r\n" .
               "Content-Type: text/plain; charset=UTF-8\r\n" .
               "X-Mailer: PHP/" . phpversion();

    $mailSent = @mail($email, $subject, $message, $headers);

    if ($mailSent) {
        sendResponse(200, 'success', '驗證碼已發送至您的信箱，請查看郵件');
    } else {
        // 伺服器未設定 mail() 或 SMTP 失敗時回傳提示 (附帶 debug_code 供開發調試)
        sendResponse(200, 'success', '驗證碼已生成，若未收到信件請檢查垃圾郵件或郵件伺服器 (SMTP) 設定', ['debug_code' => $code]);
    }
} catch (Throwable $e) {
    sendError($e, 'auth/request_reset');
}
