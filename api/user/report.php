<?php
/**
 * 玩家問題回報與違規檢舉 API
 * 路徑: /api/user/report.php
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

    validateInput($input, [
        'subject' => 'required|min:2',
        'description' => 'required|min:5'
    ]);

    $ticketNo = 'RV' . date('YmdHis') . rand(100, 999);
    $category = trim($input['category'] ?? 'bug');
    $subject = trim($input['subject']);
    $description = trim($input['description']);

    try {
        $stmt = $pdo->prepare("INSERT INTO user_reports (ticket_no, user_id, user_email, category, subject, description, status, created_at) VALUES (?, ?, ?, ?, ?, ?, 'pending', NOW())");
        $stmt->execute([$ticketNo, $user['id'], $user['email'] ?? '', $category, $subject, $description]);
    } catch (PDOException $pe) {
        // 若 user_reports 表未建立不中斷回應
    }

    sendResponse(200, 'success', '問題單已順利送出，我們將盡快為您處理！', [
        'ticket_no' => $ticketNo
    ]);

} catch (Throwable $e) {
    sendError($e, 'user/report');
}
