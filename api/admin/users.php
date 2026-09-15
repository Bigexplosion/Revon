<?php
/**
 * 會員管理 API
 * 路徑: /api/admin/users.php
 */
require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();
    
    if ($_SERVER['REQUEST_METHOD'] === 'GET') {
        $stmt = $pdo->query("SELECT id, account, real_name, nickname, gender, phone, email, birthday, vip_level, points, free_plays, role, created_at FROM users ORDER BY id DESC");
        $users = $stmt->fetchAll(PDO::FETCH_ASSOC);
        sendResponse(200, 'success', '取得會員列表成功', $users);
    } elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $input = json_decode(file_get_contents('php://input'), true) ?: $_POST;
        $action = $input['action'] ?? '';
        $userId = (int)($input['user_id'] ?? 0);
        
        if ($action === 'delete' && $userId > 0) {
            $stmt = $pdo->prepare("DELETE FROM users WHERE id = ?");
            $stmt->execute([$userId]);
            sendResponse(200, 'success', '會員刪除成功');
        } elseif ($action === 'update_role' && $userId > 0) {
            $role = $input['role'] ?? 'user';
            $stmt = $pdo->prepare("UPDATE users SET role = ? WHERE id = ?");
            $stmt->execute([$role, $userId]);
            sendResponse(200, 'success', '權限更新成功');
        } elseif ($action === 'update_points' && $userId > 0) {
            $points = (int)($input['points'] ?? 0);
            $stmt = $pdo->prepare("UPDATE users SET points = ?, free_plays = ? WHERE id = ?");
            $stmt->execute([$points, $points, $userId]);
            sendResponse(200, 'success', '點數更新成功');
        } elseif ($action === 'block' && $userId > 0) {
            $isBlocked = (int)($input['is_blocked'] ?? 1);
            // Assuming we use 'role' = 'blocked' or a new column 'is_blocked'
            // For now, let's use a new column or just set role to 'blocked'
            $stmt = $pdo->prepare("UPDATE users SET role = 'blocked' WHERE id = ?");
            $stmt->execute([$userId]);
            sendResponse(200, 'success', '會員封鎖狀態已更新');
        } elseif ($action === 'edit_profile' && $userId > 0) {
            $nickname = trim($input['nickname'] ?? '');
            $realName = trim($input['real_name'] ?? '');
            $email    = trim($input['email'] ?? '');
            $phone    = trim($input['phone'] ?? '');
            $gender   = trim($input['gender'] ?? 'other');
            $birthday = trim($input['birthday'] ?? '');
            $stmt = $pdo->prepare("UPDATE users SET nickname = ?, real_name = ?, email = ?, phone = ?, gender = ?, birthday = ? WHERE id = ?");
            $stmt->execute([$nickname, $realName, $email, $phone, $gender, $birthday ?: null, $userId]);
            sendResponse(200, 'success', '玩家資料已更新');
        }
    }
    
    sendResponse(400, 'error', '無效的請求');
} catch (Throwable $e) {
    sendError($e, 'admin/users');
}
