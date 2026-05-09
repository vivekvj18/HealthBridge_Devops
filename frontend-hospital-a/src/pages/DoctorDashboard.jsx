import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import Sidebar from '../components/Sidebar.jsx';
import { doctorService } from '../services/doctorService.js';
import { useAuth } from '../context/AuthContext.jsx';
import { useNavigate } from 'react-router-dom';
import { hieService } from '../services/hieService.js';
import StatusBadge from '../components/StatusBadge.jsx';
import { z } from 'zod';
import DOMPurify from 'dompurify';

const submitSchema = z.object({
  patientId: z.string().min(1, 'Required'),
  patientFirstName: z.string().min(1, 'Required'),
  patientLastName: z.string().min(1, 'Required'),
  visitDate: z.string().min(1, 'Required'),
  symptoms: z.string().min(1, 'Required'),
  temperature: z.string().refine(val => !isNaN(parseFloat(val)), 'Must be a number'),
  bloodPressure: z.string().min(1, 'Required'),
});

const SIDEBAR_ITEMS = [
  { to: '/doctor/dashboard', label: 'Dashboard', icon: '📊', end: true },
];

const DATA_TYPES = ['OP_CONSULT', 'PRESCRIPTION', 'LAB_RESULT'];

// ── Small helpers ──────────────────────────────────────────────
const FieldRow = ({ label, name, type = 'text', placeholder, value, onChange, error, readOnly }) => (
  <div className="form-group">
    <label className="form-label">{label}</label>
    <input
      name={name} type={type}
      className={`form-input ${error ? 'input-error' : ''}`}
      placeholder={placeholder} value={value}
      onChange={onChange} readOnly={readOnly}
    />
    {error && <span className="field-error">{error}</span>}
  </div>
);

const TypeCheckbox = ({ type, checked, onChange }) => (
  <label className="consent-type-check">
    <input type="checkbox" checked={checked} onChange={onChange} />
    {type.replace(/_/g, ' ')}
  </label>
);

const PATIENT_DETAIL_FIELDS = [
  ['ABHA-ID', 'abhaId'],
  ['Username', 'username'],
  ['Name', 'fullName', 'name'],
  ['Email', 'email'],
  ['Phone', 'phone'],
  ['Date of Birth', 'dateOfBirth', 'dob'],
  ['Gender', 'gender'],
  ['Blood Group', 'bloodGroup'],
];

const patientDetailValue = (details, keys) => {
  const value = keys.map((key) => details?.[key]).find((item) => item !== undefined && item !== null && item !== '');
  return value || 'Not provided';
};

const PatientDetailsGrid = ({ details }) => (
  <div className="detail-grid">
    {PATIENT_DETAIL_FIELDS.map(([label, ...keys]) => (
      <div className="detail-row" key={label}>
        <span className="detail-label">{label}:</span>
        <span className="detail-value">{patientDetailValue(details, keys)}</span>
      </div>
    ))}
  </div>
);

const getApiErrorMessage = (err, fallback) => {
  const status = err?.response?.status;
  const data = err?.response?.data;
  const rawMessage =
    (typeof data === 'string' && data.trim()) ||
    data?.message ||
    data?.error ||
    err?.message ||
    fallback;

  return status ? `${rawMessage} (HTTP ${status})` : rawMessage;
};

const hasPdfAttachment = (record) => !!record?.prescriptionPdfBase64;

const splitFullName = (fullName = '') => {
  const trimmed = fullName.trim();
  if (!trimmed) {
    return { firstName: '', lastName: '' };
  }

  const parts = trimmed.split(/\s+/);
  return {
    firstName: parts[0] || '',
    lastName: parts.slice(1).join(' '),
  };
};

const shouldLookupPatientIdentifier = (identifier) => {
  const trimmed = identifier.trim();
  if (!trimmed) return false;
  if (trimmed.startsWith('ABHA-')) return trimmed.length >= 12;
  return trimmed.length >= 6;
};

// ══════════════════════════════════════════════════════════════
const HospitalADashboard = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [activePanel, setActivePanel] = useState('submit');
  const [copyFeedback, setCopyFeedback] = useState('');

  // TC-05 Fix: Clear transient HIE/FHIR results when switching panels
  useEffect(() => {
    setHieFhirResult('');
    setHieStatus(null);
    setFhirResult(null);
    setFhirError('');
    setSubmitResult('');
    setSubmitError('');
    setCreatePatientResult(null);
    setCopyFeedback('');
  }, [activePanel]);

  // ── Hospital A: Submit ────────────────────────────────────────
  const [submitForm, setSubmitForm] = useState({
    patientId: '', abhaId: '', patientFirstName: '', patientLastName: '',
    doctorName: user?.username || '', visitDate: '',
    symptoms: '', temperature: '', bloodPressure: '',
  });
  const [submitPdf, setSubmitPdf] = useState(null);
  const [submitErrors, setSubmitErrors] = useState({});
  const [submitLoading, setSubmitLoading] = useState(false);
  const [submitResult, setSubmitResult] = useState('');
  const [submitError, setSubmitError] = useState('');
  const [patientLookupLoading, setPatientLookupLoading] = useState(false);
  const [patientLookupMessage, setPatientLookupMessage] = useState('');
  const [patientLookupError, setPatientLookupError] = useState('');

  // ── Consent Initiation ────────────────────────────────────────
  const [consentForm, setConsentForm] = useState({
    patientId: '', purpose: '', requestedDataTypes: ['OP_CONSULT'],
  });
  const [consentLoading, setConsentLoading] = useState(false);
  const [consentResult, setConsentResult] = useState(null);
  const [consentError, setConsentError] = useState('');

  const [hieForm, setHieForm] = useState({
    abhaId: '', scope: ['OP_CONSULT'], purpose: ''
  });
  const [hieLoading, setHieLoading] = useState(false);
  const [hieStatus, setHieStatus] = useState(null);
  const [hiePolling, setHiePolling] = useState(false);
  const [hieFhirResult, setHieFhirResult] = useState('');
  const [hieError, setHieError] = useState('');

  // ── Add Patient / ABHA Link ───────────────────────────────────
  const [fhirInput, setFhirInput] = useState('');
  const [fhirLoading, setFhirLoading] = useState(false);
  const [fhirResult, setFhirResult] = useState(null);
  const [fhirError, setFhirError] = useState('');
  const [abhaIdInput, setAbhaIdInput] = useState('');
  const [patientDetails, setPatientDetails] = useState(null);
  const [linkLoading, setLinkLoading] = useState(false);
  const [createPatientResult, setCreatePatientResult] = useState(null);
  const [createPatientError, setCreatePatientError] = useState('');

  // ── Inbound Notifications ─────────────────────────────────────
  const [notifications, setNotifications] = useState([]);
  const [notifLoading, setNotifLoading] = useState(false);
  const [expandedNotif, setExpandedNotif] = useState(null);

  // Poll for inbound notifications
  useEffect(() => {
    if (activePanel !== 'inbound') return;
    const fetchNotifs = async () => {
      try {
        setNotifLoading(true);
        const data = await doctorService.getInboundNotifications();
        setNotifications(data);
      } catch (err) {
        console.error('Failed to fetch notifications', err);
      } finally {
        setNotifLoading(false);
      }
    };
    fetchNotifs();
    const interval = setInterval(fetchNotifs, 10000);
    return () => clearInterval(interval);
  }, [activePanel]);

  // ── Handlers ─────────────────────────────────────────────────
  const validateSubmit = (data) => {
    const result = submitSchema.safeParse(data);
    if (!result.success) {
      const e = {};
      result.error.issues.forEach(issue => {
        e[issue.path[0]] = issue.message;
      });
      return e;
    }
    return {};
  };

  const handleSubmitChange = (e) => {
    const { name, value } = e.target;
    setSubmitForm((f) => ({ ...f, [name]: value }));
    setSubmitErrors((p) => ({ ...p, [name]: '' }));
    if (name === 'patientId') {
      setPatientLookupMessage('');
      setPatientLookupError('');
      if (!value.trim()) {
        setSubmitForm((f) => ({ ...f, abhaId: '' }));
      }
    }
  };

  useEffect(() => {
    if (activePanel !== 'submit') return undefined;

    const identifier = submitForm.patientId.trim();
    if (!shouldLookupPatientIdentifier(identifier)) {
      setPatientLookupLoading(false);
      if (!identifier) {
        setPatientLookupMessage('');
        setPatientLookupError('');
      }
      return undefined;
    }

    let cancelled = false;
    const timeoutId = window.setTimeout(async () => {
      setPatientLookupLoading(true);
      setPatientLookupError('');
      try {
        const patient = await doctorService.lookupPatient(identifier);
        if (cancelled) return;

        const { firstName, lastName } = splitFullName(patient.fullName || '');
        setSubmitForm((current) => {
          if (current.patientId.trim() !== identifier) return current;
          return {
            ...current,
            abhaId: patient.abhaId || (identifier.startsWith('ABHA-') ? identifier : ''),
            patientFirstName: firstName,
            patientLastName: lastName,
          };
        });
        setPatientLookupMessage(
          patient.source === 'LOCAL'
            ? 'Patient details autofilled from the hospital registry.'
            : 'Patient details autofilled from the patient registry.'
        );
      } catch (err) {
        if (cancelled) return;
        setPatientLookupMessage('');
        if (err?.response?.status !== 404) {
          setPatientLookupError(getApiErrorMessage(err, 'Failed to autofill patient details.'));
        }
      } finally {
        if (!cancelled) {
          setPatientLookupLoading(false);
        }
      }
    }, 450);

    return () => {
      cancelled = true;
      window.clearTimeout(timeoutId);
    };
  }, [activePanel, submitForm.patientId]);

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => setSubmitPdf(reader.result.split(',')[1]);
    reader.readAsDataURL(file);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    // Sanitize inputs
    const sanitizedForm = {
      ...submitForm,
      patientId: DOMPurify.sanitize(submitForm.patientId),
      patientFirstName: DOMPurify.sanitize(submitForm.patientFirstName),
      patientLastName: DOMPurify.sanitize(submitForm.patientLastName),
      doctorName: DOMPurify.sanitize(submitForm.doctorName),
      symptoms: DOMPurify.sanitize(submitForm.symptoms),
      bloodPressure: DOMPurify.sanitize(submitForm.bloodPressure),
    };

    const errs = validateSubmit(sanitizedForm);
    if (Object.keys(errs).length) { setSubmitErrors(errs); return; }
    
    setSubmitLoading(true);
    setSubmitError(''); setSubmitResult('');
    try {
      const msg = await doctorService.submitPatientData({
        ...sanitizedForm,
        temperature: parseFloat(sanitizedForm.temperature),
        prescriptionPdfBase64: submitPdf || '',
      });
      // TC-02 Fix: msg might be a JSON object (FHIR bundle) returned as string but parsed by Axios
      const displayMsg = (typeof msg === 'object') ? 'Record submitted and converted to FHIR successfully.' : (msg || 'Record submitted successfully.');
      setSubmitResult(displayMsg);
      setSubmitForm({ patientId: '', abhaId: '', patientFirstName: '', patientLastName: '', doctorName: user?.username || '', visitDate: '', symptoms: '', temperature: '', bloodPressure: '' });
      setSubmitPdf(null);
      setPatientLookupMessage('');
      setPatientLookupError('');
    } catch (err) {
      setSubmitError(err?.response?.data?.message || err.message || 'Submission failed.');
    } finally {
      setSubmitLoading(false);
    }
  };

  const handleConsentSubmit = async (e) => {
    e.preventDefault();
    const sanitizedPatientId = DOMPurify.sanitize(consentForm.patientId);
    const sanitizedPurpose = DOMPurify.sanitize(consentForm.purpose);

    if (!sanitizedPatientId.trim() || !sanitizedPurpose.trim()) {
      setConsentError('Patient ID and Purpose are required.');
      return;
    }
    setConsentLoading(true); setConsentError(''); setConsentResult(null);
    try {
      const result = await doctorService.initiateConsent(
        sanitizedPatientId, sanitizedPurpose, consentForm.requestedDataTypes,
      );
      setConsentResult(result);
      setConsentForm({ patientId: '', purpose: '', requestedDataTypes: ['OP_CONSULT'] });
    } catch (err) {
      setConsentError(err?.response?.data?.message || err.message || 'Failed to initiate consent.');
    } finally {
      setConsentLoading(false);
    }
  };

  const toggleConsentType = (type) =>
    setConsentForm((p) => ({
      ...p,
      requestedDataTypes: p.requestedDataTypes.includes(type)
        ? p.requestedDataTypes.filter((t) => t !== type)
        : [...p.requestedDataTypes, type],
    }));

  const handleFhirReceive = async (e) => {
    e.preventDefault();
    if (!fhirInput.trim()) return;
    setFhirLoading(true);
    setFhirError('');
    setFhirResult(null);
    try {
      const result = await doctorService.receiveFhirAtHospitalA(fhirInput.trim());
      setFhirResult(result);
    } catch (err) {
      setFhirError(getApiErrorMessage(err, 'Failed to receive FHIR bundle.'));
    } finally {
      setFhirLoading(false);
    }
  };

  const handleHieSubmit = async (e) => {
    e.preventDefault();
    const sanitizedAbhaId = DOMPurify.sanitize(hieForm.abhaId);
    const sanitizedPurpose = DOMPurify.sanitize(hieForm.purpose);

    if (!sanitizedAbhaId.trim()) return;
    setHieLoading(true);
    setHieError('');
    setHieStatus(null);
    setHieFhirResult('');
    try {
      const result = await hieService.requestExchange(
        sanitizedAbhaId, hieForm.scope, sanitizedPurpose
      );
      setHieStatus(result);
      if (result.status === 'CONSENT_PENDING') {
        startPolling(result.consentRequestId);
      }
      if (result.status === 'SUCCESS') {
        setHieFhirResult(result.fhirBundle);
      }
    } catch (err) {
      setHieError(err?.response?.data?.message || err.message || 'Exchange failed.');
    } finally {
      setHieLoading(false);
    }
  };

  const handleConsentOnly = async (e) => {
    e.preventDefault();
    const sanitizedAbhaId = DOMPurify.sanitize(hieForm.abhaId);
    const sanitizedPurpose = DOMPurify.sanitize(hieForm.purpose);

    if (!sanitizedAbhaId) return setHieError('ABHA-ID required.');
    setHieLoading(true);
    setHieError(null);
    try {
      const result = await hieService.initiateConsentOnly(sanitizedAbhaId, hieForm.scope, sanitizedPurpose);
      setHieStatus(result);
      if (result.status === 'CONSENT_PENDING') {
        startPolling(result.consentRequestId);
      }
    } catch (err) {
      setHieError(getApiErrorMessage(err, 'Consent request failed.'));
    } finally {
      setHieLoading(false);
    }
  };

  const handlePullOnly = async (e) => {
    e.preventDefault();
    const sanitizedAbhaId = DOMPurify.sanitize(hieForm.abhaId);
    if (!sanitizedAbhaId) return setHieError('ABHA-ID required.');
    setHieLoading(true);
    setHieError(null);
    try {
      const result = await hieService.pullOnly(sanitizedAbhaId, hieForm.scope);
      if (result.status === 'SUCCESS') {
        setHieFhirResult(result.fhirBundle);
      } else {
        setHieError(result.message || 'No active consent found.');
      }
    } catch (err) {
      setHieError(getApiErrorMessage(err, 'Pull failed.'));
    } finally {
      setHieLoading(false);
    }
  };

  const startPolling = (consentId) => {
    setHiePolling(true);
    const interval = setInterval(async () => {
      try {
        const result = await hieService.pollStatus(consentId);
        setHieStatus(result);
        if (result.status === 'SUCCESS') {
          setHieFhirResult(result.fhirBundle);
          setHiePolling(false);
          clearInterval(interval);
        }
        if (result.status === 'DENIED' || result.status === 'REVOKED') {
          setHiePolling(false);
          clearInterval(interval);
        }
      } catch (err) {
        setHiePolling(false);
        clearInterval(interval);
        const msg = err?.response?.data?.message || err?.message || 'Try clicking "Pull Data" manually.';
        setHieError(`Polling stopped: ${msg}`);
        console.error('Poll error:', err?.response?.data || err);
      }
    }, 3000);
  };

  const toggleHieScope = (type) =>
    setHieForm(p => ({
      ...p,
      scope: p.scope.includes(type)
        ? p.scope.filter(t => t !== type)
        : [...p.scope, type],
    }));

  const copyBundle = async (bundle) => {
    if (!bundle) return;
    try {
      const text = typeof bundle === 'string' ? bundle : JSON.stringify(bundle, null, 2);
      await navigator.clipboard.writeText(text);
      setCopyFeedback('Bundle copied');
    } catch {
      setCopyFeedback('Copy failed');
    } finally {
      window.setTimeout(() => setCopyFeedback(''), 1800);
    }
  };

  // ── Notification polling ──────────────────────────────────────────────
  const fetchNotifications = async () => {
    try {
      setNotifLoading(true);
      const data = await doctorService.getInboundNotifications();
      setNotifications(data || []);
    } catch (err) {
      console.error('Failed to fetch notifications:', err);
    } finally {
      setNotifLoading(false);
    }
  };

  useEffect(() => {
    fetchNotifications();
    const intervalId = setInterval(fetchNotifications, 30000);
    return () => clearInterval(intervalId);
  }, []);

  const handleMarkRead = async (id) => {
    try {
      await doctorService.markNotificationRead(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true } : n))
      );
    } catch (err) {
      console.error('Failed to mark notification as read:', err);
    }
  };

  const unreadCount = notifications.filter((n) => !n.read).length;

  const PANELS = [
    { id: 'create_patient', label: 'Add Patient', icon: '🧑‍⚕️', subtitle: 'Register a new patient' },
    { id: 'submit', label: 'Submit Consult', icon: '📝', subtitle: 'Hospital A → FHIR' },
    { id: 'hie', label: 'Request via HIE', icon: '🔗', subtitle: 'Federated exchange' },
    { id: 'receive', label: 'Receive Bundle', icon: '📥', subtitle: 'Hospital A intake' },
    { id: 'inbound', label: 'Inbound Records', icon: '🔔', subtitle: 'Patient-pushed records', badge: unreadCount },
  ];

  return (
    <div className="dashboard-layout">
      <Sidebar items={SIDEBAR_ITEMS} />

      <div className="dashboard-main">
        <header className="dashboard-topbar">
          <div>
            <h1 className="page-title">City General Hospital</h1>
            <p className="page-subtitle">Hospital A · Doctor Dashboard</p>
          </div>
          <div className="topbar-actions">
            <span className="role-badge">👨‍⚕️ {user?.username}</span>
            <button className="btn-outline" onClick={() => { logout(); navigate('/login'); }}>Sign Out</button>
          </div>
        </header>

        <motion.div
          className="page-content"
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.35 }}
        >
          {/* Panel Selector */}
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
            {PANELS.map((p) => (
              <button
                key={p.id}
                onClick={() => setActivePanel(p.id)}
                style={{
                  flex: 1, minWidth: '160px',
                  padding: '14px 18px',
                  borderRadius: 'var(--r-lg)',
                  border: `1.5px solid ${activePanel === p.id ? 'var(--c-primary)' : 'var(--c-border)'}`,
                  background: activePanel === p.id ? 'var(--c-primary-bg)' : 'white',
                  cursor: 'pointer', textAlign: 'left',
                  transition: 'all 0.15s ease',
                  boxShadow: activePanel === p.id ? 'var(--shadow-teal)' : 'var(--shadow-xs)',
                  position: 'relative',
                }}
              >
                {p.badge > 0 && (
                  <span style={{
                    position: 'absolute', top: '8px', right: '10px',
                    background: '#ef4444', color: '#fff',
                    borderRadius: '999px', fontSize: '11px', fontWeight: '700',
                    padding: '1px 7px', lineHeight: '18px',
                    minWidth: '20px', textAlign: 'center',
                  }}>{p.badge}</span>
                )}
                <div style={{ fontSize: '20px', marginBottom: '6px' }}>{p.icon}</div>
                <div style={{
                  fontFamily: "'Syne', sans-serif",
                  fontWeight: '700', fontSize: '13.5px',
                  color: activePanel === p.id ? 'var(--c-primary-dark)' : 'var(--c-text-primary)',
                }}>
                  {p.label}
                </div>
                <div style={{ fontSize: '11.5px', color: 'var(--c-text-muted)', marginTop: '2px' }}>
                  {p.subtitle}
                </div>
              </button>
            ))}
          </div>

          {/* ── Hospital A: Submit OP Consult ──────────────────────────── */}
          <AnimatePresence mode="wait">
            {activePanel === 'submit' && (
              <motion.div
                key="submit"
                className="card"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.2 }}
              >
                <div className="panel-header" style={{ borderBottom: '1px solid var(--c-divider)' }}>
                  <div className="panel-accent-bar panel-accent-bar--teal" />
                  <div className="panel-icon panel-icon--teal">🏥</div>
                  <div>
                    <div className="panel-title">Hospital A — Submit OP Consult</div>
                    <div className="panel-subtitle">Record and convert patient visit to FHIR R4 Bundle</div>
                  </div>
                </div>

                <div className="submit-form">
                  <AnimatePresence>
                    {submitResult && (
                      <motion.div className="alert-success" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                        ✅ {submitResult}
                      </motion.div>
                    )}
                    {submitError && (
                      <motion.div className="alert-error" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                        ⚠️ {submitError}
                      </motion.div>
                    )}
                  </AnimatePresence>

                  <form onSubmit={handleSubmit} noValidate>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '13px' }}>
                      <div className="form-row">
                        <FieldRow label="Patient ID / ABHA-ID" name="patientId" placeholder="e.g. P-1001 or ABHA-1234-5678-9012-34" value={submitForm.patientId} onChange={handleSubmitChange} error={submitErrors.patientId} />
                        <FieldRow label="Visit Date" name="visitDate" type="date" value={submitForm.visitDate} onChange={handleSubmitChange} error={submitErrors.visitDate} />
                      </div>
                      {(patientLookupLoading || patientLookupMessage || patientLookupError) && (
                        <div style={{ fontSize: '12px', marginTop: '-4px', color: patientLookupError ? 'var(--c-error-text)' : 'var(--c-text-muted)' }}>
                          {patientLookupLoading
                            ? 'Looking up patient details...'
                            : patientLookupError || patientLookupMessage}
                        </div>
                      )}
                      <div className="form-row">
                        <FieldRow label="First Name" name="patientFirstName" placeholder="Patient first name" value={submitForm.patientFirstName} onChange={handleSubmitChange} error={submitErrors.patientFirstName} />
                        <FieldRow label="Last Name" name="patientLastName" placeholder="Patient last name" value={submitForm.patientLastName} onChange={handleSubmitChange} error={submitErrors.patientLastName} />
                      </div>
                      <FieldRow label="Doctor Name" name="doctorName" placeholder="Dr. Name" value={submitForm.doctorName} onChange={handleSubmitChange} />

                      <div className="form-group">
                        <label className="form-label">Symptoms / Clinical Notes</label>
                        <textarea
                          name="symptoms"
                          className={`form-textarea ${submitErrors.symptoms ? 'input-error' : ''}`}
                          placeholder="Describe patient symptoms in detail..."
                          value={submitForm.symptoms} onChange={handleSubmitChange} rows={3}
                        />
                        {submitErrors.symptoms && <span className="field-error">{submitErrors.symptoms}</span>}
                      </div>

                      <div className="form-row">
                        <FieldRow label="Temperature (°C)" name="temperature" placeholder="e.g. 37.5" value={submitForm.temperature} onChange={handleSubmitChange} error={submitErrors.temperature} />
                        <FieldRow label="Blood Pressure" name="bloodPressure" placeholder="e.g. 120/80" value={submitForm.bloodPressure} onChange={handleSubmitChange} error={submitErrors.bloodPressure} />
                      </div>

                      <div className="form-group">
                        <label className="form-label">Prescription PDF (Optional)</label>
                        <div className="file-upload-area">
                          <input type="file" id="pdf-upload" accept=".pdf" className="file-input" onChange={handleFileChange} />
                          <label htmlFor="pdf-upload" className="file-label">
                            <span className="file-icon">📎</span>
                            <span>{submitPdf ? '✅ PDF attached — ready to send' : 'Click to upload PDF prescription'}</span>
                          </label>
                        </div>
                      </div>

                      <button type="submit" className="btn-primary btn-full" disabled={submitLoading}>
                        {submitLoading ? <><span className="btn-spinner" /> Saving…</> : 'Save'}
                      </button>
                    </div>
                  </form>
                </div>
              </motion.div>
            )}

            {activePanel === 'receive' && (
              <motion.div
                key="receive"
                className="card"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.2 }}
              >
                <div className="panel-header" style={{ borderBottom: '1px solid var(--c-divider)' }}>
                  <div className="panel-accent-bar panel-accent-bar--teal" />
                  <div className="panel-icon panel-icon--teal">📥</div>
                  <div>
                    <div className="panel-title">Hospital A - Receive FHIR Bundle</div>
                    <div className="panel-subtitle">Parse and store an inbound FHIR bundle from another hospital</div>
                  </div>
                </div>

                <div className="submit-form">
                  {fhirError && <div className="alert-error">⚠️ {fhirError}</div>}
                  <form onSubmit={handleFhirReceive}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '13px' }}>
                      <div className="form-group">
                        <label className="form-label">FHIR JSON Bundle</label>
                        <textarea
                          className="form-textarea"
                          rows={9}
                          placeholder="Paste FHIR JSON bundle here..."
                          value={fhirInput}
                          onChange={(e) => setFhirInput(e.target.value)}
                          style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '12px' }}
                        />
                      </div>
                      <button type="submit" className="btn-primary" disabled={fhirLoading || !fhirInput.trim()}>
                        {fhirLoading ? <><span className="btn-spinner" /> Parsing...</> : '📥 Parse FHIR Bundle'}
                      </button>
                    </div>
                  </form>

                  {fhirResult && (
                    <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} style={{ marginTop: '16px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px', flexWrap: 'wrap' }}>
                        <div className="panel-icon panel-icon--teal" style={{ width: '28px', height: '28px', fontSize: '14px' }}>✅</div>
                        <span style={{ fontFamily: "'Syne', sans-serif", fontWeight: '700', fontSize: '14px', color: 'var(--c-success-text)', flex: 1 }}>Parsed Successfully</span>
                        <button
                          type="button"
                          className="btn-outline"
                          style={{ fontSize: '12px', padding: '5px 14px', display: 'flex', alignItems: 'center', gap: '6px' }}
                          onClick={() => copyBundle(JSON.stringify(fhirResult, null, 2))}
                        >
                          📋 {copyFeedback || 'Copy Parsed JSON'}
                        </button>
                      </div>
                      <div className="detail-grid">
                        {[
                          ['Patient ID', fhirResult.patientId],
                          ['ABHA-ID', fhirResult.abhaId],
                          ['Patient Name', [fhirResult.patientFirstName, fhirResult.patientLastName].filter(Boolean).join(' ')],
                          ['Visit Date', fhirResult.visitDate],
                          ['Doctor', fhirResult.doctorName],
                          ['Clinical Notes', fhirResult.symptoms],
                          ['Blood Pressure', fhirResult.bloodPressure],
                          ['Temperature', fhirResult.temperature],
                          ['Prescription PDF', hasPdfAttachment(fhirResult) ? <a href={`data:application/pdf;base64,${fhirResult.prescriptionPdfBase64}`} target="_blank" rel="noopener noreferrer" style={{ color: 'var(--c-primary)', textDecoration: 'underline' }}>View PDF</a> : 'Not attached'],
                        ].map(([label, val]) => (
                          <div key={label} className="detail-row">
                            <span className="detail-label">{label}</span>
                            <span className="detail-value">{val || 'N/A'}</span>
                          </div>
                        ))}
                      </div>
                    </motion.div>
                  )}
                </div>
              </motion.div>
            )}

            {/* ── Consent Initiation ─────────────────────────────────── */}
            {activePanel === 'consent' && (
              <motion.div
                key="consent"
                className="card"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.2 }}
              >
                <div className="panel-header" style={{ borderBottom: '1px solid var(--c-divider)' }}>
                  <div className="panel-accent-bar panel-accent-bar--violet" />
                  <div className="panel-icon panel-icon--violet">🔒</div>
                  <div>
                    <div className="panel-title">Initiate Consent Request</div>
                    <div className="panel-subtitle">Request patient authorization to access their health records</div>
                  </div>
                </div>

                <div className="submit-form">
                  <AnimatePresence>
                    {consentResult && (
                      <motion.div className="alert-success" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                        ✅ Consent request sent — Status: <strong>{consentResult.status}</strong> · ID: #{consentResult.id}
                      </motion.div>
                    )}
                    {consentError && (
                      <motion.div className="alert-error" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                        ⚠️ {consentError}
                      </motion.div>
                    )}
                  </AnimatePresence>

                  <form onSubmit={handleConsentSubmit}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '13px' }}>
                      <div className="form-group">
                        <label className="form-label">Patient ID</label>
                        <input className="form-input" placeholder="e.g. P-1001"
                          value={consentForm.patientId}
                          onChange={(e) => setConsentForm({ ...consentForm, patientId: e.target.value })}
                        />
                      </div>
                      <div className="form-group">
                        <label className="form-label">Purpose of Request</label>
                        <input className="form-input" placeholder="e.g. Follow-up consultation, Emergency review"
                          value={consentForm.purpose}
                          onChange={(e) => setConsentForm({ ...consentForm, purpose: e.target.value })}
                        />
                      </div>
                      <div className="form-group">
                        <label className="form-label">Requested Data Types</label>
                        <div className="consent-types-row">
                          {DATA_TYPES.map((type) => (
                            <TypeCheckbox
                              key={type} type={type}
                              checked={consentForm.requestedDataTypes.includes(type)}
                              onChange={() => toggleConsentType(type)}
                            />
                          ))}
                        </div>
                      </div>
                      <button type="submit" className="btn-primary" disabled={consentLoading}>
                        {consentLoading ? '⏳ Sending request…' : '📨 Send Consent Request'}
                      </button>
                    </div>
                  </form>
                </div>
              </motion.div>
            )}



            {activePanel === 'hie' && (
              <motion.div
                key="hie"
                className="card"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.2 }}
              >
                <div className="panel-header" style={{ borderBottom: '1px solid var(--c-divider)' }}>
                  <div className="panel-accent-bar panel-accent-bar--teal" />
                  <div className="panel-icon panel-icon--teal">🔗</div>
                  <div>
                    <div className="panel-title">Request Data via HIE Gateway</div>
                    <div className="panel-subtitle">
                      Federated pull — patient consent obtained before data moves
                    </div>
                  </div>
                </div>

                <div className="submit-form">
                  {hieError && <div className="alert-error">⚠️ {hieError}</div>}

                  {hieStatus && hieStatus.status === 'CONSENT_PENDING' && (
                    <div className="alert-info">
                      ⏳ Consent request #{hieStatus.consentRequestId} sent to patient.
                      {hiePolling ? ' Waiting for approval…' : ' Polling stopped.'}
                    </div>
                  )}

                  {hieStatus && hieStatus.status === 'DENIED' && (
                    <div className="alert-error">❌ Patient denied this consent request.</div>
                  )}

                  <form onSubmit={handleHieSubmit}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '13px' }}>
                      <div className="form-group">
                        <label className="form-label">Patient ABHA-ID</label>
                        <input
                          className="form-input"
                          placeholder="e.g. ABHA-1234-5678-9012-34"
                          value={hieForm.abhaId}
                          onChange={e => setHieForm({ ...hieForm, abhaId: e.target.value })}
                        />
                      </div>
                      <div className="form-group">
                        <label className="form-label">Purpose</label>
                        <input
                          className="form-input"
                          placeholder="e.g. Follow-up consultation across hospitals"
                          value={hieForm.purpose}
                          onChange={e => setHieForm({ ...hieForm, purpose: e.target.value })}
                        />
                      </div>
                      <div className="form-group">
                        <label className="form-label">Data scope requested</label>
                        <div className="consent-types-row">
                          {['OP_CONSULT', 'PRESCRIPTION', 'LAB_RESULT'].map(type => (
                            <label key={type} className="consent-type-check">
                              <input
                                type="checkbox"
                                checked={hieForm.scope.includes(type)}
                                onChange={() => toggleHieScope(type)}
                              />
                              {type.replace(/_/g, ' ')}
                            </label>
                          ))}
                        </div>
                      </div>
                      <div className="form-group" style={{ display: 'flex', gap: '12px', marginTop: '8px' }}>
                        <button
                          type="button"
                          className="btn-primary"
                          style={{ 
                            flex: 1, 
                            background: '#6366f1', 
                            borderColor: '#6366f1',
                            color: '#fff',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: '8px'
                          }}
                          onClick={handleConsentOnly}
                          disabled={hieLoading || hiePolling}
                        >
                          {hieLoading ? <span className="btn-spinner" /> : <><span style={{fontSize: '18px'}}>🔒</span> 1. Request Consent</>}
                        </button>

                        <button
                          type="button"
                          className="btn-primary"
                          style={{ 
                            flex: 1,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: '8px'
                          }}
                          onClick={handlePullOnly}
                          disabled={hieLoading || hiePolling}
                        >
                          {hieLoading ? <span className="btn-spinner" /> : <><span style={{fontSize: '18px'}}>📥</span> 2. Pull Data</>}
                        </button>
                      </div>
                      
                      <div style={{ textAlign: 'center', opacity: 0.4, fontSize: '11px', margin: '8px 0', letterSpacing: '1px' }}>— OR —</div>

                      <button
                        type="submit"
                        className="btn-primary"
                        style={{ 
                          width: '100%', 
                          background: 'transparent', 
                          border: '1px dashed var(--c-primary)', 
                          color: 'var(--c-primary)',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          gap: '8px'
                        }}
                        disabled={hieLoading || hiePolling}
                      >
                        {hieLoading ? <span className="btn-spinner" /> : <><span style={{fontSize: '18px'}}>🔗</span> Auto Orchestrate (1 + 2)</>}
                      </button>
                    </div>
                  </form>

                  {hieFhirResult && (
                    <motion.div
                      initial={{ opacity: 0, y: 8 }}
                      animate={{ opacity: 1, y: 0 }}
                      style={{ marginTop: '16px' }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '10px' }}>
                        <span style={{
                          fontFamily: "'Syne', sans-serif", fontWeight: '700',
                          fontSize: '14px', color: 'var(--c-success-text)'
                        }}>
                          ✅ Data received from Hospital B
                        </span>
                      </div>
                      <div className="fhir-json-section">
                        <span className="fhir-json-label">FHIR Bundle</span>
                        <pre className="fhir-json">
                          {(() => {
                            try {
                              const parsed = typeof hieFhirResult === 'string' ? JSON.parse(hieFhirResult) : hieFhirResult;
                              return JSON.stringify(parsed, null, 2);
                            } catch (e) {
                              return hieFhirResult;
                            }
                          })()}
                        </pre>
                      </div>
                    </motion.div>
                  )}
                </div>
              </motion.div>
            )}

            {/* ── Create Patient Panel ─────────────────────────────── */}
            {activePanel === 'create_patient' && (
              <motion.div
                key="create_patient"
                className="card"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.2 }}
              >
                <div className="panel-header" style={{ borderBottom: '1px solid var(--c-divider)' }}>
                  <div className="panel-accent-bar" style={{ background: 'var(--c-primary)' }} />
                  <div className="panel-icon" style={{ color: 'var(--c-primary)' }}>🧑‍⚕️</div>
                  <div>
                    <div className="panel-title">Register New Patient</div>
                    <div className="panel-subtitle">Create a patient record and generate login credentials</div>
                  </div>
                </div>

                <div className="submit-form">
                  <AnimatePresence>
                    {createPatientResult && (
                      <motion.div className="alert-success" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                        <div style={{ marginBottom: '8px' }}>✅ <strong>{createPatientResult.message}</strong></div>
                        <div className="detail-grid">
                          <div className="detail-row"><span className="detail-label">Patient ID:</span> <span className="detail-value">{createPatientResult.localPatientId || createPatientResult.patientId || createPatientResult.abhaId}</span></div>
                          <div className="detail-row"><span className="detail-label">Patient Name:</span> <span className="detail-value">{createPatientResult.fullName || 'Not available'}</span></div>
                        </div>
                      </motion.div>
                    )}
                    {createPatientError && (
                      <motion.div className="alert-error" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                        ⚠️ {createPatientError}
                      </motion.div>
                    )}
                  </AnimatePresence>

                  <form onSubmit={async (e) => {
                    e.preventDefault();
                    if (!abhaIdInput) return;
                    setLinkLoading(true); setCreatePatientError(''); setCreatePatientResult(null); setPatientDetails(null);
                    try {
                      const res = await doctorService.getPatientByAbhaId(abhaIdInput);
                      setPatientDetails(res);
                    } catch (err) {
                      setCreatePatientError(err?.response?.data?.message || err.message || 'Failed to fetch patient details.');
                    } finally {
                      setLinkLoading(false);
                    }
                  }}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '13px' }}>
                      <div className="form-group">
                        <label className="form-label">Patient ABHA-ID</label>
                        <input
                          className="form-input"
                          placeholder="e.g. ABHA-1234-5678-9012-34"
                          value={abhaIdInput}
                          onChange={(e) => setAbhaIdInput(e.target.value)}
                        />
                      </div>
                      <button type="submit" className="btn-primary" disabled={linkLoading || !abhaIdInput}>
                        {linkLoading ? '⏳ Fetching…' : '🔍 Fetch Patient Details'}
                      </button>
                    </div>
                  </form>

                  <AnimatePresence>
                    {patientDetails && (
                      <motion.div
                        initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
                        style={{ marginTop: '16px', background: 'var(--c-bg-alt)', padding: '16px', borderRadius: 'var(--r-md)' }}
                      >
                        <h4 style={{ margin: '0 0 12px 0', fontSize: '14px' }}>Patient Details Found:</h4>
                        <PatientDetailsGrid details={patientDetails} />

                        <button
                          className="btn-primary"
                          style={{ marginTop: '16px', width: '100%' }}
                          onClick={async () => {
                            setLinkLoading(true); setCreatePatientError(''); setCreatePatientResult(null);
                            try {
                              const res = await doctorService.linkPatientByAbhaId(abhaIdInput);
                              setCreatePatientResult(res);
                              setPatientDetails(null);
                              setAbhaIdInput('');
                            } catch (err) {
                              setCreatePatientError(err?.response?.data?.message || err.message || 'Failed to link patient.');
                            } finally {
                              setLinkLoading(false);
                            }
                          }}
                          disabled={linkLoading}
                        >
                          {linkLoading ? '⏳ Linking...' : '✅ Register Patient at City General Hospital'}
                        </button>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              </motion.div>
            )}

            {/* ── Inbound Records Panel ───────────────────────────── */}
            {activePanel === 'inbound' && (
              <motion.div
                key="inbound"
                className="card"
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -8 }}
                transition={{ duration: 0.2 }}
              >
                <div className="panel-header" style={{ borderBottom: '1px solid var(--c-divider)' }}>
                  <div className="panel-accent-bar" style={{ background: unreadCount > 0 ? '#ef4444' : 'var(--c-primary)' }} />
                  <div className="panel-icon" style={{ color: unreadCount > 0 ? '#ef4444' : 'var(--c-primary)', fontSize: '22px' }}>🔔</div>
                  <div style={{ flex: 1 }}>
                    <div className="panel-title">Inbound Records</div>
                    <div className="panel-subtitle">Patient-pushed FHIR records — {unreadCount > 0 ? `${unreadCount} unread` : 'all read'}</div>
                  </div>
                  <button
                    className="btn-outline"
                    style={{ fontSize: '12px', padding: '6px 14px' }}
                    onClick={fetchNotifications}
                    disabled={notifLoading}
                  >
                    {notifLoading ? '⏳' : '↻ Refresh'}
                  </button>
                </div>

                <div className="submit-form">
                  {notifications.length === 0 && !notifLoading && (
                    <div style={{
                      textAlign: 'center', padding: '40px 20px',
                      color: 'var(--c-text-muted)', fontSize: '14px',
                    }}>
                      <div style={{ fontSize: '40px', marginBottom: '12px' }}>📭</div>
                      No inbound records yet. When a patient pushes their records to you, they'll appear here.
                    </div>
                  )}

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                    {notifications.map((notif) => (
                      <motion.div
                        key={notif.id}
                        initial={{ opacity: 0, x: -10 }}
                        animate={{ opacity: 1, x: 0 }}
                        style={{
                          borderRadius: 'var(--r-md)',
                          border: `1px solid ${notif.read ? 'var(--c-border)' : 'var(--c-primary)'}`,
                          background: notif.read ? 'var(--c-bg-alt)' : 'var(--c-primary-bg)',
                          padding: '16px',
                          borderLeft: `4px solid ${notif.read ? 'var(--c-border)' : '#3b82f6'}`,
                          position: 'relative',
                        }}
                      >
                        {/* Header row */}
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '8px', flexWrap: 'wrap' }}>
                          <div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                              {!notif.read && (
                                <span style={{
                                  background: '#3b82f6', color: '#fff',
                                  borderRadius: '999px', fontSize: '10px', fontWeight: '700',
                                  padding: '2px 8px',
                                }}>NEW</span>
                              )}
                              <span style={{ fontWeight: '700', fontSize: '14px', color: 'var(--c-text-primary)' }}>
                                {notif.patientName || 'Unknown Patient'}
                              </span>
                            </div>
                            <div style={{ fontSize: '12px', color: 'var(--c-text-muted)', marginTop: '3px' }}>
                              ABHA-ID: {notif.patientAbhaId}
                            </div>
                          </div>
                          <div style={{ textAlign: 'right', fontSize: '11px', color: 'var(--c-text-muted)' }}>
                            {new Date(notif.pushedAt).toLocaleString()}
                          </div>
                        </div>

                        {/* Data types */}
                        <div style={{ marginTop: '10px', display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                          {(notif.dataTypes || '').split(',').filter(Boolean).map((dt) => (
                            <span key={dt} style={{
                              background: 'var(--c-primary-bg)',
                              color: 'var(--c-primary-dark)',
                              border: '1px solid var(--c-primary)',
                              borderRadius: '999px',
                              fontSize: '11px', fontWeight: '600',
                              padding: '2px 10px',
                            }}>
                              {dt.replace(/_/g, ' ')}
                            </span>
                          ))}
                        </div>

                        {/* Action buttons */}
                        <div style={{ marginTop: '12px', display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                          <button
                            className="btn-outline"
                            style={{ fontSize: '12px', padding: '6px 14px' }}
                            onClick={() => setExpandedNotif(expandedNotif === notif.id ? null : notif.id)}
                          >
                            {expandedNotif === notif.id ? '▲ Hide FHIR Bundle' : '▼ View FHIR Bundle'}
                          </button>
                          {!notif.read && (
                            <button
                              className="btn-primary"
                              style={{ fontSize: '12px', padding: '6px 14px', background: 'var(--c-success, #10b981)', borderColor: 'var(--c-success, #10b981)' }}
                              onClick={() => handleMarkRead(notif.id)}
                            >
                              ✓ Mark as Read
                            </button>
                          )}
                        </div>

                        {/* FHIR bundle expand */}
                        <AnimatePresence>
                          {expandedNotif === notif.id && (
                            <motion.div
                              initial={{ opacity: 0, height: 0 }}
                              animate={{ opacity: 1, height: 'auto' }}
                              exit={{ opacity: 0, height: 0 }}
                              style={{ overflow: 'hidden', marginTop: '12px' }}
                            >
                              <div className="fhir-json-section">
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                                  <span className="fhir-json-label" style={{ margin: 0 }}>FHIR Bundle</span>
                                  <button
                                    type="button"
                                    className="btn-outline"
                                    style={{ fontSize: '11px', padding: '4px 10px', background: 'transparent', borderColor: 'rgba(255,255,255,0.2)', color: 'rgba(255,255,255,0.7)' }}
                                    onClick={() => {
                                      try {
                                        copyBundle(JSON.stringify(JSON.parse(notif.fhirBundleJson), null, 2));
                                      } catch {
                                        copyBundle(notif.fhirBundleJson);
                                      }
                                    }}
                                  >
                                    📋 {copyFeedback || 'Copy JSON'}
                                  </button>
                                </div>
                                <pre className="fhir-json" style={{ maxHeight: '300px', overflowY: 'auto', marginTop: 0 }}>
                                  {(() => {
                                    try {
                                      return JSON.stringify(JSON.parse(notif.fhirBundleJson), null, 2);
                                    } catch { return notif.fhirBundleJson; }
                                  })()}
                                </pre>
                              </div>
                            </motion.div>
                          )}
                        </AnimatePresence>
                      </motion.div>
                    ))}
                  </div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>
      </div>
    </div>
  );
};

export default HospitalADashboard;
