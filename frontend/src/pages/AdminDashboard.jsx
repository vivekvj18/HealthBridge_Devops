import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, Legend,
} from 'recharts';
import Sidebar from '../components/Sidebar.jsx';
import StatusBadge from '../components/StatusBadge.jsx';
import LoadingSpinner from '../components/LoadingSpinner.jsx';
import { adminService } from '../services/adminService.js';
import { useAuth } from '../context/AuthContext.jsx';
import { useNavigate } from 'react-router-dom';

const SIDEBAR_ITEMS = [
  { to: '/admin/dashboard', label: 'Overview', icon: '📊', end: true },
  { to: '/admin/transfers', label: 'Transfers', icon: '🔄' },
  { to: '/admin/audit-logs', label: 'Audit Logs', icon: '📋' },
  { to: '/admin/users', label: 'Users', icon: '👥' },
];

const FILTER_OPTIONS = ['ALL', 'SUCCESS', 'PENDING', 'FAILED'];

const CustomTooltip = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null;
  return (
    <div style={{
      background: 'var(--c-navy)', border: '1px solid rgba(255,255,255,0.08)',
      borderRadius: 'var(--r-md)', padding: '10px 14px', fontSize: '12px',
    }}>
      <p style={{ color: 'rgba(255,255,255,0.5)', marginBottom: '6px', fontWeight: '600' }}>{label}</p>
      {payload.map((p) => (
        <p key={p.name} style={{ color: p.color, fontWeight: '600' }}>
          {p.name}: {p.value}
        </p>
      ))}
    </div>
  );
};

const AdminDashboard = () => {
  const { logout } = useAuth();
  const navigate = useNavigate();

  const [transfers, setTransfers] = useState([]);
  const [auditLogs, setAuditLogs] = useState([]);
  const [healthData, setHealthData] = useState([]);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('transfers');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [logSearch, setLogSearch] = useState('');

  useEffect(() => {
    const fetchAll = async () => {
      setLoading(true);
      try {
        const [t, a, h, u] = await Promise.all([
          adminService.getAllTransfers(),
          adminService.getAuditLogs(),
          adminService.getSystemHealth(),
          adminService.getUsers(),
        ]);
        setTransfers(t);
        setAuditLogs(a);
        setHealthData(h);
        setUsers(u);
      } finally {
        setLoading(false);
      }
    };
    fetchAll();
  }, []);

  const filteredTransfers = statusFilter === 'ALL'
    ? transfers
    : transfers.filter((t) => t.status === statusFilter);

  const filteredLogs = auditLogs.filter((log) => {
    const q = logSearch.toLowerCase();
    return !q || log.user?.toLowerCase().includes(q)
      || log.action?.toLowerCase().includes(q)
      || log.resource?.toLowerCase().includes(q);
  });

  const stats = {
    total: transfers.length,
    success: transfers.filter((t) => t.status === 'SUCCESS').length,
    pending: transfers.filter((t) => t.status === 'PENDING').length,
    failed: transfers.filter((t) => t.status === 'FAILED').length,
  };

  const STAT_CARDS = [
    { label: 'Total Transfers', value: stats.total, icon: '🔄', color: 'teal' },
    { label: 'Successful', value: stats.success, icon: '✅', color: 'green' },
    { label: 'Pending', value: stats.pending, icon: '⏳', color: 'amber' },
    { label: 'Failed', value: stats.failed, icon: '❌', color: 'red' },
  ];

  return (
    <div className="dashboard-layout">
      <Sidebar items={SIDEBAR_ITEMS} />

      <div className="dashboard-main">
        <header className="dashboard-topbar">
          <div>
            <h1 className="page-title">System Overview</h1>
            <p className="page-subtitle">Monitor transfers, audit logs, and system health</p>
          </div>
          <div className="topbar-actions">
            <span className="system-status-indicator">
              <span className="pulse-dot" /> All Systems Operational
            </span>
            <button className="btn-outline" onClick={() => { logout(); navigate('/login'); }}>
              Sign Out
            </button>
          </div>
        </header>

        {loading ? (
          <div className="page-loading">
            <LoadingSpinner message="Loading system data..." />
          </div>
        ) : (
          <motion.div
            className="page-content"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.35 }}
          >
            {/* Stat Cards */}
            <div className="stats-grid">
              {STAT_CARDS.map((s, i) => (
                <motion.div
                  key={s.label}
                  className={`stat-card stat-card--${s.color}`}
                  initial={{ opacity: 0, y: 14 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.07 }}
                >
                  <div className="stat-icon">{s.icon}</div>
                  <div className="stat-value">{s.value}</div>
                  <div className="stat-label">{s.label}</div>
                </motion.div>
              ))}
            </div>

            {/* Chart */}
            <div className="card">
              <div className="card-header">
                <div>
                  <div className="card-title">Weekly Transfer Activity</div>
                  <div className="card-subtitle">7-day transfer volume and failure rate</div>
                </div>
                <span style={{
                  fontSize: '11px', fontWeight: '600', color: 'var(--c-text-muted)',
                  background: 'var(--c-divider)', padding: '4px 10px',
                  borderRadius: 'var(--r-full)',
                }}>
                  Last 7 days
                </span>
              </div>
              <div className="chart-wrap">
                <ResponsiveContainer width="100%" height={240}>
                  <AreaChart data={healthData} margin={{ top: 6, right: 16, left: -10, bottom: 0 }}>
                    <defs>
                      <linearGradient id="gTeal" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#0D9488" stopOpacity={0.25} />
                        <stop offset="95%" stopColor="#0D9488" stopOpacity={0} />
                      </linearGradient>
                      <linearGradient id="gRed" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#EF4444" stopOpacity={0.18} />
                        <stop offset="95%" stopColor="#EF4444" stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="#F1F5F9" />
                    <XAxis dataKey="name" tick={{ fontSize: 11, fill: '#94A3B8' }} axisLine={false} tickLine={false} />
                    <YAxis tick={{ fontSize: 11, fill: '#94A3B8' }} axisLine={false} tickLine={false} />
                    <Tooltip content={<CustomTooltip />} />
                    <Legend
                      iconType="circle" iconSize={8}
                      wrapperStyle={{ fontSize: '12px', color: '#64748B' }}
                    />
                    <Area
                      type="monotone" dataKey="transfers" stroke="#0D9488"
                      strokeWidth={2.5} fill="url(#gTeal)" name="Transfers"
                    />
                    <Area
                      type="monotone" dataKey="failures" stroke="#EF4444"
                      strokeWidth={2} fill="url(#gRed)" name="Failures"
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </div>

            {/* Tab Nav */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
              <div className="tab-nav">
                <button
                  className={`tab-btn ${activeTab === 'transfers' ? 'tab-btn--active' : ''}`}
                  onClick={() => setActiveTab('transfers')}
                >
                  🔄 Transfers
                  <span className="tab-badge">{transfers.length}</span>
                </button>
                <button
                  className={`tab-btn ${activeTab === 'audit' ? 'tab-btn--active' : ''}`}
                  onClick={() => setActiveTab('audit')}
                >
                  📋 Audit Logs
                </button>
                <button
                  className={`tab-btn ${activeTab === 'users' ? 'tab-btn--active' : ''}`}
                  onClick={() => setActiveTab('users')}
                >
                  👥 Users
                </button>
              </div>

              {activeTab === 'transfers' && (
                <div className="filter-pills">
                  {FILTER_OPTIONS.map((opt) => (
                    <button
                      key={opt}
                      className={`filter-pill ${statusFilter === opt ? 'filter-pill--active' : ''}`}
                      onClick={() => setStatusFilter(opt)}
                    >
                      {opt}
                    </button>
                  ))}
                </div>
              )}
              {activeTab === 'audit' && (
                <input
                  type="text"
                  className="search-input"
                  placeholder="Search by user, action, resource…"
                  value={logSearch}
                  onChange={(e) => setLogSearch(e.target.value)}
                />
              )}
            </div>

            {/* Transfers Table */}
            {activeTab === 'transfers' && (
              <motion.div
                className="card"
                initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.25 }}
              >
                <div className="table-wrap">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>ID</th>
                        <th>Patient</th>
                        <th>Source</th>
                        <th>Target</th>
                        <th>Resources</th>
                        <th>Status</th>
                        <th>Timestamp</th>
                        <th>Failure Reason</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredTransfers.length === 0 ? (
                        <tr>
                          <td colSpan={8} className="table-empty">
                            No transfers found for the selected filter.
                          </td>
                        </tr>
                      ) : (
                        filteredTransfers.map((t) => (
                          <tr key={t.id} className="table-row">
                            <td className="table-id">#{t.id}</td>
                            <td><span className="patient-id-tag">{t.patientId}</span></td>
                            <td style={{ fontSize: '13px', color: 'var(--c-text-secondary)' }}>{t.sourceHospital}</td>
                            <td style={{ fontSize: '13px', color: 'var(--c-text-secondary)' }}>{t.targetHospital}</td>
                            <td>
                              <span style={{
                                fontFamily: "'JetBrains Mono', monospace",
                                fontSize: '12px', fontWeight: '600',
                                color: 'var(--c-text-primary)',
                              }}>
                                {t.bundleResourceCount}
                              </span>
                            </td>
                            <td><StatusBadge status={t.status} /></td>
                            <td className="timestamp">{new Date(t.timestamp).toLocaleString()}</td>
                            <td className="failure-reason">{t.failureReason || <span style={{ color: 'var(--c-text-disabled)' }}>—</span>}</td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </motion.div>
            )}

            {/* Audit Logs Table */}
            {activeTab === 'audit' && (
              <motion.div
                className="card"
                initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.25 }}
              >
                <div className="table-wrap">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Timestamp</th>
                        <th>User</th>
                        <th>Action</th>
                        <th>Resource</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredLogs.length === 0 ? (
                        <tr>
                          <td colSpan={5} className="table-empty">
                            No logs match your search.
                          </td>
                        </tr>
                      ) : (
                        filteredLogs.map((log) => (
                          <tr key={log.id} className="table-row">
                            <td className="timestamp">{new Date(log.timestamp).toLocaleString()}</td>
                            <td><span className="user-tag">@{log.user}</span></td>
                            <td><span className="action-tag">{log.action}</span></td>
                            <td style={{ fontSize: '13px', color: 'var(--c-text-secondary)', maxWidth: '220px' }}>{log.resource}</td>
                            <td><StatusBadge status={log.status} /></td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </motion.div>
            )}

            {/* Users Table */}
            {activeTab === 'users' && (
              <motion.div
                className="card"
                initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.25 }}
              >
                <div className="table-wrap">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Username</th>
                        <th>Role</th>
                        <th>Full Name</th>
                        <th>Hospital</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {users.length === 0 ? (
                        <tr>
                          <td colSpan={5} className="table-empty">
                            No users found.
                          </td>
                        </tr>
                      ) : (
                        users.map((u) => (
                          <tr key={u.id} className="table-row">
                            <td><span className="user-tag">@{u.username}</span></td>
                            <td><StatusBadge status={u.role} /></td>
                            <td style={{ fontSize: '13px', color: 'var(--c-text-secondary)' }}>{u.fullName || '—'}</td>
                            <td style={{ fontSize: '13px', color: 'var(--c-text-secondary)' }}>{u.hospitalId || '—'}</td>
                            <td>
                              <button
                                className="btn-outline"
                                style={{ padding: '4px 8px', fontSize: '11px', color: 'var(--c-danger)', borderColor: 'var(--c-danger)' }}
                                onClick={async () => {
                                  if (confirm(`Are you sure you want to delete ${u.username}?`)) {
                                    await adminService.deleteUser(u.id);
                                    setUsers(users.filter(x => x.id !== u.id));
                                  }
                                }}
                              >
                                Deactivate
                              </button>
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </motion.div>
            )}
          </motion.div>
        )}
      </div>
    </div>
  );
};

export default AdminDashboard;