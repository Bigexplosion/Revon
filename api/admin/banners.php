<?php
/**
 * 輪播牆 Banner 管理 API
 * 路徑: /api/admin/banners.php
 */
require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();

    $pdo->exec("CREATE TABLE IF NOT EXISTS banners (
        id INT AUTO_INCREMENT PRIMARY KEY,
        title VARCHAR(255),
        image_url VARCHAR(500) NOT NULL,
        sort_order INT DEFAULT 0,
        is_active TINYINT DEFAULT 1,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

    if ($_SERVER['REQUEST_METHOD'] === 'GET') {
        $stmt = $pdo->query("SELECT * FROM banners ORDER BY sort_order ASC, id DESC");
        sendResponse(200, 'success', '取得 Banner 清單成功', $stmt->fetchAll(PDO::FETCH_ASSOC));

    } elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $action = $_POST['action'] ?? '';
        
        // 若為 JSON input
        if (empty($action)) {
            $input = json_decode(file_get_contents('php://input'), true);
            $action = $input['action'] ?? 'upload';
        }

        if ($action === 'upload') {
            if (!isset($_FILES['banner_file']) || $_FILES['banner_file']['error'] !== UPLOAD_ERR_OK) {
                sendResponse(400, 'error', '請選擇上傳的 Banner 圖片');
            }

            $file = $_FILES['banner_file'];
            // 5MB 限制
            if ($file['size'] > 5 * 1024 * 1024) {
                sendResponse(400, 'error', '檔案大小不可超過 5MB');
            }

            $allowedTypes = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
            $finfo = finfo_open(FILEINFO_MIME_TYPE);
            $mime = finfo_file($finfo, $file['tmp_name']);
            finfo_close($finfo);

            if (!in_array($mime, $allowedTypes)) {
                sendResponse(400, 'error', '不支援的圖片格式，僅接受 JPG, PNG, WEBP, GIF');
            }

            $ext = pathinfo($file['name'], PATHINFO_EXTENSION);
            $uploadDir = dirname(__DIR__, 2) . '/uploads/banners/';
            if (!file_exists($uploadDir)) {
                mkdir($uploadDir, 0777, true);
            }

            $filename = 'banner_' . time() . '_' . rand(1000, 9999) . '.' . $ext;
            $targetPath = $uploadDir . $filename;

            if (move_uploaded_file($file['tmp_name'], $targetPath)) {
                $relativeUrl = 'uploads/banners/' . $filename;
                $title = trim($_POST['title'] ?? '');

                $stmt = $pdo->prepare("INSERT INTO banners (title, image_url) VALUES (?, ?)");
                $stmt->execute([$title, $relativeUrl]);
                sendResponse(200, 'success', 'Banner 上傳成功', ['id' => $pdo->lastInsertId(), 'url' => $relativeUrl]);
            } else {
                sendError($e, 'admin/banners');
            }

        } elseif ($action === 'delete') {
            $id = (int)($input['id'] ?? $_POST['id'] ?? 0);
            $stmt = $pdo->prepare("SELECT image_url FROM banners WHERE id = ?");
            $stmt->execute([$id]);
            $banner = $stmt->fetch(PDO::FETCH_ASSOC);

            if ($banner) {
                $filePath = dirname(__DIR__, 2) . '/' . $banner['image_url'];
                if (file_exists($filePath)) {
                    @unlink($filePath);
                }
                $delStmt = $pdo->prepare("DELETE FROM banners WHERE id = ?");
                $delStmt->execute([$id]);
                sendResponse(200, 'success', 'Banner 已成功刪除');
            }
            sendResponse(404, 'error', '找不到此 Banner');
        }
    }
    sendResponse(400, 'error', '無效的請求');
} catch (Throwable $e) {
    sendError($e, 'admin/banners');
}
