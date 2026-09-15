<?php
/**
 * 用戶頭像上傳 API
 * 路徑: /api/user/upload_avatar.php
 * 方法: POST (multipart/form-data)
 * 欄位: avatar (file)
 * Header: Authorization: Bearer <token>
 */

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendResponse(405, 'error', '不支援的請求方法');
}

try {
    $pdo = getDB();
    enforceRateLimit($pdo, 'upload_avatar', 5);

    $user = verifyTokenAndGetUser($pdo);

    if (!isset($_FILES['avatar']) || $_FILES['avatar']['error'] !== UPLOAD_ERR_OK) {
        $errorCode = $_FILES['avatar']['error'] ?? '未選擇檔案';
        sendResponse(400, 'error', '請選擇有效的圖片檔案 (代碼: ' . $errorCode . ')');
    }

    $file = $_FILES['avatar'];
    $tmpPath = $file['tmp_name'];
    $fileSize = $file['size'];

    // 限制圖片大小 (例如 10MB)
    if ($fileSize > 10 * 1024 * 1024) {
        sendResponse(400, 'error', '圖片檔案過大，請勿超過 10MB');
    }

    // 檢查 MIME type / 副檔名
    $finfo = finfo_open(FILEINFO_MIME_TYPE);
    $mimeType = finfo_file($finfo, $tmpPath);
    finfo_close($finfo);

    $allowedMimes = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
    if (!in_array($mimeType, $allowedMimes)) {
        sendResponse(400, 'error', '不支援的圖片格式，請上傳 JPG, PNG, WEBP 或 GIF 格式圖片');
    }

    $ext = 'jpg';
    if ($mimeType === 'image/png') $ext = 'png';
    else if ($mimeType === 'image/webp') $ext = 'webp';
    else if ($mimeType === 'image/gif') $ext = 'gif';

    // 儲存目錄: 專案根目錄下的 profile_image_url/ 夾
    // web/revon_android/profile_image_url/
    $rootDir = dirname(dirname(__DIR__));
    $targetDir = $rootDir . '/profile_image_url';

    if (!is_dir($targetDir)) {
        @mkdir($targetDir, 0755, true);
    }

    $accountClean = preg_replace('/[^a-zA-Z0-9_\-]/', '', $user['account'] ?? 'user');
    $roleClean = $user['role'] ?? 'user';

    // 清理該用戶舊有的頭像檔案（相符 {id}_* 規則）
    $oldPattern = $targetDir . '/' . $user['id'] . '_*.*';
    foreach (glob($oldPattern) as $oldFile) {
        if (is_file($oldFile)) {
            @unlink($oldFile);
        }
    }

    $fileName = $user['id'] . '_' . $accountClean . '_' . $roleClean . '.' . $ext;
    $targetFilePath = $targetDir . '/' . $fileName;

    if (!move_uploaded_file($tmpPath, $targetFilePath)) {
        sendResponse(500, 'error', '圖片儲存失敗，請檢查伺服器目錄權限');
    }

    // 計算相對/完整 URL (例如 /profile_image_url/avatar_user_19_xxx.jpg)
    $avatarUrl = '/profile_image_url/' . $fileName;

    // 動態確保 users 表格中存在 avatar_url 與 profile_image_url 欄位
    try {
        $pdo->exec("ALTER TABLE users ADD COLUMN profile_image_url VARCHAR(255) NULL");
    } catch (Throwable $e) {}
    try {
        $pdo->exec("ALTER TABLE users ADD COLUMN avatar_url VARCHAR(255) NULL");
    } catch (Throwable $e) {}

    // 更新 users 資料庫中的 profile_image_url 與 avatar_url 欄位
    $sql = "UPDATE users SET profile_image_url = ?, avatar_url = ? WHERE id = ?";
    $stmt = $pdo->prepare($sql);
    $stmt->execute([$avatarUrl, $avatarUrl, $user['id']]);

    sendResponse(200, 'success', '頭像上傳成功', [
        'avatar_url' => $avatarUrl,
        'profile_image_url' => $avatarUrl
    ]);

} catch (Throwable $e) {
    sendError($e, 'user/upload_avatar');
}
