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
  patientName: z.string().min(1, 'Required'),
  consultDate: z.string().min(1, 'Required'),
  clinicalNotes: z.string().min(1, 'Required'),
  temperature: z.string().refine(val => !isNaN(parseFloat(val)), 'Required and must be a number'),
  bloodPressure: z.string().min(1, 'Required'),
});

const SIDEBAR_ITEMS = [
  { to: '/doctor/dashboard', label: 'Dashboard', icon: '📊', end: true },
];

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

const getHospitalBDisplayId = (record) => {
  return record?.uhid || record?.patientId || record?.abhaId || 'N/A';
};

const hasPdfAttachment = (record) => !!record?.prescriptionPdfBase64;

const shouldLookupPatientIdentifier = (identifier) => {
  const trimmed = identifier.trim();
  if (!trimmed) return false;
  if (trimmed.startsWith('ABHA-')) return trimmed.length >= 12;
  return trimmed.length >= 6;
};

const DoctorDashboard = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [activePanel, setActivePanel] = useState('submit');

  useEffect(() => {
    setHieFhirResult('');
    setHieStatus(null);
    setFhirResult(null);
    setCreatePatientResult(null);
    setSubmitResult('');
    setSubmitError('');
    setCopyFeedback('');
  }, [activePanel]);

  const [submitForm, setSubmitForm] = useState({
    patientId: '',
    abhaId: '',
    patientName: '',
    consultDate: '',
    doctor: user?.username || '',
    clinicalNotes: '',
    temperature: '',
    bloodPressure: '',
  });
  const [submitPdf, setSubmitPdf] = useState(null);
  const [submitErrors, setSubmitErrors] = useState({});
  const [submitLoading, setSubmitLoading] = useState(false);
  const [submitResult, setSubmitResult] = useState('');
  const [submitError, setSubmitError] = useState('');
  const [patientLookupLoading, setPatientLookupLoading] = useState(false);
  const [patientLookupMessage, setPatientLookupMessage] = useState('');
  const [patientLookupError, setPatientLookupError] = useState('');

  const [fhirInput, setFhirInput] = useState('');
  const [fhirLoading, setFhirLoading] = useState(false);
  const [fhirResult, setFhirResult] = useState(null);
  const [fhirError, setFhirError] = useState('');
  const [intakeRecords, setIntakeRecords] = useState([]);
  const [intakeLoading, setIntakeLoading] = useState(false);

  const fetchIntake = async () => {
    setIntakeLoading(true);
    try {
      const data = await doctorService.getHospitalBConsults();
      setIntakeRecords(data);
    } catch (e) {
      console.error('Failed to fetch intake:', e);
    } finally {
      setIntakeLoading(false);
    }
  };

  useEffect(() => {
    if (activePanel === 'receive') {
      fetchIntake();
    }
  }, [activePanel]);

  const [abhaIdInput, setAbhaIdInput] = useState('');
  const [patientDetails, setPatientDetails] = useState(null);
  const [linkLoading, setLinkLoading] = useState(false);
  const [createPatientResult, setCreatePatientResult] = useState(null);
  const [createPatientError, setCreatePatientError] = useState('');

  const [hieForm, setHieForm] = useState({
    abhaId: '', scope: ['OP_CONSULT'], purpose: ''
  });
  const [hieLoading, setHieLoading] = useState(false);
  const [hieStatus, setHieStatus] = useState(null);
  const [hieFhirResult, setHieFhirResult] = useState('');
  const [hieError, setHieError] = useState('');
  const [copyFeedback, setCopyFeedback] = useState('');

  // ── Inbound Notifications ────────────────────────────────────────────
  const [notifications, setNotifications] = useState([]);
  const [notifLoading, setNotifLoading] = useState(false);
  const [expandedNotif, setExpandedNotif] = useState(null);

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
    setSubmitForm((prev) => ({ ...prev, [name]: value }));
    setSubmitErrors((prev) => ({ ...prev, [name]: '' }));
    if (name === 'patientId') {
      setPatientLookupMessage('');
      setPatientLookupError('');
      if (!value.trim()) {
        setSubmitForm((prev) => ({ ...prev, abhaId: '' }));
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

        setSubmitForm((current) => {
          if (current.patientId.trim() !== identifier) return current;
          return {
            ...current,
            abhaId: patient.abhaId || (identifier.startsWith('ABHA-') ? identifier : ''),
            patientName: patient.fullName || '',
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

  const handleNativeSubmit = async (e) => {
    e.preventDefault();
    
    // Sanitize inputs
    const sanitizedForm = {
      ...submitForm,
      patientId: DOMPurify.sanitize(submitForm.patientId),
      patientName: DOMPurify.sanitize(submitForm.patientName),
      doctor: DOMPurify.sanitize(submitForm.doctor),
      clinicalNotes: DOMPurify.sanitize(submitForm.clinicalNotes),
      bloodPressure: DOMPurify.sanitize(submitForm.bloodPressure),
    };

    const errors = validateSubmit(sanitizedForm);
    if (Object.keys(errors).length) {
      setSubmitErrors(errors);
      return;
    }
    setSubmitLoading(true);
    setSubmitError('');
    setSubmitResult('');
    try {
      const message = await doctorService.submitHospitalBConsult({
        ...sanitizedForm,
        prescriptionPdfBase64: submitPdf || '',
      });
      setSubmitResult(message || 'Consult stored successfully.');
      setSubmitForm({
        patientId: '',
        abhaId: '',
        patientName: '',
        consultDate: '',
        doctor: user?.username || '',
        clinicalNotes: '',
        temperature: '',
        bloodPressure: '',
      });
      setSubmitPdf(null);
      setPatientLookupMessage('');
      setPatientLookupError('');
      fetchIntake();
    } catch (err) {
      setSubmitError(err?.response?.data?.message || err.message || 'Failed to store consult.');
    } finally {
      setSubmitLoading(false);
    }
  };

  const handleFhirReceive = async (e) => {
    e.preventDefault();
    if (!fhirInput.trim()) return;
    setFhirLoading(true); setFhirError('');
    try {
      const result = await doctorService.receiveFhirAtHospitalB(fhirInput.trim());
      setFhirResult(result);
      fetchIntake();
    } catch (err) {
      setFhirError(err?.response?.data?.message || err.message || 'Failed to receive FHIR bundle.');
    } finally {
      setFhirLoading(false);
    }
  };

  const handleConsentOnly = async (e) => {
    e.preventDefault();
    const sanitizedAbhaId = DOMPurify.sanitize(hieForm.abhaId);
    const sanitizedPurpose = DOMPurify.sanitize(hieForm.purpose);

    if (!sanitizedAbhaId) return setHieError('ABHA-ID required.');
    setHieLoading(true); setHieError(null); setHieFhirResult(''); setCopyFeedback('');
    try {
      const result = await hieService.initiateConsentOnly(sanitizedAbhaId, hieForm.scope, sanitizedPurpose);
      setHieStatus(result);
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
    setHieLoading(true); setHieError(null); setHieFhirResult(''); setCopyFeedback('');
    try {
      const result = await hieService.pullOnly(sanitizedAbhaId, hieForm.scope);
      if (result.status === 'SUCCESS') {
        setHieFhirResult(result.fhirBundle);
        setHieStatus(result);
      } else {
        setHieError(result.message || 'No active consent found.');
      }
    } catch (err) {
      setHieError(getApiErrorMessage(err, 'Pull failed.'));
    } finally {
      setHieLoading(false);
    }
  };

  const toggleHieScope = (type) =>
    setHieForm(p => ({
      ...p,
      scope: p.scope.includes(type) ? p.scope.filter(t => t !== type) : [...p.scope, type],
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

  const PANELS = [
    { id: 'create_patient', label: 'Add Patient', icon: '🧑‍⚕️', subtitle: 'Register a new patient' },
    { id: 'submit', label: 'Submit Consult', icon: '📝', subtitle: 'Hospital B native record' },
    { id: 'hie', label: 'Request via HIE', icon: '🔗', subtitle: 'Federated exchange' },
    { id: 'receive', label: 'Receive Bundle', icon: '📥', subtitle: 'Hospital B intake' },
    { id: 'inbound', label: 'Inbound Records', icon: '🔔', subtitle: 'Patient-pushed records', badge: unreadCount },
  ];

  return (
    <div className="dashboard-layout">
      <Sidebar items={SIDEBAR_ITEMS} />
      <div className="dashboard-main">
        <header className="dashboard-topbar">
          <div>
            <h1 className="page-title">Metro Medical Center</h1>
            <p className="page-subtitle">Hospital B · Doctor Dashboard</p>
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
                  boxShadow: activePanel === p.id ? 'var(--shadow-purple)' : 'var(--shadow-xs)',
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
                <div style={{ fontFamily: "'Syne', sans-serif", fontWeight: '700', fontSize: '13.5px', color: activePanel === p.id ? 'var(--c-primary-dark)' : 'var(--c-text-primary)' }}>{p.label}</div>
                <div style={{ fontSize: '11.5px', color: 'var(--c-text-muted)', marginTop: '2px' }}>{p.subtitle}</div>
              </button>
            ))}
          </div>

          <AnimatePresence mode="wait">
            {activePanel === 'submit' && (
              <motion.div key="submit" className="card" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: 0.2 }}>
                <div className="panel-header" style={{ borderBottom: '1px solid var(--c-divider)' }}>
                  <div className="panel-accent-bar panel-accent-bar--green" />
                  <div className="panel-icon panel-icon--green">📝</div>
                  <div>
                    <div className="panel-title">Hospital B — Submit OP Consult</div>
                    <div className="panel-subtitle">Create a local consult record that HIE can serve by ABHA-ID</div>
                  </div>
                </div>
                <div className="submit-form">
                  {submitResult && <div className="alert-success">✅ {submitResult}</div>}
                  {submitError && <div className="alert-error">⚠️ {submitError}</div>}
                  <form onSubmit={handleNativeSubmit}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '13px' }}>
                      <div className="form-row">
                        <FieldRow label="Patient ID / ABHA-ID" name="patientId" value={submitForm.patientId} onChange={handleSubmitChange} placeholder="e.g. HB-P-1234 or ABHA-1234-5678-9012-34" error={submitErrors.patientId} />
                        <FieldRow label="Consult Date" name="consultDate" type="date" value={submitForm.consultDate} onChange={handleSubmitChange} error={submitErrors.consultDate} />
                      </div>
                      {(patientLookupLoading || patientLookupMessage || patientLookupError) && (
                        <div style={{ fontSize: '12px', marginTop: '-4px', color: patientLookupError ? 'var(--c-error-text)' : 'var(--c-text-muted)' }}>
                          {patientLookupLoading
                            ? 'Looking up patient details...'
                            : patientLookupError || patientLookupMessage}
                        </div>
                      )}
                      <div className="form-row">
                        <FieldRow label="Patient Name" name="patientName" value={submitForm.patientName} onChange={handleSubmitChange} placeholder="Full patient name" error={submitErrors.patientName} />
                        <FieldRow label="Doctor" name="doctor" value={submitForm.doctor} onChange={handleSubmitChange} placeholder="Doctor name" />
                      </div>
                      <div className="form-group">
                        <label className="form-label">Clinical Notes</label>
                        <textarea name="clinicalNotes" className={`form-textarea ${submitErrors.clinicalNotes ? 'input-error' : ''}`} rows={4} value={submitForm.clinicalNotes} onChange={handleSubmitChange} placeholder="Consult summary, diagnosis, and treatment notes" />
                        {submitErrors.clinicalNotes && <span className="field-error">{submitErrors.clinicalNotes}</span>}
                      </div>
                      <div className="form-row">
                        <FieldRow label="Temperature" name="temperature" value={submitForm.temperature} onChange={handleSubmitChange} placeholder="e.g. 98.6" error={submitErrors.temperature} />
                        <FieldRow label="Blood Pressure" name="bloodPressure" value={submitForm.bloodPressure} onChange={handleSubmitChange} placeholder="e.g. 120/80" error={submitErrors.bloodPressure} />
                      </div>
                      <div className="form-group">
                        <label className="form-label">Prescription PDF (Optional)</label>
                        <div className="file-upload-area">
                          <input type="file" id="hospital-b-pdf-upload" accept=".pdf" className="file-input" onChange={handleFileChange} />
                          <label htmlFor="hospital-b-pdf-upload" className="file-label">
                            <span className="file-icon">📎</span>
                            <span>{submitPdf ? '✅ PDF attached — ready to store' : 'Click to upload PDF prescription'}</span>
                          </label>
                        </div>
                      </div>
                      <button type="submit" className="btn-primary" disabled={submitLoading}>
                        {submitLoading ? <><span className="btn-spinner" /> Saving…</> : 'Save'}
                      </button>
                    </div>
                  </form>
                </div>
              </motion.div>
            )}

            {activePanel === 'receive' && (
              <motion.div key="receive" className="card" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: 0.2 }}>
                <div className="panel-header" style={{ borderBottom: '1px solid var(--c-divider)' }}>
                  <div className="panel-accent-bar panel-accent-bar--green" />
                  <div className="panel-icon panel-icon--green">🏨</div>
                  <div>
                    <div className="panel-title">Hospital B — Receive FHIR Bundle</div>
                    <div className="panel-subtitle">Parse and extract data from an inbound FHIR JSON bundle</div>
                  </div>
                </div>
                <div className="submit-form">
                  {fhirError && <div className="alert-error">⚠️ {fhirError}</div>}
                  <form onSubmit={handleFhirReceive}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '13px' }}>
                      <div className="form-group">
                        <label className="form-label">FHIR JSON Bundle</label>
                        <textarea className="form-textarea" rows={9} placeholder='Paste FHIR JSON bundle here…' value={fhirInput} onChange={(e) => setFhirInput(e.target.value)} style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '12px' }} />
                      </div>
                      <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                        <button type="submit" className="btn-primary" disabled={fhirLoading || !fhirInput.trim()}>
                          {fhirLoading ? <><span className="btn-spinner" /> Parsing…</> : '📥 Parse FHIR Bundle'}
                        </button>
                        <button type="button" className="btn-outline" onClick={() => copyBundle(fhirInput)} disabled={!fhirInput.trim()}>
                          Copy Bundle
                        </button>
                        {copyFeedback && <span style={{ fontSize: '12px', color: 'var(--c-text-muted)', alignSelf: 'center' }}>{copyFeedback}</span>}
                      </div>
                    </div>
                  </form>
                  <AnimatePresence>
                    {fhirResult && (
                      <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} style={{ marginTop: '16px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px', flexWrap: 'wrap' }}>
                          <div className="panel-icon panel-icon--green" style={{ width: '28px', height: '28px', fontSize: '14px' }}>✅</div>
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
                          {[['UHID', getHospitalBDisplayId(fhirResult)], ['Patient Name', fhirResult.patientName], ['Consult Date', fhirResult.consultDate], ['Doctor', fhirResult.doctor], ['Clinical Notes', fhirResult.clinicalNotes], ['Blood Pressure', fhirResult.vitals?.bp], ['Temperature', fhirResult.vitals?.temp], ['Prescription PDF', hasPdfAttachment(fhirResult) ? <a href={`data:application/pdf;base64,${fhirResult.prescriptionPdfBase64}`} target="_blank" rel="noopener noreferrer" style={{ color: 'var(--c-primary)', textDecoration: 'underline' }}>View PDF</a> : 'Not attached']].map(([label, val]) => (
                            <div key={label} className="detail-row">
                              <span className="detail-label">{label}</span>
                              <span className="detail-value">{val || 'N/A'}</span>
                            </div>
                          ))}
                        </div>
                      </motion.div>
                    )}
                  </AnimatePresence>
                  <div style={{ marginTop: '24px', borderTop: '1px solid var(--c-divider)', paddingTop: '20px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
                      <h3 style={{ fontFamily: "'Syne', sans-serif", fontSize: '15px', fontWeight: '700' }}>📥 Inbound Records</h3>
                      <button className="btn-outline" style={{ padding: '4px 10px', fontSize: '11px' }} onClick={fetchIntake} disabled={intakeLoading}>Refresh</button>
                    </div>
                    {intakeRecords.length === 0 ? <div className="alert-info">No records yet.</div> : (
                      <div className="detail-grid">
                        {intakeRecords.map(rec => (
                          <div key={rec.id} style={{ padding: '10px', borderBottom: '1px solid var(--c-divider)', display: 'flex', justifyContent: 'space-between' }}>
                            <div>
                              <div style={{ fontWeight: '600', fontSize: '13px' }}>{rec.patientName}</div>
                              <div style={{ fontSize: '11px' }}>{getHospitalBDisplayId(rec)} · {rec.consultDate}</div>
                            </div>
                            <StatusBadge status={rec.consentVerified ? 'GRANTED' : 'PENDING'} />
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
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
                      Federated pull from Hospital A after patient consent
                    </div>
                  </div>
                </div>
                <div className="submit-form">
                  {hieError && <div className="alert-error">⚠️ {hieError}</div>}

                  {hieStatus && hieStatus.status === 'CONSENT_PENDING' && (
                    <div className="alert-info">
                      ⏳ Consent request #{hieStatus.consentRequestId} sent. Ask the patient to approve, then click `Pull Data`.
                    </div>
                  )}

                  {hieStatus && hieStatus.status === 'DENIED' && (
                    <div className="alert-error">❌ Patient denied this consent request.</div>
                  )}

                  <form onSubmit={(e) => e.preventDefault()}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '13px' }}>
                      <div className="form-group">
                        <label className="form-label">Patient ABHA-ID</label>
                        <input className="form-input" placeholder="e.g. ABHA-1234-5678-9012-34" value={hieForm.abhaId} onChange={e => setHieForm({ ...hieForm, abhaId: e.target.value })} />
                      </div>
                      <div className="form-group">
                        <label className="form-label">Purpose</label>
                        <input className="form-input" placeholder="e.g. Second opinion or continuity of care" value={hieForm.purpose} onChange={e => setHieForm({ ...hieForm, purpose: e.target.value })} />
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
                      <div
                        className="form-group"
                        style={{
                          display: 'grid',
                          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                          gap: '12px',
                          marginTop: '8px',
                          alignItems: 'stretch',
                        }}
                      >
                        <button
                          type="button"
                          className="btn-primary"
                          style={{
                            background: '#6366f1',
                            borderColor: '#6366f1',
                            color: '#fff',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: '8px',
                            minHeight: '52px',
                            width: '100%',
                          }}
                          onClick={handleConsentOnly}
                          disabled={hieLoading}
                        >
                          {hieLoading ? <span className="btn-spinner" /> : <><span style={{ fontSize: '18px' }}>🔒</span> 1. Request Consent</>}
                        </button>

                        <button
                          type="button"
                          className="btn-primary"
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: '8px',
                            minHeight: '52px',
                            width: '100%',
                          }}
                          onClick={handlePullOnly}
                          disabled={hieLoading}
                        >
                          {hieLoading ? <span className="btn-spinner" /> : <><span style={{ fontSize: '18px' }}>📥</span> 2. Pull Data</>}
                        </button>
                      </div>

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
                          ✅ Data received from Hospital A
                        </span>
                        <button type="button" className="btn-outline" onClick={() => copyBundle(hieFhirResult)}>
                          Copy Bundle
                        </button>
                        {copyFeedback && <span style={{ fontSize: '12px', color: 'var(--c-text-muted)' }}>{copyFeedback}</span>}
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

            {activePanel === 'create_patient' && (
              <motion.div key="create_patient" className="card" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: 0.2 }}>
                <div className="panel-header">
                  <div className="panel-title">Add Patient</div>
                </div>
                <div className="submit-form">
                  {createPatientError && <div className="alert-error">{createPatientError}</div>}
                  {createPatientResult && (
                    <div className="alert-success">
                      <div style={{ marginBottom: '8px' }}>✅ <strong>{createPatientResult.message}</strong></div>
                      <div className="detail-grid">
                        <div className="detail-row"><span className="detail-label">Patient ID:</span> <span className="detail-value">{createPatientResult.localPatientId || createPatientResult.patientId || createPatientResult.abhaId}</span></div>
                        <div className="detail-row"><span className="detail-label">Patient Name:</span> <span className="detail-value">{createPatientResult.fullName || 'Not available'}</span></div>
                      </div>
                    </div>
                  )}
                  <form onSubmit={async (e) => {
                    e.preventDefault();
                    setLinkLoading(true);
                    setCreatePatientError('');
                    setCreatePatientResult(null);
                    setPatientDetails(null);
                    try { const res = await doctorService.getPatientByAbhaId(abhaIdInput); setPatientDetails(res); setCreatePatientError(''); }
                    catch (err) { setCreatePatientError(err.message); }
                    finally { setLinkLoading(false); }
                  }}>
                    <input className="form-input" value={abhaIdInput} onChange={e => setAbhaIdInput(e.target.value)} placeholder="ABHA-ID" />
                    <button type="submit" className="btn-primary" disabled={linkLoading}>Fetch</button>
                  </form>
                  {patientDetails && (
                    <div style={{ marginTop: '16px' }}>
                      <h4 style={{ margin: '0 0 12px 0', fontSize: '14px' }}>Patient Details Found:</h4>
                      <PatientDetailsGrid details={patientDetails} />
                      <button className="btn-primary" onClick={async () => {
                        setLinkLoading(true);
                        setCreatePatientError('');
                        setCreatePatientResult(null);
                        try { const res = await doctorService.linkPatientByAbhaId(abhaIdInput); setCreatePatientResult(res); setPatientDetails(null); setCreatePatientError(''); }
                        catch (err) { setCreatePatientError(err.message); }
                        finally { setLinkLoading(false); }
                      }}>Link Patient</button>
                    </div>
                  )}
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
                        }}
                      >
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
                              style={{ fontSize: '12px', padding: '6px 14px', background: '#10b981', borderColor: '#10b981' }}
                              onClick={() => handleMarkRead(notif.id)}
                            >
                              ✓ Mark as Read
                            </button>
                          )}
                        </div>

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
                                    try { return JSON.stringify(JSON.parse(notif.fhirBundleJson), null, 2); }
                                    catch { return notif.fhirBundleJson; }
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

export default DoctorDashboard;
