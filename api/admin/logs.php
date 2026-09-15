<?php
/**
 * Admin 系統 Log / 錯誤日誌 API
 * 路徑: /api/admin/logs.php
 */
require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();
    
    // 自動確保 error_logs 資料表存在
    $pdo->exec("CREATE TABLE IF NOT EXISTS `error_logs` (
        `id` INT AUTO_INCREMENT PRIMARY KEY,
        `context` VARCHAR(100) NULL,
        `error_type` VARCHAR(100) NULL,
        `message` TEXT NULL,
        `file` VARCHAR(255) NULL,
        `line` INT NULL,
        `sql_state` VARCHAR(20) NULL,
        `trace` TEXT NULL,
        `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

    if ($_SERVER['REQUEST_METHOD'] === 'GET') {
        $limit = (int)($_GET['limit'] ?? 100);
        $search = trim($_GET['search'] ?? '');
        
        $sql = "SELECT * FROM error_logs";
        $params = [];
        if ($search !== '') {
            $sql .= " WHERE message LIKE :search OR context LIKE :search OR error_type LIKE :search OR file LIKE :search";
            $params[':search'] = '%' . $search . '%';
        }
        $sql .= " ORDER BY id DESC LIMIT {$limit}";
        
        $stmt = $pdo->prepare($sql);
        $stmt->execute($params);
        $logs = $stmt->fetchAll(PDO::FETCH_ASSOC);

        // 如果 DB 筆數較少，也可以讀取本地檔案 fallback
        $fileLogPath = dirname(__DIR__) . '/logs/api_errors.log';
        $fileLogs = [];
        if (file_exists($fileLogPath)) {
            $lines = array_reverse(array_filter(file($fileLogPath)));
            foreach (array_slice($lines, 0, 50) as $idx => $line) {
                $fileLogs[] = [
                    'id' => 'file-' . ($idx + 1),
                    'context' => 'system_log_file',
                    'error_type' => 'FileLog',
                    'message' => trim($line),
                    'file' => 'api_errors.log',
                    'line' => 0,
                    'created_at' => date('Y-m-d H:i:s')
                ];
            }
        }

        // 如果沒有錯誤紀錄，加入一筆系統狀態正常的通告
        if (empty($logs) && empty($fileLogs)) {
            $logs = [
                [
                    'id' => 1,
                    'context' => 'system/health',
                    'error_type' => 'SystemInfo',
                    'message' => 'REV-ON 系統日誌監控中樞運作正常，目前尚無任何 API 拋出未處理例外或連線異常紀錄。',
                    'file' => 'api/response.php',
                    'line' => 1,
                    'sql_state' => null,
                    'created_at' => date('Y-m-d H:i:s')
                ]
            ];
        }

        sendResponse(200, 'success', '取得 Log 成功', [
            'db_logs' => $logs,
            'file_logs' => $fileLogs
        ]);

    } elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $input = json_decode(file_get_contents('php://input'), true) ?: $_POST;
        $action = $input['action'] ?? '';

        if ($action === 'log_client_error') {
            $context = trim($input['context'] ?? 'admin_client');
            $errorType = trim($input['error_type'] ?? 'ClientError');
            $message = trim($input['message'] ?? '');
            $file = trim($input['file'] ?? 'frontend');
            $line = (int)($input['line'] ?? 0);

            if ($message !== '') {
                logAdminError($context, $errorType, $message, $file, $line);
            }
            sendResponse(200, 'success', 'Client error logged');
        }

        if ($action === 'clear') {
            $pdo->exec("TRUNCATE TABLE error_logs");
            $fileLogPath = dirname(__DIR__) . '/logs/api_errors.log';
            if (file_exists($fileLogPath)) {
                @file_put_contents($fileLogPath, '');
            }
            sendResponse(200, 'success', 'Logs 已清空');
        }
    }

    sendResponse(400, 'error', '無效的請求');
} catch (Throwable $e) {
    sendError($e, 'admin/logs');
}
