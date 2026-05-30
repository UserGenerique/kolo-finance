/* ============================================
   Kolo Finance — View Renderers
   ============================================ */

const Fmt = {
    amount(val) {
        return new Intl.NumberFormat('fr-FR').format(val || 0);
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
    }
};

/* ---- DASHBOARD ---- */
const DashboardView = {
    async render(container) {
        container.innerHTML = '<div class="spinner-wrapper"><div class="spinner-border text-primary"></div></div>';
        try {
            const data = await Api.getDashboard();
            const cats = data.expensesByCategory || {};
            const catHtml = Object.entries(cats).map(([cat, amount]) =>
                `<div class="d-flex justify-content-between align-items-center py-2 border-bottom">
                    <span class="badge-category badge-${cat}">${cat}</span>
                    <span class="amount">${Fmt.amount(amount)} F</span>
                </div>`
            ).join('') || '<div class="text-muted py-2">Aucune dépense</div>';

            container.innerHTML = `
                <div class="row g-3 mb-4">
                    <div class="col-6 col-lg-3">
                        <div class="stat-card">
                            <div class="d-flex justify-content-between">
                                <div>
                                    <div class="stat-value">${Fmt.amount(data.totalFunds)}</div>
                                    <div class="stat-label">Fonds totaux (FCFA)</div>
                                </div>
                                <i class="bi bi-wallet2 stat-icon"></i>
                            </div>
                        </div>
                    </div>
                    <div class="col-6 col-lg-3">
                        <div class="stat-card success">
                            <div class="d-flex justify-content-between">
                                <div>
                                    <div class="stat-value">${Fmt.amount(data.totalBalance)}</div>
                                    <div class="stat-label">Solde restant (FCFA)</div>
                                </div>
                                <i class="bi bi-cash-stack stat-icon"></i>
                            </div>
                        </div>
                    </div>
                    <div class="col-6 col-lg-3">
                        <div class="stat-card accent">
                            <div class="d-flex justify-content-between">
                                <div>
                                    <div class="stat-value">${Fmt.amount(data.totalExpenses)}</div>
                                    <div class="stat-label">Dépenses totales (FCFA)</div>
                                </div>
                                <i class="bi bi-cart-dash stat-icon"></i>
                            </div>
                        </div>
                    </div>
                    <div class="col-6 col-lg-3">
                        <div class="stat-card warning">
                            <div class="d-flex justify-content-between">
                                <div>
                                    <div class="stat-value">${data.agentCount}</div>
                                    <div class="stat-label">Agents actifs</div>
                                </div>
                                <i class="bi bi-people stat-icon"></i>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="row g-3">
                    <div class="col-lg-7">
                        <div class="data-card">
                            <div class="data-card-header">
                                <h5><i class="bi bi-clock-history"></i> Dernières dépenses</h5>
                            </div>
                            <div id="recentExpenses"></div>
                        </div>
                    </div>
                    <div class="col-lg-5">
                        <div class="data-card">
                            <div class="data-card-header">
                                <h5><i class="bi bi-pie-chart"></i> Par catégorie</h5>
                            </div>
                            <div class="p-3">${catHtml}</div>
                        </div>
                    </div>
                </div>`;

            // Load recent expenses
            const expenses = await Api.getExpenses();
            const recentEl = document.getElementById('recentExpenses');
            if (expenses.length === 0) {
                recentEl.innerHTML = '<div class="empty-state"><i class="bi bi-inbox"></i><p>Aucune dépense</p></div>';
            } else {
                recentEl.innerHTML = `<div class="table-responsive"><table class="data-table">
                    <thead><tr><th>Date</th><th>Agent</th><th>Description</th><th>Montant</th></tr></thead>
                    <tbody>${expenses.slice(0, 8).map(e => `
                        <tr>
                            <td>${Fmt.dateShort(e.confirmedAt)}</td>
                            <td>${e.agent?.name || '—'}</td>
                            <td>${e.description} <span class="badge-category badge-${e.category || 'DIVERS'}">${e.category || 'DIVERS'}</span></td>
                            <td class="amount">${Fmt.amount(e.amount)} F</td>
                        </tr>`).join('')}
                    </tbody></table></div>`;
            }
        } catch (err) {
            container.innerHTML = `<div class="alert alert-danger">${err.message}</div>`;
        }
    }
};

/* ---- EXPENSES ---- */
const ExpensesView = {
    allExpenses: [],
    allDrafts: [],

    async render(container) {
        container.innerHTML = '<div class="spinner-wrapper"><div class="spinner-border text-primary"></div></div>';
        try {
            const [expenses, drafts, users] = await Promise.all([
                Api.getExpenses(), Api.getDrafts(), Api.getUsers()
            ]);
            this.allExpenses = expenses;
            this.allDrafts = drafts;

            const agentOptions = users
                .filter(u => u.role === 'AGENT')
                .map(u => `<option value="${u.id}">${u.name}</option>`).join('');

            const categories = [...new Set(expenses.map(e => e.category).filter(Boolean))];
            const catOptions = categories.map(c => `<option value="${c}">${c}</option>`).join('');

            container.innerHTML = `
                <div class="data-card mb-4">
                    <div class="data-card-header">
                        <h5><i class="bi bi-receipt"></i> Dépenses confirmées</h5>
                        <div class="filter-bar">
                            <select class="form-select" id="filterAgent">
                                <option value="">Tous les agents</option>${agentOptions}
                            </select>
                            <select class="form-select" id="filterCategory">
                                <option value="">Toutes catégories</option>${catOptions}
                            </select>
                            <input type="text" class="form-control" id="filterSearch" placeholder="Rechercher...">
                        </div>
                    </div>
                    <div class="table-responsive">
                        <table class="data-table">
                            <thead><tr>
                                <th>Date</th><th>Agent</th><th>Description</th><th>Catégorie</th><th>Montant</th>
                            </tr></thead>
                            <tbody id="expenseRows"></tbody>
                        </table>
                    </div>
                    <div id="expenseEmpty" class="empty-state d-none"><i class="bi bi-inbox"></i><p>Aucune dépense trouvée</p></div>
                </div>

                <div class="data-card">
                    <div class="data-card-header">
                        <h5><i class="bi bi-pencil-square"></i> Brouillons</h5>
                    </div>
                    <div class="table-responsive">
                        <table class="data-table">
                            <thead><tr>
                                <th>Date</th><th>Agent</th><th>Description</th><th>Montant</th><th>Statut</th>
                            </tr></thead>
                            <tbody id="draftRows"></tbody>
                        </table>
                    </div>
                    <div id="draftEmpty" class="empty-state d-none"><i class="bi bi-inbox"></i><p>Aucun brouillon</p></div>
                </div>`;

            // Bind filters
            ['filterAgent', 'filterCategory', 'filterSearch'].forEach(id => {
                document.getElementById(id).addEventListener('input', () => this.applyFilters());
            });

            this.applyFilters();
            this.renderDrafts();
        } catch (err) {
            container.innerHTML = `<div class="alert alert-danger">${err.message}</div>`;
        }
    },

    applyFilters() {
        const agentId = document.getElementById('filterAgent').value;
        const category = document.getElementById('filterCategory').value;
        const search = document.getElementById('filterSearch').value.toLowerCase();

        let filtered = this.allExpenses;
        if (agentId) filtered = filtered.filter(e => String(e.agent?.id) === agentId);
        if (category) filtered = filtered.filter(e => e.category === category);
        if (search) filtered = filtered.filter(e => e.description.toLowerCase().includes(search));

        const tbody = document.getElementById('expenseRows');
        const empty = document.getElementById('expenseEmpty');

        if (filtered.length === 0) {
            tbody.innerHTML = '';
            empty.classList.remove('d-none');
        } else {
            empty.classList.add('d-none');
            tbody.innerHTML = filtered.map(e => `
                <tr>
                    <td>${Fmt.date(e.confirmedAt)}</td>
                    <td>${e.agent?.name || '—'}</td>
                    <td>${e.description}</td>
                    <td><span class="badge-category badge-${e.category || 'DIVERS'}">${e.category || 'DIVERS'}</span></td>
                    <td class="amount">${Fmt.amount(e.amount)} F</td>
                </tr>`).join('');
        }
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
                <tr>
                    <td>${Fmt.date(d.createdAt)}</td>
                    <td>${d.agent?.name || '—'}</td>
                    <td>${d.description}</td>
                    <td class="amount">${Fmt.amount(d.amount)} F</td>
                    <td><span class="badge-status badge-${d.status}">${d.status}</span></td>
                </tr>`).join('');
        }
    }
};

/* ---- AGENTS ---- */
const AgentsView = {
    async render(container) {
        container.innerHTML = '<div class="spinner-wrapper"><div class="spinner-border text-primary"></div></div>';
        try {
            const users = await Api.getUsers();
            container.innerHTML = `
                <div class="data-card">
                    <div class="data-card-header">
                        <h5><i class="bi bi-people"></i> Utilisateurs</h5>
                        <button class="btn-kf" onclick="AgentsView.showModal()">
                            <i class="bi bi-plus-lg"></i> Ajouter
                        </button>
                    </div>
                    <div class="table-responsive">
                        <table class="data-table">
                            <thead><tr>
                                <th>Nom</th><th>Téléphone</th><th>Rôle</th><th>Statut</th><th>Inscrit le</th>
                            </tr></thead>
                            <tbody>${users.map(u => `
                                <tr>
                                    <td><strong>${u.name}</strong></td>
                                    <td>${u.phoneNumber}</td>
                                    <td><span class="badge-role badge-${u.role}">${u.role}</span></td>
                                    <td>${u.active ? '<span class="text-success">● Actif</span>' : '<span class="text-muted">● Inactif</span>'}</td>
                                    <td>${Fmt.dateShort(u.createdAt)}</td>
                                </tr>`).join('')}
                            </tbody>
                        </table>
                    </div>
                    ${users.length === 0 ? '<div class="empty-state"><i class="bi bi-people"></i><p>Aucun utilisateur</p></div>' : ''}
                </div>

                <!-- Modal ajout -->
                <div class="modal fade" id="addUserModal" tabindex="-1">
                    <div class="modal-dialog">
                        <div class="modal-content">
                            <div class="modal-header">
                                <h5 class="modal-title">Nouvel utilisateur</h5>
                                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                            </div>
                            <form id="addUserForm">
                                <div class="modal-body">
                                    <div class="mb-3">
                                        <label class="form-label">Nom</label>
                                        <input type="text" class="form-control" id="userName" required>
                                    </div>
                                    <div class="mb-3">
                                        <label class="form-label">Numéro WhatsApp</label>
                                        <input type="text" class="form-control" id="userPhone" placeholder="22378550131" required>
                                    </div>
                                    <div class="mb-3">
                                        <label class="form-label">Rôle</label>
                                        <select class="form-select" id="userRole" required>
                                            <option value="AGENT">Agent</option>
                                            <option value="MANAGER">Manager</option>
                                            <option value="BOSS">Boss</option>
                                        </select>
                                    </div>
                                    <div id="addUserError" class="alert alert-danger d-none"></div>
                                </div>
                                <div class="modal-footer">
                                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Annuler</button>
                                    <button type="submit" class="btn-kf">Créer</button>
                                </div>
                            </form>
                        </div>
                    </div>
                </div>`;

            document.getElementById('addUserForm').addEventListener('submit', (e) => this.handleCreate(e));
        } catch (err) {
            container.innerHTML = `<div class="alert alert-danger">${err.message}</div>`;
        }
    },

    showModal() {
        new bootstrap.Modal(document.getElementById('addUserModal')).show();
    },

    async handleCreate(e) {
        e.preventDefault();
        const errEl = document.getElementById('addUserError');
        errEl.classList.add('d-none');
        try {
            await Api.createUser({
                name: document.getElementById('userName').value,
                phoneNumber: document.getElementById('userPhone').value,
                role: document.getElementById('userRole').value
            });
            bootstrap.Modal.getInstance(document.getElementById('addUserModal')).hide();
            App.navigate('agents');
        } catch (err) {
            errEl.textContent = err.message;
            errEl.classList.remove('d-none');
        }
    }
};

/* ---- FUNDS ---- */
const FundsView = {
    async render(container) {
        container.innerHTML = '<div class="spinner-wrapper"><div class="spinner-border text-primary"></div></div>';
        try {
            const [funds, users] = await Promise.all([Api.getFunds(), Api.getUsers()]);
            const agents = users.filter(u => u.role === 'AGENT');
            const agentOptions = agents.map(u => `<option value="${u.id}">${u.name} (${u.phoneNumber})</option>`).join('');

            container.innerHTML = `
                <div class="data-card">
                    <div class="data-card-header">
                        <h5><i class="bi bi-wallet2"></i> Fonds confiés</h5>
                        <button class="btn-kf" onclick="FundsView.showModal()">
                            <i class="bi bi-plus-lg"></i> Confier des fonds
                        </button>
                    </div>
                    <div class="table-responsive">
                        <table class="data-table">
                            <thead><tr>
                                <th>Agent</th><th>Description</th><th>Montant initial</th><th>Solde</th><th>Utilisé</th><th>Date</th>
                            </tr></thead>
                            <tbody>${funds.map(f => {
                                const used = f.initialAmount - f.balance;
                                const pct = f.initialAmount > 0 ? Math.round((used / f.initialAmount) * 100) : 0;
                                return `
                                <tr>
                                    <td><strong>${f.agent?.name || '—'}</strong></td>
                                    <td>${f.description || '—'}</td>
                                    <td class="amount">${Fmt.amount(f.initialAmount)} F</td>
                                    <td class="amount amount-positive">${Fmt.amount(f.balance)} F</td>
                                    <td>
                                        <div class="d-flex align-items-center gap-2">
                                            <div class="progress flex-grow-1" style="height:6px">
                                                <div class="progress-bar ${pct > 80 ? 'bg-danger' : pct > 50 ? 'bg-warning' : 'bg-success'}"
                                                     style="width:${pct}%"></div>
                                            </div>
                                            <small class="text-muted">${pct}%</small>
                                        </div>
                                    </td>
                                    <td>${Fmt.dateShort(f.createdAt)}</td>
                                </tr>`;
                            }).join('')}
                            </tbody>
                        </table>
                    </div>
                    ${funds.length === 0 ? '<div class="empty-state"><i class="bi bi-wallet2"></i><p>Aucun fonds</p></div>' : ''}
                </div>

                <!-- Modal ajout -->
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
                </div>`;

            document.getElementById('addFundForm').addEventListener('submit', (e) => this.handleCreate(e));
        } catch (err) {
            container.innerHTML = `<div class="alert alert-danger">${err.message}</div>`;
        }
    },

    showModal() {
        new bootstrap.Modal(document.getElementById('addFundModal')).show();
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
    }
};
