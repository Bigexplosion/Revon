<?php
/**
 * REV-ON API 回應與輔助工具
 * 負責標準化 JSON 輸出與 Token 驗證
 */

require_once __DIR__ . '/config.php';

/**
 * 標準化 API 回傳 JSON
 *
 * @param int $httpCode HTTP 狀態碼
 * @param string $status 狀態 (success/error)
 * @param string|null $message 補充訊息 (通常 error 時使用)
 * @param mixed $data 回傳的資料結構
 */
function sendResponse(int $httpCode, string $status, ?string $message = null, $data = null) {
    // 為了避免 Synology Nginx 攔截 40x / 50x 錯誤並強制回傳 HTML 錯誤頁面，
    // 底層 HTTP 狀態碼一律回傳 200 OK，真正的錯誤代碼放在 JSON 的 'code' 欄位。
    http_response_code(200);
    $response = [
        'status' => $status,
        'code'   => $httpCode
    ];

    if ($message !== null) {
        $response['message'] = $message;
    }

    if ($data !== null) {
        $response['data'] = $data;
    }

    echo json_encode($response, JSON_UNESCAPED_UNICODE);
    exit;
}

/**
 * 統一錯誤回報（含完整除錯資訊）
 *
 * 所有 catch (Throwable $e) 區塊應改為呼叫此函數。
 * 回傳 JSON 包含：
 *   - message    : 給前端顯示的錯誤說明
 *   - debug.type : 例外類別名稱
 *   - debug.msg  : 原始例外訊息
 *   - debug.file : 出錯檔案路徑
 *   - debug.line : 出錯行號
 *   - debug.trace: 呼叫堆疊（精簡版，只取前 8 個 frame）
 *   - debug.sql_state : PDOException 時的 SQL State 碼
 *
 * @param Throwable   $e       捕捉到的例外
 * @param string      $context 呼叫端描述，例如 'login', 'upload_race'
 * @param int         $httpCode HTTP 語意碼 (預設 500)
 */
function sendError(Throwable $e, string $context = '', int $httpCode = 500): void {
    $traceFrames = [];
    foreach (array_slice($e->getTrace(), 0, 8) as $frame) {
        $traceFrames[] = sprintf(
            '%s(%s): %s%s%s',
            $frame['file']     ?? '[internal]',
            $frame['line']     ?? '?',
            $frame['class']    ?? '',
            $frame['type']     ?? '',
            $frame['function'] ?? '?'
        );
    }

    $debug = [
        'type'  => get_class($e),
        'msg'   => $e->getMessage(),
        'file'  => $e->getFile(),
        'line'  => $e->getLine(),
        'trace' => $traceFrames,
    ];

    // PDO 額外帶出 SQL State
    if ($e instanceof PDOException && $e->errorInfo) {
        $debug['sql_state'] = $e->errorInfo[0] ?? null;
        $debug['sql_code']  = $e->errorInfo[1] ?? null;
        $debug['sql_msg']   = $e->errorInfo[2] ?? null;
    }

    if ($context !== '') {
        $debug['context'] = $context;
    }

    $userMsg = match(true) {
        $e instanceof PDOException => '資料庫操作失敗: ' . $e->getMessage(),
        default => '伺服器錯誤: ' . $e->getMessage(),
    };

    // 同時寫入 DB 與 PHP error_log，方便 Admin 後台查詢
    try {
        $pdo = getDB();
        $stmt = $pdo->prepare("INSERT INTO error_logs (context, error_type, message, file, line, sql_state, trace, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, NOW())");
        $stmt->execute([
            $context,
            $debug['type'] ?? 'Exception',
            $debug['msg'] ?? '',
            $debug['file'] ?? '',
            (int)($debug['line'] ?? 0),
            $debug['sql_state'] ?? null,
            json_encode($traceFrames, JSON_UNESCAPED_UNICODE)
        ]);
    } catch (Throwable $eDb) {
        // Fallback: 如果 DB 表格不存在或連線失敗，寫入系統檔
        $logDir = __DIR__ . '/logs';
        if (!is_dir($logDir)) {
            @mkdir($logDir, 0777, true);
        }
        $logFile = $logDir . '/api_errors.log';
        $logLine = sprintf("[%s] [%s] [%s] %s in %s:%d\n", date('Y-m-d H:i:s'), $context, get_class($e), $e->getMessage(), $e->getFile(), $e->getLine());
        @file_put_contents($logFile, $logLine, FILE_APPEND);
    }

    error_log(sprintf(
        '[REV-ON API ERROR] context=%s type=%s msg=%s file=%s line=%d',
        $context, get_class($e), $e->getMessage(), $e->getFile(), $e->getLine()
    ));

    http_response_code(200);
    echo json_encode([
        'status'  => 'error',
        'code'    => $httpCode,
        'message' => $userMsg,
        'debug'   => $debug,
    ], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
    exit;
}

/**
 * 手動儲存特定錯誤訊息至 error_logs 資料庫與系統 File Log
 */
function logAdminError(string $context, string $errorType, string $message, ?string $file = null, int $line = 0): void {
    try {
        $pdo = getDB();
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
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        $stmt = $pdo->prepare("INSERT INTO error_logs (context, error_type, message, file, line, created_at) VALUES (?, ?, ?, ?, ?, NOW())");
        $stmt->execute([
            $context,
            $errorType,
            $message,
            $file ?? ($_SERVER['SCRIPT_NAME'] ?? 'unknown'),
            $line
        ]);
    } catch (Throwable $eDb) {
        $logDir = __DIR__ . '/logs';
        if (!is_dir($logDir)) {
            @mkdir($logDir, 0777, true);
        }
        $logFile = $logDir . '/api_errors.log';
        $logLine = sprintf("[%s] [%s] [%s] %s in %s:%d\n", date('Y-m-d H:i:s'), $context, $errorType, $message, $file ?? 'unknown', $line);
        @file_put_contents($logFile, $logLine, FILE_APPEND);
    }
}



/**
 * 驗證 Authorization: Bearer Token 並回傳用戶資料
 * 若驗證失敗，直接中斷執行並回傳 401
 *
 * @param PDO $pdo 資料庫連線實例
 * @return array 成功時回傳用戶資料陣列
 */
function verifyTokenAndGetUser(PDO $pdo): array {
    $headers = getallheaders();
    $authHeader = $headers['Authorization'] ?? $headers['authorization'] ?? '';

    if (empty($authHeader) || !preg_match('/Bearer\s+(.*)$/i', $authHeader, $matches)) {
        logAdminError('auth/verifyToken', 'AuthTokenMissing', '缺少或無效的 Authorization Token', $_SERVER['SCRIPT_NAME'] ?? '', __LINE__);
        sendResponse(401, 'error', '缺少或無效的 Authorization Token');
    }

    $token = trim($matches[1]);
    $hashedToken = hash('sha256', $token);

    $stmt = $pdo->prepare("SELECT * FROM users WHERE remember_token = ?");
    $stmt->execute([$hashedToken]);
    $user = $stmt->fetch();

    if (!$user) {
        logAdminError('auth/verifyToken', 'AuthTokenExpired', 'Token 驗證失敗或已過期，請重新登入', $_SERVER['SCRIPT_NAME'] ?? '', __LINE__);
        sendResponse(401, 'error', 'Token 驗證失敗或已過期，請重新登入');
    }

    return $user;
}

/**
 * IP Rate Limiter (防刷保護)
 * 若超過頻率限制，直接中斷執行並回傳 429
 *
 * @param PDO $pdo
 * @param string $endpoint API 識別名 (如 'login')
 * @param int $maxPerMinute 每分鐘最大請求數
 */
function enforceRateLimit(PDO $pdo, string $endpoint, int $maxPerMinute) {
    $ip = $_SERVER['REMOTE_ADDR'] ?? '127.0.0.1';
    $windowStart = date('Y-m-d H:i:00'); // 當前分鐘

    try {
        $stmt = $pdo->prepare("INSERT INTO api_rate_limit (ip, endpoint, count, window_start)
            VALUES (?, ?, 1, ?)
            ON DUPLICATE KEY UPDATE
              count = IF(window_start = ?, count + 1, 1),
              window_start = IF(window_start = ?, window_start, ?)");
        
        $stmt->execute([$ip, $endpoint, $windowStart, $windowStart, $windowStart, $windowStart]);
        
        $stmt = $pdo->prepare("SELECT count FROM api_rate_limit WHERE ip = ? AND endpoint = ?");
        $stmt->execute([$ip, $endpoint]);
        $row = $stmt->fetch();

        if ($row && $row['count'] > $maxPerMinute) {
            sendResponse(429, 'error', '請求過於頻繁，請稍後再試');
        }
    } catch (PDOException $e) {
        // 若 api_rate_limit 表不存在，暫時不阻斷執行，避免全站癱瘓
        error_log("Rate Limit Error: " . $e->getMessage());
    }
}

/**
 * 嚴格輸入參數檢測機制
 *
 * @param array $input 接收到的輸入資料
 * @param array $rules 驗證規則對照表，例：['account' => 'required|min:3', 'time_ms' => 'required|int|positive']
 */
function validateInput(array $input, array $rules) {
    foreach ($rules as $field => $ruleStr) {
        $rulesArr = explode('|', $ruleStr);
        $val = $input[$field] ?? null;

        foreach ($rulesArr as $rule) {
            if ($rule === 'required') {
                if ($val === null || (is_string($val) && trim($val) === '') || (is_array($val) && empty($val))) {
                    $errMsg = "欄位 '{$field}' 為必填且不可為空值";
                    logAdminError('validateInput', 'ValidationError', $errMsg);
                    sendResponse(400, 'error', $errMsg);
                }
            }

            if ($val !== null && $val !== '') {
                if ($rule === 'int' && !filter_var($val, FILTER_VALIDATE_INT) && $val !== 0) {
                    sendResponse(400, 'error', "欄位 '{$field}' 必須為有效整數數字");
                }
                if ($rule === 'numeric' && !is_numeric($val)) {
                    sendResponse(400, 'error', "欄位 '{$field}' 必須為有效數字");
                }
                if ($rule === 'positive' && (float)$val <= 0) {
                    sendResponse(400, 'error', "欄位 '{$field}' 數值必須大於 0");
                }
                if (strpos($rule, 'min:') === 0) {
                    $min = (int)substr($rule, 4);
                    if (is_string($val) && mb_strlen($val) < $min) {
                        sendResponse(400, 'error', "欄位 '{$field}' 長度至少需包含 {$min} 個字元");
                    }
                }
                if (strpos($rule, 'max:') === 0) {
                    $max = (int)substr($rule, 4);
                    if (is_string($val) && mb_strlen($val) > $max) {
                        sendResponse(400, 'error', "欄位 '{$field}' 長度不能超過 {$max} 個字元");
                    }
                }
                if ($rule === 'email' && !filter_var($val, FILTER_VALIDATE_EMAIL)) {
                    sendResponse(400, 'error', "欄位 '{$field}' 電子郵件格式不正確");
                }
            }
        }
    }
}

