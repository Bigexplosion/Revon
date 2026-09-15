<?php
/**
 * 賽道審核與管理 API (含座標與點位編輯與 SQL 全量匯入)
 * 路徑: /api/admin/tracks.php
 */
require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

// Polyfills for PHP < 8.0 compatibility (Synology NAS support)
if (!function_exists('str_starts_with')) {
    function str_starts_with($haystack, $needle) {
        return $needle === '' || strpos($haystack, $needle) === 0;
    }
}
if (!function_exists('str_ends_with')) {
    function str_ends_with($haystack, $needle) {
        return $needle === '' || (string)$needle === substr($haystack, -strlen($needle));
    }
}

try {
    $pdo = getDB();
    
    if ($_SERVER['REQUEST_METHOD'] === 'GET') {
        $sortOrder = $_GET['order'] ?? 'ASC';
        $sortOrder = strtoupper($sortOrder) === 'ASC' ? 'ASC' : 'DESC';
        $type = $_GET['type'] ?? 'official';
        $search = trim($_GET['search'] ?? '');
        
        $whereSql = "";
        $params = [];
        if ($search !== '') {
            $whereSql = " WHERE name LIKE :search OR country LIKE :search OR city LIKE :search ";
            $params[':search'] = '%' . $search . '%';
        }
        
        if ($type === 'custom') {
            $stmt = $pdo->prepare("SELECT *, 'custom' as track_source_type, created_at as updated_at FROM custom_tracks {$whereSql} ORDER BY id {$sortOrder}");
            $stmt->execute($params);
            $tracks = $stmt->fetchAll(PDO::FETCH_ASSOC);
        } elseif ($type === 'circuit') {
            $stmt = $pdo->prepare("SELECT *, 'circuit' as track_source_type, line_p1_lat as start_lat, line_p1_lng as start_lng, line_p2_lat as end_lat, line_p2_lng as end_lng, created_at, created_at as updated_at FROM circuits {$whereSql} ORDER BY id {$sortOrder}");
            $stmt->execute($params);
            $tracks = $stmt->fetchAll(PDO::FETCH_ASSOC);
        } elseif ($type === 'all') {
            $whereT = $search !== '' ? " WHERE name LIKE :s1 OR country LIKE :s2 OR city LIKE :s3 " : "";
            $whereC = $search !== '' ? " WHERE name LIKE :s4 OR country LIKE :s5 OR city LIKE :s6 " : "";
            $whereK = $search !== '' ? " WHERE name LIKE :s7 OR country LIKE :s8 OR city LIKE :s9 " : "";
            
            $sql = "
                SELECT id, name, city, country, start_lat, start_lng, end_lat, end_lng, created_at, 'official' as track_source_type FROM tracks {$whereT}
                UNION ALL
                SELECT id, name, city, country, start_lat, start_lng, end_lat, end_lng, created_at, 'custom' as track_source_type FROM custom_tracks {$whereC}
                UNION ALL
                SELECT id, name, city, country, line_p1_lat as start_lat, line_p1_lng as start_lng, line_p2_lat as end_lat, line_p2_lng as end_lng, created_at, 'circuit' as track_source_type FROM circuits {$whereK}
                ORDER BY id {$sortOrder}
            ";
            $stmt = $pdo->prepare($sql);
            if ($search !== '') {
                $searchPattern = '%' . $search . '%';
                $binds = [];
                for ($i = 1; $i <= 9; $i++) {
                    $binds[":s{$i}"] = $searchPattern;
                }
                $stmt->execute($binds);
            } else {
                $stmt->execute();
            }
            $tracks = $stmt->fetchAll(PDO::FETCH_ASSOC);
        } else {
            $stmt = $pdo->prepare("SELECT *, 'official' as track_source_type FROM tracks {$whereSql} ORDER BY id {$sortOrder}");
            $stmt->execute($params);
            $tracks = $stmt->fetchAll(PDO::FETCH_ASSOC);
        }
        sendResponse(200, 'success', '取得賽道列表成功', $tracks);

    } elseif ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $input = json_decode(file_get_contents('php://input'), true) ?: $_POST;
        $action = $input['action'] ?? '';
        $trackId = (int)($input['track_id'] ?? 0);
        $isCustom = !empty($input['is_custom']);
        $table = $isCustom ? 'custom_tracks' : 'tracks';
        
        if ($action === 'create') {
            $name = trim($input['name'] ?? '');
            if (empty($name)) {
                sendResponse(400, 'error', '賽道名稱不得為空');
            }

            $startLat = isset($input['start_lat']) && $input['start_lat'] !== '' ? round((float)$input['start_lat'], 7) : null;
            $startLng = isset($input['start_lng']) && $input['start_lng'] !== '' ? round((float)$input['start_lng'], 7) : null;
            $endLat = isset($input['end_lat']) && $input['end_lat'] !== '' ? round((float)$input['end_lat'], 7) : null;
            $endLng = isset($input['end_lng']) && $input['end_lng'] !== '' ? round((float)$input['end_lng'], 7) : null;

            $mid1Lat = isset($input['mid1_lat']) && $input['mid1_lat'] !== '' ? round((float)$input['mid1_lat'], 7) : null;
            $mid1Lng = isset($input['mid1_lng']) && $input['mid1_lng'] !== '' ? round((float)$input['mid1_lng'], 7) : null;
            $mid2Lat = isset($input['mid2_lat']) && $input['mid2_lat'] !== '' ? round((float)$input['mid2_lat'], 7) : null;
            $mid2Lng = isset($input['mid2_lng']) && $input['mid2_lng'] !== '' ? round((float)$input['mid2_lng'], 7) : null;

            $city = trim($input['city'] ?? 'Taipei');
            $country = trim($input['country'] ?? 'Taiwan');
            $difficulty = trim($input['difficulty'] ?? 'NORMAL');
            $vehicleType = trim($input['vehicle_type'] ?? 'all');
            $distanceKm = isset($input['distance_km']) ? (float)$input['distance_km'] : 2.0;
            $cornersCount = isset($input['corners_count']) ? (int)$input['corners_count'] : 10;
            $path = $input['path'] ?? null;

            if ($isCustom) {
                // 新增至 custom_tracks
                $stmt = $pdo->prepare("INSERT INTO custom_tracks (
                    name, country, city, start_lat, start_lng, end_lat, end_lng, 
                    mid1_lat, mid1_lng, mid2_lat, mid2_lng, 
                    is_hidden, is_vip_only, vehicle_type, 
                    creator_id, creator_account, status, path, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, 1, 'Admin', 'approved', ?, NOW(), NOW())");
                $stmt->execute([
                    $name, $country, $city, $startLat, $startLng, $endLat, $endLng,
                    $mid1Lat, $mid1Lng, $mid2Lat, $mid2Lng,
                    $vehicleType, $path
                ]);
            } else {
                // 新增至官方 tracks
                $stmt = $pdo->prepare("INSERT INTO tracks (
                    name, city, difficulty, distance_km, corners_count, vehicle_type,
                    start_lat, start_lng, end_lat, end_lng, 
                    mid1_lat, mid1_lng, mid2_lat, mid2_lng, 
                    is_hidden, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NOW(), NOW())");
                $stmt->execute([
                    $name, $city, $difficulty, $distanceKm, $cornersCount, $vehicleType,
                    $startLat, $startLng, $endLat, $endLng,
                    $mid1Lat, $mid1Lng, $mid2Lat, $mid2Lng
                ]);
            }

            $newId = $pdo->lastInsertId();
            sendResponse(200, 'success', '新增賽道成功！', ['id' => $newId]);

        } elseif ($action === 'delete' && $trackId > 0) {
            $stmt = $pdo->prepare("DELETE FROM {$table} WHERE id = ?");
            $stmt->execute([$trackId]);
            sendResponse(200, 'success', '賽道刪除成功');

        } elseif ($action === 'batch_delete') {
            $trackIds = $input['track_ids'] ?? [];
            if (is_array($trackIds) && count($trackIds) > 0) {
                $placeholders = implode(',', array_fill(0, count($trackIds), '?'));
                $stmt = $pdo->prepare("DELETE FROM {$table} WHERE id IN ({$placeholders})");
                $stmt->execute(array_map('intval', $trackIds));
                sendResponse(200, 'success', '已成功批次刪除 ' . count($trackIds) . ' 筆賽道');
            }
            sendResponse(400, 'error', '未選擇要刪除的賽道');

        } elseif ($action === 'update_waypoints' && $trackId > 0) {
            // 編輯座標點位 (限制小數點下7位)
            $startLat = isset($input['start_lat']) ? round((float)$input['start_lat'], 7) : null;
            $startLng = isset($input['start_lng']) ? round((float)$input['start_lng'], 7) : null;
            $endLat = isset($input['end_lat']) ? round((float)$input['end_lat'], 7) : null;
            $endLng = isset($input['end_lng']) ? round((float)$input['end_lng'], 7) : null;
            
            $mid1Lat = isset($input['mid1_lat']) && $input['mid1_lat'] !== '' ? round((float)$input['mid1_lat'], 7) : null;
            $mid1Lng = isset($input['mid1_lng']) && $input['mid1_lng'] !== '' ? round((float)$input['mid1_lng'], 7) : null;
            $mid2Lat = isset($input['mid2_lat']) && $input['mid2_lat'] !== '' ? round((float)$input['mid2_lat'], 7) : null;
            $mid2Lng = isset($input['mid2_lng']) && $input['mid2_lng'] !== '' ? round((float)$input['mid2_lng'], 7) : null;
            $path = $input['path'] ?? null;

            if ($isCustom) {
                $stmt = $pdo->prepare("UPDATE custom_tracks SET 
                    start_lat = ?, start_lng = ?, 
                    end_lat = ?, end_lng = ?, 
                    mid1_lat = ?, mid1_lng = ?, 
                    mid2_lat = ?, mid2_lng = ?, 
                    path = ?, updated_at = NOW() 
                    WHERE id = ?");
                $stmt->execute([$startLat, $startLng, $endLat, $endLng, $mid1Lat, $mid1Lng, $mid2Lat, $mid2Lng, $path, $trackId]);
            } else {
                // 官方賽道 tracks
                $stmt = $pdo->prepare("UPDATE tracks SET 
                    start_lat = ?, start_lng = ?, 
                    end_lat = ?, end_lng = ?, 
                    mid1_lat = ?, mid1_lng = ?, 
                    mid2_lat = ?, mid2_lng = ?, 
                    updated_at = NOW() 
                    WHERE id = ?");
                $stmt->execute([$startLat, $startLng, $endLat, $endLng, $mid1Lat, $mid1Lng, $mid2Lat, $mid2Lng, $trackId]);
            }

            sendResponse(200, 'success', '座標點位已成功更新');

        } elseif ($action === 'delete_waypoint' && $trackId > 0) {
            $pointKey = $input['point_key'] ?? '';
            if ($pointKey === 'mid1') {
                $stmt = $pdo->prepare("UPDATE {$table} SET mid1_lat = NULL, mid1_lng = NULL, updated_at = NOW() WHERE id = ?");
                $stmt->execute([$trackId]);
            } elseif ($pointKey === 'mid2') {
                $stmt = $pdo->prepare("UPDATE {$table} SET mid2_lat = NULL, mid2_lng = NULL, updated_at = NOW() WHERE id = ?");
                $stmt->execute([$trackId]);
            }
            sendResponse(200, 'success', "點位 {$pointKey} 已刪除");

        } elseif ($action === 'import_sql') {
            // 上傳並全量匯入 SQL 檔案 (自動將 INSERT INTO 轉換為 REPLACE INTO，並拆分每筆賽道獨立執行與記錄失敗原因)
            $sqlContent = '';
            if (isset($_FILES['sql_file']) && $_FILES['sql_file']['error'] === UPLOAD_ERR_OK) {
                $sqlContent = file_get_contents($_FILES['sql_file']['tmp_name']);
            } elseif (!empty($input['sql_content'])) {
                $sqlContent = $input['sql_content'];
            }

            if (empty($sqlContent)) {
                sendResponse(400, 'error', '未收到 SQL 檔案或檔案內容為空');
            }

            // 移除 UTF-8 BOM
            $sqlContent = preg_replace('/^\xEF\xBB\xBF/', '', $sqlContent);

            // 將 INSERT INTO 轉換為 REPLACE INTO 確保主鍵 (id) 精準留存並更新完整數據
            $modifiedSql = preg_replace('/INSERT\s+INTO\s+[`"\']?custom_tracks[`"\']?/i', 'REPLACE INTO `custom_tracks`', $sqlContent);
            $modifiedSql = preg_replace('/INSERT\s+INTO\s+[`"\']?tracks[`"\']?/i', 'REPLACE INTO `tracks`', $modifiedSql);

            // 統一換行符並過濾掉註解列 (-- 與 /* ... */)
            $lines = explode("\n", str_replace("\r\n", "\n", $modifiedSql));
            $cleanedLines = [];
            $inBlockComment = false;
            foreach ($lines as $line) {
                $trimmed = trim($line);
                if ($inBlockComment) {
                    if (strpos($trimmed, '*/') !== false) $inBlockComment = false;
                    continue;
                }
                if (strpos($trimmed, '/*') === 0) {
                    if (strpos($trimmed, '*/') === false) $inBlockComment = true;
                    continue;
                }
                if (strpos($trimmed, '--') === 0 || $trimmed === '') {
                    continue;
                }
                $cleanedLines[] = $line;
            }
            $cleanedSql = implode("\n", $cleanedLines);

            // 切割為獨立 SQL 指令 (以分號 ; 結尾)
            $rawStatements = preg_split('/;\s*$/m', $cleanedSql);

            $importedCount = 0;
            $executedQueries = 0;
            $failedList = [];

            foreach ($rawStatements as $rawStmt) {
                $rawStmt = trim($rawStmt);
                if (empty($rawStmt)) continue;

                $upper = strtoupper($rawStmt);
                $valuesPos = stripos($rawStmt, 'VALUES');

                if (strpos($upper, 'REPLACE') !== false && $valuesPos !== false) {
                    // 多筆 INSERT / REPLACE 語句：解析標頭與各個元組 (...)，獨立執行每筆紀錄
                    $header = preg_replace('/\s+/', ' ', trim(substr($rawStmt, 0, $valuesPos + 6)));
                    $valuesBody = trim(substr($rawStmt, $valuesPos + 6));

                    $i = 0;
                    $len = strlen($valuesBody);
                    while ($i < $len) {
                        while ($i < $len && $valuesBody[$i] !== '(') $i++;
                        if ($i >= $len) break;
                        $start = $i;
                        $inStr = false;
                        $strChar = '';
                        $end = -1;
                        for ($j = $start + 1; $j < $len; $j++) {
                            $c = $valuesBody[$j];
                            if ($inStr) {
                                if ($c === $strChar && ($j === 0 || $valuesBody[$j - 1] !== '\\')) {
                                    $inStr = false;
                                }
                            } else {
                                if ($c === "'" || $c === '"') {
                                    $inStr = true;
                                    $strChar = $c;
                                } elseif ($c === ')') {
                                    $end = $j;
                                    break;
                                }
                            }
                        }

                        if ($end !== -1) {
                            $tupleStr = substr($valuesBody, $start, $end - $start + 1);
                            $itemSql = $header . " " . $tupleStr . ";";
                            
                            // 解析賽道 ID 與名稱方便在 UI 上精準標註
                            $trackInfo = "SQL 賽道紀錄";
                            if (preg_match('/^\(\s*(\d+)\s*,\s*[\'\"]([^\'\"]+)[\'\"]/', $tupleStr, $infoMatch)) {
                                $trackInfo = "[ID {$infoMatch[1]} - {$infoMatch[2]}]";
                            } elseif (preg_match('/^\(\s*(\d+)/', $tupleStr, $infoMatch)) {
                                $trackInfo = "[ID {$infoMatch[1]}]";
                            }

                            $executedQueries++;
                            try {
                                $pdo->exec($itemSql);
                                $importedCount++;
                            } catch (Throwable $eExec) {
                                $failedList[] = [
                                    'statement' => $trackInfo,
                                    'error'     => $eExec->getMessage()
                                ];
                            }
                            $i = $end + 1;
                        } else {
                            $i = $start + 1;
                        }
                    }
                } else {
                    // DDL / DML 單條指令 (例如 DDL 設定、CREATE TABLE 等)
                    $itemSql = (substr($rawStmt, -1) === ';') ? $rawStmt : $rawStmt . ';';
                    $executedQueries++;
                    try {
                        $pdo->exec($itemSql);
                        if (!str_starts_with($upper, 'SET') && !str_starts_with($upper, 'START') && !str_starts_with($upper, 'COMMIT') && !str_starts_with($upper, 'CREATE') && !str_starts_with($upper, 'ALTER')) {
                            $importedCount++;
                        }
                    } catch (Throwable $eExec) {
                        $snippet = mb_substr($rawStmt, 0, 100);
                        if (mb_strlen($rawStmt) > 100) $snippet .= '...';
                        $failedList[] = [
                            'statement' => $snippet,
                            'error'     => $eExec->getMessage()
                        ];
                    }
                }
            }

            // 寫入系統日誌 Log
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
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
                $logStmt = $pdo->prepare("INSERT INTO error_logs (`context`, `error_type`, `message`, `file`, `line`) VALUES (?, ?, ?, ?, ?)");
                $logStmt->execute([
                    'admin/tracks',
                    'TrackSqlImport',
                    "SQL 賽道全量匯入完成：成功執行 {$executedQueries} 筆獨立指令，寫入/更新 {$importedCount} 筆賽道，失敗 " . count($failedList) . " 筆。",
                    'api/admin/tracks.php',
                    0
                ]);
            } catch (Throwable $logError) {}

            sendResponse(200, 'success', 'SQL 匯入完成', [
                'imported' => $importedCount,
                'executed' => $executedQueries,
                'skipped'  => count($failedList),
                'failed_list' => $failedList,
                'php_version' => PHP_VERSION
            ]);
        }
    }
    
    sendResponse(400, 'error', '無效的請求');
} catch (Throwable $e) {
    sendError($e, 'admin/tracks');
}

