// =============================================
// Pull-Down Notification System
// =============================================
const NOTIFY_ICONS = {
    success: '<i class="fa-solid fa-circle-check"></i>',
    error:   '<i class="fa-solid fa-circle-xmark"></i>',
    warning: '<i class="fa-solid fa-triangle-exclamation"></i>',
    info:    '<i class="fa-solid fa-circle-info"></i>'
};

function showNotify(type, title, text = '', duration = 3500) {
    const container = document.getElementById('revon-notify-container');
    if (!container) return;

    const el = document.createElement('div');
    el.className = `revon-notify ${type}`;
    el.innerHTML = `
        <div class="revon-notify-inner">
            <div class="revon-notify-icon">${NOTIFY_ICONS[type] || NOTIFY_ICONS.info}</div>
            <div class="revon-notify-body">
                <div class="revon-notify-title">${title}</div>
                ${text ? `<div class="revon-notify-text">${text}</div>` : ''}
            </div>
            <button class="revon-notify-close" onclick="this.closest('.revon-notify').remove()">&times;</button>
        </div>
        <div class="revon-notify-progress" style="animation-duration: ${duration}ms"></div>
    `;

    container.appendChild(el);

    const dismiss = () => {
        if (!el.parentNode) return;
        el.classList.add('hiding');
        el.addEventListener('animationend', () => el.remove(), { once: true });
    };

    const timer = setTimeout(dismiss, duration);
    el.querySelector('.revon-notify-close').addEventListener('click', () => clearTimeout(timer));
}

// 全域 HTML escape 輔助函式
window.escapeHtml = function(str) {
    return (str || '').toString().replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\"/g, '&quot;').replace(/'/g, '&#039;');
};

// 全域刪除紀錄函數 (防 ReferenceError)
window.deleteRecord = async function(id) {
    try {
        const res = await Api.deleteRecord(id);
        if (res.status === 'success') {
            showNotify('success', '成績紀錄已刪除');
            if (typeof window.loadRecordsView === 'function') {
                window.loadRecordsView();
            } else {
                location.reload();
            }
        } else {
            showNotify('error', res.message || '刪除失敗');
        }
    } catch (e) {
        console.error('刪除失敗:', e);
        showNotify('error', '刪除失敗', e.message || '');
    }
};

window.loadRecordsView = function() {
    if (typeof window.triggerLoadRecordsView === 'function') {
        window.triggerLoadRecordsView();
    }
};

document.addEventListener("DOMContentLoaded", () => {
    // UI Elements
    const loginOverlay = document.getElementById('loginOverlay');
    const loginForm = document.getElementById('loginForm');
    const logoutBtn = document.getElementById('logoutBtn');
    const sidebarCollapse = document.getElementById('sidebarCollapse');
    const sidebar = document.getElementById('sidebar');
    const content = document.getElementById('content');

    const navItems = document.querySelectorAll('.sidebar ul li[data-view]');
    const views = document.querySelectorAll('.view-section');

    // Global helper: navigate to a view from anywhere (e.g., quick-action buttons)
    window.loadViewById = function(viewId) {
        if (viewId) {
            try { localStorage.setItem('revon_admin_active_tab', viewId); } catch(e){}
        }
        navItems.forEach(nav => nav.classList.remove('active'));
        const targetNav = document.querySelector(`.sidebar ul li[data-view="${viewId}"]`);
        if (targetNav) targetNav.classList.add('active');

        views.forEach(view => {
            if (view.id === `view-${viewId}`) {
                view.classList.remove('d-none');
                view.classList.add('active');
                loadViewData(viewId);
            } else {
                view.classList.add('d-none');
                view.classList.remove('active');
            }
        });
    };

    // Sidebar Toggle
    sidebarCollapse.addEventListener('click', () => {
        sidebar.classList.toggle('active');
        content.classList.toggle('active');
    });

    // View Navigation
    navItems.forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            const viewId = item.getAttribute('data-view');
            window.loadViewById(viewId);
        });
    });

    // Initial Data Load
    function initDashboard() {
        const activeTab = localStorage.getItem('revon_admin_active_tab') || 'dashboard';
        window.loadViewById(activeTab);
    }

    // Authentication Check
    checkAuth();

    function checkAuth() {
        const token = Api.getToken();
        if (token) {
            loginOverlay.classList.add('d-none');
            initDashboard();
        } else {
            loginOverlay.classList.remove('d-none');
        }
    }

    // Router/Loader for Views
    function loadViewData(viewId) {
        // Stop monitor timers when leaving monitor view
        if (viewId !== 'monitor') {
            if (window.monitorTimer) { clearInterval(window.monitorTimer); window.monitorTimer = null; }
            if (window.monitorCountdownTimer) { clearInterval(window.monitorCountdownTimer); window.monitorCountdownTimer = null; }
        }
        // Stop dashboard mini-monitor timer when leaving dashboard
        if (viewId !== 'dashboard') {
            if (window.dashMonitorTimer) { clearInterval(window.dashMonitorTimer); window.dashMonitorTimer = null; }
        }

        if (viewId === 'dashboard') {
            loadDashboardStats();
            loadDashboardMonitor();
            setTimeout(() => window.dashSearchUsers?.(), 0);
        } else if (viewId === 'users') {
            loadUsersView();
        } else if (viewId === 'tracks') {
            loadTracksView();
        } else if (viewId === 'records') {
            loadRecordsView();
        } else if (viewId === 'content_mgr') {
            loadContentMgrView();
        } else if (viewId === 'reports') {
            loadReportsView();
        } else if (viewId === 'monitor') {
            loadMonitorView();
        } else if (viewId === 'region_story') {
            loadRegionStoryView();
        } else if (viewId === 'snapshots') {
            loadSnapshotsView();
        } else if (viewId === 'coupons') {
            loadCouponsView();
        } else if (viewId === 'clubs') {
            loadClubsView();
        } else if (viewId === 'system_logs') {
            loadSystemLogsView();
        }
    }


    // =============================================
    // Dashboard Stats
    // =============================================
    async function loadDashboardStats() {
        try {
            const res = await Api.getStats();
            if (res.status === 'success' && res.data) {
                document.getElementById('stat-users').textContent = res.data.users ?? 0;
                document.getElementById('stat-active').textContent = res.data.active ?? 0;
                document.getElementById('stat-pending-tracks').textContent = res.data.pendingTracks ?? 0;
                document.getElementById('stat-reports').textContent = res.data.reports ?? 0;
            }
        } catch (e) {
            console.error('Failed to load dashboard stats', e);
        }
    }

    // =============================================
    // Dashboard Mini Monitor Panel (auto-refresh 5s)
    // =============================================
    async function loadDashboardMonitor() {
        if (window.dashMonitorTimer) { clearInterval(window.dashMonitorTimer); }
        await fetchDashMonitorData();
        window.dashMonitorTimer = setInterval(fetchDashMonitorData, 5000);
    }

    async function fetchDashMonitorData() {
        try {
            const res = await Api.getMonitorData();
            if (res.status !== 'success') return;
            const list = document.getElementById('dash-monitor-list');
            if (!list) return;

            const racing = (res.data.live_racing || []);
            const racingCount = racing.filter(r => r.status === 'racing' || !r.status || r.status === 'LIVE').length;
            const finishedToday = res.data.today_sessions || res.data.today_finished || 0;
            const totalToday = res.data.today_total || racing.length;

            const rcEl = document.getElementById('dash-racing-count');
            const scEl = document.getElementById('dash-sessions-count');
            const fcEl = document.getElementById('dash-finished-count');
            if (rcEl) rcEl.textContent = racingCount;
            if (scEl) scEl.textContent = totalToday;
            if (fcEl) fcEl.textContent = finishedToday;

            if (racing.length === 0) {
                list.innerHTML = '<div class="text-center text-muted py-3"><i class="fa-solid fa-circle-info me-1"></i>目前無競速中玩家</div>';
                return;
            }
            list.innerHTML = racing.map(r => {
                const isRacing = !r.status || r.status === 'racing' || r.status === 'LIVE' || r.status === 'RACING';
                const initial = (r.user || 'R').charAt(0).toUpperCase();
                const progressPct = Math.min(100, Math.max(0, parseFloat(r.progress_pct || 0)));
                const speed = parseFloat(r.speed || 0).toFixed(1);

                return `
                <div class="dash-monitor-row ${isRacing ? 'racing' : 'finished'} flex-column align-items-stretch">
                    <div class="d-flex align-items-center justify-content-between w-100 mb-1">
                        <div class="d-flex align-items-center gap-2">
                            <div class="dash-user-av" style="border: 2px solid ${isRacing ? '#22c55e' : '#444'};">${initial}</div>
                            <div class="dash-user-info">
                                <div class="dash-user-name fw-bold">${escapeHtml(r.user || '-')}</div>
                                <div class="dash-user-sub text-warning"><i class="fa-solid fa-map-pin me-1"></i>${escapeHtml(r.track || '-')}</div>
                            </div>
                        </div>
                        <div style="text-align:right; flex-shrink:0;">
                            ${isRacing
                                ? `<span class="badge" style="background:rgba(34,197,94,0.15);color:#22c55e;border:1px solid rgba(34,197,94,0.4);font-size:10px;">⚡ 比賽中 (${r.elapsed || ''})</span><div style="font-size:11px;color:#f59e0b;font-weight:700;margin-top:2px;">${speed} km/h</div>`
                                : `<span class="badge" style="background:rgba(120,120,120,0.15);color:#888;border:1px solid rgba(120,120,120,0.25);font-size:10px;">✓ 已完成</span>`
                            }
                        </div>
                    </div>
                    <!-- 山路進度條 -->
                    <div class="w-100 mt-1">
                        <div class="d-flex justify-content-between align-items-center mb-1" style="font-size: 10px;">
                            <span class="text-secondary font-monospace">路線進行度</span>
                            <span class="font-monospace text-info fw-bold">${progressPct.toFixed(1)}%</span>
                        </div>
                        <div class="progress" style="height: 5px; background: rgba(255,255,255,0.08);">
                            <div class="progress-bar ${progressPct >= 100 ? 'bg-success' : 'bg-danger progress-bar-striped progress-bar-animated'}" style="width: ${progressPct}%;"></div>
                        </div>
                    </div>
                </div>`;
            }).join('');
        } catch(e) { console.error('dashMonitor error', e); }
    }

    // =============================================
    // Dashboard User Search
    // =============================================
    let dashUserSearchTimer = null;
    window.dashSearchUsers = async () => {
        const q = (document.getElementById('dash-user-search')?.value || '').trim();
        document.getElementById('dash-users-list').innerHTML = `<div class="text-center text-muted py-2">${q ? '搜尋中...' : '載入所有玩家中...'}</div>`;
        try {
            const res = await Api.getUsers();
            if (res.status === 'success' && res.data) {
                const lower = q.toLowerCase();
                const filtered = res.data.filter(u => !lower ||
                    (u.nickname || '').toLowerCase().includes(lower) ||
                    (u.account || '').toLowerCase().includes(lower) ||
                    (u.email || '').toLowerCase().includes(lower)
                );

                if (!filtered.length) {
                    document.getElementById('dash-users-list').innerHTML = '<div class="text-center text-muted py-3">找不到符合的玩家</div>';
                    return;
                }
                document.getElementById('dash-users-list').innerHTML = filtered.map(u => {
                    const initial = (u.nickname || u.account || 'U').charAt(0).toUpperCase();
                    const roleColor = u.role === 'admin' ? '#e10600' : (u.role === 'blocked' ? '#666' : '#63b3ed');
                    return `
                    <div class="dash-user-item">
                        <div class="dash-user-av">${initial}</div>
                        <div class="dash-user-info">
                            <div class="dash-user-name">${escapeHtml(u.nickname || u.account)}</div>
                            <div class="dash-user-sub">${escapeHtml(u.email || u.account || '-')}</div>
                        </div>
                        <div style="display:flex;gap:4px;flex-shrink:0;">
                            <span class="badge" style="background:rgba(99,179,237,0.15);color:${roleColor};border:1px solid ${roleColor}55;font-size:10px;">${u.role || 'user'}</span>
                            <button class="btn btn-sm" style="background:none;border:1px solid rgba(255,255,255,0.1);color:#aaa;font-size:11px;padding:2px 7px;" onclick="updateUserRole(${u.id}, '${u.role || 'user'}')" title="變更權限"><i class="fa-solid fa-user-shield"></i></button>
                        </div>
                    </div>`;
                }).join('');
            }
        } catch(e) {
            document.getElementById('dash-users-list').innerHTML = '<div class="text-center text-danger py-2">搜尋失敗，請稍後再試</div>';
        }
    };

    // bind Enter key on search input
    document.addEventListener('keyup', (e) => {
        if (e.target && e.target.id === 'dash-user-search' && e.key === 'Enter') {
            window.dashSearchUsers();
        }
    });

    // =============================================
    // Users Management View - with search & inline edit
    // =============================================
    let allUsersCache = [];
    let usersSearchTimer = null;

    async function loadUsersView() {
        const container = document.getElementById('view-users');
        container.innerHTML = `
            <div class="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
                <h2><i class="fa-solid fa-users text-danger"></i> 玩家管理</h2>
                <div class="d-flex gap-2 align-items-center">
                    <div class="input-group" style="width:280px;">
                        <span class="input-group-text bg-dark text-muted border-secondary"><i class="fa-solid fa-magnifying-glass"></i></span>
                        <input type="text" id="usersSearchInput" class="form-control bg-dark text-light border-secondary" placeholder="搜尋暱稱、帳號或 Email..." oninput="debounceUsersSearch(this.value)">
                        <button class="btn btn-danger" onclick="debounceUsersSearch(document.getElementById('usersSearchInput').value)"><i class="fa-solid fa-search"></i></button>
                    </div>
                    <span id="users-count-badge" class="badge bg-secondary">--</span>
                </div>
            </div>
            <div id="usersCardList" class="row g-3">
                <div class="col-12 text-center py-5 text-muted"><i class="fa-solid fa-spinner fa-spin me-2"></i>載入玩家資料...</div>
            </div>
        `;

        try {
            const res = await Api.getUsers();
            if (res.status === 'success' && res.data) {
                allUsersCache = res.data;
                renderUsersCards(allUsersCache);
            } else {
                document.getElementById('usersCardList').innerHTML = '<div class="col-12 text-center text-muted py-5">目前沒有玩家資料</div>';
            }
        } catch (e) {
            if (document.getElementById('usersCardList')) {
                document.getElementById('usersCardList').innerHTML = '<div class="col-12 text-center text-danger py-5">載入失敗，請稍後再試</div>';
            }
        }
    }

    function renderUsersCards(data) {
        const container = document.getElementById('usersCardList');
        const badge = document.getElementById('users-count-badge');
        if (!container) return;
        if (badge) badge.textContent = `${data.length} 人`;

        if (!data.length) {
            container.innerHTML = '<div class="col-12 text-center text-muted py-5">找不到符合條件的玩家</div>';
            return;
        }

        container.innerHTML = data.map(u => {
            const initial = (u.nickname || u.account || 'U').charAt(0).toUpperCase();
            const roleClass = u.role === 'admin' ? 'bg-danger' : (u.role === 'blocked' ? 'bg-secondary' : 'bg-primary');
            const roleLbl = u.role || 'user';
            const genderTxt = u.gender === 'male' ? '男' : (u.gender === 'female' ? '女' : '不透漏');
            return `
            <div class="col-12">
                <div class="user-card">
                    <div class="user-avatar">${initial}</div>
                    <div class="user-info">
                        <div class="user-name">${escapeHtml(u.nickname || '-')} ${u.real_name ? `<small class="text-muted fs-7">(${escapeHtml(u.real_name)})</small>` : ''}
                            <span class="badge ${roleClass} ms-2" style="font-size:10px;">${roleLbl}</span>
                        </div>
                        <div class="user-meta"><i class="fa-solid fa-at fa-xs me-1"></i>帳號: ${escapeHtml(u.account || '-')}</div>
                        <div class="user-meta"><i class="fa-solid fa-envelope fa-xs me-1"></i>Email: ${escapeHtml(u.email || '-')}</div>
                        <div class="user-meta"><i class="fa-solid fa-phone fa-xs me-1"></i>電話: ${escapeHtml(u.phone || '未設定')} &nbsp;|&nbsp; <i class="fa-solid fa-venus-mars fa-xs me-1"></i>性別: ${genderTxt} &nbsp;|&nbsp; 生日: ${u.birthday || '未設定'}</div>
                        <div class="user-meta mt-1">
                            <span class="badge bg-warning text-dark me-1">VIP ${u.vip_level || 0}</span>
                            <span class="badge bg-primary">${u.points !== undefined ? u.points : (u.free_plays || 0)} 點</span>
                        </div>
                    </div>
                    <div class="d-flex flex-column gap-1" style="flex-shrink:0;">
                        <button class="btn btn-sm btn-outline-info" style="font-size:11px;" title="編輯玩家" onclick="editUserModal(${u.id})"><i class="fa-solid fa-pen-to-square"></i> 編輯</button>
                        <button class="btn btn-sm btn-outline-warning" style="font-size:11px;" title="修改點數" onclick="updateUserPoints(${u.id}, ${u.points !== undefined ? u.points : (u.free_plays || 0)})"><i class="fa-solid fa-coins"></i> 點數</button>
                        <button class="btn btn-sm btn-outline-danger" style="font-size:11px;" title="刪除玩家" onclick="deleteUser(${u.id})"><i class="fa-solid fa-trash"></i> 刪除</button>
                    </div>
                </div>
            </div>`;
        }).join('');
    }

    window.debounceUsersSearch = (val) => {
        clearTimeout(usersSearchTimer);
        usersSearchTimer = setTimeout(() => {
            if (!allUsersCache.length) return;
            const q = val.trim().toLowerCase();
            if (!q) {
                renderUsersCards(allUsersCache);
                return;
            }
            const filtered = allUsersCache.filter(u => 
                (u.nickname && u.nickname.toLowerCase().includes(q)) ||
                (u.real_name && u.real_name.toLowerCase().includes(q)) ||
                (u.account && u.account.toLowerCase().includes(q)) ||
                (u.email && u.email.toLowerCase().includes(q)) ||
                (u.phone && u.phone.toLowerCase().includes(q))
            );
            renderUsersCards(filtered);
        }, 300);
    };

    // Edit user modal: nickname, real_name, email, phone, gender, birthday, role
    window.editUserModal = async (userId) => {
        const u = allUsersCache.find(x => x.id === userId);
        if (!u) return;
        const { value: formValues } = await Swal.fire({
            title: `<i class="fa-solid fa-pen-to-square text-info me-2"></i>編輯玩家 #${u.id}`,
            width: '580px',
            background: '#18191a',
            color: '#fff',
            html: `
                <div class="row text-start g-3">
                    <div class="col-md-6">
                        <label class="form-label mb-1 fs-7 text-light">暱稱 (Nickname)</label>
                        <input id="edit-user-nickname" class="form-control form-control-sm bg-dark text-light border-secondary" value="${escapeHtml(u.nickname || '')}">
                    </div>
                    <div class="col-md-6">
                        <label class="form-label mb-1 fs-7 text-light">真實姓名 (Real Name)</label>
                        <input id="edit-user-real-name" class="form-control form-control-sm bg-dark text-light border-secondary" value="${escapeHtml(u.real_name || '')}">
                    </div>
                    <div class="col-md-6">
                        <label class="form-label mb-1 fs-7 text-light">Email</label>
                        <input id="edit-user-email" type="email" class="form-control form-control-sm bg-dark text-light border-secondary" value="${escapeHtml(u.email || '')}">
                    </div>
                    <div class="col-md-6">
                        <label class="form-label mb-1 fs-7 text-light">電話 (Phone)</label>
                        <input id="edit-user-phone" type="text" class="form-control form-control-sm bg-dark text-light border-secondary" value="${escapeHtml(u.phone || '')}">
                    </div>
                    <div class="col-md-6">
                        <label class="form-label mb-1 fs-7 text-light">性別 (Gender)</label>
                        <select id="edit-user-gender" class="form-select form-select-sm bg-dark text-light border-secondary">
                            <option value="male" ${u.gender === 'male' ? 'selected' : ''}>男 (Male)</option>
                            <option value="female" ${u.gender === 'female' ? 'selected' : ''}>女 (Female)</option>
                            <option value="other" ${u.gender === 'other' || !u.gender ? 'selected' : ''}>不透漏 (Other)</option>
                        </select>
                    </div>
                    <div class="col-md-6">
                        <label class="form-label mb-1 fs-7 text-light">出生年月日 (Birthday)</label>
                        <input id="edit-user-birthday" type="date" class="form-control form-control-sm bg-dark text-light border-secondary" value="${u.birthday || ''}">
                    </div>
                    <div class="col-12">
                        <label class="form-label mb-1 fs-7 text-light">角色權限 (Role)</label>
                        <select id="edit-user-role" class="form-select form-select-sm bg-dark text-light border-secondary">
                            <option value="user" ${u.role === 'user' || !u.role ? 'selected' : ''}>普通玩家 (user)</option>
                            <option value="admin" ${u.role === 'admin' ? 'selected' : ''}>管理員 (admin)</option>
                            <option value="blocked" ${u.role === 'blocked' ? 'selected' : ''}>封鎖 (blocked)</option>
                        </select>
                    </div>
                    <div class="col-12">
                        <div class="p-2 rounded border border-secondary bg-dark">
                            <small class="text-muted d-block">帳號: <strong class="text-light">${escapeHtml(u.account || '-')}</strong></small>
                            <small class="text-muted d-block">VIP 等級: <strong class="text-warning">${u.vip_level || 0}</strong> &nbsp; 點數: <strong class="text-info">${u.points !== undefined ? u.points : (u.free_plays || 0)}</strong></small>
                            <small class="text-muted d-block">註冊時間: ${u.created_at || '-'}</small>
                        </div>
                    </div>
                </div>
            `,
            showCancelButton: true,
            confirmButtonText: '儲存變更',
            cancelButtonText: '取消',
            preConfirm: () => {
                return {
                    action: 'edit_profile',
                    user_id: u.id,
                    nickname: document.getElementById('edit-user-nickname').value.trim(),
                    real_name: document.getElementById('edit-user-real-name').value.trim(),
                    email: document.getElementById('edit-user-email').value.trim(),
                    phone: document.getElementById('edit-user-phone').value.trim(),
                    gender: document.getElementById('edit-user-gender').value,
                    birthday: document.getElementById('edit-user-birthday').value,
                    role: document.getElementById('edit-user-role').value,
                };
            }
        });

        if (formValues) {
            try {
                // update role
                if (formValues.role !== (u.role || 'user')) {
                    await Api.updateUser({ action: 'update_role', user_id: u.id, role: formValues.role });
                }
                // update profile attributes
                await Api.updateUser(formValues);
                showNotify('success', '玩家資料已更新');
                loadUsersView();
            } catch(e) {
                showNotify('error', '更新失敗', e.message || '');
            }
        }
    };


    // Tracks View Logic (含排序切換、手動新增賽道、Google 地圖點位編輯與搜尋過濾)
    let currentTrackType = 'all';
    let currentTrackOrder = 'ASC';
    let currentTrackSearch = '';

    async function fetchAndUpdateTracksTable() {
        const tbody = document.getElementById('tracksTableBody');
        const countBadge = document.getElementById('tracks-count-badge');
        if (!tbody) return;
        try {
            const res = await Api.getTracks(currentTrackType, currentTrackOrder, currentTrackSearch);
            if (res.status === 'success' && res.data && res.data.length > 0) {
                if (countBadge) countBadge.textContent = `總筆數：${res.data.length}`;
                tbody.innerHTML = res.data.map(t => {
                    const startLat = t.start_lat !== null && t.start_lat !== undefined ? parseFloat(t.start_lat).toFixed(7) : '-';
                    const startLng = t.start_lng !== null && t.start_lng !== undefined ? parseFloat(t.start_lng).toFixed(7) : '-';
                    const endLat = t.end_lat !== null && t.end_lat !== undefined ? parseFloat(t.end_lat).toFixed(7) : '-';
                    const endLng = t.end_lng !== null && t.end_lng !== undefined ? parseFloat(t.end_lng).toFixed(7) : '-';
                    const createdAt = t.created_at || '-';
                    const region = t.city || t.region || (t.country && t.country !== 'Taiwan' ? t.country : '台灣');
                    const trackDataJson = escapeHtml(JSON.stringify(t));
                    const isCustomTrack = t.track_source_type === 'custom' || currentTrackType === 'custom';

                    return `
                    <tr>
                        <td><input type="checkbox" class="form-check-input track-select-cb" value="${t.id}"></td>
                        <td>${t.id}</td>
                        <td class="fw-bold text-warning">${t.name || t.track_name || '賽道'}</td>
                        <td><small class="text-info">${region}</small></td>
                        <td><small class="font-monospace text-info">${startLat}, ${startLng}</small></td>
                        <td><small class="font-monospace text-info">${endLat}, ${endLng}</small></td>
                        <td><small class="text-muted">${createdAt}</small></td>
                        <td>
                            <button class="btn btn-sm btn-outline-info me-1" title="編輯點位與地圖" onclick="editTrackWaypoints('${trackDataJson}')"><i class="fa-solid fa-location-dot"></i> 編輯點位</button>
                            <button class="btn btn-sm btn-outline-danger" title="刪除賽道" onclick="deleteTrackDirect(${t.id}, ${isCustomTrack})"><i class="fa-solid fa-trash"></i></button>
                        </td>
                    </tr>
                    `;
                }).join('');
            } else {
                if (countBadge) countBadge.textContent = '總筆數：0';
                tbody.innerHTML = `<tr><td colspan="8" class="text-center text-muted">目前無賽道資料</td></tr>`;
            }
        } catch (e) {
            console.error(e);
            if (tbody) tbody.innerHTML = `<tr><td colspan="8" class="text-center text-danger">載入賽道失敗: ${e.message || e}</td></tr>`;
        }
    }

    async function loadTracksView() {
        const container = document.getElementById('view-tracks');
        container.innerHTML = `
            <div class="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
                <h2><i class="fa-solid fa-map-location-dot text-danger"></i> 賽道管理與座標點位編輯</h2>
                <div class="d-flex align-items-center gap-2 flex-wrap">
                    <!-- 批次刪除選取賽道按鈕 (無需確認) -->
                    <button class="btn btn-outline-danger fw-bold" onclick="batchDeleteSelectedTracks()">
                        <i class="fa-solid fa-trash-can me-1"></i> 刪除選取項目
                    </button>

                    <!-- 上傳匯入 SQL 檔案按鈕 -->
                    <button class="btn btn-outline-success fw-bold" onclick="triggerImportTrackSql()">
                        <i class="fa-solid fa-file-import me-1"></i> 匯入 SQL 賽道檔
                    </button>
                    <!-- 隱藏之 File Input -->
                    <input type="file" id="sqlFileInput" accept=".sql" style="display:none;" onchange="handleTrackSqlFileUpload(this)">

                    <!-- 手動新增賽道按鈕 -->
                    <button class="btn btn-danger fw-bold" onclick="showCreateTrackModal()">
                        <i class="fa-solid fa-plus-circle me-1"></i> 手動新增賽道
                    </button>

                    <!-- 搜尋關鍵字 -->
                    <div class="input-group" style="width: 220px;">
                        <span class="input-group-text bg-dark text-muted border-secondary"><i class="fa-solid fa-magnifying-glass"></i></span>
                        <input type="text" id="trackSearchInput" class="form-select bg-dark text-light border-secondary" placeholder="搜尋賽道/城市/國家" value="${escapeHtml(currentTrackSearch)}" oninput="debounceTrackSearch(this.value)">
                    </div>

                    <!-- 賽道類型過濾 -->
                    <select id="trackTypeSelect" class="form-select bg-dark text-light border-secondary" style="width: auto;" onchange="changeTrackType(this.value)">
                        <option value="all" ${currentTrackType === 'all' ? 'selected' : ''}>全部賽道 (All)</option>
                        <option value="official" ${currentTrackType === 'official' ? 'selected' : ''}>官方賽道 (tracks)</option>
                        <option value="custom" ${currentTrackType === 'custom' ? 'selected' : ''}>玩家自訂 (custom_tracks)</option>
                        <option value="circuit" ${currentTrackType === 'circuit' ? 'selected' : ''}>國際賽車場 (circuits)</option>
                    </select>

                    <!-- 排序切換按鈕 -->
                    <button class="btn btn-outline-light" onclick="toggleTrackOrder()">
                        <i class="fa-solid ${currentTrackOrder === 'ASC' ? 'fa-sort-numeric-up' : 'fa-sort-numeric-down-alt'}"></i> 
                        排序: ${currentTrackOrder === 'ASC' ? 'ID 低到高' : 'ID 高到低'}
                    </button>
                    <span id="tracks-count-badge" class="badge bg-secondary">總筆數：--</span>
                </div>
            </div>
            <div class="table-responsive">
                <table class="table table-dark table-striped table-hover rounded overflow-hidden align-middle">
                    <thead>
                        <tr>
                            <th style="width:40px;"><input type="checkbox" class="form-check-input" id="selectAllTracks" onchange="toggleSelectAllTracks(this)"></th>
                            <th>ID</th>
                            <th>賽道名稱</th>
                            <th>區域</th>
                            <th>起點座標 (Lat, Lng)</th>
                            <th>終點座標 (Lat, Lng)</th>
                            <th>建立時間</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody id="tracksTableBody">
                        <tr><td colspan="8" class="text-center">載入中...</td></tr>
                    </tbody>
                </table>
            </div>
        `;

        await fetchAndUpdateTracksTable();
    }

    let trackSearchTimer = null;
    window.debounceTrackSearch = (val) => {
        currentTrackSearch = val;
        clearTimeout(trackSearchTimer);
        trackSearchTimer = setTimeout(() => {
            if (document.getElementById('tracksTableBody')) {
                fetchAndUpdateTracksTable();
            } else {
                loadTracksView();
            }
        }, 300);
    };

    // HTML escape 輔助函式
    function escapeHtml(str) {
        return (str || '').toString().replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
    }

    window.changeTrackType = (type) => {
        currentTrackType = type;
        loadTracksView();
    };

    window.toggleTrackOrder = () => {
        currentTrackOrder = currentTrackOrder === 'DESC' ? 'ASC' : 'DESC';
        loadTracksView();
    };

    // 刪除賽道
    window.deleteTrack = async (trackId, isCustom) => {
        const result = await Swal.fire({
            title: '確認刪除賽道？',
            text: `即將刪除 ID #${trackId} 的賽道，此操作無法復原！`,
            icon: 'warning',
            showCancelButton: true,
            confirmButtonColor: '#d33',
            cancelButtonColor: '#6c757d',
            confirmButtonText: '確定刪除',
            cancelButtonText: '取消',
            background: '#1e1e1e',
            color: '#fff'
        });

        if (result.isConfirmed) {
            const res = await Api.deleteTrack(trackId, isCustom);
            if (res && res.status === 'success') {
                showNotify('success', '賽道已刪除');
                loadTracksView();
            } else {
                showNotify('error', '刪除失敗', (res && res.message) || '');
            }
        }
    };

    // 通用 Google 地圖初始化：輸入完整 Lat/Lng 或於地圖點擊、拖曳標記時皆會同步。
    function initGoogleTrackMap(initialData = null) {
        const mapContainer = document.getElementById('track-google-map');
        if (!mapContainer || !window.google?.maps) {
            showNotify('error', 'Google 地圖載入失敗', '請確認 Maps JavaScript API 已啟用且金鑰網域已授權');
            return;
        }

        const points = ['start', 'end', 'mid1', 'mid2'];
        const POINT_CONFIGS = {
            start: { color: '#198754', label: '起點 S' }, end: { color: '#dc3545', label: '終點 E' },
            mid1: { color: '#ffc107', label: '中途 1' }, mid2: { color: '#0dcaf0', label: '中途 2' }
        };
        const validPosition = (latValue, lngValue) => {
            const lat = Number(latValue), lng = Number(lngValue);
            return Number.isFinite(lat) && Number.isFinite(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
                ? { lat, lng } : null;
        };
        const initialStart = initialData && validPosition(initialData.start_lat, initialData.start_lng);
        const map = new google.maps.Map(mapContainer, {
            center: initialStart || { lat: 23.7, lng: 120.95 }, zoom: initialStart ? 14 : 8,
            mapTypeControl: true, streetViewControl: false, fullscreenControl: true
        });
        let activePointType = 'start';
        const markers = {};
        const routePolyline = new google.maps.Polyline({ map, strokeColor: '#ff4757', strokeWeight: 4, strokeOpacity: 0.85 });

        const updateMap = (changedPoint = null) => {
            const route = [];
            points.forEach(pt => {
                const position = validPosition(document.getElementById(`swal-${pt}-lat`)?.value, document.getElementById(`swal-${pt}-lng`)?.value);
                if (!position) {
                    markers[pt]?.setMap(null);
                    delete markers[pt];
                    return;
                }
                route.push(position);
                if (!markers[pt]) {
                    const config = POINT_CONFIGS[pt];
                    markers[pt] = new google.maps.Marker({ map, position, draggable: true, label: { text: config.label, color: '#fff', fontWeight: '700' } });
                    markers[pt].addListener('dragend', event => {
                        document.getElementById(`swal-${pt}-lat`).value = event.latLng.lat().toFixed(7);
                        document.getElementById(`swal-${pt}-lng`).value = event.latLng.lng().toFixed(7);
                        updateMap();
                    });
                } else markers[pt].setPosition(position);
            });
            routePolyline.setPath(route);
            if (changedPoint && markers[changedPoint]) {
                map.panTo(markers[changedPoint].getPosition());
                if (map.getZoom() < 14) map.setZoom(14);
            }
        };

        const selectorEl = document.getElementById('point-type-selector');
        selectorEl?.querySelectorAll('button').forEach(btn => btn.addEventListener('click', () => {
            selectorEl.querySelectorAll('button').forEach(button => button.classList.remove('active'));
            btn.classList.add('active');
            activePointType = btn.dataset.point;
        }));

        points.forEach(pt => {
            if (initialData) {
                const position = validPosition(initialData[`${pt}_lat`], initialData[`${pt}_lng`]);
                if (position) {
                    document.getElementById(`swal-${pt}-lat`).value = position.lat.toFixed(7);
                    document.getElementById(`swal-${pt}-lng`).value = position.lng.toFixed(7);
                }
            }
            ['lat', 'lng'].forEach(axis => document.getElementById(`swal-${pt}-${axis}`)?.addEventListener('input', () => updateMap(pt)));
        });
        updateMap();

        map.addListener('click', event => {
            document.getElementById(`swal-${activePointType}-lat`).value = event.latLng.lat().toFixed(7);
            document.getElementById(`swal-${activePointType}-lng`).value = event.latLng.lng().toFixed(7);
            updateMap();
            showNotify('info', `已在地圖設定 ${POINT_CONFIGS[activePointType].label}`, `${event.latLng.lat().toFixed(7)}, ${event.latLng.lng().toFixed(7)}`, 1500);
        });
    }

    // 手動新增賽道 Modal (含地圖)
    window.showCreateTrackModal = async () => {
        const { value: formValues } = await Swal.fire({
            title: '<i class="fa-solid fa-road text-danger me-2"></i>手動新增賽道 (含地圖選點)',
            width: '950px',
            background: '#18191a',
            color: '#fff',
            html: `
                <div class="row text-start g-3">
                    <!-- 左側 Google 地圖 -->
                    <div class="col-md-7">
                        <div class="d-flex justify-content-between align-items-center mb-2">
                            <span class="fw-bold text-warning fs-7"><i class="fa-solid fa-map-pin"></i> 點擊地圖取點：</span>
                            <div class="btn-group btn-group-sm" id="point-type-selector">
                                <button type="button" class="btn btn-success active" data-point="start">🟢 起點</button>
                                <button type="button" class="btn btn-danger" data-point="end">🔴 終點</button>
                                <button type="button" class="btn btn-warning" data-point="mid1">🟡 中途1</button>
                                <button type="button" class="btn btn-info" data-point="mid2">🔵 中途2</button>
                            </div>
                        </div>
                        <div id="track-google-map" style="height: 380px; width: 100%; border-radius: 8px; border: 1px solid #444;" class="bg-dark"></div>
                        <small class="text-muted mt-1 d-block"><i class="fa-solid fa-circle-info"></i> 直接輸入一組完整 Lat、Lng 即會繪製點位；亦可點擊地圖或拖曳標記微調</small>
                    </div>

                    <!-- 右側賽道詳細資訊表單 -->
                    <div class="col-md-5">
                        <div class="mb-2">
                            <label class="form-label mb-1 fs-7 text-light">賽道名稱 *</label>
                            <input id="track-name" class="form-control form-control-sm bg-dark text-light border-secondary" placeholder="例如：136線道 (太平-國姓)">
                        </div>
                        <div class="row g-2 mb-2">
                            <div class="col-6">
                                <label class="form-label mb-1 fs-7 text-light">賽道分類</label>
                                <select id="track-is-custom" class="form-select form-select-sm bg-dark text-light border-secondary">
                                    <option value="0">官方賽道 (tracks)</option>
                                    <option value="1">玩家自訂路線 (custom_tracks)</option>
                                </select>
                            </div>
                            <div class="col-6">
                                <label class="form-label mb-1 fs-7 text-light">縣市/地區</label>
                                <input id="track-city" class="form-control form-control-sm bg-dark text-light border-secondary" value="台中市" placeholder="例如：台中市">
                            </div>
                        </div>
                        <div class="row g-2 mb-2">
                            <div class="col-6">
                                <label class="form-label mb-1 fs-7 text-light">難度等級</label>
                                <select id="track-difficulty" class="form-select form-select-sm bg-dark text-light border-secondary">
                                    <option value="EASY">EASY (簡單)</option>
                                    <option value="NORMAL" selected>NORMAL (普通)</option>
                                    <option value="HARD">HARD (困難)</option>
                                    <option value="EXPERT">EXPERT (專家)</option>
                                </select>
                            </div>
                            <div class="col-6">
                                <label class="form-label mb-1 fs-7 text-light">適用車款</label>
                                <select id="track-vehicle-type" class="form-select form-select-sm bg-dark text-light border-secondary">
                                    <option value="all" selected>全部車款 (all)</option>
                                    <option value="car">僅限汽車 (car)</option>
                                    <option value="scooter">僅限機車 (scooter)</option>
                                </select>
                            </div>
                        </div>
                        <div class="row g-2 mb-2">
                            <div class="col-6">
                                <label class="form-label mb-1 fs-7 text-light">路線長度 (km)</label>
                                <input type="number" step="0.1" id="track-distance" class="form-control form-control-sm bg-dark text-light border-secondary" value="2.5">
                            </div>
                            <div class="col-6">
                                <label class="form-label mb-1 fs-7 text-light">彎道數量</label>
                                <input type="number" id="track-corners" class="form-control form-control-sm bg-dark text-light border-secondary" value="12">
                            </div>
                        </div>

                        <hr class="border-secondary my-2">

                        <!-- 座標資訊 -->
                        <div class="mb-1">
                            <small class="fw-bold text-success">🟢 起點座標 (Lat, Lng)</small>
                            <div class="input-group input-group-sm">
                                <input type="number" step="any" id="swal-start-lat" class="form-control bg-dark text-light border-secondary" placeholder="Start Lat">
                                <input type="number" step="any" id="swal-start-lng" class="form-control bg-dark text-light border-secondary" placeholder="Start Lng">
                            </div>
                        </div>
                        <div class="mb-1">
                            <small class="fw-bold text-danger">🔴 終點座標 (Lat, Lng)</small>
                            <div class="input-group input-group-sm">
                                <input type="number" step="any" id="swal-end-lat" class="form-control bg-dark text-light border-secondary" placeholder="End Lat">
                                <input type="number" step="any" id="swal-end-lng" class="form-control bg-dark text-light border-secondary" placeholder="End Lng">
                            </div>
                        </div>
                        <div class="mb-1">
                            <small class="fw-bold text-warning">🟡 中途點 1 (選填)</small>
                            <div class="input-group input-group-sm">
                                <input type="number" step="any" id="swal-mid1-lat" class="form-control bg-dark text-light border-secondary" placeholder="Mid1 Lat">
                                <input type="number" step="any" id="swal-mid1-lng" class="form-control bg-dark text-light border-secondary" placeholder="Mid1 Lng">
                            </div>
                        </div>
                        <div class="mb-1">
                            <small class="fw-bold text-info">🔵 中途點 2 (選填)</small>
                            <div class="input-group input-group-sm">
                                <input type="number" step="any" id="swal-mid2-lat" class="form-control bg-dark text-light border-secondary" placeholder="Mid2 Lat">
                                <input type="number" step="any" id="swal-mid2-lng" class="form-control bg-dark text-light border-secondary" placeholder="Mid2 Lng">
                            </div>
                        </div>
                    </div>
                </div>
            `,
            didOpen: () => {
                setTimeout(() => {
                    initGoogleTrackMap(null);
                }, 200);
            },
            showCancelButton: true,
            confirmButtonText: '確定建立賽道',
            cancelButtonText: '取消',
            preConfirm: () => {
                const name = document.getElementById('track-name').value.trim();
                if (!name) {
                    Swal.showValidationMessage('請輸入賽道名稱');
                    return false;
                }
                const startLat = document.getElementById('swal-start-lat').value;
                const startLng = document.getElementById('swal-start-lng').value;
                const endLat = document.getElementById('swal-end-lat').value;
                const endLng = document.getElementById('swal-end-lng').value;

                if (!startLat || !startLng || !endLat || !endLng) {
                    Swal.showValidationMessage('請至少在上點選起點與終點座標');
                    return false;
                }

                return {
                    name,
                    is_custom: parseInt(document.getElementById('track-is-custom').value),
                    city: document.getElementById('track-city').value,
                    difficulty: document.getElementById('track-difficulty').value,
                    vehicle_type: document.getElementById('track-vehicle-type').value,
                    distance_km: parseFloat(document.getElementById('track-distance').value),
                    corners_count: parseInt(document.getElementById('track-corners').value),
                    start_lat: startLat,
                    start_lng: startLng,
                    end_lat: endLat,
                    end_lng: endLng,
                    mid1_lat: document.getElementById('swal-mid1-lat').value || null,
                    mid1_lng: document.getElementById('swal-mid1-lng').value || null,
                    mid2_lat: document.getElementById('swal-mid2-lat').value || null,
                    mid2_lng: document.getElementById('swal-mid2-lng').value || null,
                };
            }
        });

        if (formValues) {
            const res = await Api.createTrack(formValues);
            if (res && res.status === 'success') {
                showNotify('success', '賽道建立成功！');
                loadTracksView();
            } else {
                showNotify('error', '建立失敗', (res && res.message) || '伺服器發生錯誤');
            }
        }
    };

    // 編輯點位座標 Modal (含地圖)
    window.editTrackWaypoints = async (trackJsonStr) => {
        const trackData = JSON.parse(trackJsonStr);

        const { value: formValues } = await Swal.fire({
            title: `<i class="fa-solid fa-location-dot text-info me-2"></i>編輯點位與地圖 #${trackData.id} - ${trackData.name || '賽道'}`,
            width: '950px',
            background: '#18191a',
            color: '#fff',
            html: `
                <div class="row text-start g-3">
                    <!-- 左側 Google 地圖 -->
                    <div class="col-md-7">
                        <div class="d-flex justify-content-between align-items-center mb-2">
                            <span class="fw-bold text-warning fs-7"><i class="fa-solid fa-map-pin"></i> 點擊地圖取點：</span>
                            <div class="btn-group btn-group-sm" id="point-type-selector">
                                <button type="button" class="btn btn-success active" data-point="start">🟢 起點</button>
                                <button type="button" class="btn btn-danger" data-point="end">🔴 終點</button>
                                <button type="button" class="btn btn-warning" data-point="mid1">🟡 中途1</button>
                                <button type="button" class="btn btn-info" data-point="mid2">🔵 中途2</button>
                            </div>
                        </div>
                        <div id="track-google-map" style="height: 380px; width: 100%; border-radius: 8px; border: 1px solid #444;" class="bg-dark"></div>
                        <small class="text-muted mt-1 d-block"><i class="fa-solid fa-circle-info"></i> 可直接點擊地圖或拖拽 Marker 變更座標點位</small>
                    </div>

                    <!-- 右側點位座標輸入 -->
                    <div class="col-md-5">
                        <div class="p-2 mb-2 bg-dark rounded border border-secondary">
                            <small class="text-muted d-block">賽道分類: <strong>${currentTrackType === 'custom' ? '玩家自訂路線' : '官方賽道'}</strong></small>
                            <small class="text-muted d-block">賽道名稱: <strong>${trackData.name || '-'}</strong></small>
                        </div>

                        <!-- 座標資訊 -->
                        <div class="mb-2">
                            <small class="fw-bold text-success">🟢 起點座標 (Start Lat, Lng)</small>
                            <div class="input-group input-group-sm">
                                <input type="number" step="any" id="swal-start-lat" class="form-control bg-dark text-light border-secondary" placeholder="Start Lat">
                                <input type="number" step="any" id="swal-start-lng" class="form-control bg-dark text-light border-secondary" placeholder="Start Lng">
                            </div>
                        </div>
                        <div class="mb-2">
                            <small class="fw-bold text-danger">🔴 終點座標 (End Lat, Lng)</small>
                            <div class="input-group input-group-sm">
                                <input type="number" step="any" id="swal-end-lat" class="form-control bg-dark text-light border-secondary" placeholder="End Lat">
                                <input type="number" step="any" id="swal-end-lng" class="form-control bg-dark text-light border-secondary" placeholder="End Lng">
                            </div>
                        </div>
                        <div class="mb-2">
                            <small class="fw-bold text-warning">🟡 中途點 1 (Mid1 Lat, Lng)</small>
                            <div class="input-group input-group-sm">
                                <input type="number" step="any" id="swal-mid1-lat" class="form-control bg-dark text-light border-secondary" placeholder="Mid1 Lat">
                                <input type="number" step="any" id="swal-mid1-lng" class="form-control bg-dark text-light border-secondary" placeholder="Mid1 Lng">
                            </div>
                        </div>
                        <div class="mb-2">
                            <small class="fw-bold text-info">🔵 中途點 2 (Mid2 Lat, Lng)</small>
                            <div class="input-group input-group-sm">
                                <input type="number" step="any" id="swal-mid2-lat" class="form-control bg-dark text-light border-secondary" placeholder="Mid2 Lat">
                                <input type="number" step="any" id="swal-mid2-lng" class="form-control bg-dark text-light border-secondary" placeholder="Mid2 Lng">
                            </div>
                        </div>
                    </div>
                </div>
            `,
            didOpen: () => {
                setTimeout(() => {
                    initGoogleTrackMap(trackData);
                }, 200);
            },
            showCancelButton: true,
            confirmButtonText: '儲存點位變更',
            cancelButtonText: '取消',
            preConfirm: () => {
                return {
                    action: 'update_waypoints',
                    track_id: trackData.id,
                    is_custom: currentTrackType === 'custom',
                    start_lat: document.getElementById('swal-start-lat').value,
                    start_lng: document.getElementById('swal-start-lng').value,
                    end_lat: document.getElementById('swal-end-lat').value,
                    end_lng: document.getElementById('swal-end-lng').value,
                    mid1_lat: document.getElementById('swal-mid1-lat').value || null,
                    mid1_lng: document.getElementById('swal-mid1-lng').value || null,
                    mid2_lat: document.getElementById('swal-mid2-lat').value || null,
                    mid2_lng: document.getElementById('swal-mid2-lng').value || null,
                };
            }
        });

        if (formValues) {
            const res = await Api.updateTrack(formValues);
            if (res && res.status === 'success') {
                showNotify('success', '座標點位更新成功');
                loadTracksView();
            } else {
                showNotify('error', '更新失敗', (res && res.message) || '');
            }
        }
    };


    // 成績紀錄全選與複選刪除功能 (無須確認直接刪除)
    window.toggleSelectAllRecords = function(masterCb) {
        const cbs = document.querySelectorAll('.record-select-cb');
        cbs.forEach(cb => cb.checked = masterCb.checked);
    };

    window.batchDeleteSelectedRecords = async function() {
        const checkedCbs = document.querySelectorAll('.record-select-cb:checked');
        if (checkedCbs.length === 0) {
            showNotify('warning', '請先勾選要刪除的成績紀錄');
            return;
        }
        const recordIds = Array.from(checkedCbs).map(cb => parseInt(cb.value)).filter(id => id > 0);
        try {
            const res = await Api.batchDeleteRecords(recordIds);
            if (res.status === 'success') {
                showNotify('success', `已成功刪除 ${recordIds.length} 筆成績紀錄`);
                loadRecordsView();
            } else {
                showNotify('error', res.message || '刪除失敗');
            }
        } catch (e) {
            showNotify('error', '刪除失敗', e.message || '');
        }
    };

    // 賽道管理全選與複選刪除功能 (無須確認直接刪除)
    window.toggleSelectAllTracks = function(masterCb) {
        const cbs = document.querySelectorAll('.track-select-cb');
        cbs.forEach(cb => cb.checked = masterCb.checked);
    };

    window.deleteTrackDirect = async function(trackId, isCustom = false) {
        try {
            const res = await Api.deleteTrack(trackId, isCustom);
            if (res.status === 'success') {
                showNotify('success', '賽道已成功刪除');
                loadTracksView();
            } else {
                showNotify('error', res.message || '刪除失敗');
            }
        } catch (e) {
            showNotify('error', '刪除失敗', e.message || '');
        }
    };

    window.batchDeleteSelectedTracks = async function() {
        const checkedCbs = document.querySelectorAll('.track-select-cb:checked');
        if (checkedCbs.length === 0) {
            showNotify('warning', '請先勾選要刪除的賽道');
            return;
        }
        const trackIds = Array.from(checkedCbs).map(cb => parseInt(cb.value)).filter(id => id > 0);
        try {
            const res = await Api.batchDeleteTracks(trackIds, currentTrackType === 'custom');
            if (res.status === 'success') {
                showNotify('success', `已成功刪除 ${trackIds.length} 筆賽道`);
                loadTracksView();
            } else {
                showNotify('error', res.message || '刪除失敗');
            }
        } catch (e) {
            showNotify('error', '刪除失敗', e.message || '');
        }
    };

    // Records View Logic
    async function loadRecordsView() {
        window.triggerLoadRecordsView = loadRecordsView;
        const container = document.getElementById('view-records');
        container.innerHTML = `
            <div class="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
                <h2><i class="fa-solid fa-stopwatch text-danger"></i> 競速成績與軌跡 Log 管理</h2>
                <div class="d-flex align-items-center gap-2">
                    <button class="btn btn-outline-danger fw-bold" onclick="batchDeleteSelectedRecords()">
                        <i class="fa-solid fa-trash-can me-1"></i> 刪除選取項目
                    </button>
                    <button class="btn btn-outline-light btn-sm" onclick="loadRecordsView()"><i class="fa-solid fa-rotate me-1"></i> 刷新</button>
                </div>
            </div>
            <div class="table-responsive">
                <table class="table table-dark table-striped table-hover rounded overflow-hidden align-middle">
                    <thead>
                        <tr>
                            <th style="width:40px;"><input type="checkbox" class="form-check-input" id="selectAllRecords" onchange="toggleSelectAllRecords(this)"></th>
                            <th>ID</th>
                            <th>車手 (User)</th>
                            <th>賽道名稱 (Track)</th>
                            <th>完成成績 (Lap Time)</th>
                            <th>載具類型</th>
                            <th>軌跡 Telemetry Log</th>
                            <th>紀錄時間</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody id="recordsTableBody">
                        <tr><td colspan="9" class="text-center">載入中...</td></tr>
                    </tbody>
                </table>
            </div>
        `;

        try {
            const res = await Api.getRecords();
            const tbody = document.getElementById('recordsTableBody');
            if (res.status === 'success' && res.data && res.data.length > 0) {
                tbody.innerHTML = res.data.map(r => `
                    <tr>
                        <td><input type="checkbox" class="form-check-input record-select-cb" value="${r.id}"></td>
                        <td><span class="badge bg-secondary">#${r.id}</span></td>
                        <td class="fw-bold text-light">${escapeHtml(r.user_name || '車手 #' + r.user_id)}</td>
                        <td><span class="text-warning">${escapeHtml(r.track_name || r.track_id)}</span></td>
                        <td class="fw-bold text-success font-monospace">${r.lap_time || r.time || '-'}</td>
                        <td><span class="badge bg-info">${r.vehicle_type || 'CAR'}</span></td>
                        <td>
                            ${r.has_telemetry ? 
                                `<button class="btn btn-sm btn-success fw-bold" title="點擊進行動態軌跡回顧與 Log 檢視" onclick="previewTelemetry(${r.id})"><i class="fa-solid fa-play me-1"></i>已保存 JSON Log (點擊回顧)</button>` : 
                                `<span class="badge bg-dark text-muted">無 Telemetry Log</span>`}
                        </td>
                        <td class="small text-muted">${r.created_at || '-'}</td>
                        <td>
                            ${r.has_telemetry ? `<button class="btn btn-sm btn-outline-warning me-1" title="軌跡動態回顧" onclick="previewTelemetry(${r.id})"><i class="fa-solid fa-map-location-dot"></i> 預覽</button>` : ''}
                            <button class="btn btn-sm btn-outline-danger" title="刪除成績" onclick="deleteRecord(${r.id})"><i class="fa-solid fa-trash"></i></button>
                        </td>
                    </tr>
                `).join('');
            } else {
                tbody.innerHTML = `<tr><td colspan="9" class="text-center text-muted">目前無競速單圈紀錄</td></tr>`;
            }
        } catch (e) {
            document.getElementById('recordsTableBody').innerHTML = `<tr><td colspan="9" class="text-center text-danger">載入成績紀錄失敗</td></tr>`;
        }
    }

    // 全域直接刪除紀錄 (無須確認彈框)
    window.deleteRecord = async function(id) {
        try {
            const res = await Api.deleteRecord(id);
            if (res.status === 'success') {
                showNotify('success', '成績紀錄已刪除');
                loadRecordsView();
            } else {
                showNotify('error', res.message || '刪除失敗');
            }
        } catch (e) {
            console.error('刪除失敗:', e);
            showNotify('error', '刪除失敗', e.message || '');
        }
    };

    // =============================================
    // Telemetry Replay & Preview Engine (Leaflet Heatmap & Animation)
    // =============================================
    let currentTelemetryData = null;
    let telemetryPoints = [];
    let telemetryMap = null;
    let currentPolylineSegments = [];
    let vehicleMarker = null;
    let playbackAnimFrame = null;
    let isTelemetryPlaying = false;
    let playbackSpeed = 1;
    let playbackStartTime = null;
    let playbackCurrentTimeMs = 0;
    let totalDurationMs = 0;

    // Speed heatmap color mapper (Blue -> Green -> Yellow -> Orange -> Red)
    function getSpeedColor(speed, minSpeed, maxSpeed) {
        const range = Math.max(1, maxSpeed - minSpeed);
        const ratio = Math.min(1, Math.max(0, (speed - minSpeed) / range));
        if (ratio < 0.25) return '#3b82f6'; // Blue
        if (ratio < 0.50) return '#10b981'; // Green
        if (ratio < 0.75) return '#f59e0b'; // Yellow/Orange
        return '#ef4444'; // Red
    }

    function formatTimeMs(ms) {
        const totalSec = Math.floor(ms / 1000);
        const mins = Math.floor(totalSec / 60);
        const secs = totalSec % 60;
        const hundredths = Math.floor((ms % 1000) / 10);
        return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}.${String(hundredths).padStart(2, '0')}`;
    }

    window.previewTelemetry = async function(recordId) {
        try {
            showNotify('info', '正在下載並載入 Telemetry Log...');
            const res = await Api.getTelemetryLog(recordId);
            if (res.status !== 'success' || !res.telemetry) {
                showNotify('error', '無法載入 Telemetry Log', res.message || 'JSON 紀錄不存在');
                return;
            }

            currentTelemetryData = res.telemetry;
            telemetryPoints = res.points || [];
            
            if (telemetryPoints.length === 0) {
                showNotify('warning', 'Log 無點位數據');
                return;
            }

            // Update badge & raw JSON display
            document.getElementById('telemetryRecordIdBadge').textContent = `#${recordId}`;
            const jsonPre = document.getElementById('telemetryJsonPre');
            if (jsonPre) {
                jsonPre.textContent = typeof res.raw_json === 'string' ? res.raw_json : JSON.stringify(currentTelemetryData, null, 2);
            }

            // Calculate overall stats
            let maxSpeed = 0;
            let totalSpeed = 0;
            let maxG = 0;
            let maxLeanL = 0;
            let maxLeanR = 0;
            let minSpeed = 999;

            const t0 = telemetryPoints[0].t || 0;
            telemetryPoints.forEach(p => {
                const s = p.s || 0;
                if (s > maxSpeed) maxSpeed = s;
                if (s < minSpeed) minSpeed = s;
                totalSpeed += s;

                const g = p.g || 0;
                if (g > maxG) maxG = g;

                const lean = p.l || 0;
                if (lean < 0 && Math.abs(lean) > maxLeanL) maxLeanL = Math.abs(lean);
                if (lean > 0 && lean > maxLeanR) maxLeanR = lean;
            });

            const avgSpeed = (totalSpeed / telemetryPoints.length).toFixed(1);
            totalDurationMs = Math.max(0, (telemetryPoints[telemetryPoints.length - 1].t || 0) - t0);

            document.getElementById('telemetrySampleCount').textContent = `${telemetryPoints.length} 點`;
            document.getElementById('telemetryMaxSpeed').textContent = `${maxSpeed.toFixed(1)} km/h`;
            document.getElementById('telemetryAvgSpeed').textContent = `${avgSpeed} km/h`;
            document.getElementById('telemetryLeanStats').textContent = `${maxLeanL.toFixed(1)}° / ${maxLeanR.toFixed(1)}°`;
            document.getElementById('telemetryMaxG').textContent = `${maxG.toFixed(2)} G`;
            document.getElementById('telemetryTotalTime').textContent = formatTimeMs(totalDurationMs);

            // Show Bootstrap Modal
            const modalEl = document.getElementById('telemetryPreviewModal');
            const modal = new bootstrap.Modal(modalEl);
            modal.show();

            // Initialize Leaflet Map once shown
            modalEl.addEventListener('shown.bs.modal', () => {
                initTelemetryMap(telemetryPoints, minSpeed, maxSpeed);
            }, { once: true });

            // Reset playback state
            resetTelemetryPlayback();

        } catch (e) {
            console.error('previewTelemetry error:', e);
            showNotify('error', '載入失敗', e.message || '');
        }
    };

    function initTelemetryMap(points, minSpeed, maxSpeed) {
        if (!telemetryMap) {
            telemetryMap = L.map('telemetryMap', {
                zoomControl: true,
                attributionControl: false
            });
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19
            }).addTo(telemetryMap);
        }

        // Clear previous polylines & marker
        currentPolylineSegments.forEach(seg => telemetryMap.removeLayer(seg));
        currentPolylineSegments = [];
        if (vehicleMarker) {
            telemetryMap.removeLayer(vehicleMarker);
            vehicleMarker = null;
        }

        telemetryMap.invalidateSize();

        const latLngs = points.map(p => [p.lt, p.lg]);
        const bounds = L.latLngBounds(latLngs);
        telemetryMap.fitBounds(bounds, { padding: [30, 30] });

        // Draw speed-gradient polylines segment by segment
        for (let i = 0; i < points.length - 1; i++) {
            const p1 = points[i];
            const p2 = points[i + 1];
            const avgS = (p1.s + p2.s) / 2;
            const color = getSpeedColor(avgS, minSpeed, maxSpeed);

            const seg = L.polyline([[p1.lt, p1.lg], [p2.lt, p2.lg]], {
                color: color,
                weight: 5,
                opacity: 0.85
            }).addTo(telemetryMap);
            currentPolylineSegments.push(seg);
        }

        // Custom Vehicle Icon Marker
        const vehicleIcon = L.divIcon({
            className: 'custom-vehicle-marker',
            html: '<div style="width:20px;height:20px;background:#ef4444;border:3px solid #ffffff;border-radius:50%;box-shadow:0 0 10px rgba(239,68,68,0.9);transform:translate(-50%,-50%);"></div>',
            iconSize: [20, 20],
            iconAnchor: [10, 10]
        });

        vehicleMarker = L.marker([points[0].lt, points[0].lg], { icon: vehicleIcon }).addTo(telemetryMap);
        updateTelemetryHUD(0);
    }

    function resetTelemetryPlayback() {
        if (playbackAnimFrame) cancelAnimationFrame(playbackAnimFrame);
        isTelemetryPlaying = false;
        playbackCurrentTimeMs = 0;
        const playBtn = document.getElementById('btnTelemetryPlay');
        if (playBtn) playBtn.innerHTML = '<i class="fa-solid fa-play me-1"></i> 播放';
        updateTelemetryHUD(0);
    }

    window.toggleTelemetryPlayback = function() {
        if (isTelemetryPlaying) {
            pauseTelemetryPlayback();
        } else {
            startTelemetryPlayback();
        }
    };

    function startTelemetryPlayback() {
        if (telemetryPoints.length === 0) return;
        isTelemetryPlaying = true;
        playbackStartTime = performance.now() - (playbackCurrentTimeMs / playbackSpeed);

        const playBtn = document.getElementById('btnTelemetryPlay');
        if (playBtn) playBtn.innerHTML = '<i class="fa-solid fa-pause me-1"></i> 暫停';

        function step(timestamp) {
            if (!isTelemetryPlaying) return;
            playbackCurrentTimeMs = (timestamp - playbackStartTime) * playbackSpeed;

            if (playbackCurrentTimeMs >= totalDurationMs) {
                playbackCurrentTimeMs = totalDurationMs;
                updateTelemetryHUD(playbackCurrentTimeMs);
                pauseTelemetryPlayback();
                return;
            }

            updateTelemetryHUD(playbackCurrentTimeMs);
            playbackAnimFrame = requestAnimationFrame(step);
        }

        playbackAnimFrame = requestAnimationFrame(step);
    }

    function pauseTelemetryPlayback() {
        isTelemetryPlaying = false;
        if (playbackAnimFrame) cancelAnimationFrame(playbackAnimFrame);
        const playBtn = document.getElementById('btnTelemetryPlay');
        if (playBtn) playBtn.innerHTML = '<i class="fa-solid fa-play me-1"></i> 播放';
    }

    window.setTelemetrySpeed = function(spd) {
        playbackSpeed = spd;
        if (isTelemetryPlaying) {
            playbackStartTime = performance.now() - (playbackCurrentTimeMs / playbackSpeed);
        }
    };

    window.onTelemetryScrub = function(pct) {
        pauseTelemetryPlayback();
        playbackCurrentTimeMs = (pct / 100) * totalDurationMs;
        updateTelemetryHUD(playbackCurrentTimeMs);
    };

    function updateTelemetryHUD(timeMs) {
        if (telemetryPoints.length === 0) return;
        const t0 = telemetryPoints[0].t || 0;
        const targetT = t0 + timeMs;

        // Find closest telemetry point
        let ptIdx = 0;
        for (let i = 0; i < telemetryPoints.length - 1; i++) {
            if (telemetryPoints[i + 1].t > targetT) {
                ptIdx = i;
                break;
            }
            ptIdx = i + 1;
        }

        const point = telemetryPoints[ptIdx];
        if (!point) return;

        // Update Vehicle Marker Position
        if (vehicleMarker && telemetryMap) {
            vehicleMarker.setLatLng([point.lt, point.lg]);
        }

        // Update HUD Speed Gauge
        const speed = point.s || 0;
        document.getElementById('telemetryCurSpeed').textContent = speed.toFixed(1);
        const speedPct = Math.min(100, (speed / 200) * 100);
        document.getElementById('telemetrySpeedBar').style.width = `${speedPct}%`;

        // Update Time & Scrubber
        document.getElementById('telemetryCurrentTime').textContent = formatTimeMs(timeMs);
        const pct = totalDurationMs > 0 ? (timeMs / totalDurationMs) * 100 : 0;
        document.getElementById('telemetryScrubber').value = pct.toFixed(1);

        // Update Instantaneous Stats
        document.getElementById('telemetryCurLean').textContent = `${(point.l || 0).toFixed(1)}°`;
        document.getElementById('telemetryCurG').textContent = `${(point.g || 0).toFixed(2)} G`;
        document.getElementById('telemetryCurGDetail').textContent = `${(point.ga || 0).toFixed(2)} / ${(point.gb || 0).toFixed(2)}`;
    }

    window.downloadCurrentTelemetryJson = function() {
        if (!currentTelemetryData) {
            showNotify('warning', '目前無 JSON 資料可供下載');
            return;
        }
        const str = JSON.stringify(currentTelemetryData, null, 2);
        const blob = new Blob([str], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `telemetry_log_record_${document.getElementById('telemetryRecordIdBadge').textContent}.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    };

    window.toggleTelemetryJsonCollapse = function() {
        const body = document.getElementById('telemetryJsonBody');
        const icon = document.getElementById('telemetryJsonIcon');
        if (body.style.display === 'none') {
            body.style.display = 'block';
            icon.className = 'fa-solid fa-chevron-down';
        } else {
            body.style.display = 'none';
            icon.className = 'fa-solid fa-chevron-right';
        }
    };



    // Coupons View Logic
    async function loadCouponsView() {
        const container = document.getElementById('view-coupons');
        container.innerHTML = `
            <div class="d-flex justify-content-between align-items-center mb-4">
                <h2>優惠碼管理</h2>
                <button class="btn btn-danger" onclick="showCreateCouponModal()"><i class="fa-solid fa-plus"></i> 新增優惠碼</button>
            </div>
            <div class="table-responsive">
                <table class="table table-dark table-striped table-hover rounded overflow-hidden">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>代碼</th>
                            <th>獎勵 VIP 天數</th>
                            <th>建立時間</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody id="couponsTableBody">
                        <tr><td colspan="5" class="text-center">載入中...</td></tr>
                    </tbody>
                </table>
            </div>
        `;

        try {
            const res = await Api.getCoupons();
            const tbody = document.getElementById('couponsTableBody');
            
            if (res.status === 'success' && res.data.length > 0) {
                tbody.innerHTML = res.data.map(c => `
                    <tr>
                        <td>${c.id}</td>
                        <td class="text-warning fw-bold">${c.code}</td>
                        <td>${c.reward_days} 天</td>
                        <td>${c.created_at}</td>
                        <td>
                            <button class="btn btn-sm btn-outline-danger" onclick="deleteCoupon(${c.id})"><i class="fa-solid fa-trash"></i></button>
                        </td>
                    </tr>
                `).join('');
            } else {
                tbody.innerHTML = `<tr><td colspan="5" class="text-center text-muted">目前沒有任何優惠碼</td></tr>`;
            }
        } catch (e) {
            document.getElementById('couponsTableBody').innerHTML = `<tr><td colspan="5" class="text-center text-danger">載入失敗</td></tr>`;
        }
    }

    // Clubs View Logic (車隊管理與每一項成員/屬性編輯)
    async function loadClubsView() {
        const container = document.getElementById('view-clubs');
        container.innerHTML = `
            <div class="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
                <h2><i class="fa-solid fa-users-viewfinder text-danger"></i> 車隊管理面板 (全成員/屬性可編輯)</h2>
                <button class="btn btn-danger fw-bold" onclick="showCreateClubModal()">
                    <i class="fa-solid fa-plus-circle me-1"></i> 手動創建新車隊
                </button>
            </div>
            <div class="table-responsive">
                <table class="table table-dark table-striped table-hover rounded overflow-hidden align-middle">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>隊徽標籤</th>
                            <th>車隊名稱</th>
                            <th>據點地區</th>
                            <th>隊長暱稱</th>
                            <th>成員人數 / 上限</th>
                            <th>競速總積分</th>
                            <th>車隊等級</th>
                            <th>隊徽主題色</th>
                            <th>最後修改時間</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody id="clubsTableBody">
                        <tr><td colspan="11" class="text-center">載入中...</td></tr>
                    </tbody>
                </table>
            </div>
        `;

        try {
            const res = await Api.getAdminClubs();
            const tbody = document.getElementById('clubsTableBody');
            if (res.status === 'success' && res.data && res.data.length > 0) {
                tbody.innerHTML = res.data.map(c => {
                    const accentColor = c.accent_color || '#E10600';
                    const badgeLetters = c.badge_letters || (c.name ? c.name.substring(0, 2).toUpperCase() : 'RV');
                    const clubDataJson = escapeHtml(JSON.stringify(c));

                    return `
                    <tr>
                        <td>${c.id}</td>
                        <td>
                            <span class="badge rounded-pill" style="background-color: ${accentColor}; font-size: 13px; font-weight: bold; padding: 6px 12px;">
                                ${badgeLetters}
                            </span>
                        </td>
                        <td class="fw-bold text-warning">${c.name || '車隊'}</td>
                        <td><span class="badge bg-secondary">${c.region || 'Taiwan'}</span></td>
                        <td><span class="text-info fw-bold">${c.captain_nickname || 'Admin'}</span></td>
                        <td><strong>${c.member_count || 1}</strong> / <span class="text-muted">${c.max_members || 50}</span> 人</td>
                        <td class="fw-bold text-success">${c.total_points || 0} pts</td>
                        <td><span class="badge bg-danger">Lv.${c.level || 1}</span></td>
                        <td><code style="color: ${accentColor};">${accentColor}</code></td>
                        <td><small class="text-muted">${c.updated_at || c.created_at || '-'}</small></td>
                        <td>
                            <button class="btn btn-sm btn-outline-info me-1" title="編輯車隊屬性與成員" onclick="editClubModal('${clubDataJson}')">
                                <i class="fa-solid fa-pen-to-square"></i> 編輯
                            </button>
                            <button class="btn btn-sm btn-outline-danger" title="刪除車隊" onclick="deleteAdminClub(${c.id})">
                                <i class="fa-solid fa-trash"></i>
                            </button>
                        </td>
                    </tr>
                    `;
                }).join('');
            } else {
                tbody.innerHTML = `<tr><td colspan="11" class="text-center text-muted">目前尚無任何車隊資料</td></tr>`;
            }
        } catch (e) {
            console.error(e);
            document.getElementById('clubsTableBody').innerHTML = `<tr><td colspan="11" class="text-center text-danger">載入車隊失敗: ${e.message || e}</td></tr>`;
        }
    }

    // 創建新車隊 Modal
    window.showCreateClubModal = async () => {
        const { value: formValues } = await Swal.fire({
            title: '<i class="fa-solid fa-users text-danger me-2"></i>手動創建車隊 (每一項欄位皆可自由調整)',
            width: '750px',
            background: '#18191a',
            color: '#fff',
            html: `
                <div class="row text-start g-3">
                    <div class="col-md-6">
                        <label class="form-label mb-1 fs-7 text-light">車隊名稱 *</label>
                        <input id="swal-club-name" class="form-control form-control-sm bg-dark text-light border-secondary" placeholder="例如：赤城 RedSuns">
                    </div>
                    <div class="col-md-6">
                        <label class="form-label mb-1 fs-7 text-light">隊徽簡稱 (2-3字) *</label>
                        <input id="swal-club-badge" class="form-control form-control-sm bg-dark text-light border-secondary" placeholder="例如：RS">
                    </div>

                    <div class="col-md-6">
                        <label class="form-label mb-1 fs-7 text-light">隊長暱稱 / 管理員</label>
                        <input id="swal-club-captain" class="form-control form-control-sm bg-dark text-light border-secondary" value="REVON_ADMIN" placeholder="隊長暱稱">
                    </div>
                    <div class="col-md-6">
                        <label class="form-label mb-1 fs-7 text-light">據點地區 / 縣市</label>
                        <input id="swal-club-region" class="form-control form-control-sm bg-dark text-light border-secondary" value="Taiwan" placeholder="例如：Taiwan 或 台中市">
                    </div>

                    <div class="col-md-12">
                        <label class="form-label mb-1 fs-7 text-light">隊訓 / 宣言</label>
                        <textarea id="swal-club-motto" class="form-control form-control-sm bg-dark text-light border-secondary" rows="2" placeholder="車隊隊訓與社群宣言"></textarea>
                    </div>

                    <div class="col-md-3">
                        <label class="form-label mb-1 fs-7 text-light">當前成員數</label>
                        <input type="number" id="swal-club-member-count" class="form-control form-control-sm bg-dark text-light border-secondary" value="1">
                    </div>
                    <div class="col-md-3">
                        <label class="form-label mb-1 fs-7 text-light">成員上限</label>
                        <input type="number" id="swal-club-max-members" class="form-control form-control-sm bg-dark text-light border-secondary" value="50">
                    </div>
                    <div class="col-md-3">
                        <label class="form-label mb-1 fs-7 text-light">總競速積分</label>
                        <input type="number" id="swal-club-points" class="form-control form-control-sm bg-dark text-light border-secondary" value="0">
                    </div>
                    <div class="col-md-3">
                        <label class="form-label mb-1 fs-7 text-light">車隊等級</label>
                        <input type="number" id="swal-club-level" class="form-control form-control-sm bg-dark text-light border-secondary" value="1">
                    </div>

                    <div class="col-md-6">
                        <label class="form-label mb-1 fs-7 text-light">隊徽主題色 (HEX 色碼)</label>
                        <div class="input-group input-group-sm">
                            <input type="color" class="form-control form-control-color bg-dark border-secondary" id="swal-club-color-picker" value="#E10600" onchange="document.getElementById('swal-club-color').value = this.value">
                            <input type="text" id="swal-club-color" class="form-control bg-dark text-light border-secondary" value="#E10600">
                        </div>
                    </div>
                </div>
            `,
            showCancelButton: true,
            confirmButtonText: '確定創建車隊',
            cancelButtonText: '取消',
            preConfirm: () => {
                const name = document.getElementById('swal-club-name').value.trim();
                if (!name) {
                    Swal.showValidationMessage('請輸入車隊名稱');
                    return false;
                }
                return {
                    name,
                    badge_letters: document.getElementById('swal-club-badge').value.trim(),
                    captain_nickname: document.getElementById('swal-club-captain').value.trim(),
                    region: document.getElementById('swal-club-region').value.trim(),
                    motto: document.getElementById('swal-club-motto').value.trim(),
                    member_count: parseInt(document.getElementById('swal-club-member-count').value),
                    max_members: parseInt(document.getElementById('swal-club-max-members').value),
                    total_points: parseInt(document.getElementById('swal-club-points').value),
                    level: parseInt(document.getElementById('swal-club-level').value),
                    accent_color: document.getElementById('swal-club-color').value.trim()
                };
            }
        });

        if (formValues) {
            const res = await Api.createClub(formValues);
            if (res && res.status === 'success') {
                showNotify('success', '車隊創建成功！');
                loadClubsView();
            } else {
                showNotify('error', '創建失敗', (res && res.message) || '');
            }
        }
    };

    // 編輯車隊每一項屬性 Modal
    window.editClubModal = async (clubJsonStr) => {
        const clubData = JSON.parse(clubJsonStr);
        let members = [];
        try {
            const membersRes = await Api.getClubMembers(clubData.id);
            if (membersRes.status === 'success' && Array.isArray(membersRes.data)) members = membersRes.data;
        } catch (error) {
            console.error('Failed to load club members', error);
        }
        const membersHtml = members.length
            ? members.map(member => {
                const isCaptain = member.role === 'captain';
                return `<div id="club-member-row-${member.userId}" class="d-flex align-items-center gap-2 py-2 border-bottom border-secondary">
                    <i class="fa-solid ${isCaptain ? 'fa-crown text-warning' : 'fa-user text-info'}"></i>
                    <span class="flex-grow-1 text-truncate">${escapeHtml(member.memberNickname || '隊員')}</span>
                    <span class="badge ${isCaptain ? 'bg-warning text-dark' : 'bg-secondary'}">${isCaptain ? '隊長' : '成員'}</span>
                    ${isCaptain ? '' : `<button type="button" class="btn btn-sm btn-outline-danger" onclick="kickClubMember(${clubData.id}, ${member.userId})"><i class="fa-solid fa-user-minus"></i> 踢出</button>`}
                </div>`;
            }).join('')
            : '<div class="text-muted text-center py-3">尚無可顯示的成員資料</div>';

        const { value: formValues } = await Swal.fire({
            title: `<i class="fa-solid fa-pen-to-square text-info me-2"></i>編輯車隊屬性與成員 #${clubData.id}`,
            width: '750px',
            background: '#18191a',
            color: '#fff',
            html: `
                <div class="row text-start g-3">
                    <div class="col-md-6">
                        <label class="form-label mb-1 fs-7 text-light">車隊名稱 *</label>
                        <input id="swal-club-name" class="form-control form-control-sm bg-dark text-light border-secondary" value="${escapeHtml(clubData.name || '')}">
                    </div>
                    <div class="col-md-6">
                        <label class="form-label mb-1 fs-7 text-light">隊徽簡稱 (2-3字)</label>
                        <input id="swal-club-badge" class="form-control form-control-sm bg-dark text-light border-secondary" value="${escapeHtml(clubData.badge_letters || '')}">
                    </div>

                    <div class="col-md-6">
                        <label class="form-label mb-1 fs-7 text-light">隊長暱稱 / 管理員</label>
                        <input id="swal-club-captain" class="form-control form-control-sm bg-dark text-light border-secondary" value="${escapeHtml(clubData.captain_nickname || '')}">
                    </div>
                    <div class="col-md-6">
                        <label class="form-label mb-1 fs-7 text-light">據點地區 / 縣市</label>
                        <input id="swal-club-region" class="form-control form-control-sm bg-dark text-light border-secondary" value="${escapeHtml(clubData.region || 'Taiwan')}">
                    </div>

                    <div class="col-md-12">
                        <label class="form-label mb-1 fs-7 text-light">隊訓 / 宣言</label>
                        <textarea id="swal-club-motto" class="form-control form-control-sm bg-dark text-light border-secondary" rows="2">${escapeHtml(clubData.motto || clubData.description || '')}</textarea>
                    </div>

                    <div class="col-md-3">
                        <label class="form-label mb-1 fs-7 text-light">當前成員數</label>
                        <input type="number" id="swal-club-member-count" class="form-control form-control-sm bg-dark text-light border-secondary" value="${clubData.member_count || 1}">
                    </div>
                    <div class="col-md-3">
                        <label class="form-label mb-1 fs-7 text-light">成員上限</label>
                        <input type="number" id="swal-club-max-members" class="form-control form-control-sm bg-dark text-light border-secondary" value="${clubData.max_members || 50}">
                    </div>
                    <div class="col-md-3">
                        <label class="form-label mb-1 fs-7 text-light">總競速積分</label>
                        <input type="number" id="swal-club-points" class="form-control form-control-sm bg-dark text-light border-secondary" value="${clubData.total_points || 0}">
                    </div>
                    <div class="col-md-3">
                        <label class="form-label mb-1 fs-7 text-light">車隊等級</label>
                        <input type="number" id="swal-club-level" class="form-control form-control-sm bg-dark text-light border-secondary" value="${clubData.level || 1}">
                    </div>

                    <div class="col-md-6">
                        <label class="form-label mb-1 fs-7 text-light">隊徽主題色 (HEX 色碼)</label>
                        <div class="input-group input-group-sm">
                            <input type="color" class="form-control form-control-color bg-dark border-secondary" id="swal-club-color-picker" value="${clubData.accent_color || '#E10600'}" onchange="document.getElementById('swal-club-color').value = this.value">
                            <input type="text" id="swal-club-color" class="form-control bg-dark text-light border-secondary" value="${clubData.accent_color || '#E10600'}">
                        </div>
                    </div>
                    <div class="col-12">
                        <label class="form-label mb-1 fs-7 text-light">車隊成員 <small class="text-muted">（隊長不可踢出）</small></label>
                        <div class="border border-secondary rounded px-2" style="max-height: 220px; overflow-y: auto;">${membersHtml}</div>
                    </div>
                </div>
            `,
            showCancelButton: true,
            confirmButtonText: '儲存變更',
            cancelButtonText: '取消',
            preConfirm: () => {
                const name = document.getElementById('swal-club-name').value.trim();
                if (!name) {
                    Swal.showValidationMessage('請輸入車隊名稱');
                    return false;
                }
                return {
                    id: clubData.id,
                    name,
                    badge_letters: document.getElementById('swal-club-badge').value.trim(),
                    captain_nickname: document.getElementById('swal-club-captain').value.trim(),
                    region: document.getElementById('swal-club-region').value.trim(),
                    motto: document.getElementById('swal-club-motto').value.trim(),
                    member_count: parseInt(document.getElementById('swal-club-member-count').value),
                    max_members: parseInt(document.getElementById('swal-club-max-members').value),
                    total_points: parseInt(document.getElementById('swal-club-points').value),
                    level: parseInt(document.getElementById('swal-club-level').value),
                    accent_color: document.getElementById('swal-club-color').value.trim()
                };
            }
        });

        if (formValues) {
            const res = await Api.updateClub(formValues);
            if (res && res.status === 'success') {
                showNotify('success', '車隊資訊更新成功！');
                loadClubsView();
            } else {
                showNotify('error', '更新失敗', (res && res.message) || '');
            }
        }
    };

    window.kickClubMember = async (clubId, userId) => {
        const result = await Swal.fire({
            title: '確認踢出成員？', text: '此成員將被移出車隊。', icon: 'warning',
            showCancelButton: true, confirmButtonText: '踢出', cancelButtonText: '取消',
            confirmButtonColor: '#dc3545', background: '#18191a', color: '#fff'
        });
        if (!result.isConfirmed) return;
        try {
            const res = await Api.removeClubMember(clubId, userId);
            if (res.status !== 'success') throw new Error(res.message || '踢出失敗');
            document.getElementById(`club-member-row-${userId}`)?.remove();
            const countInput = document.getElementById('swal-club-member-count');
            if (countInput) countInput.value = Math.max(1, (parseInt(countInput.value, 10) || 1) - 1);
            showNotify('success', '成員已踢出車隊');
        } catch (error) {
            showNotify('error', '踢出失敗', error.message || '請稍後再試');
        }
    };

    // 刪除車隊
    window.deleteAdminClub = async (clubId) => {
        const result = await Swal.fire({
            title: '確認刪除車隊？',
            text: `即將解散/刪除 ID #${clubId} 的車隊，此操作無法復原！`,
            icon: 'warning',
            showCancelButton: true,
            confirmButtonColor: '#d33',
            cancelButtonColor: '#6c757d',
            confirmButtonText: '確定解散刪除',
            cancelButtonText: '取消',
            background: '#1e1e1e',
            color: '#fff'
        });

        if (result.isConfirmed) {
            const res = await Api.deleteClub(clubId);
            if (res && res.status === 'success') {
                showNotify('success', '車隊已順利解散刪除');
                loadClubsView();
            } else {
                showNotify('error', '刪除失敗', (res && res.message) || '');
            }
        }
    };

    // Global Functions for inline event handlers
    window.showCreateCouponModal = async () => {
        const { value: formValues } = await Swal.fire({
            title: '新增優惠碼',
            html: `
                <input id="swal-input1" class="swal2-input bg-dark text-light" placeholder="優惠碼 (如 VIP2026)">
                <input id="swal-input2" type="number" class="swal2-input bg-dark text-light" placeholder="獎勵 VIP 天數">
            `,
            background: '#1e1e1e',
            color: '#fff',
            focusConfirm: false,
            showCancelButton: true,
            confirmButtonText: '新增',
            cancelButtonText: '取消',
            preConfirm: () => {
                return {
                    code: document.getElementById('swal-input1').value,
                    reward_days: document.getElementById('swal-input2').value
                }
            }
        });

        if (formValues && formValues.code && formValues.reward_days) {
            try {
                const res = await Api.createCoupon({
                    code: formValues.code,
                    reward_days: parseInt(formValues.reward_days)
                });
                if (res.status === 'success') {
                    showNotify('success', '新增成功');
                    loadCouponsView();
                } else {
                    showNotify('error', '新增失敗', res.message);
                }
            } catch (e) {
                showNotify('error', '錯誤', '新增失敗');
            }
        }
    };

    window.deleteCoupon = async (id) => {
        const result = await Swal.fire({
            title: '確定要刪除嗎？',
            text: "此操作無法復原",
            icon: 'warning',
            showCancelButton: true,
            confirmButtonColor: '#dc3545',
            cancelButtonColor: '#6c757d',
            confirmButtonText: '是的，刪除',
            cancelButtonText: '取消',
            background: '#1e1e1e',
            color: '#fff'
        });

        if (result.isConfirmed) {
            try {
                const res = await Api.deleteCoupon(id);
                if (res.status === 'success') {
                    showNotify('success', '已刪除');
                    loadCouponsView();
                } else {
                    showNotify('error', '刪除失敗', res.message);
                }
            } catch (e) {
                showNotify('error', '錯誤', '刪除失敗');
            }
        }
    };

    // User Management Action Handlers
    window.updateUserPoints = async (userId, currentPoints) => {
        const { value: points } = await Swal.fire({
            title: '修改會員點數',
            input: 'number',
            inputValue: currentPoints,
            inputLabel: '請輸入新的點數值',
            showCancelButton: true,
            confirmButtonText: '更新',
            cancelButtonText: '取消',
            background: '#1e1e1e',
            color: '#fff'
        });

        if (points !== undefined && points !== null) {
            try {
                const res = await Api.updateUser({ action: 'update_points', user_id: userId, points: parseInt(points) });
                if (res.status === 'success') {
                    showNotify('success', '點數更新成功');
                    loadUsersView();
                } else {
                    showNotify('error', '更新失敗', res.message);
                }
            } catch (e) {
                showNotify('error', '錯誤', '更新失敗');
            }
        }
    };

    window.updateUserRole = async (userId, currentRole) => {
        const { value: role } = await Swal.fire({
            title: '變更會員角色權限',
            input: 'select',
            inputOptions: {
                'user': '普通玩家 (user)',
                'admin': '管理員 (admin)',
                'blocked': '封鎖狀態 (blocked)'
            },
            inputValue: currentRole,
            showCancelButton: true,
            confirmButtonText: '更新',
            cancelButtonText: '取消',
            background: '#1e1e1e',
            color: '#fff'
        });

        if (role) {
            try {
                const res = await Api.updateUser({ action: 'update_role', user_id: userId, role: role });
                if (res.status === 'success') {
                    showNotify('success', '角色已變更');
                    loadUsersView();
                } else {
                    showNotify('error', '更新失敗', res.message);
                }
            } catch (e) {
                showNotify('error', '錯誤', '更新失敗');
            }
        }
    };

    window.deleteUser = async (userId) => {
        const result = await Swal.fire({
            title: '確定要刪除此會員嗎？',
            text: "刪除後無法恢復該帳號！",
            icon: 'warning',
            showCancelButton: true,
            confirmButtonColor: '#dc3545',
            cancelButtonColor: '#6c757d',
            confirmButtonText: '確定刪除',
            cancelButtonText: '取消',
            background: '#1e1e1e',
            color: '#fff'
        });

        if (result.isConfirmed) {
            try {
                const res = await Api.updateUser({ action: 'delete', user_id: userId });
                if (res.status === 'success') {
                    showNotify('success', '會員已刪除');
                    loadUsersView();
                } else {
                    showNotify('error', '刪除失敗', res.message);
                }
            } catch (e) {
                showNotify('error', '錯誤', '刪除失敗');
            }
        }
    };

    window.editTrackWaypoints = async (trackJsonStr) => {
        const track = JSON.parse(trackJsonStr);
        const startLat = track.start_lat !== null && track.start_lat !== undefined ? parseFloat(track.start_lat).toFixed(7) : '';
        const startLng = track.start_lng !== null && track.start_lng !== undefined ? parseFloat(track.start_lng).toFixed(7) : '';
        const endLat = track.end_lat !== null && track.end_lat !== undefined ? parseFloat(track.end_lat).toFixed(7) : '';
        const endLng = track.end_lng !== null && track.end_lng !== undefined ? parseFloat(track.end_lng).toFixed(7) : '';
        const mid1Lat = track.mid1_lat !== null && track.mid1_lat !== undefined ? parseFloat(track.mid1_lat).toFixed(7) : '';
        const mid1Lng = track.mid1_lng !== null && track.mid1_lng !== undefined ? parseFloat(track.mid1_lng).toFixed(7) : '';
        const mid2Lat = track.mid2_lat !== null && track.mid2_lat !== undefined ? parseFloat(track.mid2_lat).toFixed(7) : '';
        const mid2Lng = track.mid2_lng !== null && track.mid2_lng !== undefined ? parseFloat(track.mid2_lng).toFixed(7) : '';

        const { value: formValues } = await Swal.fire({
            title: `編輯賽道點位 (ID: ${track.id})`,
            html: `
                <div class="text-start fs-7 text-light mb-3">
                    <p class="mb-1"><strong>賽道名稱：</strong>${track.name || '自訂路線'}</p>
                    <p class="mb-1"><strong>建立時間：</strong>${track.created_at || '-'}</p>
                    <p class="mb-3"><strong>修改時間：</strong>${track.updated_at || track.created_at || '-'}</p>
                    <small class="text-warning">* 所有座標均精確至小數點後 7 位 (0.0000001)</small>
                </div>
                
                <div class="mb-3 text-start">
                    <label class="form-label text-info fw-bold">🚩 起點座標 (Start)</label>
                    <div class="input-group mb-2">
                        <span class="input-group-text bg-secondary text-light">緯度 Lat</span>
                        <input id="wp-start-lat" type="number" step="0.0000001" class="form-control bg-dark text-light" value="${startLat}">
                        <span class="input-group-text bg-secondary text-light">經度 Lng</span>
                        <input id="wp-start-lng" type="number" step="0.0000001" class="form-control bg-dark text-light" value="${startLng}">
                    </div>
                </div>

                <div class="mb-3 text-start">
                    <label class="form-label text-warning fw-bold">📍 檢查點1 (Mid 1)</label>
                    <div class="input-group mb-2">
                        <span class="input-group-text bg-secondary text-light">緯度 Lat</span>
                        <input id="wp-mid1-lat" type="number" step="0.0000001" class="form-control bg-dark text-light" value="${mid1Lat}">
                        <span class="input-group-text bg-secondary text-light">經度 Lng</span>
                        <input id="wp-mid1-lng" type="number" step="0.0000001" class="form-control bg-dark text-light" value="${mid1Lng}">
                        <button type="button" class="btn btn-outline-danger" onclick="document.getElementById('wp-mid1-lat').value='';document.getElementById('wp-mid1-lng').value='';"><i class="fa-solid fa-trash"></i> 清除</button>
                    </div>
                </div>

                <div class="mb-3 text-start">
                    <label class="form-label text-warning fw-bold">📍 檢查點2 (Mid 2)</label>
                    <div class="input-group mb-2">
                        <span class="input-group-text bg-secondary text-light">緯度 Lat</span>
                        <input id="wp-mid2-lat" type="number" step="0.0000001" class="form-control bg-dark text-light" value="${mid2Lat}">
                        <span class="input-group-text bg-secondary text-light">經度 Lng</span>
                        <input id="wp-mid2-lng" type="number" step="0.0000001" class="form-control bg-dark text-light" value="${mid2Lng}">
                        <button type="button" class="btn btn-outline-danger" onclick="document.getElementById('wp-mid2-lat').value='';document.getElementById('wp-mid2-lng').value='';"><i class="fa-solid fa-trash"></i> 清除</button>
                    </div>
                </div>

                <div class="mb-3 text-start">
                    <label class="form-label text-danger fw-bold">🏁 終點座標 (End)</label>
                    <div class="input-group mb-2">
                        <span class="input-group-text bg-secondary text-light">緯度 Lat</span>
                        <input id="wp-end-lat" type="number" step="0.0000001" class="form-control bg-dark text-light" value="${endLat}">
                        <span class="input-group-text bg-secondary text-light">經度 Lng</span>
                        <input id="wp-end-lng" type="number" step="0.0000001" class="form-control bg-dark text-light" value="${endLng}">
                    </div>
                </div>
            `,
            width: '650px',
            background: '#1e1e1e',
            color: '#fff',
            showCancelButton: true,
            confirmButtonText: '儲存更新',
            cancelButtonText: '取消',
            preConfirm: () => {
                const parse7 = (val) => val !== '' && !isNaN(val) ? parseFloat(val).toFixed(7) : null;
                return {
                    start_lat: parse7(document.getElementById('wp-start-lat').value),
                    start_lng: parse7(document.getElementById('wp-start-lng').value),
                    mid1_lat: parse7(document.getElementById('wp-mid1-lat').value),
                    mid1_lng: parse7(document.getElementById('wp-mid1-lng').value),
                    mid2_lat: parse7(document.getElementById('wp-mid2-lat').value),
                    mid2_lng: parse7(document.getElementById('wp-mid2-lng').value),
                    end_lat: parse7(document.getElementById('wp-end-lat').value),
                    end_lng: parse7(document.getElementById('wp-end-lng').value),
                }
            }
        });

        if (formValues) {
            try {
                const res = await Api.updateTrack({
                    action: 'update_waypoints',
                    track_id: track.id,
                    is_custom: currentTrackType === 'custom',
                    ...formValues
                });
                if (res.status === 'success') {
                    showNotify('success', '點位座標已更新');
                    loadTracksView();
                } else {
                    showNotify('error', '更新失敗', res.message);
                }
            } catch (e) {
                showNotify('error', '錯誤', '更新失敗');
            }
        }
    };

    window.deleteTrack = async (trackId, isCustom = false) => {
        const result = await Swal.fire({
            title: '確定要刪除此賽道嗎？',
            text: "此操作無法復原",
            icon: 'warning',
            showCancelButton: true,
            confirmButtonColor: '#dc3545',
            cancelButtonColor: '#6c757d',
            confirmButtonText: '確定刪除',
            cancelButtonText: '取消',
            background: '#1e1e1e',
            color: '#fff'
        });

        if (result.isConfirmed) {
            try {
                const res = await Api.updateTrack({ action: 'delete', track_id: trackId, is_custom: isCustom });
                if (res.status === 'success') {
                    showNotify('success', '賽道已刪除');
                    loadTracksView();
                } else {
                    showNotify('error', '刪除失敗', res.message);
                }
            } catch (e) {
                showNotify('error', '錯誤', '刪除失敗');
            }
        }
    };


    // 1. 內容營運管理 View (跑馬燈、Banner輪播牆、News貼文、內部公告)
    async function loadContentMgrView() {
        const container = document.getElementById('view-content_mgr');
        container.innerHTML = `
            <div class="d-flex justify-content-between align-items-center mb-4">
                <h2><i class="fa-solid fa-rectangle-ad text-danger"></i> 內容營運管理中樞</h2>
            </div>

            <!-- 跑馬燈與內部公告 -->
            <div class="row g-4 mb-4">
                <div class="col-md-6">
                    <div class="card bg-dark text-light border-secondary">
                        <div class="card-header d-flex justify-content-between align-items-center">
                            <h5 class="m-0"><i class="fa-solid fa-bullhorn text-warning"></i> 跑馬燈公告</h5>
                            <button class="btn btn-sm btn-outline-warning" onclick="showAddAnnouncementModal('marquee')"><i class="fa-solid fa-plus"></i> 新增</button>
                        </div>
                        <div class="card-body">
                            <ul class="list-group list-group-flush bg-dark text-light" id="marqueeList">
                                <li class="list-group-item bg-dark text-light text-center">載入中...</li>
                            </ul>
                        </div>
                    </div>
                </div>
                <div class="col-md-6">
                    <div class="card bg-dark text-light border-secondary">
                        <div class="card-header d-flex justify-content-between align-items-center">
                            <h5 class="m-0"><i class="fa-solid fa-note-sticky text-info"></i> 內部公告管理</h5>
                            <button class="btn btn-sm btn-outline-info" onclick="showAddAnnouncementModal('internal')"><i class="fa-solid fa-plus"></i> 新增</button>
                        </div>
                        <div class="card-body">
                            <ul class="list-group list-group-flush bg-dark text-light" id="internalNoticeList">
                                <li class="list-group-item bg-dark text-light text-center">載入中...</li>
                            </ul>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Banner 輪播牆 -->
            <div class="card bg-dark text-light border-secondary mb-4">
                <div class="card-header d-flex justify-content-between align-items-center">
                    <h5 class="m-0"><i class="fa-solid fa-images text-danger"></i> 首頁 Banner 輪播牆 (限制 5MB)</h5>
                    <button class="btn btn-sm btn-danger" onclick="showUploadBannerModal()"><i class="fa-solid fa-upload"></i> 上傳 Banner</button>
                </div>
                <div class="card-body">
                    <div class="row g-3" id="bannerGrid">
                        <div class="col-12 text-center text-muted">載入中...</div>
                    </div>
                </div>
            </div>
        `;

        loadAnnouncementsData();
        loadBannersData();
    }

    async function loadAnnouncementsData() {
        try {
            const res = await Api.getAnnouncements();
            if (res.status === 'success') {
                const marquees = res.data.filter(a => a.type === 'marquee');
                const internals = res.data.filter(a => a.type === 'internal');

                const mList = document.getElementById('marqueeList');
                mList.innerHTML = marquees.length ? marquees.map(a => `
                    <li class="list-group-item bg-dark text-light d-flex justify-content-between align-items-center border-secondary">
                        <div>
                            <strong>${escapeHtml(a.title)}</strong>
                            <small class="d-block text-muted">${a.created_at}</small>
                        </div>
                        <button class="btn btn-sm btn-outline-danger" onclick="deleteAnnouncement(${a.id})"><i class="fa-solid fa-trash"></i></button>
                    </li>
                `).join('') : '<li class="list-group-item bg-dark text-muted text-center border-secondary">無跑馬燈公告</li>';

                const iList = document.getElementById('internalNoticeList');
                iList.innerHTML = internals.length ? internals.map(a => `
                    <li class="list-group-item bg-dark text-light d-flex justify-content-between align-items-center border-secondary">
                        <div>
                            <strong>${escapeHtml(a.title)}</strong>
                            <span class="badge ${a.is_active ? 'bg-success' : 'bg-secondary'} ms-2">${a.is_active ? '啟用中' : '已停用'}</span>
                            <small class="d-block text-muted">${a.content || ''}</small>
                        </div>
                        <div>
                            <button class="btn btn-sm btn-outline-warning me-1" onclick="toggleAnnouncementActive(${a.id}, ${a.is_active ? 0 : 1})"><i class="fa-solid fa-power-off"></i></button>
                            <button class="btn btn-sm btn-outline-danger" onclick="deleteAnnouncement(${a.id})"><i class="fa-solid fa-trash"></i></button>
                        </div>
                    </li>
                `).join('') : '<li class="list-group-item bg-dark text-muted text-center border-secondary">無內部公告</li>';
            }
        } catch(e) {}
    }

    async function loadBannersData() {
        try {
            const res = await Api.getBanners();
            const grid = document.getElementById('bannerGrid');
            if (res.status === 'success' && res.data.length > 0) {
                grid.innerHTML = res.data.map(b => `
                    <div class="col-md-4">
                        <div class="card bg-dark border-secondary overflow-hidden">
                            <img src="../${b.image_url}" class="card-img-top" style="height: 160px; object-fit: cover;" alt="Banner">
                            <div class="card-body p-2 d-flex justify-content-between align-items-center">
                                <small class="text-light text-truncate" style="max-width: 70%;">${b.title || 'Banner'}</small>
                                <button class="btn btn-sm btn-outline-danger" onclick="removeBanner(${b.id})"><i class="fa-solid fa-trash"></i> 刪除</button>
                            </div>
                        </div>
                    </div>
                `).join('');
            } else {
                grid.innerHTML = '<div class="col-12 text-center text-muted">目前未上傳 Banner</div>';
            }
        } catch(e) {}
    }

    async function fetchDashMonitorData() {
        try {
            const res = await Api.getMonitorData();
            if (res.status !== 'success') return;
            const list = document.getElementById('dash-monitor-list');
            if (!list) return;

            const racing = (res.data.live_racing || []);
            const racingCount = racing.filter(r => r.status === 'racing' || r.status === 'LIVE' || r.status === 'RACING' || !r.status).length;
            const finishedToday = res.data.today_sessions || res.data.today_finished || 0;
            const totalToday = res.data.today_total || racing.length;

            const rcEl = document.getElementById('dash-racing-count');
            const scEl = document.getElementById('dash-sessions-count');
            const fcEl = document.getElementById('dash-finished-count');
            if (rcEl) rcEl.textContent = racingCount;
            if (scEl) scEl.textContent = totalToday;
            if (fcEl) fcEl.textContent = finishedToday;

            if (racing.length === 0) {
                list.innerHTML = '<div class="text-center text-muted py-3"><i class="fa-solid fa-circle-info me-1"></i>目前無競速中玩家</div>';
                return;
            }

            // Remove placeholder if exists
            const placeholder = list.querySelector('.text-center');
            if (placeholder) placeholder.remove();

            const existingCards = new Map();
            list.querySelectorAll('.dash-monitor-row[data-session-id]').forEach(el => {
                existingCards.set(el.getAttribute('data-session-id'), el);
            });

            const currentSessionIds = new Set();

            racing.forEach(r => {
                const sessionId = String(r.id || r.user_id || r.user);
                currentSessionIds.add(sessionId);

                const st = (r.status || 'RACING').toUpperCase();
                const isPreparing = st === 'PREPARING';
                const isRacing = st === 'RACING' || st === 'LIVE';
                const isFinished = st === 'FINISHED' || st === 'DONE';

                const initial = (r.user || 'R').charAt(0).toUpperCase();
                const progressPct = isFinished ? 100 : Math.min(100, Math.max(0, parseFloat(r.progress_pct || 0)));
                const speed = parseFloat(r.speed || 0).toFixed(1);

                let card = existingCards.get(sessionId);
                if (!card) {
                    card = document.createElement('div');
                    card.setAttribute('data-session-id', sessionId);
                    list.appendChild(card);
                }

                card.className = `dash-monitor-row ${isRacing ? 'racing' : (isPreparing ? 'preparing' : 'finished')} flex-column align-items-stretch mb-2 p-2 rounded border border-secondary`;
                card.innerHTML = `
                    <div class="d-flex align-items-center justify-content-between w-100 mb-1">
                        <div class="d-flex align-items-center gap-2">
                            <div class="dash-user-av" style="border: 2px solid ${isFinished ? '#666' : (isPreparing ? '#f59e0b' : '#22c55e')};">${initial}</div>
                            <div class="dash-user-info">
                                <div class="dash-user-name fw-bold">${escapeHtml(r.user || '-')}</div>
                                <div class="dash-user-sub text-warning"><i class="fa-solid fa-map-pin me-1"></i>${escapeHtml(r.track || '-')}</div>
                            </div>
                        </div>
                        <div style="text-align:right; flex-shrink:0;">
                            ${isPreparing 
                                ? `<span class="badge" style="background:rgba(245,158,11,0.15);color:#f59e0b;border:1px solid rgba(245,158,11,0.4);font-size:10px;">⏳ 準備中</span>`
                                : (isRacing
                                    ? `<span class="badge" style="background:rgba(34,197,94,0.15);color:#22c55e;border:1px solid rgba(34,197,94,0.4);font-size:10px;">⚡ 比賽中 (${r.elapsed || ''})</span><div style="font-size:11px;color:#f59e0b;font-weight:700;margin-top:2px;">${speed} km/h</div>`
                                    : `<span class="badge" style="background:rgba(120,120,120,0.15);color:#888;border:1px solid rgba(120,120,120,0.25);font-size:10px;">✓ 已完成</span>`)
                            }
                        </div>
                    </div>
                    <div class="w-100 mt-1">
                        <div class="progress" style="height: 4px; background: rgba(255,255,255,0.08);">
                            <div class="progress-bar ${isFinished ? 'bg-secondary' : (isPreparing ? 'bg-warning' : 'bg-success progress-bar-striped progress-bar-animated')}" style="width: ${progressPct}%;"></div>
                        </div>
                    </div>
                `;
            });

            // Remove expired session cards
            existingCards.forEach((card, sessionId) => {
                if (!currentSessionIds.has(sessionId)) {
                    card.remove();
                }
            });
        } catch (e) {
            console.error('fetchDashMonitorData error:', e);
        }
    }

    // 2. 客服問題回報 View
    async function loadReportsView() {
        const container = document.getElementById('view-reports');
        container.innerHTML = `
            <div class="d-flex justify-content-between align-items-center mb-4">
                <h2><i class="fa-solid fa-headset text-danger"></i> 客服問題回報系統</h2>
            </div>
            
            <div class="row g-3 mb-4" id="reportStatsCards">
                <div class="col-md-3"><div class="card text-bg-warning p-3"><h6 class="m-0">待受理 (Pending)</h6><h3 id="rep-stat-pending">0</h3></div></div>
                <div class="col-md-3"><div class="card text-bg-info p-3"><h6 class="m-0">處理中 (Processing)</h6><h3 id="rep-stat-processing">0</h3></div></div>
                <div class="col-md-3"><div class="card text-bg-success p-3"><h6 class="m-0">已解決 (Resolved)</h6><h3 id="rep-stat-resolved">0</h3></div></div>
                <div class="col-md-3"><div class="card text-bg-secondary p-3"><h6 class="m-0">已關閉 (Closed)</h6><h3 id="rep-stat-closed">0</h3></div></div>
            </div>

            <div class="table-responsive">
                <table class="table table-dark table-striped table-hover rounded overflow-hidden align-middle">
                    <thead>
                        <tr>
                            <th>工單號 Ticket</th>
                            <th>玩家帳號</th>
                            <th>分類</th>
                            <th>主旨標題</th>
                            <th>狀態</th>
                            <th>建立時間</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody id="reportsTableBody">
                        <tr><td colspan="7" class="text-center">載入中...</td></tr>
                    </tbody>
                </table>
            </div>
        `;

        try {
            const res = await Api.getReports();
            if (res.status === 'success') {
                document.getElementById('rep-stat-pending').textContent = res.data.stats.pending;
                document.getElementById('rep-stat-processing').textContent = res.data.stats.processing;
                document.getElementById('rep-stat-resolved').textContent = res.data.stats.resolved;
                document.getElementById('rep-stat-closed').textContent = res.data.stats.closed;

                const tbody = document.getElementById('reportsTableBody');
                if (res.data.list.length > 0) {
                    tbody.innerHTML = res.data.list.map(r => `
                        <tr>
                            <td class="fw-bold font-monospace text-warning">${r.ticket_no}</td>
                            <td>${r.user_account}</td>
                            <td><span class="badge bg-secondary">${r.category}</span></td>
                            <td>${escapeHtml(r.title)}</td>
                            <td>
                                <span class="badge ${r.status==='pending'?'bg-warning text-dark':(r.status==='resolved'?'bg-success':'bg-info')}">${r.status}</span>
                            </td>
                            <td><small class="text-muted">${r.created_at}</small></td>
                            <td>
                                <button class="btn btn-sm btn-outline-info" onclick="openReplyReportModal('${escapeHtml(JSON.stringify(r))}')"><i class="fa-solid fa-reply"></i> 回覆處理</button>
                            </td>
                        </tr>
                    `).join('');
                } else {
                    tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">目前無工單回報</td></tr>';
                }
            }
        } catch(e) {}
    }

    // =============================================
    // 3. 即時監控面板 (3秒刷新 + 倒計時)
    // =============================================
    async function loadMonitorView() {
        const container = document.getElementById('view-monitor');
        container.innerHTML = `
            <div class="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
                <h2><i class="fa-solid fa-desktop text-danger"></i> 即時競速儀表版
                    <span class="live-pulse ms-2"></span>
                </h2>
                <div class="monitor-refresh-bar">
                    <span>自動刷新倒計時：</span>
                    <span class="monitor-refresh-countdown" id="monitorCountdown">3</span> 秒
                    <button class="btn btn-sm btn-outline-success ms-2" onclick="window._forceMonitorRefresh && window._forceMonitorRefresh()" style="font-size:11px;padding:2px 8px;"><i class="fa-solid fa-arrows-rotate"></i> 立即刷新</button>
                </div>
            </div>

            <!-- 即時統計列 -->
            <div class="monitor-header-bar mb-4">
                <div class="monitor-stat">
                    <div class="monitor-stat-num text-success" id="mon-racing-cnt">--</div>
                    <div class="monitor-stat-label">比賽中</div>
                </div>
                <div class="monitor-stat-divider"></div>
                <div class="monitor-stat">
                    <div class="monitor-stat-num text-muted" id="mon-finished-cnt">--</div>
                    <div class="monitor-stat-label">已完成</div>
                </div>
                <div class="monitor-stat-divider"></div>
                <div class="monitor-stat">
                    <div class="monitor-stat-num text-info" id="mon-today-cnt">--</div>
                    <div class="monitor-stat-label">今日總場次</div>
                </div>
            </div>

            <div class="row g-4">
                <!-- 玩家競速列表 -->
                <div class="col-12 col-lg-7">
                    <div class="panel-card">
                        <div class="panel-header">
                            <i class="fa-solid fa-flag-checkered text-success me-2"></i>競速玩家列表
                            <small class="text-muted ms-auto" id="mon-list-updated"></small>
                        </div>
                        <div class="panel-body">
                            <div id="monitorRacingList">
                                <div class="text-center text-muted py-4"><i class="fa-solid fa-spinner fa-spin me-2"></i>監控連線中...</div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 待審核賽道 -->
                <div class="col-12 col-lg-5">
                    <div class="panel-card">
                        <div class="panel-header">
                            <i class="fa-solid fa-check-to-slot text-warning me-2"></i>待審核自訂賽道
                        </div>
                        <div class="panel-body">
                            <div id="monitorPendingTracksList">
                                <div class="text-center text-muted py-4">載入中...</div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        fetchMonitorData();

        // 3-second countdown + refresh
        let countdown = 3;
        window._forceMonitorRefresh = () => {
            countdown = 3;
            const el = document.getElementById('monitorCountdown');
            if (el) el.textContent = countdown;
            fetchMonitorData();
        };

        window.monitorCountdownTimer = setInterval(() => {
            countdown--;
            const el = document.getElementById('monitorCountdown');
            if (el) el.textContent = Math.max(0, countdown);
            if (countdown <= 0) {
                countdown = 3;
                fetchMonitorData();
            }
        }, 1000);
    }

    async function fetchMonitorData() {
        try {
            const res = await Api.getMonitorData();
            if (res.status === 'success') {
                const rList = document.getElementById('monitorRacingList');
                const liveRacing = res.data.live_racing || [];

                // Calculate counts
                const racingCount = liveRacing.filter(r => !r.status || r.status === 'racing' || r.status === 'LIVE' || r.status === 'RACING').length;
                const finishedCount = liveRacing.filter(r => r.status === 'finished' || r.status === 'DONE' || r.status === 'FINISHED' || r.status === 'completed').length;
                const todayTotal = res.data.today_total || res.data.today_sessions || liveRacing.length;

                // Update stat numbers
                const monRacing = document.getElementById('mon-racing-cnt');
                const monFinished = document.getElementById('mon-finished-cnt');
                const monToday = document.getElementById('mon-today-cnt');
                if (monRacing) monRacing.textContent = racingCount;
                if (monFinished) monFinished.textContent = finishedCount;
                if (monToday) monToday.textContent = todayTotal;

                // Update racing list using DOM Diffing (In-Place Updates) to prevent screen flicker/flash
                if (rList) {
                    if (liveRacing.length === 0) {
                        rList.innerHTML = '<div class="text-center text-muted py-5"><i class="fa-solid fa-circle-info me-2"></i>目前無競速中的玩家</div>';
                    } else {
                        const placeholder = rList.querySelector('.text-center');
                        if (placeholder) placeholder.remove();

                        // Sort: preparing & racing first, then finished
                        const sorted = [...liveRacing].sort((a, b) => {
                            const aActive = (a.status || 'RACING').toUpperCase() !== 'FINISHED';
                            const bActive = (b.status || 'RACING').toUpperCase() !== 'FINISHED';
                            return bActive - aActive;
                        });

                        const existingCards = new Map();
                        rList.querySelectorAll('.racing-player-row[data-session-id]').forEach(el => {
                            existingCards.set(el.getAttribute('data-session-id'), el);
                        });

                        const currentSessionIds = new Set();

                        sorted.forEach(r => {
                            const sessionId = String(r.id || r.user_id || r.user);
                            currentSessionIds.add(sessionId);

                            const st = (r.status || 'RACING').toUpperCase();
                            const isPreparing = st === 'PREPARING';
                            const isRacing = st === 'RACING' || st === 'LIVE';
                            const isFinished = st === 'FINISHED' || st === 'DONE';

                            const initial = (r.user || 'R').charAt(0).toUpperCase();
                            const progressPct = isFinished ? 100 : Math.min(100, Math.max(0, parseFloat(r.progress_pct || 0)));
                            const speed = parseFloat(r.speed || 0).toFixed(1);

                            let badgeHtml = '';
                            let badgeStyle = '';
                            let detailText = '';

                            if (isPreparing) {
                                badgeHtml = '⏳ 正在準備中';
                                badgeStyle = 'background:rgba(245,158,11,0.2);color:#f59e0b;border:1px solid rgba(245,158,11,0.4);';
                                detailText = '<div class="font-monospace text-warning mt-1" style="font-size: 12px;">等待起跑...</div>';
                            } else if (isRacing) {
                                badgeHtml = '⚡ 比賽中';
                                badgeStyle = 'background:rgba(34,197,94,0.2);color:#22c55e;border:1px solid rgba(34,197,94,0.4);';
                                detailText = `<div class="font-monospace text-danger fw-bold mt-1" style="font-size: 13px;">${r.elapsed || '00:00'} ｜ ${speed} km/h</div>`;
                            } else {
                                badgeHtml = '✓ 已完成';
                                badgeStyle = 'background:rgba(120,120,120,0.2);color:#888;border:1px solid rgba(120,120,120,0.3);';
                                detailText = `<div class="font-monospace text-muted mt-1" style="font-size: 12px;">完成時間: ${r.elapsed || '-'}</div>`;
                            }

                            let card = existingCards.get(sessionId);
                            if (!card) {
                                card = document.createElement('div');
                                card.setAttribute('data-session-id', sessionId);
                                rList.appendChild(card);
                            }

                            card.className = `racing-player-row flex-column align-items-stretch mb-3 p-3 rounded border ${isFinished ? 'border-secondary' : (isPreparing ? 'border-warning' : 'border-success')}`;
                            card.style.backgroundColor = isFinished ? 'rgba(30,30,30,0.4)' : 'rgba(255,255,255,0.03)';
                            card.style.opacity = isFinished ? '0.75' : '1.0';

                            card.innerHTML = `
                                <div class="d-flex align-items-center justify-content-between w-100">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="racing-player-avatar" style="border: 2px solid ${isFinished ? '#666' : (isPreparing ? '#f59e0b' : '#22c55e')}; font-weight: bold; font-size: 16px; color: ${isFinished ? '#888' : '#fff'};">${initial}</div>
                                        <div class="racing-player-info">
                                            <div class="racing-player-name h6 mb-0 ${isFinished ? 'text-secondary' : 'text-light'} fw-bold">${escapeHtml(r.user || '-')}</div>
                                            <div class="racing-player-track small text-warning"><i class="fa-solid fa-map-pin me-1"></i>${escapeHtml(r.track || '-')}${r.type ? ` <span class="badge bg-secondary ms-1" style="font-size:10px;">${r.type}</span>` : ''}</div>
                                        </div>
                                    </div>
                                    <div class="text-end">
                                        <span class="racing-badge px-2 py-1 rounded small font-monospace fw-bold" style="${badgeStyle}">
                                            ${badgeHtml}
                                        </span>
                                        ${detailText}
                                    </div>
                                </div>
                                <div class="w-100 mt-2">
                                    <div class="d-flex justify-content-between align-items-center mb-1" style="font-size: 11px;">
                                        <span class="text-secondary font-monospace"><i class="fa-solid fa-flag-checkered me-1"></i>路線完跑進度</span>
                                        <span class="font-monospace ${isFinished ? 'text-muted' : 'text-info'} fw-bold">${progressPct.toFixed(1)}%</span>
                                    </div>
                                    <div class="progress" style="height: 7px; background: rgba(255,255,255,0.08);">
                                        <div class="progress-bar ${isFinished ? 'bg-secondary' : (isPreparing ? 'bg-warning' : 'bg-danger progress-bar-striped progress-bar-animated')}" style="width: ${progressPct}%;"></div>
                                    </div>
                                </div>
                            </div>`;
                        }).join('');
                    }

                    // Update timestamp
                    const updEl = document.getElementById('mon-list-updated');
                    if (updEl) updEl.textContent = '最後更新 ' + new Date().toLocaleTimeString('zh-TW', {hour:'2-digit',minute:'2-digit',second:'2-digit'});
                }

                // Update pending tracks
                const pList = document.getElementById('monitorPendingTracksList');
                if (pList) {
                    if ((res.data.pending_tracks || []).length > 0) {
                        pList.innerHTML = res.data.pending_tracks.map(t => `
                            <div class="d-flex justify-content-between align-items-center p-2 mb-2 border border-secondary rounded" style="background:rgba(20,24,50,0.6);">
                                <div>
                                    <strong class="text-warning">${escapeHtml(t.name)}</strong>
                                    <small class="d-block text-muted">建立者: ${t.creator_account || t.creator_id || '-'} | ${t.city || t.country || '-'}</small>
                                </div>
                                <div class="d-flex gap-1">
                                    <button class="btn btn-sm btn-success" onclick="reviewTrackAction(${t.id}, 'approve')"><i class="fa-solid fa-check"></i> 核准</button>
                                    <button class="btn btn-sm btn-outline-danger" onclick="reviewTrackAction(${t.id}, 'reject')"><i class="fa-solid fa-xmark"></i> 退件</button>
                                </div>
                            </div>
                        `).join('');
                    } else {
                        pList.innerHTML = '<div class="text-center text-muted py-3">目前無待審核自訂賽道</div>';
                    }
                }
            }
        } catch(e) { console.error('fetchMonitorData error:', e); }
    }

    // 4. 熱度與圖卡產生器 View (html2canvas)
    async function loadRegionStoryView() {
        const container = document.getElementById('view-region_story');
        container.innerHTML = `
            <div class="d-flex justify-content-between align-items-center mb-4">
                <h2><i class="fa-solid fa-fire text-danger"></i> 地區熱度分析與社群圖卡產生器</h2>
            </div>

            <div class="row g-4">
                <!-- 左側熱度資訊 -->
                <div class="col-md-7">
                    <div class="card bg-dark text-light border-secondary p-3 mb-4">
                        <h5 class="text-warning mb-3"><i class="fa-solid fa-map"></i> 台灣地區遊玩熱度方塊</h5>
                        <div class="row g-2" id="heatBoxGrid">
                            <div class="col-12 text-center text-muted">載入中...</div>
                        </div>
                    </div>
                </div>

                <!-- 右側限動圖卡預覽與導出 -->
                <div class="col-md-5 d-flex flex-column align-items-center">
                    <div id="storyCardExport" class="story-card-export p-4 d-flex flex-column justify-content-between mb-3 text-light">
                        <div>
                            <div class="d-flex justify-content-between align-items-center mb-4">
                                <h4 class="m-0 fw-bold text-danger"><i class="fa-solid fa-gauge-high"></i> REV-ON</h4>
                                <span class="badge bg-danger">HOT REGION</span>
                            </div>
                            <h3 class="fw-bold mb-1" id="storyRegionTitle">台北市 地頭蛇榜</h3>
                            <p class="text-muted fs-7">REV-ON LIVE LEADERBOARD</p>
                            <hr class="border-secondary mb-4">

                            <div id="storyLeaderboard">
                                <div class="d-flex justify-content-between align-items-center mb-3 p-2 rounded bg-secondary bg-opacity-25">
                                    <span>🥇 1st TaipeiDrifter</span>
                                    <span class="badge bg-warning text-dark">320 次</span>
                                </div>
                                <div class="d-flex justify-content-between align-items-center mb-3 p-2 rounded bg-secondary bg-opacity-25">
                                    <span>🥈 2nd NeoRacer</span>
                                    <span class="badge bg-light text-dark">280 次</span>
                                </div>
                                <div class="d-flex justify-content-between align-items-center mb-3 p-2 rounded bg-secondary bg-opacity-25">
                                    <span>🥉 3rd SpeedyG</span>
                                    <span class="badge bg-danger text-light">210 次</span>
                                </div>
                            </div>
                        </div>
                        <div class="text-center pt-3 border-top border-secondary">
                            <small class="text-muted font-monospace">@revon_official • revon.app</small>
                        </div>
                    </div>

                    <button class="btn btn-lg btn-danger shadow fw-bold w-75" onclick="exportStoryCardPng()"><i class="fa-solid fa-download me-2"></i> 下載社群限動 PNG</button>
                </div>
            </div>
        `;

        try {
            const res = await Api.getAnalytics();
            if (res.status === 'success') {
                const grid = document.getElementById('heatBoxGrid');
                grid.innerHTML = res.data.region_heat.map(h => `
                    <div class="col-md-4">
                        <div class="heat-box p-3 rounded">
                            <h6 class="m-0 text-light">${h.region}</h6>
                            <small class="text-danger fw-bold fs-5">${h.count} 次</small>
                        </div>
                    </div>
                `).join('');
            }
        } catch(e) {}
    }

    // 5. 強制排名快照 View
    async function loadSnapshotsView() {
        const container = document.getElementById('view-snapshots');
        container.innerHTML = `
            <div class="d-flex justify-content-between align-items-center mb-4">
                <h2><i class="fa-solid fa-camera text-danger"></i> 強制更新排名基準快照 (調校工具)</h2>
            </div>

            <div class="card bg-dark text-light border-secondary p-4 mb-4" style="max-width: 600px;">
                <h5 class="mb-3 text-warning">手動建立今日基準 Snapshot</h5>
                <div class="mb-3">
                    <label class="form-label">選擇賽道 ID</label>
                    <input type="number" id="snapTrackId" class="form-control bg-dark text-light border-secondary" value="1">
                </div>
                <div class="mb-4">
                    <label class="form-label">車輛類型</label>
                    <select id="snapVehicleType" class="form-select bg-dark text-light border-secondary">
                        <option value="car">汽車 (car)</option>
                        <option value="scooter">機車 (scooter)</option>
                    </select>
                </div>
                <button class="btn btn-danger fw-bold" onclick="triggerSnapshot()"><i class="fa-solid fa-bolt me-1"></i> 立即發動快照建立</button>
            </div>

            <div class="table-responsive">
                <table class="table table-dark table-striped table-hover rounded overflow-hidden">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>賽道 ID</th>
                            <th>車輛類型</th>
                            <th>建立者</th>
                            <th>快照建立時間</th>
                        </tr>
                    </thead>
                    <tbody id="snapshotTableBody">
                        <tr><td colspan="5" class="text-center">載入中...</td></tr>
                    </tbody>
                </table>
            </div>
        `;

        try {
            const res = await Api.getSnapshots();
            if (res.status === 'success') {
                const tbody = document.getElementById('snapshotTableBody');
                tbody.innerHTML = res.data.length ? res.data.map(s => `
                    <tr>
                        <td>${s.id}</td>
                        <td>${s.track_id}</td>
                        <td><span class="badge bg-secondary">${s.vehicle_type}</span></td>
                        <td>${s.created_by}</td>
                        <td>${s.created_at}</td>
                    </tr>
                `).join('') : '<tr><td colspan="5" class="text-center text-muted">無快照紀錄</td></tr>';
            }
        } catch(e) {}
    }

    // Modal & Event Functions
    window.showAddAnnouncementModal = async (type) => {
        const { value: formValues } = await Swal.fire({
            title: `新增${type==='marquee'?'跑馬燈':'內部'}公告`,
            html: `
                <input id="swal-ann-title" class="swal2-input bg-dark text-light" placeholder="公告標題">
                <textarea id="swal-ann-content" class="swal2-textarea bg-dark text-light" placeholder="詳細內容 (選填)"></textarea>
            `,
            background: '#1e1e1e',
            color: '#fff',
            showCancelButton: true,
            confirmButtonText: '新增',
            cancelButtonText: '取消',
            preConfirm: () => {
                return {
                    title: document.getElementById('swal-ann-title').value,
                    content: document.getElementById('swal-ann-content').value
                }
            }
        });

        if (formValues && formValues.title) {
            await Api.updateAnnouncement({ action: 'create', type, ...formValues });
            loadAnnouncementsData();
        }
    };

    window.deleteAnnouncement = async (id) => {
        await Api.updateAnnouncement({ action: 'delete', id });
        loadAnnouncementsData();
    };

    window.toggleAnnouncementActive = async (id, is_active) => {
        await Api.updateAnnouncement({ action: 'toggle', id, is_active });
        loadAnnouncementsData();
    };

    window.showUploadBannerModal = async () => {
        const { value: file } = await Swal.fire({
            title: '上傳 Banner (限制 5MB)',
            input: 'file',
            inputAttributes: {
                'accept': 'image/*',
                'aria-label': 'Upload banner image'
            },
            background: '#1e1e1e',
            color: '#fff',
            showCancelButton: true,
            confirmButtonText: '上傳',
            cancelButtonText: '取消'
        });

        if (file) {
            const formData = new FormData();
            formData.append('action', 'upload');
            formData.append('banner_file', file);
            
            try {
                const res = await fetch('../api/admin/banners.php', {
                    method: 'POST',
                    body: formData
                });
                const data = await res.json();
                if (data.status === 'success') {
                    showNotify('success', 'Banner 上傳成功');
                    loadBannersData();
                } else {
                    showNotify('error', '上傳失敗', data.message);
                }
            } catch(e) {
                showNotify('error', '錯誤', '上傳過程發生錯誤');
            }
        }
    };

    window.removeBanner = async (id) => {
        await Api.deleteBanner(id);
        loadBannersData();
    };

    window.openReplyReportModal = async (reportJsonStr) => {
        const r = JSON.parse(reportJsonStr);
        const { value: formValues } = await Swal.fire({
            title: `工單對話 #${r.ticket_no}`,
            html: `
                <div class="text-start mb-3">
                    <p class="mb-1"><strong>提問玩家：</strong>${r.user_account}</p>
                    <p class="mb-1"><strong>標題：</strong>${r.title}</p>
                    <div class="p-2 bg-dark rounded border border-secondary mb-3">${r.content}</div>
                </div>
                <textarea id="swal-reply-msg" class="swal2-textarea bg-dark text-light" placeholder="輸入管理員回覆內容">${r.admin_reply || ''}</textarea>
                <select id="swal-reply-status" class="swal2-select bg-dark text-light">
                    <option value="resolved" ${r.status==='resolved'?'selected':''}>標記為 已解決 (Resolved)</option>
                    <option value="processing" ${r.status==='processing'?'selected':''}>處理中 (Processing)</option>
                    <option value="closed" ${r.status==='closed'?'selected':''}>關閉工單 (Closed)</option>
                </select>
            `,
            background: '#1e1e1e',
            color: '#fff',
            showCancelButton: true,
            confirmButtonText: '送出回覆',
            cancelButtonText: '取消',
            preConfirm: () => {
                return {
                    reply: document.getElementById('swal-reply-msg').value,
                    status: document.getElementById('swal-reply-status').value
                }
            }
        });

        if (formValues) {
            await Api.replyReport({ report_id: r.id, ...formValues });
            loadReportsView();
        }
    };

    window.reviewTrackAction = async (trackId, action) => {
        await Api.reviewTrack({ action, track_id: trackId });
        fetchMonitorData();
    };

    window.exportStoryCardPng = () => {
        const cardNode = document.getElementById('storyCardExport');
        if (cardNode && window.html2canvas) {
            html2canvas(cardNode, { backgroundColor: '#0d0d0d' }).then(canvas => {
                const link = document.createElement('a');
                link.download = `revon_story_card_${Date.now()}.png`;
                link.href = canvas.toDataURL('image/png');
                link.click();
            });
        } else {
            showNotify('warning', '提示', 'html2canvas 載入中，請稍後重試');
        }
    };

    window.triggerSnapshot = async () => {
        const trackId = document.getElementById('snapTrackId').value;
        const vehicleType = document.getElementById('snapVehicleType').value;

        const res = await Api.createSnapshot({ track_id: parseInt(trackId), vehicle_type: vehicleType });
        if (res.status === 'success') {
            showNotify('success', '快照建立成功', res.message);
            loadSnapshotsView();
        }
    };
});

window.triggerImportTrackSql = function() {
    var fileInput = document.getElementById('sqlFileInput');
    if (fileInput) {
        fileInput.click();
    } else {
        alert('找不到檔案上傳元件 sqlFileInput');
    }
};

window.handleTrackSqlFileUpload = async function(inputEl) {
    if (!inputEl.files || inputEl.files.length === 0) return;
    var file = inputEl.files[0];
    var isCustom = document.getElementById('trackTypeSelect')?.value === 'custom';
    var targetDesc = isCustom ? '玩家自訂賽道 (custom_tracks)' : '官方賽道 (tracks)';

    var confirm = await Swal.fire({
        title: '匯入 SQL 賽道資料庫',
        html: `確定要匯入 <b>${escapeHtml(file.name)}</b> 至 [${targetDesc}] 嗎？<br><small class="text-muted">已有同名或相同紀錄之資料將會自動跳過，防止重複。</small>`,
        icon: 'question',
        showCancelButton: true,
        confirmButtonText: '開始匯入',
        cancelButtonText: '取消',
        background: '#1e1e1e',
        color: '#fff'
    });

    if (confirm.isConfirmed) {
        Swal.fire({
            title: 'SQL 匯入中...',
            text: '正在解析並比對資料庫紀錄，請稍候',
            allowOutsideClick: false,
            didOpen: () => Swal.showLoading(),
            background: '#1e1e1e',
            color: '#fff'
        });

        try {
            var res = await Api.uploadTrackSql(file, isCustom);
            if (res.status === 'success') {
                var imported = Number(res.data && res.data.imported) || 0;
                var skipped = Number(res.data && res.data.skipped) || 0;
                var failedList = (res.data && res.data.failed_list) || [];
                var importSummary = `<strong>成功寫入/更新：<span class="text-success">${imported}</span> 筆賽道</strong>${skipped > 0 ? ` ｜ 未新增/失敗：<span class="text-danger">${skipped}</span> 筆` : ''}`;
                showNotify(skipped > 0 ? 'warning' : 'success', 'SQL 匯入完成', `匯入處理完畢：新增/更新 ${imported} 筆賽道${skipped > 0 ? `，失敗/未新增 ${skipped} 筆` : ''}`, 6000);

                var failedHtml = '';
                if (failedList.length > 0) {
                    failedHtml = `
                        <div class="mt-3 text-start">
                            <h6 class="text-danger fw-bold"><i class="fa-solid fa-triangle-exclamation me-1"></i> 未新增 / 失敗項目列表 (${failedList.length} 筆)：</h6>
                            <div class="table-responsive style-scrollbar" style="max-height: 260px; font-size: 12px; background: #121212; border: 1px solid #333; border-radius: 6px; padding: 6px;">
                                <table class="table table-dark table-sm table-striped mb-0">
                                    <thead>
                                        <tr>
                                            <th style="width: 45%;">賽道紀錄 / 語句</th>
                                            <th style="width: 55%;">未新增原因 / 錯誤訊息</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        ${failedList.map(item => `
                                            <tr>
                                                <td class="font-monospace text-warning text-break">${escapeHtml(item.statement)}</td>
                                                <td class="text-danger text-break">${escapeHtml(item.error)}</td>
                                            </tr>
                                        `).join('')}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    `;
                }

                Swal.fire({
                    title: 'SQL 匯入處理完成',
                    html: `<div>${importSummary}</div>${failedHtml}`,
                    icon: skipped > 0 ? 'warning' : 'success',
                    width: failedList.length > 0 ? '780px' : '500px',
                    background: '#1e1e1e',
                    color: '#fff'
                });
                if (typeof loadTracksView === 'function') loadTracksView();
            } else {
                Swal.fire({
                    title: '匯入失敗',
                    text: res.message || '格式錯誤或 SQL 執行異常',
                    icon: 'error',
                    background: '#1e1e1e',
                    color: '#fff'
                });
            }
        } catch (err) {
            console.error(err);
            Swal.fire({
                title: '匯入失敗',
                text: err.message || '網路通訊錯誤',
                icon: 'error',
                background: '#1e1e1e',
                color: '#fff'
            });
        } finally {
            inputEl.value = '';
        }
    } else {
        inputEl.value = '';
    }
};

window.loadSystemLogsView = async function(search) {
    if (search === undefined) search = '';
    var container = document.getElementById('view-system_logs');
    if (!container) return;

    container.innerHTML = `
        <div class="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-2">
            <h2><i class="fa-solid fa-bug text-danger"></i> 系統與 SQL 錯誤 Log 監控中樞</h2>
            <div class="d-flex align-items-center gap-2">
                <div class="input-group" style="width: 260px;">
                    <span class="input-group-text bg-dark text-muted border-secondary"><i class="fa-solid fa-magnifying-glass"></i></span>
                    <input type="text" id="logSearchInput" class="form-control bg-dark text-light border-secondary" placeholder="搜尋 API Context/錯誤訊息" value="${escapeHtml(search)}" onkeyup="if(event.key==='Enter') loadSystemLogsView(this.value)">
                </div>
                <button class="btn btn-outline-light" onclick="loadSystemLogsView(document.getElementById('logSearchInput').value)">
                    <i class="fa-solid fa-rotate me-1"></i> 刷新
                </button>
                <button class="btn btn-danger fw-bold" onclick="clearSystemLogs()">
                    <i class="fa-solid fa-trash me-1"></i> 清空 Log 紀錄
                </button>
            </div>
        </div>

        <div class="card bg-dark text-light border-secondary shadow mb-4">
            <div class="card-header border-secondary d-flex justify-content-between align-items-center">
                <span class="fw-bold"><i class="fa-solid fa-list text-warning me-2"></i>最新即時 API / SQL 錯誤列表</span>
                <small class="text-muted">自動攔截包含 PDOException、Fatal error 及 sendError 回傳之紀錄</small>
            </div>
            <div class="card-body p-0">
                <div class="table-responsive">
                    <table class="table table-dark table-striped table-hover mb-0 align-middle">
                        <thead>
                            <tr>
                                <th style="width: 70px;">ID</th>
                                <th style="width: 140px;">API 模組 (Context)</th>
                                <th style="width: 150px;">錯誤類型</th>
                                <th>詳細錯誤原因 (Message)</th>
                                <th style="width: 200px;">發生位置 (File:Line)</th>
                                <th style="width: 160px;">時間</th>
                            </tr>
                        </thead>
                        <tbody id="logsTableBody">
                            <tr><td colspan="6" class="text-center py-4 text-muted">載入中...</td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    `;

    if (!window.systemLogsTimer) {
        window.systemLogsTimer = setInterval(() => {
            const activeView = document.querySelector('.view-section.active');
            if (activeView && activeView.id === 'view-system_logs') {
                const searchVal = document.getElementById('logSearchInput')?.value || '';
                loadSystemLogsView(searchVal);
            } else {
                clearInterval(window.systemLogsTimer);
                window.systemLogsTimer = null;
            }
        }, 5000);
    }

    try {
        var res = await Api.getLogs(search);
        var tbody = document.getElementById('logsTableBody');
        if (res.status === 'success' && res.data) {
            var dbLogs = res.data.db_logs || [];
            var fileLogs = res.data.file_logs || [];
            var allLogs = [...dbLogs, ...fileLogs];

            if (allLogs.length === 0) {
                tbody.innerHTML = `<tr><td colspan="6" class="text-center py-4 text-success"><i class="fa-solid fa-circle-check me-2"></i>目前完全無系統錯誤紀錄！系統運行正常。</td></tr>`;
                return;
            }

            tbody.innerHTML = allLogs.map(function(l) {
                return `
                <tr>
                    <td><span class="badge bg-secondary">#${escapeHtml(l.id)}</span></td>
                    <td><span class="badge bg-danger">${escapeHtml(l.context || 'general')}</span></td>
                    <td class="text-warning font-monospace small">${escapeHtml(l.error_type || 'Error')}</td>
                    <td>
                        <div class="fw-bold text-light mb-1">${escapeHtml(l.message || '無訊息')}</div>
                        ${l.sql_state ? `<small class="text-danger font-monospace">SQLSTATE: ${escapeHtml(l.sql_state)}</small>` : ''}
                    </td>
                    <td class="small font-monospace text-muted text-break">
                        ${escapeHtml(l.file || 'internal')}:${l.line || 0}
                    </td>
                    <td class="small text-muted">${escapeHtml(l.created_at || '')}</td>
                </tr>
                `;
            }).join('');
        } else {
            tbody.innerHTML = `<tr><td colspan="6" class="text-center py-4 text-danger">載入失敗：${escapeHtml(res.message || '未知錯誤')}</td></tr>`;
        }
    } catch (e) {
        console.error('Failed to load logs', e);
        showNotify('error', '載入失敗', '連線異常');
    }
};

window.clearSystemLogs = async function() {
    var confirm = await Swal.fire({
        title: '清空 Log 紀錄',
        text: '確定要清除資料庫與日誌檔案中的所有錯誤紀錄嗎？',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonText: '確定清空',
        cancelButtonText: '取消',
        background: '#1e1e1e',
        color: '#fff'
    });

    if (confirm.isConfirmed) {
        var res = await Api.clearLogs();
        if (res.status === 'success') {
            showNotify('success', 'Log 已清空');
            loadSystemLogsView();
        } else {
            showNotify('error', '清空失敗', res.message);
        }
    }
};
