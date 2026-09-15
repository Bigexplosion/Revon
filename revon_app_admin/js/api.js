// ==========================================
// API 設定：固定指向 Synology 遠端伺服器
// ==========================================
const API_BASE_URL = 'https://revon88.synology.me/revon_android/api/';

const Api = {
    getToken() {
        return localStorage.getItem('revon_admin_token');
    },

    setToken(token) {
        localStorage.setItem('revon_admin_token', token);
    },

    clearToken() {
        localStorage.removeItem('revon_admin_token');
    },

    async request(endpoint, method = 'GET', body = null) {
        const headers = {
            'Accept': 'application/json'
        };

        const token = this.getToken();
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        const options = { method, headers };

        if (body) {
            headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(body);
        }

        try {
            const response = await fetch(API_BASE_URL + endpoint, options);
            const data = await response.json();
            
            // 若前端收到 API 錯誤回應 (status === 'error') 且非 log 查詢本身，嘗試自動通報補存 error_logs
            if (data && data.status === 'error' && !endpoint.startsWith('admin/logs.php')) {
                fetch(API_BASE_URL + 'admin/logs.php', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', ...(token ? { 'Authorization': `Bearer ${token}` } : {}) },
                    body: JSON.stringify({
                        action: 'log_client_error',
                        context: 'admin_frontend/' + endpoint,
                        error_type: data.debug?.type || 'ApiErrorResponse',
                        message: data.message || 'API 操作失敗',
                        file: data.debug?.file || endpoint,
                        line: data.debug?.line || 0
                    })
                }).catch(() => {});
            }
            return data;
        } catch (err) {
            console.error(`API Request Error [${endpoint}]:`, err);
            // 網路離線或 JSON 解析失敗等前端 Exception 自動紀錄至後台
            if (!endpoint.startsWith('admin/logs.php')) {
                fetch(API_BASE_URL + 'admin/logs.php', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', ...(token ? { 'Authorization': `Bearer ${token}` } : {}) },
                    body: JSON.stringify({
                        action: 'log_client_error',
                        context: 'admin_frontend_network/' + endpoint,
                        error_type: 'FrontendNetworkError',
                        message: err.message || '網路通訊異常或 JSON 解析失敗',
                        file: endpoint,
                        line: 0
                    })
                }).catch(() => {});
            }
            throw err;
        }
    },

    // Auth
    async login(account, password) {
        return this.request('auth/login.php', 'POST', { account, password });
    },

    // 取得概覽統計
    async getStats() {
        return this.request('admin/stats.php', 'GET');
    },

    // Users Management
    async getUsers() {
        return this.request('admin/users.php', 'GET');
    },
    async updateUser(data) {
        return this.request('admin/users.php', 'POST', data);
    },

    async getTracks(type = 'official', order = 'ASC', search = '') {
        return this.request(`admin/tracks.php?type=${type}&order=${order}&search=${encodeURIComponent(search)}`, 'GET');
    },
    async createTrack(data) {
        return this.request('admin/tracks.php', 'POST', { action: 'create', ...data });
    },
    async updateTrack(data) {
        return this.request('admin/tracks.php', 'POST', data);
    },
    async deleteTrack(trackId, isCustom = false) {
        return this.request('admin/tracks.php', 'POST', { action: 'delete', track_id: trackId, is_custom: isCustom });
    },

    // Clubs Management
    async getAdminClubs(order = 'DESC') {
        return this.request(`admin/clubs.php?order=${order}`, 'GET');
    },
    async createClub(data) {
        return this.request('admin/clubs.php', 'POST', { action: 'create', ...data });
    },
    async updateClub(data) {
        return this.request('admin/clubs.php', 'POST', { action: 'update', ...data });
    },
    async deleteClub(id) {
        return this.request('admin/clubs.php', 'POST', { action: 'delete', id });
    },
    async getClubMembers(clubId) {
        return this.request(`admin/clubs.php?action=members&club_id=${encodeURIComponent(clubId)}`, 'GET');
    },
    async removeClubMember(clubId, userId) {
        return this.request('admin/clubs.php', 'POST', { action: 'remove_member', club_id: clubId, user_id: userId });
    },
    async deleteTrack(trackId, isCustom = false) {
        return this.request('admin/tracks.php', 'POST', { action: 'delete', track_id: trackId, is_custom: isCustom ? 1 : 0 });
    },
    async batchDeleteTracks(trackIds, isCustom = false) {
        return this.request('admin/tracks.php', 'POST', { action: 'batch_delete', track_ids: trackIds, is_custom: isCustom ? 1 : 0 });
    },
    async getRecords() {
        return this.request('admin/records.php', 'GET');
    },
    async getTelemetryLog(id) {
        return this.request(`admin/records.php?action=telemetry&id=${id}`, 'GET');
    },
    async deleteRecord(id) {
        return this.request(`admin/records.php?id=${id}`, 'DELETE');
    },
    async batchDeleteRecords(recordIds) {
        return this.request('admin/records.php', 'POST', { action: 'batch_delete', record_ids: recordIds });
    },

    // Content Management (Announcements, Banners, News)
    async getAnnouncements(type = '') {
        return this.request(`admin/announcements.php?type=${type}`, 'GET');
    },
    async updateAnnouncement(data) {
        return this.request('admin/announcements.php', 'POST', data);
    },
    async getBanners() {
        return this.request('admin/banners.php', 'GET');
    },
    async deleteBanner(id) {
        return this.request('admin/banners.php', 'POST', { action: 'delete', id });
    },
    async getNews() {
        return this.request('admin/news.php', 'GET');
    },
    async deleteNews(id) {
        return this.request('admin/news.php', 'POST', { action: 'delete', id });
    },

    // Reports / Tickets Management
    async getReports(status = '', category = '') {
        return this.request(`admin/reports.php?status=${status}&category=${category}`, 'GET');
    },
    async replyReport(data) {
        return this.request('admin/reports.php', 'POST', data);
    },

    // Monitor Operations
    async getMonitorData() {
        return this.request('admin/monitor.php', 'GET');
    },
    async reviewTrack(data) {
        return this.request('admin/monitor.php', 'POST', data);
    },

    // Analytics / Region Story
    async getAnalytics(timeframe = '7days') {
        return this.request(`admin/analytics.php?timeframe=${timeframe}`, 'GET');
    },

    // Snapshots Generator
    async getSnapshots() {
        return this.request('admin/snapshots.php', 'GET');
    },
    async createSnapshot(data) {
        return this.request('admin/snapshots.php', 'POST', data);
    },

    // Coupons
    async getCoupons() {
        return this.request('admin/coupons.php', 'GET');
    },
    async createCoupon(data) {
        return this.request('admin/coupons.php', 'POST', data);
    },
    async deleteCoupon(id) {
        return this.request(`admin/coupons.php?id=${id}`, 'DELETE');
    },

    // System Logs
    async getLogs(search = '') {
        return this.request(`admin/logs.php?search=${encodeURIComponent(search)}`, 'GET');
    },
    async clearLogs() {
        return this.request('admin/logs.php', 'POST', { action: 'clear' });
    },

    // SQL Track Import
    async uploadTrackSql(file, isCustom = false) {
        const formData = new FormData();
        formData.append('action', 'import_sql');
        formData.append('is_custom', isCustom ? '1' : '0');
        formData.append('sql_file', file);

        const headers = {};
        const token = this.getToken();
        if (token) headers['Authorization'] = `Bearer ${token}`;

        const res = await fetch(API_BASE_URL + 'admin/tracks.php', {
            method: 'POST',
            headers,
            body: formData
        });
        return await res.json();
    }
};
