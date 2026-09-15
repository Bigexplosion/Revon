<?php
/**
 * 上傳山路計時成績 API
 * 路徑: /api/race/upload.php
 * 方法: POST
 */

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    sendResponse(405, 'error', '不支援的請求方法');
}

try {
    $pdo = getDB();
    // 嚴格頻率限制：每分鐘最多上傳 5 次成績，防止刷分
    enforceRateLimit($pdo, 'upload_race', 5);

    $user = verifyTokenAndGetUser($pdo);

    $contentType = isset($_SERVER["CONTENT_TYPE"]) ? trim($_SERVER["CONTENT_TYPE"]) : '';
    if (strpos($contentType, 'application/json') !== false) {
        $input = json_decode(file_get_contents('php://input'), true);
    } else {
        $input = $_POST;
    }

    validateInput($input, [
        'time_ms'      => 'required|int|positive',
        'vehicle_type' => 'required'
    ]);

    $track_id = intval($input['track_id'] ?? 0);
    $track_code = trim($input['track_code'] ?? $input['trackCode'] ?? $input['track_name'] ?? $input['trackName'] ?? '');
    $is_custom = intval($input['is_custom'] ?? 0) === 1;
    $time_ms = intval($input['time_ms'] ?? $input['finish_time_ms'] ?? 0);
    $vehicle_type = strtolower(trim($input['vehicle_type'] ?? 'car'));
    $season_id = trim($input['season_id'] ?? date('Y-m'));

    // 防篡改簽章驗證 (HMAC-SHA256)
    $headers = getallheaders();
    $clientSignature = $headers['X-Revon-Signature'] ?? $headers['x-revon-signature'] ?? ($input['signature'] ?? '');
    if (empty($clientSignature)) {
        sendResponse(403, 'error', '缺少防篡改簽章 (Signature Missing)');
    }
    // 簽章邏輯: hash_hmac('sha256', "time_ms=$time_ms", API_SECRET_KEY)
    $expectedSignature = hash_hmac('sha256', "time_ms=$time_ms", API_SECRET_KEY);
    if (!hash_equals($expectedSignature, $clientSignature)) {
        sendResponse(403, 'error', '防篡改簽章無效 (Invalid Signature)');
    }

    // 自動檢測賽道類型 (雙向防護：即便 Client 端未傳送 is_custom 標籤，伺服器亦會根據 track_id 或賽道代碼自動精準對應)
    if ($track_id > 0) {
        if (!$is_custom) {
            // 1. 先確認是否屬於玩家自訂賽道 (custom_tracks)
            $chkCustom = $pdo->prepare("SELECT id FROM custom_tracks WHERE id = ? LIMIT 1");
            $chkCustom->execute([$track_id]);
            if ($chkCustom->fetch()) {
                $is_custom = true;
            } else {
                // 2. 再確認是否屬於賽車場 (circuits)
                $chkCircuit = $pdo->prepare("SELECT id FROM circuits WHERE id = ? LIMIT 1");
                $chkCircuit->execute([$track_id]);
                if ($chkCircuit->fetch()) {
                    $is_circuit = true;
                }
            }
        }
    } elseif (!empty($track_code)) {
        // 3. 若未給予 numeric track_id，透過名稱或代碼自動對應
        $stmtSearchCircuit = $pdo->prepare("SELECT id FROM circuits WHERE name = ? OR name LIKE ? LIMIT 1");
        $stmtSearchCircuit->execute([$track_code, "%{$track_code}%"]);
        $circuitRow = $stmtSearchCircuit->fetch(PDO::FETCH_ASSOC);
        if ($circuitRow) {
            $track_id = (int)$circuitRow['id'];
            $is_circuit = true;
        } else {
            $stmtSearchOfficial = $pdo->prepare("SELECT id FROM tracks WHERE name = ? OR name LIKE ? LIMIT 1");
            $stmtSearchOfficial->execute([$track_code, "%{$track_code}%"]);
            $officialRow = $stmtSearchOfficial->fetch(PDO::FETCH_ASSOC);
            if ($officialRow) {
                $track_id = (int)$officialRow['id'];
            } else {
                $stmtSearchCustom = $pdo->prepare("SELECT id FROM custom_tracks WHERE name = ? OR name LIKE ? LIMIT 1");
                $stmtSearchCustom->execute([$track_code, "%{$track_code}%"]);
                $customRow = $stmtSearchCustom->fetch(PDO::FETCH_ASSOC);
                if ($customRow) {
                    $track_id = (int)$customRow['id'];
                    $is_custom = true;
                }
            }
        }
    }

    if ($track_id <= 0) {
        $track_id = 1; // 預設值備援
    }

    // 動態檢查並確保 track_results 資料表具備 is_custom 欄位 (自動 Migration 防護)
    try {
        $pdo->exec("ALTER TABLE track_results ADD COLUMN is_custom TINYINT(1) DEFAULT 0");
    } catch (Throwable $ignored) {}

    $isCustomInt = $is_custom ? 1 : 0;

    if (!empty($is_circuit)) {
        // 1. 寫入/更新 賽車場最佳成績摘要表 circuit_results
        $checkStmt = $pdo->prepare("SELECT id, time_ms FROM circuit_results WHERE user_id = ? AND circuit_id = ? AND vehicle_type = ?");
        $checkStmt->execute([$user['id'], $track_id, $vehicle_type]);
        $best = $checkStmt->fetch(PDO::FETCH_ASSOC);

        $resultId = 0;
        if (!$best) {
            $ins = $pdo->prepare("INSERT INTO circuit_results (user_id, circuit_id, time_ms, vehicle_type, created_at) VALUES (?, ?, ?, ?, NOW())");
            $ins->execute([$user['id'], $track_id, $time_ms, $vehicle_type]);
            $resultId = $pdo->lastInsertId();
        } else {
            $resultId = $best['id'];
            // 直接覆蓋舊紀錄
            $upd = $pdo->prepare("UPDATE circuit_results SET time_ms = ?, created_at = NOW() WHERE id = ?");
            $upd->execute([$time_ms, $resultId]);
        }

        // 2. 更新或寫入賽車場歷程日誌表 circuit_log (同一玩家同一賽道僅保留最新一筆)
        $session_id = 'sess_' . time() . '_' . rand(1000, 9999);
        $chkLog = $pdo->prepare("SELECT id FROM circuit_log WHERE user_id = ? AND circuit_id = ? AND vehicle_type = ? LIMIT 1");
        $chkLog->execute([$user['id'], $track_id, $vehicle_type]);
        $oldLog = $chkLog->fetch();
        if ($oldLog) {
            $updLog = $pdo->prepare("UPDATE circuit_log SET time_ms = ?, session_id = ?, season_id = ?, created_at = NOW() WHERE id = ?");
            $updLog->execute([$time_ms, $session_id, $season_id, $oldLog['id']]);
        } else {
            $logStmt = $pdo->prepare("INSERT INTO circuit_log (user_id, circuit_id, session_id, season_id, time_ms, vehicle_type, created_at) VALUES (?, ?, ?, ?, ?, ?, NOW())");
            $logStmt->execute([$user['id'], $track_id, $session_id, $season_id, $time_ms, $vehicle_type]);
        }

        // 3. 寫入 賽車場軌跡數據表 circuit_telemetry_logs
        if (!empty($input['telemetry_json']) && $resultId > 0) {
            $telemetryJson = is_array($input['telemetry_json']) ? json_encode($input['telemetry_json']) : $input['telemetry_json'];
            try {
                $telStmt = $pdo->prepare("INSERT INTO circuit_telemetry_logs (result_id, telemetry_json, created_at) ON DUPLICATE KEY UPDATE telemetry_json = VALUES(telemetry_json), created_at = NOW()");
                $telStmt->execute([$resultId, $telemetryJson]);
            } catch (Throwable $eTel) {}
        }
    } else {
        // 統一將官方山路與自訂山路成績寫入 track_results 表
        $checkStmt = $pdo->prepare("SELECT id, time_ms FROM track_results WHERE user_id = ? AND track_id = ? AND vehicle_type = ? AND is_custom = ?");
        $checkStmt->execute([$user['id'], $track_id, $vehicle_type, $isCustomInt]);
        $best = $checkStmt->fetch(PDO::FETCH_ASSOC);

        $resultId = 0;
        if (!$best) {
            $ins = $pdo->prepare("INSERT INTO track_results (user_id, track_id, time_ms, vehicle_type, is_custom, created_at) VALUES (?, ?, ?, ?, ?, NOW())");
            $ins->execute([$user['id'], $track_id, $time_ms, $vehicle_type, $isCustomInt]);
            $resultId = $pdo->lastInsertId();
        } else {
            $resultId = $best['id'];
            // 無條件覆蓋更新個人最佳紀錄
            $upd = $pdo->prepare("UPDATE track_results SET time_ms = ?, created_at = NOW() WHERE id = ?");
            $upd->execute([$time_ms, $resultId]);
        }

        // 2. 更新或寫入完整日誌表 (track_log) - 同一玩家同一賽道同一車型直接覆蓋
        $chkTrackLog = $pdo->prepare("SELECT id FROM track_log WHERE user_id = ? AND track_id = ? AND vehicle_type = ? LIMIT 1");
        $chkTrackLog->execute([$user['id'], $track_id, $vehicle_type]);
        $oldTrackLog = $chkTrackLog->fetch();
        if ($oldTrackLog) {
            $updTrackLog = $pdo->prepare("UPDATE track_log SET time_ms = ?, season_id = ?, created_at = NOW() WHERE id = ?");
            $updTrackLog->execute([$time_ms, $season_id, $oldTrackLog['id']]);
        } else {
            $logStmt = $pdo->prepare("INSERT INTO track_log (user_id, track_id, season_id, time_ms, vehicle_type, created_at) VALUES (?, ?, ?, ?, ?, NOW())");
            $logStmt->execute([$user['id'], $track_id, $season_id, $time_ms, $vehicle_type]);
        }

        // 3. 儲存詳細 Telemetry JSON (自動相容 points / telemetry / telemetry_json / 完整大物件)
        $rawTelemetry = $input['telemetry_json'] ?? $input['points'] ?? $input['telemetry'] ?? null;
        if (empty($rawTelemetry) && (isset($input['header']) || isset($input['points']))) {
            $rawTelemetry = $input;
        }

        if (!empty($rawTelemetry) && $resultId > 0) {
            $telemetryJson = is_array($rawTelemetry) ? json_encode($rawTelemetry, JSON_UNESCAPED_UNICODE) : $rawTelemetry;
            $sessionId = 'sess_' . time() . '_' . rand(1000, 9999);

            // 檢查該 user_id, track_id, vehicle_type 在 track_telemetry_logs 是否已有舊紀錄
            $chkTel = $pdo->prepare("SELECT id FROM track_telemetry_logs WHERE result_id = ? OR (user_id = ? AND track_id = ? AND vehicle_type = ?) LIMIT 1");
            $chkTel->execute([$resultId, $user['id'], $track_id, $vehicle_type]);
            $oldTel = $chkTel->fetch(PDO::FETCH_ASSOC);

            if ($oldTel) {
                // 已有紀錄 ➔ 覆蓋寫入，不增加冗餘重複資料
                try {
                    $updTel = $pdo->prepare("UPDATE track_telemetry_logs SET result_id = ?, session_id = ?, time_ms = ?, telemetry_json = ?, created_at = NOW() WHERE id = ?");
                    $updTel->execute([$resultId, $sessionId, $time_ms, $telemetryJson, $oldTel['id']]);
                } catch (Throwable $eTelUpd) {
                    try {
                        $updTelBasic = $pdo->prepare("UPDATE track_telemetry_logs SET telemetry_json = ?, created_at = NOW() WHERE id = ?");
                        $updTelBasic->execute([$telemetryJson, $oldTel['id']]);
                    } catch (Throwable $ignored) {}
                }
            } else {
                // 無舊紀錄 ➔ 新增一筆
                try {
                    $telStmt = $pdo->prepare("
                        INSERT INTO track_telemetry_logs 
                        (result_id, session_id, user_id, track_id, is_custom, time_ms, vehicle_type, telemetry_json, created_at) 
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
                    ");
                    $telStmt->execute([
                        $resultId,
                        $sessionId,
                        $user['id'],
                        $track_id,
                        $isCustomInt,
                        $time_ms,
                        $vehicle_type,
                        $telemetryJson
                    ]);
                } catch (Throwable $eTel) {
                    try {
                        $telStmtBasic = $pdo->prepare("INSERT INTO track_telemetry_logs (result_id, telemetry_json, created_at) VALUES (?, ?, NOW())");
                        $telStmtBasic->execute([$resultId, $telemetryJson]);
                    } catch (Throwable $ignored) {}
                }
            }
        }
    }

    $minutes = floor($time_ms / 60000);
    $seconds = floor(($time_ms % 60000) / 1000);
    $millis = $time_ms % 1000;
    $timeDisplay = sprintf('%02d:%02d.%03d', $minutes, $seconds, $millis);

    sendResponse(200, 'success', '成績上傳成功', [
        'finish_time_ms' => $time_ms,
        'finish_time_display' => $timeDisplay,
        'vehicle_type' => strtoupper($vehicle_type)
    ]);

} catch (Throwable $e) {
    sendError($e, 'race/upload');
}
