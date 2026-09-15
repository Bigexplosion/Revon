<?php
/**
 * 用戶個人資料更新 API
 * 路徑: /api/user/update_profile.php
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
    
    $contentType = isset($_SERVER["CONTENT_TYPE"]) ? trim($_SERVER["CONTENT_TYPE"]) : '';
    if (strpos($contentType, 'application/json') !== false) {
        $input = json_decode(file_get_contents('php://input'), true);
    } else {
        $input = $_POST;
    }

    $nickname = trim($input['nickname'] ?? '');
    $realName = trim($input['real_name'] ?? $input['realName'] ?? '');
    $gender   = trim($input['gender'] ?? '');
    $phone    = trim($input['phone'] ?? '');
    $birthday = trim($input['birthday'] ?? '');
    $email    = trim($input['email'] ?? '');
    $city     = trim($input['city'] ?? '');
    $country  = trim($input['country'] ?? '');
    
    $updateFields = [];
    $params = [];
    
    if ($nickname !== '') {
        $updateFields[] = "nickname = ?";
        $params[] = $nickname;
    }
    if ($realName !== '') {
        $updateFields[] = "real_name = ?";
        $params[] = $realName;
    }
    if ($gender !== '') {
        if (in_array($gender, ['male', 'female', 'other'])) {
            $updateFields[] = "gender = ?";
            $params[] = $gender;
        }
    }
    if ($phone !== '') {
        $updateFields[] = "phone = ?";
        $params[] = $phone;
    }
    if ($birthday !== '') {
        $updateFields[] = "birthday = ?";
        $params[] = $birthday;
    }
    if ($email !== '') {
        $updateFields[] = "email = ?";
        $params[] = $email;
    }
    if ($city !== '') {
        $updateFields[] = "city = ?";
        $params[] = $city;
    }
    if ($country !== '') {
        $updateFields[] = "country = ?";
        $params[] = $country;
    }
    
    if (empty($updateFields)) {
        sendResponse(400, 'error', '沒有需要更新的欄位');
    }
    
    $params[] = $user['id'];
    $sql = "UPDATE users SET " . implode(', ', $updateFields) . " WHERE id = ?";
    $stmt = $pdo->prepare($sql);
    $stmt->execute($params);
    
    sendResponse(200, 'success', '個人資料更新成功');
    
} catch (Throwable $e) {
    sendError($e, 'user/update_profile');
}
