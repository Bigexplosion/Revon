<?php
/**
 * 新聞與貼文 API
 * 路徑: /api/admin/news.php
 */
require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();

    $pdo->exec("CREATE TABLE IF NOT EXISTS news (
        id INT AUTO_INCREMENT PRIMARY KEY,
        title VARCHAR(255) NOT NULL,
        content LONGTEXT,
        cover_image VARCHAR(500),
        views INT DEFAULT 0,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");

    if ($_SERVER['REQUEST_METHOD'] === 'GET') {
        $stmt = $pdo->query("SELECT * FROM news ORDER BY id DESC");
        sendResponse(200, 'success', '取得貼文列表成功', $stmt->fetchAll(PDO::FETCH_ASSOC));

    } elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $action = $_POST['action'] ?? '';
        if (empty($action)) {
            $input = json_decode(file_get_contents('php://input'), true);
            $action = $input['action'] ?? 'create';
        }

        if ($action === 'create') {
            $title = trim($_POST['title'] ?? $input['title'] ?? '');
            $content = $_POST['content'] ?? $input['content'] ?? '';
            $coverUrl = '';

            if (isset($_FILES['cover_image']) && $_FILES['cover_image']['error'] === UPLOAD_ERR_OK) {
                $file = $_FILES['cover_image'];
                $ext = pathinfo($file['name'], PATHINFO_EXTENSION);
                $uploadDir = dirname(__DIR__, 2) . '/uploads/news/';
                if (!file_exists($uploadDir)) {
                    mkdir($uploadDir, 0777, true);
                }
                $filename = 'news_' . time() . '_' . rand(1000, 9999) . '.' . $ext;
                if (move_uploaded_file($file['tmp_name'], $uploadDir . $filename)) {
                    $coverUrl = 'uploads/news/' . $filename;
                }
            }

            if (empty($title)) {
                sendResponse(400, 'error', '標題不得為空');
            }

            $stmt = $pdo->prepare("INSERT INTO news (title, content, cover_image) VALUES (?, ?, ?)");
            $stmt->execute([$title, $content, $coverUrl]);
            sendResponse(200, 'success', '貼文建立成功', ['id' => $pdo->lastInsertId()]);

        } elseif ($action === 'delete') {
            $id = (int)($input['id'] ?? $_POST['id'] ?? 0);
            $stmt = $pdo->prepare("SELECT cover_image FROM news WHERE id = ?");
            $stmt->execute([$id]);
            $item = $stmt->fetch(PDO::FETCH_ASSOC);

            if ($item) {
                if (!empty($item['cover_image'])) {
                    $filePath = dirname(__DIR__, 2) . '/' . $item['cover_image'];
                    if (file_exists($filePath)) @unlink($filePath);
                }
                $del = $pdo->prepare("DELETE FROM news WHERE id = ?");
                $del->execute([$id]);
                sendResponse(200, 'success', '貼文已刪除');
            }
            sendResponse(404, 'error', '找不到該貼文');
        }
    }
    sendResponse(400, 'error', '無效的請求');
} catch (Throwable $e) {
    sendError($e, 'admin/news');
}
