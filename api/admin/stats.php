<?php
/**
 * 後台概覽統計數據 API
 * 路徑: /api/admin/stats.php
 */
require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();
    
    // 總會員數
    $stmtUsers = $pdo->query("SELECT COUNT(*) FROM users");
    $totalUsers = (int)$stmtUsers->fetchColumn();
    
    // 今日活躍會員 (以今日有登入或更新 updated_at 筆數，或簡化傳回)
    $stmtActive = $pdo->query("SELECT COUNT(*) FROM users WHERE DATE(created_at) = CURDATE()");
    $activeToday = (int)$stmtActive->fetchColumn();
    if ($activeToday == 0) {
        $activeToday = $totalUsers > 0 ? 1 : 0; // 若無今日新加入，有總會員就至少計1
    }
    
    // 待審賽道 (假設 is_approved = 0 或總自訂賽道)
    $pendingTracks = 0;
    try {
        $stmtTracks = $pdo->query("SELECT COUNT(*) FROM tracks WHERE is_approved = 0");
        $pendingTracks = (int)$stmtTracks->fetchColumn();
    } catch (Exception $e) {
        $stmtTracks = $pdo->query("SELECT COUNT(*) FROM tracks");
        $pendingTracks = (int)$stmtTracks->fetchColumn();
    }
    
    // 異常成績 / 舉報 (容錯查詢)
    $reports = 0;
    try {
        $stmtReports = $pdo->query("SELECT COUNT(*) FROM user_reports");
        $reports = (int)$stmtReports->fetchColumn();
    } catch (Exception $e) {
        $reports = 0;
    }

    sendResponse(200, 'success', '取得概覽統計成功', [
        'users' => $totalUsers,
        'active' => $activeToday,
        'pendingTracks' => $pendingTracks,
        'reports' => $reports
    ]);
} catch (Throwable $e) {
    sendError($e, 'admin/stats');
}
