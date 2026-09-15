import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';

const dict = {
  en: {
    routes: 'ROUTES', ranking: 'RANKING', club: 'CLUB', profile: 'PROFILE',
    drivers_online: 'drivers online', tap_details: 'Tap to view details', race: 'RACE',
    custom_routes: 'Custom Routes', add_custom: 'Add Custom Route', custom: 'Custom', delete: 'Delete',
    region: 'Region', normal: 'NORMAL', hard: 'HARD', extreme: 'EXTREME',
    choose_vehicle: 'CHOOSE VEHICLE', car: 'CAR', motor: 'MOTOR', other: 'OTHER', start_engine: 'START ENGINE',
    important_notice: 'IMPORTANT NOTICE', keep_screen: 'Please keep your screen awake', do_not_leave: 'Do not leave this page', or_timer_fail: 'or the timer will fail!',
    to_start_point: 'To Start Point', return_location: 'Return to Current Location', stop: 'Stop', navigate_start: 'Navigate to Start',
    tut_step1: 'Tap "Navigate to Start" to plan a route to the start point.', tut_step2: 'Tap "Return to Current Location" to re-center the map anytime.', tut_step3: 'Check the GPS indicator — green = good, yellow = moderate, red = weak.',
    tut_done: 'Tutorial complete, enjoy your race!', signal_good: 'signal good, race can begin', signal_moderate: 'moderate signal, move to open ground', signal_weak: 'weak signal, please wait',
    race_in_progress: 'RACE IN PROGRESS', distance_finish: 'TO FINISH', speed: 'SPEED', end_race: 'End Race', confirm_end: 'End the race now?', distance_start: 'TO START', ready_prompt: 'READY — ENTER START POINT TO BEGIN TIMING', begin_timing: 'BEGIN TIMING', checkpoint: 'CHECKPOINT', cleared: 'CLEARED', of: 'OF', final_stretch: 'FINAL STRETCH — FINISH AHEAD', missed_cp: 'MISSED CHECKPOINT', run_invalid: 'RUN INVALID', stop_race: 'STOP RACE', confirm_stop: 'CONFIRM STOP', confirm_stop_body: 'Aborting will discard this run and return to Routes.', abort_run: 'ABORT RUN', keep_racing: 'KEEP RACING', sim_mode: 'SIM', telemetry: 'TELEMETRY', track_info: 'TRACK', segment: 'SEGMENT', splits: 'SPLITS', faster: 'FASTER', first_run_track: 'FIRST RUN ON THIS TRACK', new_pb: 'NEW PERSONAL BEST', rank_up: 'UP', rank_down: 'DOWN', no_change: 'NO CHANGE', gps_label: 'GPS', event_position: 'EVENT POSITION', pending_reward: 'PENDING REWARD', pending_note: 'Awarded when the event ends and is settled.', no_pending: 'NO REWARD FOR THIS PLACEMENT',
    results: 'RESULTS', your_time: 'Your Time', personal_best: 'Personal Best', improved: 'NEW BEST!', slower: 'slower', rank_change: 'Rank Change', share: 'Share', view_leaderboard: 'View Leaderboard', race_again: 'Race Again', back_routes: 'Back to Routes',
    hall_of_fame: 'HALL OF FAME', all_time: 'ALL TIME', this_month: 'THIS MONTH', clubs_filter: 'CLUBS', your_rank: 'Your Rank', view_my_results: 'View My Results',
    clubs_title: 'CLUBS', find_your_crew: 'FIND YOUR CREW', search_clubs: 'Search clubs', members: 'MEMBERS', request_join: 'REQUEST TO JOIN', skip: 'SKIP',
    welcome_back: 'WELCOME BACK', r_points: 'R POINTS', subscribed: 'SUBSCRIBED', cancel_renewal: 'CANCEL RENEWAL', my_racing: 'MY RACING', total_runs: 'TOTAL RUNS', best_rank: 'BEST RANK', fav_route: 'FAV ROUTE', my_results: 'MY RESULTS',
    rewards: 'REWARDS', r_points_balance: 'R POINTS BALANCE', promo_code: 'Promo code', redeem: 'REDEEM', transaction_history: 'Transaction History', rewards_catalog: 'REWARDS CATALOG', my_vouchers: 'MY VOUCHERS', out_of_stock: 'OUT OF STOCK', stock_left_unit: 'LEFT', need_more: 'NEED', confirm_redeem: 'CONFIRM REDEMPTION', balance_before: 'BALANCE BEFORE', balance_after: 'BALANCE AFTER', redeem_success: 'REDEMPTION COMPLETE', voucher_code: 'VOUCHER CODE', copy: 'COPY', copied: 'COPIED', voucher_instructions: 'Present this code to your regional distributor to collect your item in person.', voucher_unused: 'UNUSED', voucher_redeemed: 'REDEEMED', no_rewards: 'No rewards available in your region', no_vouchers: 'No vouchers yet',
    account: 'ACCOUNT', edit_profile: 'Edit Profile', change_password: 'Change Password', nickname: 'Nickname', avatar: 'Avatar', email: 'Email',
    my_region: 'MY REGION', country: 'Country', city: 'City', app_settings: 'APP SETTINGS', language: 'Language', gps_tutorial: 'GPS Tutorial Mode',
    support_legal: 'SUPPORT & LEGAL', privacy_policy: 'Privacy Policy', terms_of_service: 'Terms of Service', refund_policy: 'Return & Refund Policy', contact_support: 'Contact Support',
    support_text: 'Our IG has dedicated support staff — feel free to DM us with any questions and we will reply as soon as possible.', go_ig: 'Go to Instagram DM', close: 'Close', log_out: 'Log Out',
    agree_terms: 'I agree to the Terms of Service and Privacy Policy', continue: 'Continue', must_agree: 'You must agree to continue',
    traditional_chinese: '繁體中文', english: 'ENGLISH', save: 'Save', cancel: 'Cancel', back: 'Back',
    no_results: 'No results yet', no_clubs: 'No clubs found', no_club_title: 'No club yet', no_club_body: "You haven't joined a club yet. Browse the ranking and request to join one.", browse_clubs: 'BROWSE CLUBS', waypoints: 'Waypoints', save_route: 'Save Route', route_name: 'Route Name', confirm: 'Confirm', yes: 'Yes', no: 'No', no_times_yet: 'No times yet',
    my_club: 'MY CLUB', enter: 'ENTER', discover: 'DISCOVER', club_ranking: 'CLUB RANKING', regional: 'REGIONAL', national: 'NATIONAL', dominant: 'DOMINANT', club_points: 'POINTS', club_level: 'LEVEL', strongest_route: 'STRONGEST',
    announcement: 'ANNOUNCEMENT', pinned_by_captain: 'Pinned by Captain', club_chat: 'CLUB CHAT', type_message: 'Type a message…', send: 'SEND', weekly_ranking: 'WEEKLY RANKING', most_active: 'MOST ACTIVE THIS WEEK', runs: 'runs', runs_unit: 'runs',
    club_routes: 'CLUB ROUTES', no_club_routes: 'No custom routes yet', race_report_just_ran: 'just ran', race_report_in_club: 'in the club', back_to_clubs: 'Back to Clubs', members_only: 'Members only', captain: 'CAPTAIN', member: 'MEMBER', no_announcement: 'No announcement pinned', edit_announcement: 'Edit Announcement', pin_announcement: 'Pin Announcement', no_messages: 'No messages yet', this_week: 'THIS WEEK', rank: 'RANK', no_weekly_data: 'No runs this week yet',
    leaderboard_tab: 'LEADERBOARD', events_tab: 'EVENTS', upcoming: 'UPCOMING', live: 'LIVE', ended: 'ENDED', enter_event: 'ENTER EVENT', entry_free: 'FREE', r_points_short: 'R POINTS', reward_pool: 'REWARD POOL', per_placement: 'PER PLACEMENT', redeemable_distributor: 'Redeemable with your regional distributor.', event_rules: 'RULES', your_best: 'YOUR BEST', event_leaderboard: 'EVENT LEADERBOARD', participants: 'PARTICIPANTS', starts_in: 'STARTS IN', ends_in: 'ENDS IN', view_results: 'VIEW RESULTS', insufficient_points: 'Not enough R Points', entered: 'ENTERED', trophy_case: 'TROPHY CASE', select_title: 'SELECT TITLE', no_title: 'None', season_label: 'SEASON', season_ends_in: 'SEASON ENDS IN', season_history: 'SEASON HISTORY', club_events: 'CLUB EVENTS', create_club_event: 'CREATE EVENT', ev_name: 'Event Name', ev_start: 'Start', ev_end: 'End', honor_title: 'Honor Title', internal_event: 'INTERNAL EVENT', event_finalized: 'Event finalized — standings locked', won_event: 'WINS', points_earned: 'R POINTS EARNED', entry_cost_label: 'ENTRY', no_events: 'No events right now', event_detail: 'EVENT DETAILS', final_standings: 'FINAL STANDINGS', admin_role: 'ADMIN', dev_role: 'DEV ROLE', dev_role_hint: 'Tap to switch permission preview', place_1: '1ST PLACE', place_2: '2ND PLACE', place_3: '3RD PLACE', rewards_per_place: 'REWARDS PER PLACE', edit_banner: 'BANNER'
  },
  zh: {
    routes: '路線', ranking: '排名', club: '車隊', profile: '個人',
    drivers_online: '位車手在線', tap_details: '點擊查看詳情', race: '競速',
    custom_routes: '自訂路線', add_custom: '新增自訂路線', custom: '自訂', delete: '刪除',
    region: '區域', normal: '普通', hard: '困難', extreme: '極限',
    choose_vehicle: '選擇車輛', car: '汽車', motor: '重機', other: '其他', start_engine: '啟動引擎',
    important_notice: '重要提醒', keep_screen: '請保持螢幕常亮', do_not_leave: '請勿離開此頁面', or_timer_fail: '否則計時將失敗！',
    to_start_point: '前往起點', return_location: '回到目前位置', stop: '停止', navigate_start: '導航至起點',
    tut_step1: '點擊「導航至起點」，系統將規劃前往起點的路線。', tut_step2: '點擊「回到目前位置」可隨時將地圖置中於你的位置。', tut_step3: '確認 GPS 訊號狀態 — 綠=良好、黃=普通、紅=微弱。',
    tut_done: '教學完成，祝競速愉快！', signal_good: '訊號良好，可開始競速', signal_moderate: '訊號普通，建議移至空曠處', signal_weak: '訊號微弱，請稍候',
    race_in_progress: '競速進行中', distance_finish: '至終點', speed: '時速', end_race: '結束競速', confirm_end: '立即結束競速？', distance_start: '至起點', ready_prompt: '準備 — 進入起點即可開始計時', begin_timing: '開始計時', checkpoint: '檢查點', cleared: '通過', of: 'OF', final_stretch: '最後衝刺 — 終點在前', missed_cp: '未通過檢查點', run_invalid: '成績無效', stop_race: '停止競速', confirm_stop: '確認停止', confirm_stop_body: '中止將放棄此次成績並返回路線頁。', abort_run: '放棄競速', keep_racing: '繼續競速', sim_mode: '模擬', telemetry: '遙測', track_info: '路線', segment: '區段', splits: '分段', faster: '更快', first_run_track: '此路線首次成績', new_pb: '個人新最佳', rank_up: '上升', rank_down: '下降', no_change: '不變', gps_label: 'GPS', event_position: '賽事排名', pending_reward: '待結算獎勵', pending_note: '賽事結束並結算後發放。', no_pending: '此名次無獎勵',
    results: '成績', your_time: '你的時間', personal_best: '個人最佳', improved: '新紀錄！', slower: '較慢', rank_change: '排名變化', share: '分享', view_leaderboard: '查看排行榜', race_again: '再戰一次', back_routes: '返回路線',
    hall_of_fame: '名人堂', all_time: '全部', this_month: '本月', clubs_filter: '車隊', your_rank: '你的排名', view_my_results: '查看我的成績',
    clubs_title: '車隊', find_your_crew: '尋找你的夥伴', search_clubs: '搜尋車隊', members: '會員', request_join: '申請加入', skip: '略過',
    welcome_back: '歡迎回來', r_points: 'R 點數', subscribed: '已訂閱', cancel_renewal: '取消續訂', my_racing: '我的競速', total_runs: '總場次', best_rank: '最佳排名', fav_route: '最愛路線', my_results: '我的成績',
    rewards: '獎勵', r_points_balance: 'R 點數餘額', promo_code: '促銷代碼', redeem: '兌換', transaction_history: '交易紀錄', rewards_catalog: '獎勵商品', my_vouchers: '我的兌換券', out_of_stock: '已售完', stock_left_unit: '件', need_more: '需', confirm_redeem: '確認兌換', balance_before: '兌換前餘額', balance_after: '兌換後餘額', redeem_success: '兌換成功', voucher_code: '兌換券代碼', copy: '複製', copied: '已複製', voucher_instructions: '出示此代碼給您的區域經銷商以親自領取商品。', voucher_unused: '未使用', voucher_redeemed: '已兌換', no_rewards: '您的區域目前無獎勵', no_vouchers: '尚無兌換券',
    account: '帳號', edit_profile: '編輯個人資料', change_password: '修改密碼', nickname: '暱稱', avatar: '頭像', email: '電子郵件',
    my_region: '我的區域', country: '國家', city: '城市', app_settings: '應用設定', language: '語言', gps_tutorial: 'GPS 教學模式',
    support_legal: '支援與法律', privacy_policy: '隱私權政策', terms_of_service: '服務條款', refund_policy: '退換貨政策', contact_support: '聯絡客服',
    support_text: '我們的 IG 有專屬客服人員 — 歡迎私訊任何問題，我們將儘速回覆。', go_ig: '前往 IG 私訊', close: '關閉', log_out: '登出',
    agree_terms: '我同意服務條款與隱私權政策', continue: '繼續', must_agree: '請先同意條款才能繼續',
    traditional_chinese: '繁體中文', english: 'ENGLISH', save: '儲存', cancel: '取消', back: '返回',
    no_results: '尚無成績', no_clubs: '找不到車隊', no_club_title: '尚未加入車隊', no_club_body: '你還沒加入任何車隊。瀏覽排行榜並申請加入一個車隊吧。', browse_clubs: '瀏覽車隊', waypoints: '路徑點', save_route: '儲存路線', route_name: '路線名稱', confirm: '確認', yes: '是', no: '否', no_times_yet: '尚無成績',
    my_club: '我的車隊', enter: '進入', discover: '探索', club_ranking: '車隊排名', regional: '區域', national: '全國', dominant: '霸主', club_points: '積分', club_level: '等級', strongest_route: '最強',
    announcement: '公告', pinned_by_captain: '隊長置頂', club_chat: '車隊聊天', type_message: '輸入訊息…', send: '送出', weekly_ranking: '本週排名', most_active: '本週最活躍', runs: '場', runs_unit: '場',
    club_routes: '車隊路線', no_club_routes: '尚無自訂路線', race_report_just_ran: '剛完成', race_report_in_club: '車隊內', back_to_clubs: '返回車隊', members_only: '僅限會員', captain: '隊長', member: '會員', no_announcement: '尚無置頂公告', edit_announcement: '編輯公告', pin_announcement: '置頂公告', no_messages: '尚無訊息', this_week: '本週', rank: '排名', no_weekly_data: '本週尚無成績',
    leaderboard_tab: '排行榜', events_tab: '賽事', upcoming: '即將開始', live: '進行中', ended: '已結束', enter_event: '參加賽事', entry_free: '免費', r_points_short: 'R 點數', reward_pool: '獎池', per_placement: '名次獎勵', redeemable_distributor: '可至區域經銷商兌換。', event_rules: '規則', your_best: '你的最佳', event_leaderboard: '賽事排行榜', participants: '參賽人數', starts_in: '倒數開始', ends_in: '剩餘時間', view_results: '查看成績', insufficient_points: 'R 點數不足', entered: '已報名', trophy_case: '獎盃櫃', select_title: '選擇頭銜', no_title: '無', season_label: '賽季', season_ends_in: '賽季剩餘', season_history: '賽季紀錄', club_events: '車隊賽事', create_club_event: '建立賽事', ev_name: '賽事名稱', ev_start: '開始', ev_end: '結束', honor_title: '榮譽頭銜', internal_event: '車隊內部賽事', event_finalized: '賽事已結算 — 成績已鎖定', won_event: '奪冠', points_earned: '獲得 R 點數', entry_cost_label: '報名費', no_events: '目前無賽事', event_detail: '賽事詳情', final_standings: '最終排名', admin_role: '管理員', dev_role: '開發模式', dev_role_hint: '點擊切換權限預覽', place_1: '第一名', place_2: '第二名', place_3: '第三名', rewards_per_place: '各名次獎勵', edit_banner: '橫幅'
  }
};

const LanguageContext = createContext({ lang: 'en', setLang: () => {}, t: (k) => k });

export function LanguageProvider({ children }) {
  const [lang, setLangState] = useState(() => localStorage.getItem('revon_lang') || 'en');
  const setLang = useCallback((l) => { setLangState(l); localStorage.setItem('revon_lang', l); }, []);
  useEffect(() => { localStorage.setItem('revon_lang', lang); }, [lang]);
  const t = useCallback((k) => (dict[lang] && dict[lang][k]) || dict.en[k] || k, [lang]);
  return <LanguageContext.Provider value={{ lang, setLang, t }}>{children}</LanguageContext.Provider>;
}

export const useT = () => useContext(LanguageContext);