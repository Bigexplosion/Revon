<?php
/**
 * 背景排程模板腳本 (可由 Synology Cron Job 定時呼叫)
 * 路徑: /api/cron/ai_police_scan.php
 * 說明: 預設架構腳本，後續可自由擴充自訂邏輯 (如清理過期 Log、更新快照或發送統計)
 */

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();
    $startTime = date('Y-m-d H:i:s');
    $scannedCount = 0;

    // 範例通用邏輯：可擴充自訂背景任務 (例如維護排行榜快照或紀錄執行 Log)
    try {
        $stmt = $pdo->prepare("INSERT INTO ai_police_scan_log (scan_date, run_at, alerts_found) VALUES (CURDATE(), NOW(), ?)");
        $stmt->execute([$scannedCount]);
    } catch (PDOException $pe) {
        // Log 表若不存在則忽略
    }

    sendResponse(200, 'success', 'Cron 排程腳本執行完成', [
        'run_at' => $startTime,
        'status' => 'completed',
        'custom_task' => 'Ready for custom logic'
    ]);

} catch (Throwable $e) {
    sendResponse(500, 'error', 'Cron 排程錯誤: ' . $e->getMessage());
}
