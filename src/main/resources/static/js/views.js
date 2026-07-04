/* ============================================
   Kolo Finance — View Renderers
   ============================================ */

const Fmt = {
    amount(val) {
        return new Intl.NumberFormat('fr-FR').format(val || 0);
    },
    percent(val) {
        const n = Number(val || 0);
        return `${n > 0 ? '+' : ''}${n.toFixed(1).replace('.0', '')}%`;
    },
    date(val) {
        if (!val) return '—';
        return new Date(val).toLocaleDateString('fr-FR', {
            day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
        });
    },
    dateShort(val) {
        if (!val) return '—';
        return new Date(val).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' });
    },
    inputDate(date) {
        return date.toISOString().slice(0, 10);
    },
    html(val) {
        return String(val ?? '').replace(/[&<>"']/g, ch => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;'
        }[ch]));
    }
};

const ChartManager = {
    instances: {},
    destroyAll() {
        Object.values(this.instances).forEach(chart => chart?.destroy());
        this.instances = {};
    },
    render(id, type, data, options = {}) {
        if (!window.Chart) return;
        const canvas = document.getElementById(id);
        if (!canvas) return;
        if (this.instances[id]) this.instances[id].destroy();
        this.instances[id] = new Chart(canvas, { type, data, options });
    }
};

const ViewHelpers = {
    agentOptions(users, includeAll = true) {
        const rows = users.filter(u => u.role === 'AGENT')
            .map(u => `<option value="${u.id}">${Fmt.html(u.name)}</option>`).join('');
        return includeAll ? `<option value="">Tous les agents</option>${rows}` : rows;
    },
    fundOptions(funds, includeAll = true) {
        const rows = funds.map(f => `<option value="${f.id}">${Fmt.html(f.description || ('Fonds #' + f.id))} — ${Fmt.html(f.agent?.name || '—')}</option>`).join('');
        return includeAll ? `<option value="">Tous les fonds</option>${rows}` : rows;
    },
    categoryOptions(categories, includeAll = true) {
        const rows = categories.map(c => `<option value="${Fmt.html(c)}">${Fmt.html(c)}</option>`).join('');
        return includeAll ? `<option value="">Toutes catégories</option>${rows}` : rows;
    },
    fundStatusLabel(status) {
        return {
            PENDING_RECEIPT: 'En attente réception',
            ACTIVE: 'Actif',
            REJECTED: 'Rejeté',
            CLOSED: 'Clôturé'
        }[status || 'ACTIVE'] || status || 'Actif';
    },
    fundStatusClass(status) {
        return {
            PENDING_RECEIPT: 'warning',
            ACTIVE: 'success',
            REJECTED: 'danger',
            CLOSED: 'secondary'
        }[status || 'ACTIVE'] || 'secondary';
    },
    compactFilters(params) {
        return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''));
    }
};

/* ---- DASHBOARD ---- */
const DashboardView = {
    users: [],
    funds: [],
    categories: ['CARBURANT', 'TRANSPORT', 'MATERIAUX', 'NOURRITURE', 'PIECES', 'DIVERS'],

    async render(container) {
        ChartManager.destroyAll();
        container.innerHTML = '<div class="spinner-wrapper"><div class="spinner-border text-primary"></div></div>';
        try {
            const [users, funds] = await Promise.all([Api.getUsers(), Api.getFunds()]);
            this.users = users;
            this.funds = funds;
            container.innerHTML = this.template();
            this.bindEvents();
            await this.loadAnalytics();
        } catch (err) {
            container.innerHTML = `<div class="alert alert-danger">${Fmt.html(err.message)}</div>`;
        }
    },

    template() {
        return `
            <div class="kf-filter-panel mb-4">
                <div class="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-3">
                    <div>
                        <div class="eyebrow">Centre de pilotage</div>
                        <h4 class="m-0">Analyse des fonds confiés</h4>
                    </div>
                    <div class="quick-filters">
                        <button class="quick-filter active" data-period="month">Ce mois</button>
                        <button class="quick-filter" data-period="today">Aujourd’hui</button>
                        <button class="quick-filter" data-period="7d">7 jours</button>
                        <button class="quick-filter" data-period="30d">30 jours</button>
                        <button class="quick-filter" data-period="all">Tout</button>
                    </div>
                </div>
                <div class="row g-2 align-items-end">
                    <div class="col-sm-6 col-lg-2">
                        <label class="form-label">Période</label>
                        <select class="form-select" id="dashPeriod">
                            <option value="month" selected>Ce mois</option>
                            <option value="today">Aujourd’hui</option>
                            <option value="yesterday">Hier</option>
                            <option value="7d">7 derniers jours</option>
                            <option value="30d">30 derniers jours</option>
                            <option value="last_month">Mois dernier</option>
                            <option value="all">Toutes périodes</option>
                            <option value="custom">Dates personnalisées</option>
                        </select>
                    </div>
                    <div class="col-sm-6 col-lg-2">
                        <label class="form-label">Début</label>
                        <input type="date" class="form-control" id="dashStartDate">
                    </div>
                    <div class="col-sm-6 col-lg-2">
                        <label class="form-label">Fin</label>
                        <input type="date" class="form-control" id="dashEndDate">
                    </div>
                    <div class="col-sm-6 col-lg-2">
                        <label class="form-label">Agent</label>
                        <select class="form-select" id="dashAgent">${ViewHelpers.agentOptions(this.users)}</select>
                    </div>
                    <div class="col-sm-6 col-lg-2">
                        <label class="form-label">Catégorie</label>
                        <select class="form-select" id="dashCategory">${ViewHelpers.categoryOptions(this.categories)}</select>
                    </div>
                    <div class="col-sm-6 col-lg-2">
                        <label class="form-label">Fonds</label>
                        <select class="form-select" id="dashFund">${ViewHelpers.fundOptions(this.funds)}</select>
                    </div>
                    <div class="col-sm-6 col-lg-2">
                        <label class="form-label">Montant min</label>
                        <input type="number" class="form-control" id="dashMinAmount" min="0">
                    </div>
                    <div class="col-sm-6 col-lg-2">
                        <label class="form-label">Montant max</label>
                        <input type="number" class="form-control" id="dashMaxAmount" min="0">
                    </div>
                    <div class="col-sm-8 col-lg-4">
                        <label class="form-label">Recherche</label>
                        <input type="search" class="form-control" id="dashSearch" placeholder="description, agent, fonds...">
                    </div>
                    <div class="col-sm-4 col-lg-2 d-grid">
                        <button class="btn-kf-outline" id="dashReset"><i class="bi bi-arrow-counterclockwise"></i> Réinitialiser</button>
                    </div>
                </div>
            </div>
            <div id="dashboardResults"><div class="spinner-wrapper"><div class="spinner-border text-primary"></div></div></div>`;
    },

    bindEvents() {
        ['dashPeriod', 'dashStartDate', 'dashEndDate', 'dashAgent', 'dashCategory', 'dashFund', 'dashMinAmount', 'dashMaxAmount', 'dashSearch']
            .forEach(id => document.getElementById(id).addEventListener('input', () => this.loadAnalytics()));
        document.getElementById('dashReset').addEventListener('click', () => this.resetFilters());
        document.querySelectorAll('.quick-filter').forEach(btn => btn.addEventListener('click', () => {
            document.querySelectorAll('.quick-filter').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            document.getElementById('dashPeriod').value = btn.dataset.period;
            document.getElementById('dashStartDate').value = '';
            document.getElementById('dashEndDate').value = '';
            this.loadAnalytics();
        }));
    },

    resetFilters() {
        ['dashAgent', 'dashCategory', 'dashFund', 'dashMinAmount', 'dashMaxAmount', 'dashSearch', 'dashStartDate', 'dashEndDate']
            .forEach(id => document.getElementById(id).value = '');
        document.getElementById('dashPeriod').value = 'month';
        document.querySelectorAll('.quick-filter').forEach(b => b.classList.toggle('active', b.dataset.period === 'month'));
        this.loadAnalytics();
    },

    collectFilters() {
        const period = document.getElementById('dashPeriod').value;
        const params = {
            period,
            agentId: document.getElementById('dashAgent').value,
            category: document.getElementById('dashCategory').value,
            fundId: document.getElementById('dashFund').value,
            minAmount: document.getElementById('dashMinAmount').value,
            maxAmount: document.getElementById('dashMaxAmount').value,
            search: document.getElementById('dashSearch').value.trim()
        };
        if (period === 'custom') {
            params.startDate = document.getElementById('dashStartDate').value;
            params.endDate = document.getElementById('dashEndDate').value;
        }
        return ViewHelpers.compactFilters(params);
    },

    async loadAnalytics() {
        const target = document.getElementById('dashboardResults');
        if (!target) return;
        try {
            const data = await Api.getDashboard(this.collectFilters());
            this.renderAnalytics(target, data);
        } catch (err) {
            target.innerHTML = `<div class="alert alert-danger">${Fmt.html(err.message)}</div>`;
        }
    },

    renderAnalytics(target, data) {
        const alerts = data.alerts || [];
        const changeClass = (data.expenseChangePercent || 0) >= 0 ? 'text-danger' : 'text-success';
        target.innerHTML = `
            <div class="summary-strip mb-3">
                <i class="bi bi-funnel"></i>
                <span>${Fmt.html(data.filter?.label || 'Période sélectionnée')}</span>
                <span>${data.expenseCount || 0} dépense(s)</span>
                <span>${alerts.length} alerte(s)</span>
            </div>

            <div class="row g-3 mb-4">
                ${this.kpiCard('Dépenses période', Fmt.amount(data.periodExpenses), 'FCFA', 'bi-cash-coin', 'accent', `${Fmt.percent(data.expenseChangePercent)} vs période précédente`, changeClass)}
                ${this.kpiCard('Solde restant', Fmt.amount(data.totalBalance), 'FCFA', 'bi-wallet2', 'success', `${data.fundUsagePercent || 0}% des fonds utilisés`, 'text-muted')}
                ${this.kpiCard('Moyenne journalière', Fmt.amount(data.averageDailyExpense), 'FCFA', 'bi-activity', 'warning', 'Rythme de consommation', 'text-muted')}
                ${this.kpiCard('Brouillons en attente', data.pendingDraftCount || 0, '', 'bi-hourglass-split', '', 'À confirmer par WhatsApp', data.pendingDraftCount ? 'text-warning' : 'text-muted')}
                ${this.kpiCard('Top catégorie', Fmt.html(data.topCategory || '—'), '', 'bi-tags', '', 'Catégorie dominante', 'text-muted')}
                ${this.kpiCard('Top agent', Fmt.html(data.topAgentName || '—'), '', 'bi-person-check', 'success', 'Agent le plus actif', 'text-muted')}
                ${this.kpiCard('Plus grosse dépense', Fmt.amount(data.largestExpenseAmount), 'FCFA', 'bi-exclamation-diamond', 'accent', 'Sur la période', 'text-muted')}
                ${this.kpiCard('Agents actifs', data.agentCount || 0, '', 'bi-people', 'warning', 'Utilisateurs actifs', 'text-muted')}
            </div>

            ${alerts.length ? `<div class="row g-3 mb-4">${alerts.map(a => this.alertCard(a)).join('')}</div>` : ''}

            <div class="row g-3 mb-4">
                <div class="col-xl-8"><div class="chart-card"><div class="data-card-header"><h5><i class="bi bi-graph-up"></i> Évolution quotidienne</h5></div><canvas id="expenseTrendChart" height="120"></canvas></div></div>
                <div class="col-xl-4"><div class="chart-card"><div class="data-card-header"><h5><i class="bi bi-pie-chart"></i> Répartition catégories</h5></div><canvas id="categoryChart" height="220"></canvas></div></div>
            </div>
            <div class="row g-3 mb-4">
                <div class="col-lg-6"><div class="chart-card"><div class="data-card-header"><h5><i class="bi bi-bar-chart"></i> Agents</h5></div><canvas id="agentChart" height="180"></canvas></div></div>
                <div class="col-lg-6"><div class="chart-card"><div class="data-card-header"><h5><i class="bi bi-speedometer2"></i> Utilisation des fonds</h5></div><canvas id="fundChart" height="180"></canvas></div></div>
            </div>
            <div class="row g-3">
                <div class="col-lg-7">${this.recentTable(data.recentExpenses || [])}</div>
                <div class="col-lg-5">${this.breakdownPanel('Détails par catégorie', data.categoryBreakdown || [])}</div>
            </div>`;

        this.renderCharts(data);
    },

    kpiCard(label, value, unit, icon, tone, sub, subClass) {
        return `<div class="col-6 col-xl-3"><div class="stat-card ${tone || ''}">
            <div class="d-flex justify-content-between gap-2"><div><div class="stat-value">${value}${unit ? `<small> ${unit}</small>` : ''}</div><div class="stat-label">${label}</div><div class="small ${subClass || 'text-muted'} mt-1">${sub}</div></div><i class="bi ${icon} stat-icon"></i></div>
        </div></div>`;
    },

    alertCard(alert) {
        const icon = alert.level === 'danger' ? 'bi-exclamation-triangle' : alert.level === 'warning' ? 'bi-exclamation-circle' : 'bi-info-circle';
        return `<div class="col-md-4"><div class="alert-strip ${alert.level || 'info'}"><i class="bi ${icon}"></i><div><strong>${Fmt.html(alert.title)}</strong><p>${Fmt.html(alert.message)}</p></div></div></div>`;
    },

    renderCharts(data) {
        const daily = data.dailySeries || [];
        const categories = data.categoryBreakdown || [];
        const agents = data.agentBreakdown || [];
        const funds = data.fundUtilization || [];
        const palette = ['#0f3460', '#e94560', '#198754', '#ffc107', '#6f42c1', '#20c997', '#fd7e14'];

        ChartManager.render('expenseTrendChart', 'line', {
            labels: daily.map(p => p.label),
            datasets: [{ label: 'Dépenses', data: daily.map(p => p.amount), borderColor: '#e94560', backgroundColor: 'rgba(233,69,96,0.12)', fill: true, tension: 0.35 }]
        }, { responsive: true, plugins: { legend: { display: false } }, scales: { y: { ticks: { callback: v => Fmt.amount(v) } } } });

        ChartManager.render('categoryChart', 'doughnut', {
            labels: categories.map(i => i.label),
            datasets: [{ data: categories.map(i => i.amount), backgroundColor: palette }]
        }, { responsive: true, plugins: { legend: { position: 'bottom' } } });

        ChartManager.render('agentChart', 'bar', {
            labels: agents.slice(0, 8).map(i => i.label),
            datasets: [{ label: 'Montant', data: agents.slice(0, 8).map(i => i.amount), backgroundColor: '#0f3460' }]
        }, { responsive: true, plugins: { legend: { display: false } }, scales: { y: { ticks: { callback: v => Fmt.amount(v) } } } });

        ChartManager.render('fundChart', 'bar', {
            labels: funds.slice(0, 8).map(i => i.description || ('Fonds #' + i.fundId)),
            datasets: [{ label: 'Utilisé %', data: funds.slice(0, 8).map(i => i.usagePercent), backgroundColor: funds.slice(0, 8).map(i => i.usagePercent > 80 ? '#dc3545' : i.usagePercent > 50 ? '#ffc107' : '#198754') }]
        }, { indexAxis: 'y', responsive: true, plugins: { legend: { display: false } }, scales: { x: { max: 100, ticks: { callback: v => v + '%' } } } });
    },

    recentTable(expenses) {
        return `<div class="data-card"><div class="data-card-header"><h5><i class="bi bi-clock-history"></i> Activité récente</h5></div>
            <div class="table-responsive"><table class="data-table"><thead><tr><th>Date</th><th>Agent</th><th>Description</th><th>Montant</th></tr></thead><tbody>
            ${expenses.map(e => `<tr><td>${Fmt.dateShort(e.confirmedAt)}</td><td>${Fmt.html(e.agentName)}</td><td>${Fmt.html(e.description)} <span class="badge-category badge-${Fmt.html(e.category || 'DIVERS')}">${Fmt.html(e.category || 'DIVERS')}</span></td><td class="amount">${Fmt.amount(e.amount)} F</td></tr>`).join('') || '<tr><td colspan="4" class="text-muted text-center py-4">Aucune dépense</td></tr>'}
            </tbody></table></div></div>`;
    },

    breakdownPanel(title, items) {
        return `<div class="data-card"><div class="data-card-header"><h5><i class="bi bi-list-check"></i> ${title}</h5></div><div class="p-3">
            ${items.map(i => `<div class="breakdown-row"><div><strong>${Fmt.html(i.label)}</strong><small>${i.count} opération(s)</small></div><div class="text-end"><span class="amount">${Fmt.amount(i.amount)} F</span><small>${i.percentage}%</small></div></div>`).join('') || '<div class="text-muted">Aucune donnée</div>'}
        </div></div>`;
    }
};

/* ---- EXPENSES ---- */
const ExpensesView = {
    allExpenses: [],
    allDrafts: [],
    funds: [],
    users: [],

    async render(container) {
        ChartManager.destroyAll();
        container.innerHTML = '<div class="spinner-wrapper"><div class="spinner-border text-primary"></div></div>';
        try {
            const [expenses, drafts, users, funds] = await Promise.all([
                Api.getExpenses(), Api.getDrafts(), Api.getUsers(), Api.getFunds()
            ]);
            this.allExpenses = expenses;
            this.allDrafts = drafts;
            this.users = users;
            this.funds = funds;
            const categories = [...new Set(expenses.map(e => e.category || 'DIVERS'))];

            container.innerHTML = `
                <div class="data-card mb-4">
                    <div class="data-card-header align-items-start">
                        <div><h5><i class="bi bi-receipt"></i> Dépenses confirmées</h5><small class="text-muted">Filtre, recherche, totalise et exporte les dépenses.</small></div>
                        <button class="btn-kf-outline" id="exportExpenses"><i class="bi bi-download"></i> Export CSV</button>
                    </div>
                    <div class="kf-filter-panel light m-3">
                        <div class="row g-2 align-items-end">
                            <div class="col-sm-6 col-lg-2"><label class="form-label">Début</label><input type="date" class="form-control" id="filterStartDate"></div>
                            <div class="col-sm-6 col-lg-2"><label class="form-label">Fin</label><input type="date" class="form-control" id="filterEndDate"></div>
                            <div class="col-sm-6 col-lg-2"><label class="form-label">Agent</label><select class="form-select" id="filterAgent">${ViewHelpers.agentOptions(users)}</select></div>
                            <div class="col-sm-6 col-lg-2"><label class="form-label">Catégorie</label><select class="form-select" id="filterCategory">${ViewHelpers.categoryOptions(categories)}</select></div>
                            <div class="col-sm-6 col-lg-2"><label class="form-label">Fonds</label><select class="form-select" id="filterFund">${ViewHelpers.fundOptions(funds)}</select></div>
                            <div class="col-sm-6 col-lg-2"><label class="form-label">Montant min</label><input type="number" min="0" class="form-control" id="filterMinAmount"></div>
                            <div class="col-sm-6 col-lg-2"><label class="form-label">Montant max</label><input type="number" min="0" class="form-control" id="filterMaxAmount"></div>
                            <div class="col-lg-4"><label class="form-label">Recherche</label><input type="search" class="form-control" id="filterSearch" placeholder="description, agent, catégorie..."></div>
                        </div>
                    </div>
                    <div id="expenseSummary" class="summary-strip mx-3 mb-3"></div>
                    <div class="table-responsive">
                        <table class="data-table">
                            <thead><tr><th>Date</th><th>Agent</th><th>Fonds</th><th>Description</th><th>Catégorie</th><th>Montant</th></tr></thead>
                            <tbody id="expenseRows"></tbody>
                        </table>
                    </div>
                    <div id="expenseEmpty" class="empty-state d-none"><i class="bi bi-inbox"></i><p>Aucune dépense trouvée</p></div>
                </div>
                <div class="data-card">
                    <div class="data-card-header"><h5><i class="bi bi-pencil-square"></i> Brouillons WhatsApp</h5></div>
                    <div class="table-responsive"><table class="data-table"><thead><tr><th>Date</th><th>Agent</th><th>Description</th><th>Montant</th><th>Statut</th></tr></thead><tbody id="draftRows"></tbody></table></div>
                    <div id="draftEmpty" class="empty-state d-none"><i class="bi bi-inbox"></i><p>Aucun brouillon</p></div>
                </div>`;

            ['filterStartDate', 'filterEndDate', 'filterAgent', 'filterCategory', 'filterFund', 'filterMinAmount', 'filterMaxAmount', 'filterSearch']
                .forEach(id => document.getElementById(id).addEventListener('input', () => this.applyFilters()));
            document.getElementById('exportExpenses').addEventListener('click', () => this.exportCsv());
            this.applyInitialFiltersFromUrl();
            this.applyFilters();
            this.renderDrafts();
        } catch (err) {
            container.innerHTML = `<div class="alert alert-danger">${Fmt.html(err.message)}</div>`;
        }
    },

    applyInitialFiltersFromUrl() {
        const params = new URLSearchParams(window.location.search);
        if ((params.get('view') || '') !== 'expenses') return;

        const range = this.rangeFromPeriod(params.get('period'), params.get('startDate'), params.get('endDate'));
        if (range.start) document.getElementById('filterStartDate').value = range.start;
        if (range.end) document.getElementById('filterEndDate').value = range.end;
        this.setIfOptionExists('filterAgent', params.get('agentId'));
        this.setIfOptionExists('filterCategory', params.get('category'));
        this.setIfOptionExists('filterFund', params.get('fundId'));
        if (params.get('minAmount')) document.getElementById('filterMinAmount').value = params.get('minAmount');
        if (params.get('maxAmount')) document.getElementById('filterMaxAmount').value = params.get('maxAmount');
        if (params.get('search')) document.getElementById('filterSearch').value = params.get('search');
    },

    rangeFromPeriod(period, startDate, endDate) {
        if (startDate || endDate) {
            return { start: startDate || endDate || '', end: endDate || startDate || '' };
        }
        const today = new Date();
        const toInput = date => {
            const copy = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
            return copy.toISOString().slice(0, 10);
        };
        const start = new Date(today);
        switch ((period || 'today').toLowerCase()) {
            case '7d':
            case 'week':
            case 'semaine':
                start.setDate(today.getDate() - 6);
                return { start: toInput(start), end: toInput(today) };
            case '30d':
                start.setDate(today.getDate() - 29);
                return { start: toInput(start), end: toInput(today) };
            case 'month':
            case 'mois':
                start.setDate(1);
                return { start: toInput(start), end: toInput(today) };
            case 'last_month': {
                const previous = new Date(today.getFullYear(), today.getMonth() - 1, 1);
                const last = new Date(today.getFullYear(), today.getMonth(), 0);
                return { start: toInput(previous), end: toInput(last) };
            }
            case 'all':
                return { start: '', end: '' };
            case 'today':
            default:
                return { start: toInput(today), end: toInput(today) };
        }
    },

    setIfOptionExists(id, value) {
        if (!value) return;
        const element = document.getElementById(id);
        if ([...element.options].some(option => option.value === value)) {
            element.value = value;
        }
    },

    filteredExpenses() {
        const agentId = document.getElementById('filterAgent').value;
        const category = document.getElementById('filterCategory').value;
        const fundId = document.getElementById('filterFund').value;
        const start = document.getElementById('filterStartDate').value;
        const end = document.getElementById('filterEndDate').value;
        const min = Number(document.getElementById('filterMinAmount').value || 0);
        const maxRaw = document.getElementById('filterMaxAmount').value;
        const max = maxRaw ? Number(maxRaw) : null;
        const search = document.getElementById('filterSearch').value.toLowerCase().trim();

        return this.allExpenses.filter(e => {
            const date = e.confirmedAt ? e.confirmedAt.slice(0, 10) : '';
            const haystack = `${e.description || ''} ${e.category || ''} ${e.agent?.name || ''} ${e.fund?.description || ''}`.toLowerCase();
            return (!agentId || String(e.agent?.id) === agentId)
                && (!category || (e.category || 'DIVERS') === category)
                && (!fundId || String(e.fund?.id) === fundId)
                && (!start || date >= start)
                && (!end || date <= end)
                && (!min || (e.amount || 0) >= min)
                && (!max || (e.amount || 0) <= max)
                && (!search || haystack.includes(search));
        });
    },

    applyFilters() {
        const filtered = this.filteredExpenses();
        const tbody = document.getElementById('expenseRows');
        const empty = document.getElementById('expenseEmpty');
        const summary = document.getElementById('expenseSummary');
        const total = filtered.reduce((sum, e) => sum + (e.amount || 0), 0);
        const average = filtered.length ? Math.round(total / filtered.length) : 0;
        summary.innerHTML = `<i class="bi bi-calculator"></i><span>${filtered.length} dépense(s)</span><span>Total : ${Fmt.amount(total)} FCFA</span><span>Moyenne : ${Fmt.amount(average)} FCFA</span>`;

        if (filtered.length === 0) {
            tbody.innerHTML = '';
            empty.classList.remove('d-none');
            return;
        }
        empty.classList.add('d-none');
        tbody.innerHTML = filtered.map(e => `
            <tr>
                <td>${Fmt.date(e.confirmedAt)}</td>
                <td>${Fmt.html(e.agent?.name || '—')}</td>
                <td>${Fmt.html(e.fund?.description || '—')}</td>
                <td>${Fmt.html(e.description)}</td>
                <td><span class="badge-category badge-${Fmt.html(e.category || 'DIVERS')}">${Fmt.html(e.category || 'DIVERS')}</span></td>
                <td class="amount">${Fmt.amount(e.amount)} F</td>
            </tr>`).join('');
    },

    exportCsv() {
        const rows = this.filteredExpenses();
        const header = ['Date', 'Agent', 'Fonds', 'Description', 'Catégorie', 'Montant'];
        const csvRows = [header, ...rows.map(e => [
            e.confirmedAt || '', e.agent?.name || '', e.fund?.description || '', e.description || '', e.category || 'DIVERS', e.amount || 0
        ])].map(row => row.map(v => `"${String(v).replaceAll('"', '""')}"`).join(','));
        const blob = new Blob([csvRows.join('\n')], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `kolo-depenses-${new Date().toISOString().slice(0,10)}.csv`;
        a.click();
        URL.revokeObjectURL(url);
    },

    renderDrafts() {
        const tbody = document.getElementById('draftRows');
        const empty = document.getElementById('draftEmpty');
        if (this.allDrafts.length === 0) {
            tbody.innerHTML = '';
            empty.classList.remove('d-none');
        } else {
            empty.classList.add('d-none');
            tbody.innerHTML = this.allDrafts.map(d => `
                <tr><td>${Fmt.date(d.createdAt)}</td><td>${Fmt.html(d.agent?.name || '—')}</td><td>${Fmt.html(d.description)}</td><td class="amount">${Fmt.amount(d.amount)} F</td><td><span class="badge-status badge-${d.status}">${d.status}</span></td></tr>`).join('');
        }
    }
};

/* ---- REPORTS ---- */
const ReportsView = {
    users: [],
    lastReport: null,
    categories: ['CARBURANT', 'TRANSPORT', 'MATERIAUX', 'NOURRITURE', 'PIECES', 'DIVERS'],

    async render(container) {
        ChartManager.destroyAll();
        container.innerHTML = '<div class="spinner-wrapper"><div class="spinner-border text-primary"></div></div>';
        try {
            this.users = await Api.getUsers();
            container.innerHTML = this.template();
            this.bindEvents();
            await this.generate();
        } catch (err) {
            container.innerHTML = `<div class="alert alert-danger">${Fmt.html(err.message)}</div>`;
        }
    },

    template() {
        const recipients = this.users.map(u => `<option value="${Fmt.html(u.phoneNumber)}">${Fmt.html(u.name)} — ${Fmt.html(u.phoneNumber)}</option>`).join('');
        return `
            <div class="row g-3">
                <div class="col-xl-4">
                    <div class="data-card sticky-panel">
                        <div class="data-card-header"><h5><i class="bi bi-sliders"></i> Générateur de rapport</h5></div>
                        <div class="p-3">
                            <div class="mb-3"><label class="form-label">Type</label><select class="form-select" id="reportPeriod"><option value="today">Journalier</option><option value="7d">Hebdomadaire</option><option value="month" selected>Mensuel</option><option value="30d">30 jours</option><option value="all">Toutes périodes</option><option value="custom">Dates personnalisées</option></select></div>
                            <div class="row g-2 mb-3"><div class="col"><label class="form-label">Début</label><input type="date" class="form-control" id="reportStartDate"></div><div class="col"><label class="form-label">Fin</label><input type="date" class="form-control" id="reportEndDate"></div></div>
                            <div class="mb-3"><label class="form-label">Agent</label><select class="form-select" id="reportAgent">${ViewHelpers.agentOptions(this.users)}</select></div>
                            <div class="mb-3"><label class="form-label">Catégorie</label><select class="form-select" id="reportCategory">${ViewHelpers.categoryOptions(this.categories)}</select></div>
                            <div class="mb-3"><label class="form-label">Recherche</label><input type="search" class="form-control" id="reportSearch" placeholder="ex: carburant, chantier..."></div>
                            <div class="d-grid gap-2 mb-3"><button class="btn-kf" id="generateReport"><i class="bi bi-magic"></i> Générer</button></div>
                            <hr>
                            <label class="form-label">Envoyer par WhatsApp</label>
                            <select class="form-select mb-2" id="reportRecipient"><option value="">Choisir un destinataire</option>${recipients}</select>
                            <div class="d-grid"><button class="btn-kf-outline" id="sendReport"><i class="bi bi-whatsapp"></i> Envoyer le rapport</button></div>
                            <div id="reportSendStatus" class="small mt-2"></div>
                        </div>
                    </div>
                </div>
                <div class="col-xl-8">
                    <div class="report-toolbar mb-3">
                        <button class="btn-kf-outline" id="copyReport"><i class="bi bi-clipboard"></i> Copier version WhatsApp</button>
                        <button class="btn-kf-outline" id="printReport"><i class="bi bi-printer"></i> Imprimer / PDF</button>
                    </div>
                    <div id="reportPreview"><div class="spinner-wrapper"><div class="spinner-border text-primary"></div></div></div>
                </div>
            </div>`;
    },

    bindEvents() {
        document.getElementById('generateReport').addEventListener('click', () => this.generate());
        document.getElementById('sendReport').addEventListener('click', () => this.send());
        document.getElementById('copyReport').addEventListener('click', () => this.copy());
        document.getElementById('printReport').addEventListener('click', () => this.print());
    },

    collectFilters() {
        const period = document.getElementById('reportPeriod').value;
        const params = {
            period,
            agentId: document.getElementById('reportAgent').value,
            category: document.getElementById('reportCategory').value,
            search: document.getElementById('reportSearch').value.trim()
        };
        if (period === 'custom') {
            params.startDate = document.getElementById('reportStartDate').value;
            params.endDate = document.getElementById('reportEndDate').value;
        }
        return ViewHelpers.compactFilters(params);
    },

    async generate() {
        const preview = document.getElementById('reportPreview');
        preview.innerHTML = '<div class="spinner-wrapper"><div class="spinner-border text-primary"></div></div>';
        try {
            this.lastReport = await Api.generateReport(this.collectFilters());
            preview.innerHTML = `<div class="report-preview">${this.lastReport.html}</div><div class="data-card mt-3"><div class="data-card-header"><h5><i class="bi bi-whatsapp"></i> Version WhatsApp</h5></div><pre class="whatsapp-preview">${Fmt.html(this.lastReport.whatsappText)}</pre></div>`;
        } catch (err) {
            preview.innerHTML = `<div class="alert alert-danger">${Fmt.html(err.message)}</div>`;
        }
    },

    async send() {
        const status = document.getElementById('reportSendStatus');
        const to = document.getElementById('reportRecipient').value;
        if (!to) {
            status.innerHTML = '<span class="text-danger">Choisis un destinataire.</span>';
            return;
        }
        status.innerHTML = '<span class="text-muted">Envoi en cours...</span>';
        try {
            this.lastReport = await Api.sendReport({ ...this.collectFilters(), to });
            status.innerHTML = '<span class="text-success">Rapport envoyé.</span>';
        } catch (err) {
            status.innerHTML = `<span class="text-danger">${Fmt.html(err.message)}</span>`;
        }
    },

    async copy() {
        if (!this.lastReport) await this.generate();
        await navigator.clipboard.writeText(this.lastReport.whatsappText);
    },

    print() {
        if (!this.lastReport) return;
        const win = window.open('', '_blank');
        win.document.write(`<html><head><title>${Fmt.html(this.lastReport.title)}</title><link href="/css/style.css" rel="stylesheet"></head><body>${this.lastReport.html}</body></html>`);
        win.document.close();
        win.focus();
        setTimeout(() => win.print(), 300);
    }
};

/* ---- AGENTS ---- */
const AgentsView = {
    users: [],

    async render(container) {
        container.innerHTML = '<div class="spinner-wrapper"><div class="spinner-border text-primary"></div></div>';
        try {
            this.users = await Api.getUsers();
            container.innerHTML = this.template();
            document.getElementById('userForm').addEventListener('submit', (e) => this.handleSave(e));
        } catch (err) {
            container.innerHTML = `<div class="alert alert-danger">${Fmt.html(err.message)}</div>`;
        }
    },

    template() {
        const activeCount = this.users.filter(u => u.active).length;
        const agentCount = this.users.filter(u => u.role === 'AGENT' && u.active).length;
        return `
            <div class="summary-strip mb-3">
                <i class="bi bi-people"></i>
                <span>${this.users.length} utilisateur(s)</span>
                <span>${activeCount} actif(s)</span>
                <span>${agentCount} agent(s) WhatsApp</span>
            </div>
            <div class="data-card">
                <div class="data-card-header">
                    <div>
                        <h5><i class="bi bi-people"></i> Utilisateurs</h5>
                        <small class="text-muted">Ajoute les agents autorisés à saisir leurs dépenses par WhatsApp.</small>
                    </div>
                    <button class="btn-kf" onclick="AgentsView.showModal()">
                        <i class="bi bi-plus-lg"></i> Ajouter
                    </button>
                </div>
                <div class="table-responsive">
                    <table class="data-table">
                        <thead><tr>
                            <th>Nom</th><th>Téléphone</th><th>Rôle</th><th>Statut</th><th>Inscrit le</th><th>Actions</th>
                        </tr></thead>
                        <tbody>${this.users.map(u => this.userRow(u)).join('')}</tbody>
                    </table>
                </div>
                ${this.users.length === 0 ? '<div class="empty-state"><i class="bi bi-people"></i><p>Aucun utilisateur</p></div>' : ''}
            </div>

            <div class="modal fade" id="userModal" tabindex="-1">
                <div class="modal-dialog">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h5 class="modal-title" id="userModalTitle">Nouvel utilisateur</h5>
                            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                        </div>
                        <form id="userForm">
                            <input type="hidden" id="userId">
                            <div class="modal-body">
                                <div class="mb-3">
                                    <label class="form-label">Nom</label>
                                    <input type="text" class="form-control" id="userName" required>
                                </div>
                                <div class="mb-3">
                                    <label class="form-label">Numéro WhatsApp</label>
                                    <input type="text" class="form-control" id="userPhone" placeholder="22378550131" required>
                                    <small class="text-muted">Format recommandé : indicatif pays + numéro, sans + ni espace.</small>
                                </div>
                                <div class="mb-3">
                                    <label class="form-label">Mot de passe</label>
                                    <input type="password" class="form-control" id="userPassword" autocomplete="new-password">
                                    <small class="text-muted">Obligatoire pour permettre la connexion web. Laisser vide pour ne pas changer.</small>
                                </div>
                                <div class="mb-3">
                                    <label class="form-label">Rôle</label>
                                    <select class="form-select" id="userRole" required>
                                        <option value="AGENT">Agent — saisit les dépenses</option>
                                        <option value="MANAGER">Manager — supervision</option>
                                        <option value="BOSS">Boss — administrateur</option>
                                    </select>
                                </div>
                                <div id="userFormError" class="alert alert-danger d-none"></div>
                            </div>
                            <div class="modal-footer">
                                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Annuler</button>
                                <button type="submit" class="btn-kf" id="userSubmitBtn">Créer</button>
                            </div>
                        </form>
                    </div>
                </div>
            </div>`;
    },

    userRow(u) {
        const toggleLabel = u.active ? 'Désactiver' : 'Réactiver';
        const toggleClass = u.active ? 'btn-outline-danger' : 'btn-outline-success';
        return `
            <tr class="${u.active ? '' : 'table-light'}">
                <td><strong>${Fmt.html(u.name)}</strong></td>
                <td>${Fmt.html(u.phoneNumber)}</td>
                <td><span class="badge-role badge-${Fmt.html(u.role)}">${Fmt.html(u.role)}</span></td>
                <td>${u.active ? '<span class="text-success">● Actif</span>' : '<span class="text-muted">● Inactif</span>'}</td>
                <td>${Fmt.dateShort(u.createdAt)}</td>
                <td>
                    <div class="d-flex flex-wrap gap-1">
                        <button class="btn btn-sm btn-outline-primary" onclick="AgentsView.showModal(${u.id})"><i class="bi bi-pencil"></i> Modifier</button>
                        <button class="btn btn-sm ${toggleClass}" onclick="AgentsView.toggleActive(${u.id}, ${!u.active})">${toggleLabel}</button>
                    </div>
                </td>
            </tr>`;
    },

    showModal(userId = null) {
        const user = userId ? this.users.find(u => u.id === userId) : null;
        document.getElementById('userForm').reset();
        document.getElementById('userFormError').classList.add('d-none');
        document.getElementById('userId').value = user?.id || '';
        document.getElementById('userName').value = user?.name || '';
        document.getElementById('userPhone').value = user?.phoneNumber || '';
        document.getElementById('userRole').value = user?.role || 'AGENT';
        document.getElementById('userModalTitle').textContent = user ? 'Modifier utilisateur' : 'Nouvel utilisateur';
        document.getElementById('userSubmitBtn').textContent = user ? 'Enregistrer' : 'Créer';
        new bootstrap.Modal(document.getElementById('userModal')).show();
    },

    async handleSave(e) {
        e.preventDefault();
        const errEl = document.getElementById('userFormError');
        errEl.classList.add('d-none');
        const id = document.getElementById('userId').value;
        const data = {
            name: document.getElementById('userName').value,
            phoneNumber: document.getElementById('userPhone').value,
            role: document.getElementById('userRole').value
        };
        const password = document.getElementById('userPassword').value.trim();
        if (password) data.password = password;
        try {
            if (id) {
                await Api.updateUser(id, data);
            } else {
                await Api.createUser(data);
            }
            bootstrap.Modal.getInstance(document.getElementById('userModal')).hide();
            App.navigate('agents');
        } catch (err) {
            errEl.textContent = err.message;
            errEl.classList.remove('d-none');
        }
    },

    async toggleActive(userId, active) {
        const user = this.users.find(u => u.id === userId);
        if (!user) return;
        if (!active && !confirm(`Désactiver ${user.name} ? Il ne pourra plus saisir de nouvelles dépenses via WhatsApp.`)) {
            return;
        }
        try {
            await Api.setUserActive(userId, active);
            App.navigate('agents');
        } catch (err) {
            alert(err.message);
        }
    }
};

/* ---- PENDING FUND RECEIPTS ---- */
const PendingFundsView = {
    pending: [],

    async render(container) {
        container.innerHTML = '<div class="spinner-wrapper"><div class="spinner-border text-primary"></div></div>';
        try {
            this.pending = await Api.getPendingReceipts();
            container.innerHTML = this.template();
        } catch (err) {
            container.innerHTML = `<div class="alert alert-danger">${Fmt.html(err.message)}</div>`;
        }
    },

    template() {
        const isAgent = Api.getAuth().role === 'AGENT';
        return `
            <div class="summary-strip mb-3">
                <i class="bi bi-check2-square"></i>
                <span>${this.pending.length} affectation(s) en attente</span>
                <span>${isAgent ? 'À confirmer par toi' : 'À suivre côté agents'}</span>
            </div>
            <div class="data-card">
                <div class="data-card-header">
                    <div>
                        <h5><i class="bi bi-check2-square"></i> Affectations en attente</h5>
                        <small class="text-muted">Un fonds devient utilisable seulement après confirmation de réception.</small>
                    </div>
                    <button class="btn-kf-outline" onclick="App.navigate('pendingFunds')"><i class="bi bi-arrow-clockwise"></i> Actualiser</button>
                </div>
                <div class="table-responsive">
                    <table class="data-table">
                        <thead><tr><th>Agent</th><th>Description</th><th>Montant</th><th>Date</th><th>Statut</th><th>Actions</th></tr></thead>
                        <tbody>${this.pending.map(f => this.row(f)).join('')}</tbody>
                    </table>
                </div>
                ${this.pending.length === 0 ? '<div class="empty-state"><i class="bi bi-check-circle"></i><p>Aucune affectation en attente</p></div>' : ''}
            </div>`;
    },

    row(fund) {
        const canAct = fund.status === 'PENDING_RECEIPT';
        return `
            <tr>
                <td><strong>${Fmt.html(fund.agent?.name || '—')}</strong><br><small class="text-muted">${Fmt.html(fund.agent?.phoneNumber || '')}</small></td>
                <td>${Fmt.html(fund.description || '—')}</td>
                <td class="amount">${Fmt.amount(fund.initialAmount)} F</td>
                <td>${Fmt.dateShort(fund.createdAt)}</td>
                <td><span class="badge bg-${ViewHelpers.fundStatusClass(fund.status)}">${ViewHelpers.fundStatusLabel(fund.status)}</span></td>
                <td>
                    <div class="d-flex flex-wrap gap-1">
                        <button class="btn btn-sm btn-outline-success" ${canAct ? '' : 'disabled'} onclick="PendingFundsView.accept(${fund.id})"><i class="bi bi-check2"></i> Accepter</button>
                        <button class="btn btn-sm btn-outline-danger" ${canAct ? '' : 'disabled'} onclick="PendingFundsView.reject(${fund.id})"><i class="bi bi-x"></i> Rejeter</button>
                    </div>
                </td>
            </tr>`;
    },

    async accept(fundId) {
        try {
            await Api.acceptFundReceipt(fundId, 'Confirmé depuis le web');
            App.navigate('pendingFunds');
        } catch (err) {
            alert(err.message);
        }
    },

    async reject(fundId) {
        if (!confirm('Rejeter cette affectation de fonds ?')) return;
        try {
            await Api.rejectFundReceipt(fundId, 'Rejeté depuis le web');
            App.navigate('pendingFunds');
        } catch (err) {
            alert(err.message);
        }
    }
};

/* ---- FUNDS ---- */
const FundsView = {
    funds: [],
    users: [],

    async render(container) {
        container.innerHTML = '<div class="spinner-wrapper"><div class="spinner-border text-primary"></div></div>';
        try {
            const [funds, users] = await Promise.all([Api.getFunds(), Api.getUsers()]);
            this.funds = funds;
            this.users = users;
            container.innerHTML = this.template();
            document.getElementById('addFundForm').addEventListener('submit', (e) => this.handleCreate(e));
            document.getElementById('topUpFundForm').addEventListener('submit', (e) => this.handleTopUp(e));
        } catch (err) {
            container.innerHTML = `<div class="alert alert-danger">${Fmt.html(err.message)}</div>`;
        }
    },

    template() {
        const agents = this.users.filter(u => u.role === 'AGENT' && u.active);
        const agentOptions = agents.map(u => `<option value="${u.id}">${Fmt.html(u.name)} (${Fmt.html(u.phoneNumber)})</option>`).join('');
        const activeFunds = this.funds.filter(f => (f.status || 'ACTIVE') === 'ACTIVE');
        const pendingCount = this.funds.filter(f => f.status === 'PENDING_RECEIPT').length;
        const totalInitial = activeFunds.reduce((sum, f) => sum + (f.initialAmount || 0), 0);
        const totalBalance = activeFunds.reduce((sum, f) => sum + (f.balance || 0), 0);
        const totalUsed = totalInitial - totalBalance;

        return `
            <div class="summary-strip mb-3">
                <i class="bi bi-wallet2"></i>
                <span>Confié : ${Fmt.amount(totalInitial)} FCFA</span>
                <span>Solde : ${Fmt.amount(totalBalance)} FCFA</span>
                <span>Utilisé : ${Fmt.amount(totalUsed)} FCFA</span>
                <span>En attente : ${pendingCount}</span>
            </div>
            <div class="data-card">
                <div class="data-card-header">
                    <div>
                        <h5><i class="bi bi-wallet2"></i> Fonds confiés</h5>
                        <small class="text-muted">Les nouveaux fonds et recharges restent en attente tant que l’agent ne confirme pas la réception.</small>
                    </div>
                    <button class="btn-kf" onclick="FundsView.showModal()" ${agents.length ? '' : 'disabled'}>
                        <i class="bi bi-plus-lg"></i> Confier des fonds
                    </button>
                </div>
                ${agents.length ? '' : '<div class="alert alert-warning m-3">Ajoute d’abord un agent actif avant de confier des fonds.</div>'}
                <div class="table-responsive">
                    <table class="data-table">
                        <thead><tr>
                            <th>Agent</th><th>Description</th><th>Statut</th><th>Montant alimenté</th><th>Solde</th><th>Utilisé</th><th>Date</th><th>Actions</th>
                        </tr></thead>
                        <tbody>${this.funds.map(f => this.fundRow(f)).join('')}</tbody>
                    </table>
                </div>
                ${this.funds.length === 0 ? '<div class="empty-state"><i class="bi bi-wallet2"></i><p>Aucun fonds</p></div>' : ''}
            </div>

            <div class="modal fade" id="addFundModal" tabindex="-1">
                <div class="modal-dialog">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h5 class="modal-title">Confier des fonds</h5>
                            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                        </div>
                        <form id="addFundForm">
                            <div class="modal-body">
                                <div class="mb-3">
                                    <label class="form-label">Agent</label>
                                    <select class="form-select" id="fundAgent" required>${agentOptions}</select>
                                </div>
                                <div class="mb-3">
                                    <label class="form-label">Montant (FCFA)</label>
                                    <input type="number" class="form-control" id="fundAmount" min="1" required>
                                </div>
                                <div class="mb-3">
                                    <label class="form-label">Description</label>
                                    <input type="text" class="form-control" id="fundDesc" placeholder="ex: Fonds chantier Faladié">
                                </div>
                                <div id="addFundError" class="alert alert-danger d-none"></div>
                            </div>
                            <div class="modal-footer">
                                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Annuler</button>
                                <button type="submit" class="btn-kf">Créer</button>
                            </div>
                        </form>
                    </div>
                </div>
            </div>

            <div class="modal fade" id="topUpFundModal" tabindex="-1">
                <div class="modal-dialog">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h5 class="modal-title">Recharger un fonds</h5>
                            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                        </div>
                        <form id="topUpFundForm">
                            <input type="hidden" id="topUpFundId">
                            <div class="modal-body">
                                <div class="alert alert-info" id="topUpFundInfo"></div>
                                <div class="mb-3">
                                    <label class="form-label">Montant à ajouter (FCFA)</label>
                                    <input type="number" class="form-control" id="topUpAmount" min="1" required>
                                </div>
                                <div class="mb-3">
                                    <label class="form-label">Note</label>
                                    <input type="text" class="form-control" id="topUpDesc" placeholder="ex: Recharge semaine 2">
                                </div>
                                <div id="topUpFundError" class="alert alert-danger d-none"></div>
                            </div>
                            <div class="modal-footer">
                                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Annuler</button>
                                <button type="submit" class="btn-kf">Recharger</button>
                            </div>
                        </form>
                    </div>
                </div>
            </div>`;
    },

    fundRow(f) {
        const status = f.status || 'ACTIVE';
        const isActive = status === 'ACTIVE';
        const used = (f.initialAmount || 0) - (f.balance || 0);
        const pct = f.initialAmount > 0 ? Math.round((used / f.initialAmount) * 100) : 0;
        return `
            <tr>
                <td><strong>${Fmt.html(f.agent?.name || '—')}</strong><br><small class="text-muted">${Fmt.html(f.agent?.phoneNumber || '')}</small></td>
                <td>${Fmt.html(f.description || '—')}</td>
                <td><span class="badge bg-${ViewHelpers.fundStatusClass(status)}">${ViewHelpers.fundStatusLabel(status)}</span></td>
                <td class="amount">${Fmt.amount(f.initialAmount)} F</td>
                <td class="amount amount-positive">${Fmt.amount(f.balance)} F</td>
                <td>
                    <div class="d-flex align-items-center gap-2">
                        <div class="progress flex-grow-1" style="height:6px">
                            <div class="progress-bar ${pct > 80 ? 'bg-danger' : pct > 50 ? 'bg-warning' : 'bg-success'}" style="width:${Math.min(pct, 100)}%"></div>
                        </div>
                        <small class="text-muted">${pct}%</small>
                    </div>
                </td>
                <td>${Fmt.dateShort(f.createdAt)}</td>
                <td><button class="btn btn-sm btn-outline-success" ${isActive ? '' : 'disabled'} onclick="FundsView.showTopUpModal(${f.id})"><i class="bi bi-plus-circle"></i> Recharger</button></td>
            </tr>`;
    },

    showModal() {
        document.getElementById('addFundForm').reset();
        document.getElementById('addFundError').classList.add('d-none');
        new bootstrap.Modal(document.getElementById('addFundModal')).show();
    },

    showTopUpModal(fundId) {
        const fund = this.funds.find(f => f.id === fundId);
        if (!fund) return;
        if ((fund.status || 'ACTIVE') !== 'ACTIVE') return;
        document.getElementById('topUpFundForm').reset();
        document.getElementById('topUpFundError').classList.add('d-none');
        document.getElementById('topUpFundId').value = fund.id;
        document.getElementById('topUpFundInfo').innerHTML = `<strong>${Fmt.html(fund.agent?.name || 'Agent')}</strong><br>Solde actuel : <strong>${Fmt.amount(fund.balance)} FCFA</strong><br><small>La recharge demandera une nouvelle confirmation de réception.</small>`;
        new bootstrap.Modal(document.getElementById('topUpFundModal')).show();
    },

    async handleCreate(e) {
        e.preventDefault();
        const errEl = document.getElementById('addFundError');
        errEl.classList.add('d-none');
        try {
            await Api.createFund({
                agentId: document.getElementById('fundAgent').value,
                amount: document.getElementById('fundAmount').value,
                description: document.getElementById('fundDesc').value
            });
            bootstrap.Modal.getInstance(document.getElementById('addFundModal')).hide();
            App.navigate('funds');
        } catch (err) {
            errEl.textContent = err.message;
            errEl.classList.remove('d-none');
        }
    },

    async handleTopUp(e) {
        e.preventDefault();
        const errEl = document.getElementById('topUpFundError');
        errEl.classList.add('d-none');
        try {
            await Api.topUpFund(document.getElementById('topUpFundId').value, {
                amount: document.getElementById('topUpAmount').value,
                description: document.getElementById('topUpDesc').value
            });
            bootstrap.Modal.getInstance(document.getElementById('topUpFundModal')).hide();
            App.navigate('funds');
        } catch (err) {
            errEl.textContent = err.message;
            errEl.classList.remove('d-none');
        }
    }
};
