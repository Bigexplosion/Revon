<?php
/**
 * Admin 車隊管理 API
 * 路徑: /api/admin/clubs.php
 * 方法: GET / POST
 */

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();

    if ($_SERVER['REQUEST_METHOD'] === 'GET') {
        $action = $_GET['action'] ?? '';
        if ($action === 'members') {
            $clubId = (int)($_GET['club_id'] ?? 0);
            if ($clubId <= 0) sendResponse(400, 'error', '無效的車隊 ID');

            try {
                $stmt = $pdo->prepare("SELECT cm.user_id, cm.role, cm.joined_at, COALESCE(u.nickname, u.account, '隊員') AS member_nickname
                    FROM club_members cm LEFT JOIN users u ON u.id = cm.user_id
                    WHERE cm.club_id = ? ORDER BY FIELD(cm.role, 'captain') DESC, cm.joined_at ASC");
                $stmt->execute([$clubId]);
                $members = $stmt->fetchAll(PDO::FETCH_ASSOC);
                if (empty($members)) {
                    $captainStmt = $pdo->prepare("SELECT captain_nickname FROM clubs WHERE id = ?");
                    $captainStmt->execute([$clubId]);
                    $members = [[
                        'user_id' => 0,
                        'role' => 'captain',
                        'joined_at' => null,
                        'member_nickname' => $captainStmt->fetchColumn() ?: '隊長'
                    ]];
                }
            } catch (Throwable $memberError) {
                $captainStmt = $pdo->prepare("SELECT captain_nickname FROM clubs WHERE id = ?");
                $captainStmt->execute([$clubId]);
                $members = [[
                    'user_id' => 0,
                    'role' => 'captain',
                    'joined_at' => null,
                    'member_nickname' => $captainStmt->fetchColumn() ?: '隊長'
                ]];
            }
            sendResponse(200, 'success', '取得車隊成員成功', array_map(function ($member) {
                return [
                    'userId' => (int)$member['user_id'],
                    'role' => $member['role'],
                    'memberNickname' => $member['member_nickname'],
                    'joinedAt' => $member['joined_at']
                ];
            }, $members));
        }
        $sortOrder = $_GET['order'] ?? 'DESC';
        $sortOrder = strtoupper($sortOrder) === 'ASC' ? 'ASC' : 'DESC';

        $stmt = $pdo->query("SELECT * FROM clubs ORDER BY id {$sortOrder}");
        $clubs = $stmt->fetchAll(PDO::FETCH_ASSOC);

        foreach ($clubs as &$c) {
            $c['id'] = (int)$c['id'];
            $c['member_count'] = (int)($c['member_count'] ?? 1);
            $c['max_members'] = (int)($c['max_members'] ?? 50);
            $c['total_points'] = (int)($c['total_points'] ?? 0);
            $c['level'] = (int)($c['level'] ?? 1);
        }

        sendResponse(200, 'success', '取得車隊列表成功', $clubs);

    } elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $input = json_decode(file_get_contents('php://input'), true) ?: $_POST;
        $action = $input['action'] ?? '';
        $clubId = (int)($input['id'] ?? $input['club_id'] ?? 0);

        if ($action === 'create') {
            $name = trim($input['name'] ?? '');
            if (empty($name)) {
                sendResponse(400, 'error', '車隊名稱不得為空');
            }

            $badgeLetters = trim($input['badge_letters'] ?? strtoupper(mb_substr($name, 0, 2)));
            $motto = trim($input['motto'] ?? '');
            $region = trim($input['region'] ?? 'Taiwan');
            $captain = trim($input['captain_nickname'] ?? 'REVON_ADMIN');
            $memberCount = (int)($input['member_count'] ?? 1);
            $maxMembers = (int)($input['max_members'] ?? 50);
            $totalPoints = (int)($input['total_points'] ?? 0);
            $level = (int)($input['level'] ?? 1);
            $accentColor = trim($input['accent_color'] ?? '#E10600');

            $stmt = $pdo->prepare("INSERT INTO clubs (
                name, badge_letters, motto, region, captain_nickname, 
                member_count, max_members, total_points, level, accent_color, 
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())");

            $stmt->execute([
                $name, $badgeLetters, $motto, $region, $captain,
                $memberCount, $maxMembers, $totalPoints, $level, $accentColor
            ]);

            $newId = $pdo->lastInsertId();
            sendResponse(200, 'success', '新增車隊成功', ['id' => $newId]);

        } elseif ($action === 'update' && $clubId > 0) {
            $name = trim($input['name'] ?? '');
            if (empty($name)) {
                sendResponse(400, 'error', '車隊名稱不得為空');
            }

            $badgeLetters = trim($input['badge_letters'] ?? 'RV');
            $motto = trim($input['motto'] ?? '');
            $region = trim($input['region'] ?? 'Taiwan');
            $captain = trim($input['captain_nickname'] ?? 'Captain');
            $memberCount = (int)($input['member_count'] ?? 1);
            $maxMembers = (int)($input['max_members'] ?? 50);
            $totalPoints = (int)($input['total_points'] ?? 0);
            $level = (int)($input['level'] ?? 1);
            $accentColor = trim($input['accent_color'] ?? '#E10600');

            $stmt = $pdo->prepare("UPDATE clubs SET 
                name = ?, badge_letters = ?, motto = ?, region = ?, captain_nickname = ?,
                member_count = ?, max_members = ?, total_points = ?, level = ?, accent_color = ?,
                updated_at = NOW() 
                WHERE id = ?");

            $stmt->execute([
                $name, $badgeLetters, $motto, $region, $captain,
                $memberCount, $maxMembers, $totalPoints, $level, $accentColor,
                $clubId
            ]);

            sendResponse(200, 'success', '車隊資訊更新成功');

        } elseif ($action === 'delete' && $clubId > 0) {
            $stmt = $pdo->prepare("DELETE FROM clubs WHERE id = ?");
            $stmt->execute([$clubId]);
            sendResponse(200, 'success', '車隊刪除成功');

        } elseif ($action === 'remove_member' && $clubId > 0) {
            $userId = (int)($input['user_id'] ?? 0);
            $memberStmt = $pdo->prepare("SELECT role FROM club_members WHERE club_id = ? AND user_id = ?");
            $memberStmt->execute([$clubId, $userId]);
            $member = $memberStmt->fetch(PDO::FETCH_ASSOC);
            if (!$member) sendResponse(404, 'error', '找不到此車隊成員');
            if ($member['role'] === 'captain') sendResponse(403, 'error', '隊長不可踢出車隊');

            $pdo->beginTransaction();
            $deleteStmt = $pdo->prepare("DELETE FROM club_members WHERE club_id = ? AND user_id = ?");
            $deleteStmt->execute([$clubId, $userId]);
            $countStmt = $pdo->prepare("SELECT COUNT(*) FROM club_members WHERE club_id = ?");
            $countStmt->execute([$clubId]);
            $updateStmt = $pdo->prepare("UPDATE clubs SET member_count = ?, updated_at = NOW() WHERE id = ?");
            $updateStmt->execute([max(1, (int)$countStmt->fetchColumn()), $clubId]);
            $pdo->commit();
            sendResponse(200, 'success', '成員已踢出車隊');
        }

        sendResponse(400, 'error', '無效的操作');
    }

    sendResponse(400, 'error', '不支援的請求方法');
} catch (Throwable $e) {
    sendError($e, 'admin/clubs');
}
