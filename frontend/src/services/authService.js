import api from './api';

// ─── Mock Data Fallback ──────────────────────────────────────────────────────
const MOCK_ENABLED = false;

const MOCK_LOGIN = {
  accessToken: 'mock-access-token-abc123',
  refreshToken: 'mock-refresh-token-xyz789',
  username: 'demo_user',
  role: 'DOCTOR',
};

export const authService = {
  login: async (username, password, role) => {
    try {
      const res = await api.post('/auth/login', { username, password });
      return res.data;
    } catch (err) {
      if (MOCK_ENABLED) {
        console.warn('[Mock] Login fallback active');
        return { ...MOCK_LOGIN, role: role || 'DOCTOR', username };
      }
      throw err;
    }
  },

  register: async (username, password, role, patientId, hospitalId, fullName, specialization, email, phone, gender, dateOfBirth, bloodGroup) => {
    try {
      const payload = { username, password, role };
      if (hospitalId) payload.hospitalId = hospitalId;
      if (fullName) payload.fullName = fullName;
      if (specialization) payload.specialization = specialization;
      if (email) payload.email = email;
      if (phone) payload.phone = phone;
      if (gender) payload.gender = gender;
      if (dateOfBirth) payload.dateOfBirth = dateOfBirth;
      if (bloodGroup) payload.bloodGroup = bloodGroup;

      const registerUrl = role === 'PATIENT' ? '/auth/register/patient' : '/auth/register';
      const res = await api.post(registerUrl, payload);
      return res.data;
    } catch (err) {
      if (MOCK_ENABLED) {
        console.warn('[Mock] Register fallback active');
        return { message: 'User registered successfully', username, role };
      }
      throw err;
    }
  },

  refresh: async (refreshToken) => {
    const res = await api.post('/auth/refresh', { refreshToken });
    return res.data;
  },
};
