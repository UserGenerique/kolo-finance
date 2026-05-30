/* ============================================
   Kolo Finance — API Service
   ============================================ */

const Api = {
    /** Get stored auth info */
    getAuth() {
        return {
            key: sessionStorage.getItem('apiKey'),
            orgId: sessionStorage.getItem('orgId'),
            orgName: sessionStorage.getItem('orgName')
        };
    },

    /** Check if user is logged in */
    isLoggedIn() {
        const { key, orgId } = this.getAuth();
        return !!(key && orgId);
    },

    /** Logout */
    logout() {
        sessionStorage.clear();
        window.location.href = '/login.html';
    },

    /** Base fetch with API key header */
    async request(path, options = {}) {
        const { key } = this.getAuth();
        const res = await fetch(path, {
            ...options,
            headers: {
                'Content-Type': 'application/json',
                'X-API-Key': key,
                ...(options.headers || {})
            }
        });
        if (res.status === 401) {
            this.logout();
            throw new Error('Session expirée');
        }
        if (!res.ok) {
            const err = await res.json().catch(() => ({ error: 'Erreur serveur' }));
            throw new Error(err.error || err.message || `Erreur ${res.status}`);
        }
        return res.json();
    },

    /** Org-scoped base path */
    basePath() {
        return `/api/organizations/${this.getAuth().orgId}`;
    },

    // ---- Dashboard ----
    getDashboard() {
        return this.request(`${this.basePath()}/dashboard`);
    },

    // ---- Expenses ----
    getExpenses() {
        return this.request(`${this.basePath()}/expenses`);
    },

    // ---- Drafts ----
    getDrafts() {
        return this.request(`${this.basePath()}/drafts`);
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

    // ---- Funds ----
    getFunds() {
        return this.request(`${this.basePath()}/funds`);
    },

    createFund(data) {
        return this.request(`${this.basePath()}/funds`, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    }
};
