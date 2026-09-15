<?php
/**
 * REV-ON API 核心設定檔
 * 負責全域設定與資料庫連線
 */

// 強制關閉 PHP 錯誤輸出到網頁（生產環境安全設定）
// 開發階段可改為 1 方便除錯
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);

// 設定預設時區
date_default_timezone_set('Asia/Taipei');

// 防篡改金鑰 (HMAC Signature Secret)
define('API_SECRET_KEY', '8f3b2a5d9c1e4f7a8b6c3d2e1f4a9b5c2d3e7f8a1b4c9d6e5f2a3b4c1d9e8f7a');

// 全域 CORS 跨域設定 (讓開發階段 HTML/JS 也能呼叫)
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type, Authorization");
header("Content-Type: application/json; charset=UTF-8");

// 處理 OPTIONS 預檢請求
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

// =====================================================
// 全域例外 / 錯誤攔截器
// 確保任何未被 try/catch 捕獲的錯誤都以 JSON 回傳，
// 而非讓 Nginx 輸出 HTML 錯誤頁面。
// =====================================================
set_exception_handler(function (Throwable $e) {
    http_response_code(200);
    $traceFrames = [];
    foreach (array_slice($e->getTrace(), 0, 8) as $frame) {
        $traceFrames[] = sprintf('%s(%s): %s%s%s',
            $frame['file'] ?? '[internal]', $frame['line'] ?? '?',
            $frame['class'] ?? '', $frame['type'] ?? '', $frame['function'] ?? '?');
    }
    $debug = [
        'type'  => get_class($e),
        'msg'   => $e->getMessage(),
        'file'  => $e->getFile(),
        'line'  => $e->getLine(),
        'trace' => $traceFrames,
    ];
    if ($e instanceof PDOException && $e->errorInfo) {
        $debug['sql_state'] = $e->errorInfo[0] ?? null;
        $debug['sql_code']  = $e->errorInfo[1] ?? null;
        $debug['sql_msg']   = $e->errorInfo[2] ?? null;
    }
    error_log('[REV-ON UNCAUGHT] ' . get_class($e) . ': ' . $e->getMessage() . ' in ' . $e->getFile() . ':' . $e->getLine());
    echo json_encode([
        'status'  => 'error',
        'code'    => 500,
        'message' => '未捕獲例外: ' . $e->getMessage(),
        'debug'   => $debug,
    ], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
    exit;
});

set_error_handler(function (int $errno, string $errstr, string $errfile, int $errline) {
    // 只攔截 E_ERROR, E_WARNING, E_PARSE, E_NOTICE 等，排除 @抑制
    if (!(error_reporting() & $errno)) return false;
    $levelMap = [E_ERROR => 'E_ERROR', E_WARNING => 'E_WARNING', E_NOTICE => 'E_NOTICE',
                 E_PARSE => 'E_PARSE', E_USER_ERROR => 'E_USER_ERROR',
                 E_USER_WARNING => 'E_USER_WARNING', E_USER_NOTICE => 'E_USER_NOTICE',
                 E_DEPRECATED => 'E_DEPRECATED'];
    $level = $levelMap[$errno] ?? "E_{$errno}";
    error_log("[REV-ON PHP ERROR] [{$level}] {$errstr} in {$errfile}:{$errline}");
    // 只對嚴重錯誤中斷並輸出 JSON；非嚴重錯誤僅記錄不中斷
    if (in_array($errno, [E_ERROR, E_PARSE, E_USER_ERROR], true)) {
        http_response_code(200);
        echo json_encode([
            'status'  => 'error',
            'code'    => 500,
            'message' => "PHP 錯誤 [{$level}]: {$errstr}",
            'debug'   => ['type' => $level, 'msg' => $errstr, 'file' => $errfile, 'line' => $errline],
        ], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
        exit;
    }
    return true; // 繼續 PHP 預設行為
});

register_shutdown_function(function () {
    $err = error_get_last();
    if ($err && in_array($err['type'], [E_ERROR, E_PARSE, E_CORE_ERROR, E_COMPILE_ERROR], true)) {
        // 確保 header 已設定
        if (!headers_sent()) {
            http_response_code(200);
            header("Content-Type: application/json; charset=UTF-8");
        }
        echo json_encode([
            'status'  => 'error',
            'code'    => 500,
            'message' => 'PHP Fatal Error: ' . $err['message'],
            'debug'   => ['type' => 'FATAL', 'msg' => $err['message'], 'file' => $err['file'], 'line' => $err['line']],
        ], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
    }
});


// 資料庫連線常數 (與 index.php 保持完全一致)
define('DB_HOST', 'revon88.synology.me');
define('DB_PORT', 3306);
define('DB_NAME', 'revon_android');
define('DB_USER', 'yelux_user');
define('DB_PASS', 'Yelux_2026!App');
define('DB_CHARSET', 'utf8mb4');

/**
 * 取得 PDO 資料庫連線實例 (Singleton Pattern)
 *
 * @return PDO
 */
function getDB(): PDO {
    static $pdo = null;
    if ($pdo === null) {
        $test_targets = [
            "mysql:host=revon88.synology.me;port=" . DB_PORT . ";dbname=" . DB_NAME . ";charset=" . DB_CHARSET,
            "mysql:host=127.0.0.1;port=" . DB_PORT . ";dbname=" . DB_NAME . ";charset=" . DB_CHARSET,
            "mysql:host=localhost;port=" . DB_PORT . ";dbname=" . DB_NAME . ";charset=" . DB_CHARSET,
            "mysql:unix_socket=/run/mysqld/mysqld10.sock;dbname=" . DB_NAME . ";charset=" . DB_CHARSET,
        ];

        $lastError = "";
        foreach ($test_targets as $dsn) {
            try {
                $pdo = new PDO($dsn, DB_USER, DB_PASS, [
                    PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
                    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
                    PDO::ATTR_EMULATE_PREPARES   => false,
                    PDO::ATTR_TIMEOUT            => 2, // 縮短每個的 timeout 避免卡太久
                ]);
                try {
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
                } catch (Throwable $eTbl) {}
                break; // 連線成功就跳出迴圈
            } catch (PDOException $e) {
                $lastError = $e->getMessage();
                $pdo = null; // 清空以嘗試下一個
            }
        }

        if ($pdo === null) {
            // 所有方法都失敗
            http_response_code(200);
            echo json_encode([
                'status'  => 'error',
                'message' => '所有連線方法皆失敗: ' . $lastError,
                'code'    => 500
            ], JSON_UNESCAPED_UNICODE);
            exit;
        }

        // 自動補全相容性欄位 (如 country 及 clubs 表)
        ensureUserColumnsExist($pdo);
        ensureClubsTableExists($pdo);
    }
    return $pdo;
}

function ensureUserColumnsExist(PDO $pdo) {
    static $checked = false;
    if ($checked) return;
    $checked = true;
    try {
        $stmt = $pdo->query("SHOW COLUMNS FROM users LIKE 'country'");
        if ($stmt->rowCount() === 0) {
            $pdo->exec("ALTER TABLE users ADD COLUMN country VARCHAR(100) DEFAULT 'Taiwan' AFTER city");
        }
    } catch (Throwable $e) {}
}

function ensureClubsTableExists(PDO $pdo) {
    static $checked = false;
    if ($checked) return;
    $checked = true;
    try {
        $stmt = $pdo->query("SHOW TABLES LIKE 'clubs'");
        if ($stmt->rowCount() === 0) {
            $pdo->exec("CREATE TABLE IF NOT EXISTS `clubs` (
              `id` INT AUTO_INCREMENT PRIMARY KEY,
              `name` VARCHAR(100) NOT NULL,
              `badge_letters` VARCHAR(10) DEFAULT 'RV',
              `motto` TEXT,
              `member_count` INT DEFAULT 1,
              `max_members` INT DEFAULT 50,
              `region` VARCHAR(100) DEFAULT 'Taiwan',
              `captain_nickname` VARCHAR(100) DEFAULT 'REVON_ADMIN',
              `total_points` INT DEFAULT 0,
              `level` INT DEFAULT 1,
              `accent_color` VARCHAR(20) DEFAULT '#E10600',
              `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
              `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            $pdo->exec("INSERT INTO `clubs` (`name`, `badge_letters`, `motto`, `member_count`, `max_members`, `region`, `captain_nickname`, `total_points`, `level`, `accent_color`) VALUES
            ('REV-ON 官方車隊', 'RO', '歡迎全台山路與賽道熱血車友加入！', 42, 50, 'Taiwan', 'REVON_ADMIN', 9999, 5, '#E10600'),
            ('南區極速狂飆俱樂部', 'NS', '南部熱血車友交流車隊', 18, 30, 'Kaohsiung', 'KaohsiungRacer', 5200, 3, '#00E5FF')");
        }
    } catch (Throwable $e) {}
}
