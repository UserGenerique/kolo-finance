/* ============================================
   Kolo Finance — API Service
   ============================================ */

const Api = {
    /** Get stored auth info */
    getAuth() {
        return {
            token: sessionStorage.getItem('authToken'),
            key: sessionStorage.getItem('apiKey'),
            userType: sessionStorage.getItem('userType'),
            userName: sessionStorage.getItem('userName'),
            phoneNumber: sessionStorage.getItem('phoneNumber'),
            orgId: sessionStorage.getItem('orgId'),
            orgName: sessionStorage.getItem('orgName'),
            role: sessionStorage.getItem('role'),
            organizations: JSON.parse(sessionStorage.getItem('organizations') || '[]')
        };
    },

    /** Check if user is logged in */
    isLoggedIn() {
        const { token, key, orgId, userType } = this.getAuth();
        if (userType === 'PLATFORM_ADMIN') return !!token;
        return !!((token || key) && orgId);
    },

    isPlatformAdmin() {
        const { token, userType } = this.getAuth();
        return !!token && userType === 'PLATFORM_ADMIN';
    },

    canManageOrganization() {
        return ['BOSS', 'MANAGER'].includes(this.getAuth().role);
    },

    canManageFunds() {
        return ['BOSS', 'MANAGER'].includes(this.getAuth().role);
    },

    storeLogin(response, organization = null) {
        sessionStorage.setItem('authToken', response.token);
        sessionStorage.setItem('userType', response.userType);
        sessionStorage.setItem('userName', response.name || '');
        sessionStorage.setItem('phoneNumber', response.phoneNumber || '');
        sessionStorage.setItem('organizations', JSON.stringify(response.organizations || []));
        if (organization) {
            this.selectOrganization(organization);
        }
    },

    selectOrganization(organization) {
        sessionStorage.setItem('orgId', organization.id);
        sessionStorage.setItem('orgName', organization.name);
        sessionStorage.setItem('role', organization.role || '');
    },

    /** Logout */
    async logout() {
        const { token } = this.getAuth();
        if (token) {
            await fetch('/api/auth/logout', {
                method: 'POST',
                headers: { Authorization: `Bearer ${token}` }
            }).catch(() => null);
        }
        sessionStorage.clear();
        window.location.href = '/login.html';
    },

    /** Base fetch with Bearer token or API key fallback */
    async request(path, options = {}) {
        const { token, key } = this.getAuth();
        const headers = { ...(options.headers || {}) };
        if (!(options.body instanceof FormData)) {
            headers['Content-Type'] = headers['Content-Type'] || 'application/json';
        }
        if (token) {
            headers.Authorization = `Bearer ${token}`;
        } else if (key) {
            headers['X-API-Key'] = key;
        }

        const res = await fetch(path, { ...options, headers });
        if (res.status === 401) {
            await this.logout();
            throw new Error('Session expirée');
        }
        if (!res.ok) {
            const err = await res.json().catch(() => ({ error: 'Erreur serveur' }));
            throw new Error(err.error || err.message || `Erreur ${res.status}`);
        }
        if (res.status === 204) return null;
        return res.json();
    },

    login(phoneNumber, password) {
        return fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ phoneNumber, password })
        }).then(async res => {
            if (!res.ok) {
                const err = await res.json().catch(() => ({ error: 'Connexion impossible' }));
                throw new Error(err.error || err.message || 'Téléphone ou mot de passe invalide');
            }
            return res.json();
        });
    },

    /** Org-scoped base path */
    basePath() {
        return `/api/organizations/${this.getAuth().orgId}`;
    },

    /** Build query string from non-empty params */
    toQuery(params = {}) {
        const qs = new URLSearchParams();
        Object.entries(params).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== '') {
                qs.set(key, value);
            }
        });
        const value = qs.toString();
        return value ? `?${value}` : '';
    },

    // ---- Platform ----
    getPlatformOrganizations() {
        return this.request('/api/platform/organizations');
    },

    createPlatformOrganization(data) {
        return this.request('/api/platform/organizations', {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    updateOrganizationSubscription(id, data) {
        return this.request(`/api/platform/organizations/${id}/subscription`, {
            method: 'PUT',
            body: JSON.stringify(data)
        });
    },

    // ---- Dashboard ----
    getDashboard(params = {}) {
        return this.request(`${this.basePath()}/dashboard${this.toQuery(params)}`);
    },

    // ---- Expenses ----
    getExpenses(params = {}) {
        return this.request(`${this.basePath()}/expenses${this.toQuery(params)}`);
    },

    // ---- Drafts ----
    getDrafts() {
        return this.request(`${this.basePath()}/drafts`);
    },

    // ---- Reports ----
    generateReport(params = {}) {
        return this.request(`${this.basePath()}/reports/generate${this.toQuery(params)}`);
    },

    sendReport(data) {
        return this.request(`${this.basePath()}/reports/send`, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    // ---- Users ----
    getUsers() {
        return this.request(`${this.basePath()}/users`);
    },

    createUser(data) {
        return this.request(`${this.basePath()}/users`, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    updateUser(id, data) {
        return this.request(`${this.basePath()}/users/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data)
        });
    },

    setUserActive(id, active) {
        return this.request(`${this.basePath()}/users/${id}/${active ? 'activate' : 'deactivate'}`, {
            method: 'POST',
            body: JSON.stringify({})
        });
    },

    // ---- Funds ----
    getFunds() {
        return this.request(`${this.basePath()}/funds`);
    },

    createFund(data) {
        return this.request(`${this.basePath()}/funds`, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    topUpFund(id, data) {
        return this.request(`${this.basePath()}/funds/${id}/top-up`, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    getPendingReceipts() {
        return this.request(`${this.basePath()}/funds/pending-receipts`);
    },

    acceptFundReceipt(id, note = '') {
        return this.request(`${this.basePath()}/funds/${id}/accept-receipt`, {
            method: 'POST',
            body: JSON.stringify({ note })
        });
    },

    rejectFundReceipt(id, note = '') {
        return this.request(`${this.basePath()}/funds/${id}/reject-receipt`, {
            method: 'POST',
            body: JSON.stringify({ note })
        });
    }
};
