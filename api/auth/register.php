<?php
/**
 * 用戶註冊 API
 * 路徑: /api/auth/register.php
 * 方法: POST
 */

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendResponse(405, 'error', '不支援的請求方法');
}

try {
    $pdo = getDB();
    enforceRateLimit($pdo, 'register', 5);

    $contentType = isset($_SERVER["CONTENT_TYPE"]) ? trim($_SERVER["CONTENT_TYPE"]) : '';
    if (strpos($contentType, 'application/json') !== false) {
        $input = json_decode(file_get_contents('php://input'), true);
    } else {
        $input = $_POST;
    }

    $account     = trim($input['account'] ?? '');
    $password    = trim($input['password'] ?? '');
    $email       = trim($input['email'] ?? '');
    $nickname    = trim($input['nickname'] ?? '');
    $realName    = trim($input['real_name'] ?? $input['realName'] ?? '');
    $gender      = trim($input['gender'] ?? 'other');
    $phone       = trim($input['phone'] ?? '');
    $birthday    = trim($input['birthday'] ?? '');
    $city        = trim($input['city'] ?? 'Taichung (台中市)');
    $country     = trim($input['country'] ?? 'Taiwan (台灣)');

    // 格式化 Country 與 City 為 英文 (中文)
    if (!str_contains($country, '(')) {
        if (str_contains($country, 'Taiwan') || str_contains($country, '台灣')) $country = 'Taiwan (台灣)';
        else if (str_contains($country, 'Japan') || str_contains($country, '日本')) $country = 'Japan (日本)';
        else if (str_contains($country, 'United States') || str_contains($country, '美國')) $country = 'United States (美國)';
        else if (str_contains($country, 'Hong Kong') || str_contains($country, '香港')) $country = 'Hong Kong (香港)';
        else $country = 'Taiwan (台灣)';
    }

    if (!str_contains($city, '(')) {
        if (str_contains($city, 'Taipei') || str_contains($city, '台北')) $city = 'Taipei (台北市)';
        else if (str_contains($city, 'New Taipei') || str_contains($city, '新北')) $city = 'New Taipei (新北市)';
        else if (str_contains($city, 'Taoyuan') || str_contains($city, '桃園')) $city = 'Taoyuan (桃園市)';
        else if (str_contains($city, 'Taichung') || str_contains($city, '台中')) $city = 'Taichung (台中市)';
        else if (str_contains($city, 'Tainan') || str_contains($city, '台南')) $city = 'Tainan (台南市)';
        else if (str_contains($city, 'Kaohsiung') || str_contains($city, '高雄')) $city = 'Kaohsiung (高雄市)';
        else if (str_contains($city, 'Hsinchu') || str_contains($city, '新竹')) $city = 'Hsinchu (新竹市)';
        else $city = 'Taichung (台中市)';
    }

    if (empty($account) || empty($password) || empty($email)) {
        sendResponse(400, 'error', '電子信箱、帳號與密碼為必填欄位');
    }

    if (strlen($account) < 3 || strlen($password) < 6) {
        sendResponse(400, 'error', '帳號至少需要 3 個字元，密碼至少需要 6 個字元');
    }

    // 檢查帳號是否已存在
    $checkStmt = $pdo->prepare("SELECT id FROM users WHERE account = ?");
    $checkStmt->execute([$account]);
    if ($checkStmt->fetch()) {
        sendResponse(400, 'error', '此帳號已被註冊');
    }

    // 檢查 Email 是否已被使用
    $checkEmail = $pdo->prepare("SELECT id FROM users WHERE email = ?");
    $checkEmail->execute([$email]);
    if ($checkEmail->fetch()) {
        sendResponse(400, 'error', '此電子信箱已被註冊');
    }

    // 檢查電話是否已被使用
    if (!empty($phone)) {
        $checkPhone = $pdo->prepare("SELECT id FROM users WHERE phone = ?");
        $checkPhone->execute([$phone]);
        if ($checkPhone->fetch()) {
            sendResponse(400, 'error', '此聯絡電話已被註冊');
        }
    }

    // 若未提供暱稱或與帳號/Email相同，自動生成虛擬編號暱稱 (Revon00001~Revon99999)
    if (empty($nickname) || $nickname === $account || $nickname === $email) {
        $randomNumber = sprintf("%05d", rand(1, 99999));
        $nickname = "Revon" . $randomNumber;
    }

    // 規範 gender 格式 (male, female, other)
    if (!in_array($gender, ['male', 'female', 'other'])) {
        $gender = 'other';
    }

    $passwordHash = password_hash($password, PASSWORD_BCRYPT);
    $token = bin2hex(random_bytes(32));

    $insStmt = $pdo->prepare("INSERT INTO users (account, password, real_name, nickname, gender, phone, email, birthday, city, country, remember_token, free_plays, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 5, NOW())");
    $insStmt->execute([$account, $passwordHash, $realName, $nickname, $gender, $phone, $email, $birthday ?: null, $city, $country, $token]);

    $userId = (int)$pdo->lastInsertId();

    sendResponse(200, 'success', '註冊成功', [
        'id'         => $userId,
        'account'    => $account,
        'nickname'   => $nickname,
        'email'      => $email,
        'city'       => $city,
        'country'    => $country,
        'vip_level'  => 1,
        'free_plays' => 5,
        'token'      => $token
    ]);

} catch (Throwable $e) {
    sendError($e, 'auth/register');
}
