import { useState } from 'react';
import { useNavigate, Navigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuth } from '../context/AuthContext';

const FEATURES = [
  { icon: '🔒', label: 'HIPAA-compliant data transfer' },
  { icon: '⚡', label: 'Real-time FHIR R4 conversion' },
  { icon: '🏥', label: 'Federated Health Information Exchange' },
];

const LoginPage = () => {
  const { isAuthenticated, user, login, register, logout } = useAuth();
  const navigate = useNavigate();

  const [isRegister, setIsRegister] = useState(false);
  const [form, setForm] = useState({ username: '', password: '', fullName: '', specialization: '' });
  const [errors, setErrors] = useState({});
  const [apiError, setApiError] = useState('');
  const [loading, setLoading] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');

  if (isAuthenticated && user) {
    if (user.role === 'DOCTOR') return <Navigate to="/doctor/dashboard" replace />;
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100vh', background: 'var(--c-bg)' }}>
        <h2 style={{ color: 'var(--c-danger)', marginBottom: '16px' }}>Access Denied</h2>
        <p style={{ marginBottom: '24px' }}>This portal is restricted to Doctors.</p>
        <button className="btn-primary" onClick={() => { logout(); navigate('/login'); }}>Sign Out</button>
      </div>
    );
  }

  const validate = () => {
    const errs = {};
    if (!form.username.trim()) errs.username = 'Username is required';
    else if (form.username.length < 3) errs.username = 'At least 3 characters required';
    if (!form.password) errs.password = 'Password is required';
    else if (form.password.length < 6) errs.password = 'At least 6 characters required';
    return errs;
  };

  const handleChange = (e) => {
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }));
    setErrors((p) => ({ ...p, [e.target.name]: '' }));
    setApiError('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const errs = validate();
    if (Object.keys(errs).length > 0) { setErrors(errs); return; }
    setLoading(true);
    setApiError('');
    try {
      if (isRegister) {
        // Hardcoded to DOCTOR role and HOSP-B for Hospital B portal
        await register(form.username, form.password, 'DOCTOR', '', 'HOSP-B', form.fullName, form.specialization);
        setSuccessMsg(`Account created successfully.`);
        setIsRegister(false);
        setForm((f) => ({ ...f, password: '' }));
      } else {
        const userObj = await login(form.username, form.password);
        if (userObj.role !== 'DOCTOR') {
            setApiError('This portal is strictly for Doctor access only.');
            return;
        }
        navigate('/doctor/dashboard');
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
      <div className="login-left">
        <div className="login-left-bg" style={{background: 'linear-gradient(135deg, var(--c-navy) 0%, #4c1d95 100%)'}} />
        <div className="login-left-grid" />
        <div className="login-left-content">
          <div className="login-hero-badge">
            <span className="login-hero-badge-dot" />
            Hospital B Portal
          </div>

          <span className="login-hero-icon">🏥</span>
          <h1 className="login-hero-title">
            Metro Medical<span> Center</span>
          </h1>
          <p className="login-hero-subtitle">
            Secure clinical portal for doctors, powered by health-bridge FHIR R4 interoperability.
          </p>

          <div className="login-features">
            {FEATURES.map((f) => (
              <div key={f.label} className="login-feature-item">
                <span className="login-feature-icon">{f.icon}</span>
                <span>{f.label}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="login-right">
        <motion.div
          className="login-card"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
        >
          <div>
            <div className="login-card-eyebrow">
              {isRegister ? 'New Doctor Account' : 'Secure Access'}
            </div>
            <h2 className="login-card-title">
              {isRegister ? 'Create Doctor Profile' : 'Doctor Sign In'}
            </h2>
            <p className="login-card-subtitle" style={{ marginTop: '4px' }}>
              {isRegister
                ? 'Register to access Metro Medical clinical systems'
                : 'Enter your credentials to continue'}
            </p>
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
                
                <div className="form-group">
                  <label className="form-label" htmlFor="specialization">Specialization</label>
                  <input
                    id="specialization" name="specialization" type="text"
                    className="form-input"
                    placeholder="e.g. Cardiologist"
                    value={form.specialization} onChange={handleChange}
                  />
                </div>
              </>
            )}

            <button type="submit" className="btn-primary btn-full" disabled={loading} style={{ marginTop: '12px' }}>
              {loading ? (
                <>
                  <span className="btn-spinner" />
                  {isRegister ? 'Creating profile...' : 'Signing in...'}
                </>
              ) : (
                isRegister ? 'Register as Doctor' : 'Sign In →'
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
