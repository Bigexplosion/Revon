<?php
/**
 * App 版本與系統配置 API
 * 路徑: /api/system/config.php
 * 方法: GET 或 POST
 */

require_once dirname(__DIR__) . '/config.php';
require_once dirname(__DIR__) . '/response.php';

try {
    $configData = [
        'min_app_version' => '1.0.0',
        'latest_app_version' => '1.2.0',
        'force_update' => false,
        'announcements' => [
            [
                'id' => 1,
                'title' => '🏁 REV-ON 系統維護完成公告',
                'content' => '歡迎體驗全新登場的山路與賽道圈速計時功能！',
                'created_at' => date('Y-m-d H:i:s')
            ]
        ],
        'banners' => [
            'banner_1.jpg',
            'banner_2.jpg'
        ]
    ];

    sendResponse(200, 'success', '取得系統配置成功', $configData);

} catch (Throwable $e) {
    sendResponse(500, 'error', '伺服器錯誤: ' . $e->getMessage() . ' in ' . $e->getFile() . ' on line ' . $e->getLine());
}
