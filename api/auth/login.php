<?php
/**
 * 用戶登入 API
 * 路徑: /api/auth/login.php
 * 方法: POST
 */

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

// 確保是 POST 請求
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendResponse(405, 'error', '不支援的請求方法 (Method Not Allowed)');
}

try {
    $pdo = getDB();

    // 頻率限制：每分鐘同 IP 最多 5 次嘗試登入
    enforceRateLimit($pdo, 'login', 5);

    // 解析 POST 參數 (支援 JSON 或 Form-Data)
    $contentType = isset($_SERVER["CONTENT_TYPE"]) ? trim($_SERVER["CONTENT_TYPE"]) : '';
    if (strpos($contentType, 'application/json') !== false) {
        $input = json_decode(file_get_contents('php://input'), true) ?? [];
    } else {
        $input = $_POST;
    }

    validateInput($input, [
        'account'  => 'required|min:3',
        'password' => 'required|min:6'
    ]);

    $account = trim($input['account'] ?? '');
    $password = trim($input['password'] ?? '');

    // 查詢使用者 (支援輸入帳號 account 或 Email 登入)
    $stmt = $pdo->prepare("SELECT * FROM users WHERE account = ? OR email = ?");
    $stmt->execute([$account, $account]);
    $user = $stmt->fetch();

    if (!$user || !password_verify($password, $user['password'])) {
        sendResponse(401, 'error', '帳號或密碼錯誤');
    }

    // 密碼驗證成功，產生新 Token
    $newToken = bin2hex(random_bytes(32));
    $hashedToken = hash('sha256', $newToken);
    
    // 更新資料庫
    $updateStmt = $pdo->prepare("UPDATE users SET remember_token = ? WHERE id = ?");
    $updateStmt->execute([$hashedToken, $user['id']]);
    
    $userData = [
        'id'         => $user['id'],
        'account'    => $user['account'],
        'nickname'   => $user['nickname'] ?? '',
        'vip_level'  => $user['vip_level'] ?? 1,
        'free_plays' => $user['free_plays'] ?? 5,
        'role'       => $user['role'] ?? 'user',
        'token'      => $newToken
    ];
    
    sendResponse(200, 'success', '登入成功', $userData);

} catch (Throwable $e) {
    sendError($e, 'auth/login');
}
