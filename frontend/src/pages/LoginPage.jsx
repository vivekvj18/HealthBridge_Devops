import { useState, useEffect } from 'react';
import { useNavigate, Navigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuth } from '../context/AuthContext';
import { z } from 'zod';
import DOMPurify from 'dompurify';

const loginSchema = z.object({
  username: z.string().min(3, 'At least 3 characters required'),
  password: z.string().min(6, 'At least 6 characters required'),
  role: z.string().min(1, 'Please select a role')
});

const registerSchema = loginSchema.extend({
  fullName: z.string().optional(),
  email: z.string().email('Invalid email address').optional().or(z.literal('')),
  phone: z.string().optional(),
  gender: z.string().optional(),
  dateOfBirth: z.string().optional(),
  bloodGroup: z.string().optional()
});

const ROLES = [
  { value: 'ADMIN', label: 'Hospital Admin', icon: '🏥' },
  { value: 'PATIENT', label: 'Patient', icon: '🧑' },
];

const ROLE_ROUTES = {
  ADMIN: '/admin/dashboard',
  PATIENT: '/patient/dashboard',
};

const DOCTOR_PORTAL_MESSAGE = 'Doctor sign-in is available only through the hospital portals. Please use the City General Hospital or Metro Medical Center doctor portal.';

const FEATURES = [
  { icon: '🔒', label: 'HIPAA-compliant data transfer' },
  { icon: '⚡', label: 'Real-time FHIR R4 conversion' },
  { icon: '🏥', label: 'Multi-hospital interoperability' },
  { icon: '✅', label: 'Patient consent management' },
];

const LoginPage = () => {
  const { isAuthenticated, user, login, register, logout } = useAuth();
  const navigate = useNavigate();

  const [isRegister, setIsRegister] = useState(false);
  const [form, setForm] = useState({ username: '', password: '', role: 'PATIENT', patientId: '', hospitalId: '', fullName: '', specialization: '', email: '', phone: '', gender: '', dateOfBirth: '', bloodGroup: '' });
  const [errors, setErrors] = useState({});
  const [apiError, setApiError] = useState('');
  const [loading, setLoading] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');

  useEffect(() => {
    if (isAuthenticated && user?.role === 'DOCTOR') {
      logout();
      setApiError(DOCTOR_PORTAL_MESSAGE);
    }
  }, [isAuthenticated, user, logout]);

  if (isAuthenticated && user) {
    if (user.role === 'DOCTOR') {
      return null;
    }
    return <Navigate to={ROLE_ROUTES[user.role] || '/login'} replace />;
  }

  const validate = (data) => {
    const schema = isRegister ? registerSchema : loginSchema;
    const result = schema.safeParse(data);
    if (!result.success) {
      const errs = {};
      result.error.issues.forEach(issue => {
        errs[issue.path[0]] = issue.message;
      });
      return errs;
    }
    return {};
  };

  const handleChange = (e) => {
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }));
    setErrors((p) => ({ ...p, [e.target.name]: '' }));
    setApiError('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    // Sanitize inputs to prevent XSS
    const sanitizedForm = {
      ...form,
      username: DOMPurify.sanitize(form.username),
      fullName: DOMPurify.sanitize(form.fullName),
      email: DOMPurify.sanitize(form.email),
      phone: DOMPurify.sanitize(form.phone),
    };

    const errs = validate(sanitizedForm);
    if (Object.keys(errs).length > 0) { setErrors(errs); return; }
    
    setLoading(true);
    setApiError('');
    try {
      if (isRegister) {
        const result = await register(sanitizedForm.username, form.password, sanitizedForm.role, '', sanitizedForm.hospitalId, sanitizedForm.fullName, sanitizedForm.specialization, sanitizedForm.email, sanitizedForm.phone, sanitizedForm.gender, sanitizedForm.dateOfBirth, sanitizedForm.bloodGroup);
        setSuccessMsg(
          form.role === 'PATIENT' && result?.abhaId
            ? `Patient registered successfully. ABHA-ID: ${result.abhaId}`
            : 'Account created successfully.'
        );
        setIsRegister(false);
        setForm((f) => ({ ...f, password: '' }));
      } else {
        const userObj = await login(sanitizedForm.username, form.password);
        if (userObj.role === 'DOCTOR') {
          logout();
          setApiError(DOCTOR_PORTAL_MESSAGE);
          return;
        }
        navigate(ROLE_ROUTES[userObj.role] || '/login');
      }
    } catch (err) {
      setApiError(err?.response?.data?.message || err.message || 'Authentication failed. Check your credentials.');
    } finally {
      setLoading(false);
    }
  };

  const switchMode = () => {
    setIsRegister((v) => !v);
    setErrors({});
    setApiError('');
    setSuccessMsg('');
  };

  return (
    <div className="login-page">
      {/* Left panel */}
      <div className="login-left">
        <div className="login-left-bg" />
        <div className="login-left-grid" />
        <div className="login-left-content">
          <div className="login-hero-badge">
            <span className="login-hero-badge-dot" />
            FHIR R4 Certified
          </div>

          <span className="login-hero-icon">⚕️</span>
          <h1 className="login-hero-title">
            Health<span>Bridge</span>
          </h1>
          <p className="login-hero-subtitle">
            Secure, standards-based health record interoperability between hospitals — powered by HAPI FHIR R4.
          </p>

          <div className="login-features">
            {FEATURES.map((f) => (
              <div key={f.label} className="login-feature-item">
                <span className="login-feature-icon">{f.icon}</span>
                <span>{f.label}</span>
              </div>
            ))}
          </div>

          <div style={{ marginTop: '40px', paddingTop: '28px', borderTop: '1px solid rgba(255,255,255,0.08)' }}>
            <div style={{ display: 'flex', gap: '20px' }}>
              {[
                { value: 'DEPA', label: 'Compliant' },
                { value: 'ABDM', label: 'Ready' },
                { value: 'HL7', label: 'FHIR R4' },
              ].map((tag) => (
                <div key={tag.value} style={{ textAlign: 'center' }}>
                  <div style={{
                    fontFamily: "'Syne', sans-serif",
                    fontSize: '16px', fontWeight: '700',
                    color: 'rgba(255,255,255,0.9)'
                  }}>{tag.value}</div>
                  <div style={{ fontSize: '10px', color: 'rgba(255,255,255,0.35)', fontWeight: '500', textTransform: 'uppercase', letterSpacing: '0.06em', marginTop: '2px' }}>{tag.label}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Right panel */}
      <div className="login-right">
        <motion.div
          className="login-card"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
        >
          <div>
            <div className="login-card-eyebrow">
              {isRegister ? 'New Account' : 'Secure Access'}
            </div>
            <h2 className="login-card-title">
              {isRegister ? 'Create your account' : 'Sign in to health-bridge'}
            </h2>
            <p className="login-card-subtitle" style={{ marginTop: '4px' }}>
              {isRegister
                ? 'Register to access the interoperability platform'
                : 'Enter your credentials to continue'}
            </p>
            {!isRegister && (
              <p className="login-card-subtitle" style={{ marginTop: '10px', fontSize: '13px' }}>
                Doctor access is handled in the hospital-specific portals.
              </p>
            )}
          </div>

          <AnimatePresence mode="wait">
            {successMsg && (
              <motion.div
                className="alert-success"
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
              >
                ✅ {successMsg}
              </motion.div>
            )}
            {apiError && (
              <motion.div
                className="alert-error"
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
              >
                ⚠️ {apiError}
              </motion.div>
            )}
          </AnimatePresence>

          <form onSubmit={handleSubmit} className="login-form" noValidate>
            <div className="form-group">
              <label className="form-label" htmlFor="username">Username</label>
              <input
                id="username" name="username" type="text"
                className={`form-input ${errors.username ? 'input-error' : ''}`}
                placeholder="Enter your username"
                value={form.username} onChange={handleChange}
                autoComplete="username"
              />
              {errors.username && <span className="field-error">{errors.username}</span>}
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="password">Password</label>
              <input
                id="password" name="password" type="password"
                className={`form-input ${errors.password ? 'input-error' : ''}`}
                placeholder="Enter your password"
                value={form.password} onChange={handleChange}
                autoComplete={isRegister ? 'new-password' : 'current-password'}
              />
              {errors.password && <span className="field-error">{errors.password}</span>}
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="role">Role</label>
              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                {ROLES.map((r) => (
                  <label key={r.value} style={{
                    flex: '1', minWidth: '100px',
                    display: 'flex', alignItems: 'center', gap: '7px',
                    padding: '9px 12px',
                    border: `1.5px solid ${form.role === r.value ? 'var(--c-primary)' : 'var(--c-border)'}`,
                    borderRadius: 'var(--r-md)',
                    background: form.role === r.value ? 'var(--c-primary-bg)' : 'white',
                    cursor: 'pointer',
                    transition: 'all 0.12s ease',
                    fontSize: '12.5px',
                    fontWeight: '500',
                    color: form.role === r.value ? 'var(--c-primary-dark)' : 'var(--c-text-secondary)',
                  }}>
                    <input
                      type="radio" name="role" value={r.value}
                      checked={form.role === r.value}
                      onChange={handleChange}
                      style={{ width: '13px', height: '13px', accentColor: 'var(--c-primary)' }}
                    />
                    {r.icon} {r.label}
                  </label>
                ))}
              </div>
              {errors.role && <span className="field-error">{errors.role}</span>}
            </div>

            {isRegister && (
              <>
                <div className="form-group">
                  <label className="form-label" htmlFor="fullName">Full Name</label>
                  <input
                    id="fullName" name="fullName" type="text"
                    className="form-input"
                    placeholder="Enter your full name"
                    value={form.fullName} onChange={handleChange}
                  />
                </div>
                
                {form.role === 'PATIENT' && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '15px', marginTop: '5px' }}>
                    <div style={{ display: 'flex', gap: '10px' }}>
                      <div className="form-group" style={{ flex: 1 }}>
                        <label className="form-label" htmlFor="email">Email</label>
                        <input id="email" name="email" type="email" className="form-input" placeholder="Email address" value={form.email} onChange={handleChange} />
                      </div>
                      <div className="form-group" style={{ flex: 1 }}>
                        <label className="form-label" htmlFor="phone">Phone</label>
                        <input id="phone" name="phone" type="text" className="form-input" placeholder="Phone number" value={form.phone} onChange={handleChange} />
                      </div>
                    </div>
                    
                    <div style={{ display: 'flex', gap: '10px' }}>
                      <div className="form-group" style={{ flex: 1 }}>
                        <label className="form-label" htmlFor="gender">Gender</label>
                        <select id="gender" name="gender" className="form-input" value={form.gender} onChange={handleChange}>
                          <option value="">-- Select --</option>
                          <option value="Male">Male</option>
                          <option value="Female">Female</option>
                          <option value="Other">Other</option>
                        </select>
                      </div>
                      <div className="form-group" style={{ flex: 1 }}>
                        <label className="form-label" htmlFor="dateOfBirth">Date of Birth</label>
                        <input id="dateOfBirth" name="dateOfBirth" type="date" className="form-input" value={form.dateOfBirth} onChange={handleChange} />
                      </div>
                    </div>
                    
                    <div className="form-group">
                      <label className="form-label" htmlFor="bloodGroup">Blood Group (Optional)</label>
                      <select id="bloodGroup" name="bloodGroup" className="form-input" value={form.bloodGroup} onChange={handleChange}>
                        <option value="">-- Select --</option>
                        <option value="A+">A+</option><option value="A-">A-</option>
                        <option value="B+">B+</option><option value="B-">B-</option>
                        <option value="AB+">AB+</option><option value="AB-">AB-</option>
                        <option value="O+">O+</option><option value="O-">O-</option>
                      </select>
                    </div>
                  </div>
                )}
              </>
            )}



            <button type="submit" className="btn-primary btn-full" disabled={loading} style={{ marginTop: '4px' }}>
              {loading ? (
                <>
                  <span className="btn-spinner" />
                  {isRegister ? 'Creating account...' : 'Signing in...'}
                </>
              ) : (
                isRegister ? 'Create Account' : 'Sign In →'
              )}
            </button>
          </form>

          <div className="divider" />

          <div className="login-toggle">
            <span>{isRegister ? 'Already have an account?' : "Don't have an account?"}</span>
            <button type="button" className="toggle-btn" onClick={switchMode}>
              {isRegister ? 'Sign In' : 'Register'}
            </button>
          </div>
        </motion.div>
      </div>
    </div>
  );
};

export default LoginPage;
