<?php
/**
 * 競速紀錄管理 API
 * 路徑: /api/admin/records.php
 */
require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();
    
    // 自動備援補齊 track_telemetry_logs 的 result_id 欄位 (防止舊格式 MySQL 缺欄位)
    try {
        $pdo->exec("ALTER TABLE track_telemetry_logs ADD COLUMN result_id INT(11) DEFAULT NULL AFTER id");
    } catch (Throwable $ignored) {}

    if ($_SERVER['REQUEST_METHOD'] === 'GET') {
        $action = $_GET['action'] ?? '';
        $recordId = (int)($_GET['id'] ?? 0);

        if ($action === 'telemetry' || ($recordId > 0 && isset($_GET['telemetry']))) {
            if ($recordId <= 0) {
                sendResponse(400, 'error', '缺少紀錄 ID');
            }

            $rawJson = null;
            $recordInfo = null;

            if ($recordId >= 200000) {
                $realId = $recordId - 200000;
                $stmtRec = $pdo->prepare("SELECT cr.*, u.nickname AS user_name, c.name AS track_name FROM circuit_results cr LEFT JOIN users u ON cr.user_id = u.id LEFT JOIN circuits c ON cr.circuit_id = c.id WHERE cr.id = ? LIMIT 1");
                $stmtRec->execute([$realId]);
                $recordInfo = $stmtRec->fetch(PDO::FETCH_ASSOC);

                $stmt = $pdo->prepare("SELECT telemetry_json FROM circuit_telemetry_logs WHERE result_id = ? ORDER BY id DESC LIMIT 1");
                $stmt->execute([$realId]);
                $row = $stmt->fetch(PDO::FETCH_ASSOC);
                if ($row) $rawJson = $row['telemetry_json'];
            } else {
                $realId = $recordId >= 100000 ? ($recordId - 100000) : $recordId;

                // 1. 優先直接以主鍵 ID 查詢 track_telemetry_logs
                $stmtDirect = $pdo->prepare("SELECT ttl.*, u.nickname AS user_name, COALESCE(t.name, ct.name) AS track_name FROM track_telemetry_logs ttl LEFT JOIN users u ON ttl.user_id = u.id LEFT JOIN tracks t ON ttl.track_id = t.id LEFT JOIN custom_tracks ct ON ttl.track_id = ct.id WHERE ttl.id = ? LIMIT 1");
                $stmtDirect->execute([$realId]);
                $directRow = $stmtDirect->fetch(PDO::FETCH_ASSOC);
                if ($directRow && !empty($directRow['telemetry_json'])) {
                    $rawJson = $directRow['telemetry_json'];
                    $recordInfo = $directRow;
                }

                // 2. 若無則查詢 track_results 或 track_log 關聯
                if (!$rawJson) {
                    $stmtRec = $pdo->prepare("SELECT tr.*, u.nickname AS user_name, COALESCE(t.name, ct.name) AS track_name FROM track_results tr LEFT JOIN users u ON tr.user_id = u.id LEFT JOIN tracks t ON tr.track_id = t.id LEFT JOIN custom_tracks ct ON tr.track_id = ct.id WHERE tr.id = ? LIMIT 1");
                    $stmtRec->execute([$realId]);
                    $recordInfo = $stmtRec->fetch(PDO::FETCH_ASSOC);

                    if (!$recordInfo) {
                        $stmtRecLog = $pdo->prepare("SELECT tl.*, u.nickname AS user_name, COALESCE(t.name, ct.name) AS track_name FROM track_log tl LEFT JOIN users u ON tl.user_id = u.id LEFT JOIN tracks t ON tl.track_id = t.id LEFT JOIN custom_tracks ct ON tl.track_id = ct.id WHERE tl.id = ? LIMIT 1");
                        $stmtRecLog->execute([$realId]);
                        $recordInfo = $stmtRecLog->fetch(PDO::FETCH_ASSOC);
                    }

                    $userId = $recordInfo['user_id'] ?? 0;
                    $trackId = $recordInfo['track_id'] ?? 0;
                    $stmt = $pdo->prepare("SELECT telemetry_json FROM track_telemetry_logs WHERE result_id = ? OR (user_id = ? AND track_id = ? AND user_id > 0) ORDER BY id DESC LIMIT 1");
                    $stmt->execute([$realId, $userId, $trackId]);
                    $row = $stmt->fetch(PDO::FETCH_ASSOC);
                    if ($row) $rawJson = $row['telemetry_json'];
                }

                // 3. 若仍未找到，嘗試直接讀取最新一筆 telemetry log
                if (!$rawJson) {
                    $stmtFallback = $pdo->query("SELECT telemetry_json FROM track_telemetry_logs ORDER BY id DESC LIMIT 1");
                    $rowFallback = $stmtFallback->fetch(PDO::FETCH_ASSOC);
                    if ($rowFallback) $rawJson = $rowFallback['telemetry_json'];
                }
            }

            if (!$rawJson) {
                sendResponse(404, 'error', '找不到該單圈的 Telemetry Log 數據');
            }

            $parsed = json_decode($rawJson, true);
            sendResponse(200, 'success', '取得 Telemetry Log 成功', [
                'record_id'   => $recordId,
                'user_name'   => $recordInfo['user_name'] ?? '車手',
                'track_name'  => $recordInfo['track_name'] ?? ('賽道 #' . $recordId),
                'vehicle_type'=> strtoupper($recordInfo['vehicle_type'] ?? 'CAR'),
                'created_at'  => $recordInfo['created_at'] ?? date('Y-m-d H:i:s'),
                'raw_json'    => $rawJson,
                'parsed'      => $parsed ?: []
            ]);
        }

        $sql = "
            SELECT * FROM (
                SELECT 
                    l.id AS id,
                    l.user_id AS user_id,
                    COALESCE(u.nickname, u.real_name, u.account, '車手') AS user_name,
                    l.track_id AS track_id,
                    COALESCE(t.name, ct.name, c.name, CONCAT('賽道 #', l.track_id)) AS track_name,
                    l.time_ms AS time_ms,
                    l.vehicle_type AS vehicle_type,
                    l.created_at AS created_at,
                    IF(ttl.id IS NOT NULL, 1, 0) AS has_telemetry
                FROM track_log l
                LEFT JOIN users u ON l.user_id = u.id
                LEFT JOIN tracks t ON l.track_id = t.id
                LEFT JOIN custom_tracks ct ON l.track_id = ct.id
                LEFT JOIN circuits c ON l.track_id = c.id
                LEFT JOIN track_telemetry_logs ttl ON (l.user_id = ttl.user_id AND l.track_id = ttl.track_id AND ABS(TIMESTAMPDIFF(SECOND, l.created_at, ttl.created_at)) < 120)

                UNION ALL

                SELECT 
                    cl.id + 200000 AS id,
                    cl.user_id AS user_id,
                    COALESCE(u.nickname, u.real_name, u.account, '車手') AS user_name,
                    cl.circuit_id AS track_id,
                    COALESCE(c.name, CONCAT('賽車場 #', cl.circuit_id)) AS track_name,
                    cl.time_ms AS time_ms,
                    cl.vehicle_type AS vehicle_type,
                    cl.created_at AS created_at,
                    IF(ctl.id IS NOT NULL, 1, 0) AS has_telemetry
                FROM circuit_log cl
                LEFT JOIN users u ON cl.user_id = u.id
                LEFT JOIN circuits c ON cl.circuit_id = c.id
                LEFT JOIN circuit_telemetry_logs ctl ON (cl.user_id = ctl.result_id OR ABS(TIMESTAMPDIFF(SECOND, cl.created_at, ctl.created_at)) < 120)
            ) combined_records
            ORDER BY created_at DESC
            LIMIT 100
        ";

        $stmt = $pdo->query($sql);
        $rawRecords = $stmt->fetchAll(PDO::FETCH_ASSOC);

        $records = array_map(function($r) {
            $ms = (int)$r['time_ms'];
            $minutes = floor($ms / 60000);
            $seconds = floor(($ms % 60000) / 1000);
            $millis = $ms % 1000;
            $timeDisplay = sprintf('%02d:%02d.%03d', $minutes, $seconds, $millis);

            return [
                'id' => (int)$r['id'],
                'user_id' => (int)$r['user_id'],
                'user_name' => $r['user_name'],
                'track_id' => $r['track_name'] ?: ('#' . $r['track_id']),
                'track_name' => $r['track_name'],
                'time_ms' => $ms,
                'lap_time' => $timeDisplay,
                'lap_time_display' => $timeDisplay,
                'car_id' => strtoupper($r['vehicle_type'] ?? 'CAR'),
                'vehicle_type' => strtoupper($r['vehicle_type'] ?? 'CAR'),
                'has_telemetry' => (int)($r['has_telemetry'] ?? 0),
                'created_at' => $r['created_at']
            ];
        }, $rawRecords);

        sendResponse(200, 'success', '取得紀錄列表成功', $records);
    } elseif ($_SERVER['REQUEST_METHOD'] === 'DELETE' || $_SERVER['REQUEST_METHOD'] === 'POST') {
        $input = json_decode(file_get_contents('php://input'), true) ?: $_POST;
        $action = $input['action'] ?? '';
        
        // 支援複選批次刪除
        $recordIds = $input['record_ids'] ?? [];
        if (empty($recordIds) && !empty($input['ids']) && is_array($input['ids'])) {
            $recordIds = $input['ids'];
        }

        if (!empty($recordIds) && is_array($recordIds)) {
            foreach ($recordIds as $rId) {
                $rId = (int)$rId;
                if ($rId <= 0) continue;
                if ($rId >= 300000) {
                    $realId = $rId - 300000;
                    $pdo->prepare("DELETE FROM circuit_log WHERE id = ?")->execute([$realId]);
                } elseif ($rId >= 200000) {
                    $realId = $rId - 200000;
                    $pdo->prepare("DELETE FROM circuit_results WHERE id = ?")->execute([$realId]);
                    $pdo->prepare("DELETE FROM circuit_telemetry_logs WHERE result_id = ?")->execute([$realId]);
                } elseif ($rId >= 100000) {
                    $realId = $rId - 100000;
                    $pdo->prepare("DELETE FROM track_log WHERE id = ?")->execute([$realId]);
                } else {
                    $pdo->prepare("DELETE FROM track_results WHERE id = ?")->execute([$rId]);
                    $pdo->prepare("DELETE FROM track_telemetry_logs WHERE result_id = ?")->execute([$rId]);
                }
            }
            sendResponse(200, 'success', '已成功批次刪除選取的成績紀錄');
        }

        $recordId = (int)($_GET['id'] ?? $input['record_id'] ?? $input['id'] ?? 0);
        if ($recordId > 0 || ($action === 'delete' && $recordId > 0)) {
            if ($recordId >= 300000) {
                // 刪除 circuit_log 歷程紀錄
                $realId = $recordId - 300000;
                $stmt = $pdo->prepare("DELETE FROM circuit_log WHERE id = ?");
                $stmt->execute([$realId]);
            } elseif ($recordId >= 200000) {
                // 刪除 circuit_results 最佳成績與遙測軌跡
                $realId = $recordId - 200000;
                $stmt = $pdo->prepare("DELETE FROM circuit_results WHERE id = ?");
                $stmt->execute([$realId]);
                $stmtTel = $pdo->prepare("DELETE FROM circuit_telemetry_logs WHERE result_id = ?");
                $stmtTel->execute([$realId]);
            } elseif ($recordId >= 100000) {
                // 刪除 track_log 歷程紀錄
                $realId = $recordId - 100000;
                $stmt = $pdo->prepare("DELETE FROM track_log WHERE id = ?");
                $stmt->execute([$realId]);
            } else {
                // 刪除 track_results 最佳成績與遙測軌跡
                $stmt = $pdo->prepare("DELETE FROM track_results WHERE id = ?");
                $stmt->execute([$recordId]);
                $stmtTel = $pdo->prepare("DELETE FROM track_telemetry_logs WHERE result_id = ?");
                $stmtTel->execute([$recordId]);
            }
            sendResponse(200, 'success', '紀錄刪除成功');
        }
    }
    
    sendResponse(400, 'error', '無效的請求');
} catch (Throwable $e) {
    sendError($e, 'admin/records');
}

