/* ============================================
   Kolo Finance — App Controller
   ============================================ */

const App = {
    currentView: 'dashboard',

    /** Routes mapping */
    routes: {
        dashboard: { view: DashboardView, title: 'Tableau de bord', icon: 'bi-grid' },
        expenses:  { view: ExpensesView,  title: 'Dépenses',        icon: 'bi-receipt' },
        reports:   { view: ReportsView,   title: 'Rapports',        icon: 'bi-file-earmark-bar-graph' },
        agents:    { view: AgentsView,    title: 'Utilisateurs',    icon: 'bi-people' },
        funds:     { view: FundsView,     title: 'Fonds',           icon: 'bi-wallet2' },
        pendingFunds: { view: PendingFundsView, title: 'Affectations', icon: 'bi-check2-square' }
    },

    /** Initialize app */
    init() {
        if (!Api.isLoggedIn()) {
            sessionStorage.setItem('postLoginRedirect', window.location.href);
            window.location.href = '/login.html';
            return;
        }
        if (Api.isPlatformAdmin() && !Api.getAuth().orgId) {
            window.location.href = '/super-admin.html';
            return;
        }

        // Set org name in sidebar
        const auth = Api.getAuth();
        document.getElementById('orgName').textContent = auth.orgName;
        document.getElementById('userName').textContent = auth.userName || auth.phoneNumber || '—';
        document.getElementById('userRole').textContent = auth.role || '—';
        this.applyRoleVisibility();

        // Setup sidebar navigation
        document.querySelectorAll('.nav-item[data-view]').forEach(item => {
            item.addEventListener('click', () => {
                this.navigate(item.dataset.view);
                this.closeSidebar();
            });
        });

        // Mobile sidebar toggle
        document.getElementById('sidebarToggle').addEventListener('click', () => this.toggleSidebar());
        document.getElementById('sidebarOverlay').addEventListener('click', () => this.closeSidebar());

        // Logout
        document.getElementById('logoutBtn').addEventListener('click', () => Api.logout());

        // Load initial view from hash or default
        const queryView = new URLSearchParams(window.location.search).get('view');
        const hash = window.location.hash.replace('#', '');
        this.navigate(queryView && this.routes[queryView] ? queryView : (hash && this.routes[hash] ? hash : 'dashboard'));

        // Handle hash changes
        window.addEventListener('hashchange', () => {
            const h = window.location.hash.replace('#', '');
            if (h && this.routes[h] && h !== this.currentView) {
                this.navigate(h);
            }
        });
    },

    /** Navigate to a view */
    navigate(viewName) {
        if (!this.isRouteAllowed(viewName)) {
            viewName = 'pendingFunds';
        }
        const route = this.routes[viewName];
        if (!route) return;

        this.currentView = viewName;
        window.location.hash = viewName;

        // Update active nav
        document.querySelectorAll('.nav-item[data-view]').forEach(item => {
            item.classList.toggle('active', item.dataset.view === viewName);
        });

        // Update topbar title
        document.getElementById('pageTitle').innerHTML =
            `<i class="bi ${route.icon}"></i> ${route.title}`;

        // Render view
        const container = document.getElementById('pageContent');
        route.view.render(container);
    },

    /** Sidebar toggle for mobile */
    toggleSidebar() {
        document.getElementById('sidebar').classList.toggle('open');
        document.getElementById('sidebarOverlay').classList.toggle('open');
    },

    closeSidebar() {
        document.getElementById('sidebar').classList.remove('open');
        document.getElementById('sidebarOverlay').classList.remove('open');
    },

    applyRoleVisibility() {
        if (Api.canManageOrganization()) return;
        document.querySelectorAll('[data-view]').forEach(item => {
            item.classList.toggle('d-none', !['expenses', 'pendingFunds'].includes(item.dataset.view));
        });
    },

    isRouteAllowed(viewName) {
        if (Api.canManageOrganization()) return true;
        return ['expenses', 'pendingFunds'].includes(viewName);
    }
};

// Boot
document.addEventListener('DOMContentLoaded', () => App.init());
