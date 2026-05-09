import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import LoadingSpinner from '../components/LoadingSpinner.jsx';
import { patientService } from '../services/patientService.js';
import { hospitalService } from '../services/hospitalService.js';
import { useAuth } from '../context/AuthContext.jsx';
import { useNavigate } from 'react-router-dom';

const NAV_TABS = [
  { id: 'consults', label: 'My Consultations', icon: '🩺' },
  { id: 'consent', label: 'Consent Requests', icon: '🔒' },
  { id: 'transfer', label: 'Push Records', icon: '📤' },
  { id: 'history', label: 'Activity Log', icon: '🕐' },
];

const DATA_TYPES = ['OP_CONSULT', 'PRESCRIPTION', 'LAB_RESULT'];
const PUSH_TYPES = ['OP_CONSULT', 'PRESCRIPTION', 'LAB_RESULT', 'INPATIENT'];

const STATUS_MAP = {
  PENDING: { label: 'Pending', cls: 'consent-status-pill--pending', icon: '🟡' },
  GRANTED: { label: 'Granted', cls: 'consent-status-pill--granted', icon: '🟢' },
  DENIED: { label: 'Denied', cls: 'consent-status-pill--denied', icon: '🔴' },
  REVOKED: { label: 'Revoked', cls: 'consent-status-pill--revoked', icon: '🔴' },
};

const TypeCheckbox = ({ type, checked, onChange }) => (
  <label className="consent-type-check">
    <input type="checkbox" checked={checked} onChange={onChange} />
    {type.replace(/_/g, ' ')}
  </label>
);

const formatConsultTimestamp = (consult) => {
  const raw = consult.recordedAt || consult.visitDate;
  if (!raw) return 'Date unavailable';

  const parsed = new Date(raw);
  return Number.isNaN(parsed.getTime()) ? raw : parsed.toLocaleString();
};

// ══════════════════════════════════════════════════════════════
const PatientDashboard = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const patientId = user?.patientId;

  const [consents, setConsents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('consults');
  const [consults, setConsults] = useState([]);
  const [grantedTypes, setGrantedTypes] = useState({});
  const [consentLoading, setConsentLoading] = useState({});
  const [consentMsg, setConsentMsg] = useState({ id: null, text: '', ok: true });
  const [pushForm, setPushForm] = useState({ targetRequesterId: '', targetHospitalId: '', dataTypes: ['OP_CONSULT'] });
  const [pushLoading, setPushLoading] = useState(false);
  const [pushResult, setPushResult] = useState('');
  const [activityLogs, setActivityLogs] = useState([]);
  const [activityLoading, setActivityLoading] = useState(false);
  
  const [hospitals, setHospitals] = useState([]);
  const [doctors, setDoctors] = useState([]);

  useEffect(() => {
    hospitalService.getHospitals().then(setHospitals);
  }, []);

  useEffect(() => {
    if (pushForm.targetHospitalId) {
      hospitalService.getDoctors(pushForm.targetHospitalId).then(setDoctors);
    } else {
      setDoctors([]);
    }
  }, [pushForm.targetHospitalId]);

  const loadPatientData = useCallback(async ({ showSpinner = false } = {}) => {
    if (!patientId) {
      setLoading(false);
      return;
    }

    if (showSpinner) setLoading(true);

    try {
      const [consentData, consultData] = await Promise.all([
        patientService.getConsents(patientId),
        patientService.getConsultations(patientId)
      ]);

      setConsents(consentData);
      setConsults(consultData);
      const init = {};
      consentData.forEach((c) => {
        init[c.id] = c.grantedDataTypes?.length ? c.grantedDataTypes : (c.requestedDataTypes?.length ? c.requestedDataTypes : ['OP_CONSULT']);
      });
      setGrantedTypes(init);
    } catch (e) {
      console.error(e);
    } finally {
      if (showSpinner) setLoading(false);
    }
  }, [patientId]);

  useEffect(() => {
    if (!patientId) {
      setLoading(false);
      return undefined;
    }

    loadPatientData({ showSpinner: true });
    const refreshId = window.setInterval(() => loadPatientData(), 5000);
    return () => window.clearInterval(refreshId);
  }, [patientId, loadPatientData]);

  const handleRespond = async (consent, grant) => {
    setConsentLoading((p) => ({ ...p, [consent.id]: true }));
    setConsentMsg({ id: null, text: '', ok: true });
    try {
      const types = grantedTypes[consent.id] || (consent.requestedDataTypes?.length ? consent.requestedDataTypes : ['OP_CONSULT']);
      const updated = await patientService.respondToConsent(consent.id, grant, types);
      setConsents((p) => p.map((c) => (c.id === consent.id ? { ...c, ...updated } : c)));
      setConsentMsg({
        id: consent.id,
        text: `Consent ${grant ? 'granted' : 'denied'} for ${consent.requesterId}`,
        ok: grant,
      });
      fetchActivity();
    } catch (err) {
      setConsentMsg({ id: consent.id, text: err?.response?.data?.message || 'Failed to respond.', ok: false });
    } finally {
      setConsentLoading((p) => ({ ...p, [consent.id]: false }));
    }
  };

  const handleRevoke = async (consent) => {
    setConsentLoading((p) => ({ ...p, [consent.id]: true }));
    try {
      await patientService.revokeConsent(consent.id);
      setConsents((p) => p.map((c) => (c.id === consent.id ? { ...c, status: 'REVOKED' } : c)));
      setConsentMsg({ id: consent.id, text: `Consent revoked for ${consent.requesterId}`, ok: false });
      fetchActivity();
    } catch (err) {
      setConsentMsg({ id: consent.id, text: err?.response?.data?.message || 'Failed to revoke.', ok: false });
    } finally {
      setConsentLoading((p) => ({ ...p, [consent.id]: false }));
    }
  };

  const toggleGrantedType = (cId, type) =>
    setGrantedTypes((p) => {
      const cur = p[cId] || ['OP_CONSULT'];
      return { ...p, [cId]: cur.includes(type) ? cur.filter((t) => t !== type) : [...cur, type] };
    });

  const handlePushSubmit = async (e) => {
    e.preventDefault();
    if (!pushForm.targetRequesterId.trim() || !pushForm.dataTypes.length) return;
    setPushLoading(true); setPushResult('');
    try {
      const msg = await patientService.pushRecords(pushForm.targetRequesterId, pushForm.dataTypes, pushForm.targetHospitalId);
      const displayMsg = (typeof msg === 'object') ? (msg.message || 'Records pushed successfully!') : (msg || 'Records pushed successfully!');
      setPushResult(`✅ ${displayMsg}`);
      setPushForm({ targetRequesterId: '', targetHospitalId: '', dataTypes: ['OP_CONSULT'] });
      fetchActivity();
    } catch (err) {
      setPushResult(`⚠️ ${err?.response?.data?.message || err.message || 'Push failed.'}`);
    } finally {
      setPushLoading(false);
    }
  };

  const fetchActivity = async () => {
    if (!patientId) return;
    setActivityLoading(true);
    try {
      const data = await patientService.getAuditLogs(patientId);
      setActivityLogs(data);
    } catch (e) {
      console.error('Failed to fetch activity logs:', e);
    } finally {
      setActivityLoading(false);
    }
  };

  useEffect(() => {
    if (activeTab === 'history') {
      fetchActivity();
    }
  }, [activeTab, patientId]);

  const togglePushType = (type) =>
    setPushForm((p) => ({
      ...p,
      dataTypes: p.dataTypes.includes(type)
        ? p.dataTypes.filter((t) => t !== type)
        : [...p.dataTypes, type],
    }));

  const pendingCount = consents.filter((c) => c.status === 'PENDING').length;
  const grantedCount = consents.filter((c) => c.status === 'GRANTED').length;

  return (
    <div className="dashboard-layout">
      <aside className="sidebar">
        <div className="sidebar-logo">
          <div className="sidebar-logo-icon">⚕️</div>
          <div>
            <div className="sidebar-logo-text">health-bridge</div>
            <div className="sidebar-logo-sub">Patient Portal</div>
          </div>
        </div>
        <nav className="sidebar-nav">
          {NAV_TABS.map((tab) => (
            <button
              key={tab.id}
              className={`sidebar-link ${activeTab === tab.id ? 'sidebar-link--active' : ''}`}
              style={{ background: 'none', border: 'none', width: '100%', textAlign: 'left', cursor: 'pointer' }}
              onClick={() => setActiveTab(tab.id)}
            >
              <span className="sidebar-icon">{tab.icon}</span>
              <span className="sidebar-label">{tab.label}</span>
              {tab.id === 'consent' && pendingCount > 0 && (
                <span className="sidebar-badge">{pendingCount}</span>
              )}
            </button>
          ))}
        </nav>
        <div className="sidebar-footer">
          <div className="sidebar-footer-dot" />
          <span className="sidebar-footer-text">FHIR R4</span>
        </div>
      </aside>

      <div className="dashboard-main">
        <header className="dashboard-topbar">
          <div>
            <h1 className="page-title">Patient Dashboard</h1>
            <p className="page-subtitle">
              {patientId
                ? `${user?.username} · ABHA-ID: `
                : 'Manage your health data sharing preferences'}
              {patientId && <span className="patient-id-tag">{patientId}</span>}
            </p>
          </div>
          <div className="topbar-actions">
            <span className="role-badge">🧑 {user?.username}</span>
            <button className="btn-outline" onClick={() => { logout(); navigate('/login'); }}>Sign Out</button>
          </div>
        </header>

        {loading ? (
          <div className="page-loading"><LoadingSpinner message="Loading your data..." /></div>
        ) : (
          <motion.div
            className="page-content"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.35 }}
          >
            <div className="stats-grid stats-grid--3">
              <div className="stat-card stat-card--amber">
                <div className="stat-icon">⏳</div>
                <div className="stat-value">{pendingCount}</div>
                <div className="stat-label">Pending Requests</div>
              </div>
              <div className="stat-card stat-card--green">
                <div className="stat-icon">✅</div>
                <div className="stat-value">{grantedCount}</div>
                <div className="stat-label">Active Grants</div>
              </div>
              <div className="stat-card stat-card--teal">
                <div className="stat-icon">🔒</div>
                <div className="stat-value">{consents.length}</div>
                <div className="stat-label">Total Requests</div>
              </div>
            </div>

            {!patientId && (
              <div className="alert-error">
                ⚠️ No Patient ID detected. Please log out and sign in again with your Patient account.
              </div>
            )}

            {activeTab === 'consults' && (
              <motion.div className="card" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
                <div className="card-header">
                  <div>
                    <div className="card-title">🩺 My Consultations</div>
                    <div className="card-subtitle">Your medical visits and clinical notes</div>
                  </div>
                </div>
                <div style={{ padding: '20px 22px' }}>
                  {consults.length === 0 ? (
                    <div className="empty-state" style={{ padding: '32px' }}>
                      <div className="empty-icon">📝</div>
                      <div className="empty-title">No consultations found</div>
                      <div className="empty-desc">Your clinical visits will appear here once saved by your doctor.</div>
                    </div>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
                      {consults.map(c => (
                        <div key={c.id} style={{ border: '1px solid var(--c-border)', borderRadius: 'var(--r-md)', padding: '16px' }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                            <strong style={{ fontSize: '15px', color: 'var(--c-primary-dark)' }}>{c.hospitalName}</strong>
                            <span className="timestamp">{formatConsultTimestamp(c)}</span>
                          </div>
                          <div style={{ fontSize: '14px', marginBottom: '4px' }}><strong>Doctor:</strong> {c.doctorName}</div>
                          {c.visitDate && (
                            <div style={{ fontSize: '14px', marginBottom: '4px' }}><strong>Visit date:</strong> {c.visitDate}</div>
                          )}
                          <div style={{ fontSize: '14px', marginBottom: '4px' }}>
                            <strong>Clinical notes:</strong> {c.clinicalNotes || 'No notes recorded'}
                          </div>
                          <div style={{ fontSize: '14px', marginBottom: '4px' }}>
                            <strong>Vitals:</strong> BP {c.bloodPressure || 'N/A'} · Temp {c.temperature || 'N/A'}
                          </div>
                          <div style={{ fontSize: '14px' }}>
                            <strong>Prescription:</strong> {c.prescriptionAvailable ? 'Attached' : 'Not attached'}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </motion.div>
            )}

            {activeTab === 'consent' && (
              <AnimatePresence mode="wait">
                <motion.div key="consent" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
                  {consents.length === 0 ? (
                    <div className="card">
                      <div className="empty-state">
                        <div className="empty-icon">📭</div>
                        <div className="empty-title">No consent requests yet</div>
                        <div className="empty-desc">
                          When doctors request access to your health records, they will appear here for your review.
                        </div>
                      </div>
                    </div>
                  ) : (
                    <div className="consent-list">
                      {consents.map((c) => {
                        const cfg = STATUS_MAP[c.status] || STATUS_MAP.PENDING;
                        const isLoading = consentLoading[c.id];
                        const hasMsg = consentMsg.id === c.id;
                        return (
                          <motion.div
                            key={c.id} className="consent-card"
                            layout initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
                          >
                            <div className="consent-card-top">
                              <div style={{ flex: 1 }}>
                                <div className="consent-hospital">
                                  Dr. / System: <strong>{c.requesterId}</strong>
                                </div>
                                <div className="consent-doctor">
                                  Purpose: {c.purpose}
                                </div>
                                <div className="consent-meta">
                                  Requested {new Date(c.createdAt).toLocaleString()}
                                  {' · '}
                                  Data: {(c.requestedDataTypes || []).join(', ') || 'N/A'}
                                </div>
                              </div>
                              <span className={`consent-status-pill ${cfg.cls}`}>
                                {cfg.icon} {cfg.label}
                              </span>
                            </div>

                            {c.status === 'PENDING' && (
                              <div className="consent-action-area">
                                <span className="consent-type-label">Select data types to grant access:</span>
                                <div className="consent-types-row">
                                  {DATA_TYPES.map((type) => (
                                    <TypeCheckbox
                                      key={type} type={type}
                                      checked={(grantedTypes[c.id] || (c.requestedDataTypes?.length ? c.requestedDataTypes : ['OP_CONSULT'])).includes(type)}
                                      onChange={() => toggleGrantedType(c.id, type)}
                                    />
                                  ))}
                                </div>
                                <div className="consent-action-btns">
                                  <button
                                    className="btn-primary"
                                    style={{ flex: 1 }}
                                    disabled={isLoading}
                                    onClick={() => handleRespond(c, true)}
                                  >
                                    {isLoading ? '⏳ Processing…' : '✅ Grant Access'}
                                  </button>
                                  <button
                                    className="btn-outline"
                                    style={{ flex: 1 }}
                                    disabled={isLoading}
                                    onClick={() => handleRespond(c, false)}
                                  >
                                    {isLoading ? '…' : '❌ Deny'}
                                  </button>
                                </div>
                              </div>
                            )}

                            {c.status === 'GRANTED' && (
                              <div className="consent-action-area">
                                <div style={{ fontSize: '12px', color: 'var(--c-text-muted)', marginBottom: '10px' }}>
                                  Granted access to: <strong style={{ color: 'var(--c-success-text)' }}>
                                    {(c.grantedDataTypes || []).join(', ') || 'N/A'}
                                  </strong>
                                </div>
                                <button className="btn-danger" disabled={isLoading} onClick={() => handleRevoke(c)}>
                                  {isLoading ? '⏳ Revoking…' : '🔒 Revoke Consent'}
                                </button>
                              </div>
                            )}

                            <AnimatePresence>
                              {hasMsg && (
                                <motion.div
                                  className={consentMsg.ok ? 'alert-success' : 'alert-error'}
                                  style={{ margin: '0 20px 16px' }}
                                  initial={{ opacity: 0, height: 0 }}
                                  animate={{ opacity: 1, height: 'auto' }}
                                  exit={{ opacity: 0, height: 0 }}
                                >
                                  {consentMsg.text}
                                </motion.div>
                              )}
                            </AnimatePresence>
                          </motion.div>
                        );
                      })}
                    </div>
                  )}
                </motion.div>
              </AnimatePresence>
            )}

            {activeTab === 'transfer' && (
              <motion.div className="card" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
                <div className="card-header">
                  <div>
                    <div className="card-title">📤 Push Records to a Doctor</div>
                    <div className="card-subtitle">
                      Your identity is read securely from your session token
                    </div>
                  </div>
                </div>

                <div className="submit-form">
                  {pushResult && (
                    <div className={pushResult.startsWith('✅') ? 'alert-success' : 'alert-error'}>
                      {pushResult}
                    </div>
                  )}
                  <form onSubmit={handlePushSubmit}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '13px' }}>
                      <div style={{ display: 'flex', gap: '15px' }}>
                        <div className="form-group" style={{ flex: 1 }}>
                          <label className="form-label">Target Hospital</label>
                          <select
                            className="form-input"
                            value={pushForm.targetHospitalId}
                            onChange={(e) => setPushForm({ ...pushForm, targetHospitalId: e.target.value, targetRequesterId: '' })}
                          >
                            <option value="">-- Select a Hospital --</option>
                            {hospitals.map(h => (
                              <option key={h.id} value={h.id}>{h.name} ({h.id})</option>
                            ))}
                          </select>
                        </div>
                        <div className="form-group" style={{ flex: 1 }}>
                          <label className="form-label">Target Doctor</label>
                          <select
                            className="form-input"
                            disabled={!pushForm.targetHospitalId || doctors.length === 0}
                            value={pushForm.targetRequesterId}
                            onChange={(e) => setPushForm({ ...pushForm, targetRequesterId: e.target.value })}
                          >
                            <option value="">{pushForm.targetHospitalId ? (doctors.length ? '-- Select Doctor --' : 'No doctors available') : 'Select hospital first'}</option>
                            {doctors.map(d => (
                              <option key={d.username} value={d.username}>Dr. {d.fullName} ({d.specialization})</option>
                            ))}
                          </select>
                        </div>
                      </div>
                      <div className="form-group">
                        <label className="form-label">Data Types to Push</label>
                        <div className="consent-types-row">
                          {PUSH_TYPES.map((type) => (
                            <TypeCheckbox
                              key={type} type={type}
                              checked={pushForm.dataTypes.includes(type)}
                              onChange={() => togglePushType(type)}
                            />
                          ))}
                        </div>
                      </div>
                      <button type="submit" className="btn-primary" disabled={pushLoading}>
                        {pushLoading ? <><span className="btn-spinner" /> Pushing…</> : '🚀 Push Records Now'}
                      </button>
                    </div>
                  </form>
                </div>
              </motion.div>
            )}

            {activeTab === 'history' && (
              <motion.div className="card" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
                <div className="card-header">
                  <div>
                    <div className="card-title">🕐 Session Activity Log</div>
                    <div className="card-subtitle">Persistent history of data exchanges from the HIE Gateway</div>
                  </div>
                </div>
                <div style={{ padding: '20px 22px' }}>
                  {activityLoading ? (
                    <div style={{ padding: '40px', textAlign: 'center' }}>
                      <LoadingSpinner message="Fetching history..." />
                    </div>
                  ) : activityLogs.length === 0 ? (
                    <div className="empty-state" style={{ padding: '32px' }}>
                      <div className="empty-icon">📋</div>
                      <div className="empty-title">No activity history</div>
                      <div className="empty-desc">Your clinical data exchange history will appear here.</div>
                    </div>
                  ) : (
                    <div className="timeline">
                      {activityLogs.map((item, i) => (
                        <div key={item.id || i} className="timeline-item">
                          <div className={`timeline-dot ${item.status === 'SUCCESS' ? 'timeline-dot--green'
                            : item.status === 'PENDING' ? 'timeline-dot--amber'
                              : 'timeline-dot--red'
                            }`} />
                          <div className="timeline-content">
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                              <div>
                                <span className={`timeline-action ${item.status === 'SUCCESS' ? 'action-grant'
                                  : item.status === 'PENDING' ? 'action-deny'
                                    : 'action-revoke'
                                  }`}>
                                  {item.status}
                                </span>
                                <span className="timeline-target">Source: {item.sourceHospital} → Target: {item.targetHospital}</span>
                              </div>
                              <span className="timeline-time">{new Date(item.timestamp).toLocaleString()}</span>
                            </div>
                            <div style={{ fontSize: '12px', color: 'var(--c-text-muted)', marginTop: '4px' }}>
                              Resources transferred: {item.bundleResourceCount}
                              {item.failureReason && <div style={{ color: 'var(--c-error-text)', marginTop: '2px' }}>Reason: {item.failureReason}</div>}
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </motion.div>
            )}
          </motion.div>
        )}
      </div>
    </div>
  );
};

export default PatientDashboard;
