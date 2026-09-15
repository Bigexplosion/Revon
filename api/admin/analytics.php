<?php
/**
 * 地區熱度與排行榜分析 API
 * 路徑: /api/admin/analytics.php
 */
require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $pdo = getDB();

    $timeframe = $_GET['timeframe'] ?? '7days';

    // 模擬或真實聚合數據
    $regionHeat = [
        ['region' => '台北市', 'count' => 1240, 'level' => 5],
        ['region' => '新北市', 'count' => 980, 'level' => 4],
        ['region' => '台中市', 'count' => 850, 'level' => 4],
        ['region' => '高雄市', 'count' => 1100, 'level' => 5],
        ['region' => '台南市', 'count' => 620, 'level' => 3],
        ['region' => '桃園市', 'count' => 740, 'level' => 3],
        ['region' => '新竹市', 'count' => 410, 'level' => 2],
        ['region' => '宜蘭縣', 'count' => 300, 'level' => 2]
    ];

    $topPlayersByRegion = [
        '台北市' => [
            ['rank' => 1, 'nickname' => 'TaipeiDrifter', 'plays' => 320],
            ['rank' => 2, 'nickname' => 'NeoRacer', 'plays' => 280],
            ['rank' => 3, 'nickname' => 'SpeedyG', 'plays' => 210]
        ],
        '高雄市' => [
            ['rank' => 1, 'nickname' => 'SouthernKing', 'plays' => 410],
            ['rank' => 2, 'nickname' => 'KaohsiungV', 'plays' => 350],
            ['rank' => 3, 'nickname' => 'BayRacer', 'plays' => 290]
        ]
    ];

    $topTracks = [
        ['name' => '大嶺山道 Touge #1', 'total_plays' => 1890, 'top_players' => [
            ['rank' => 1, 'name' => 'RacerX', 'time' => '01:12.450'],
            ['rank' => 2, 'name' => 'GhostDrift', 'time' => '01:13.120'],
            ['rank' => 3, 'name' => 'ApexHunter', 'time' => '01:13.880']
        ]],
        ['name' => '麗寶競速場 Circuit', 'total_plays' => 1420, 'top_players' => [
            ['rank' => 1, 'name' => 'TrackMaster', 'time' => '01:45.300'],
            ['rank' => 2, 'name' => 'ProRacer', 'time' => '01:46.100'],
            ['rank' => 3, 'name' => 'SpeedDemon', 'time' => '01:46.850']
        ]]
    ];

    sendResponse(200, 'success', '取得地區熱度與排行榜分析成功', [
        'timeframe' => $timeframe,
        'region_heat' => $regionHeat,
        'top_players_region' => $topPlayersByRegion,
        'top_tracks' => $topTracks
    ]);

} catch (Throwable $e) {
    sendError($e, 'admin/analytics');
}
