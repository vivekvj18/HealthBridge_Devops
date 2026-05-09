import api from './api';

// ─── Mock Data ───────────────────────────────────────────────────────────────
const MOCK_ENABLED = false;

const MOCK_TRANSFERS = [
  {
    id: 1,
    patientId: 'P-1001',
    sourceHospital: 'Hospital A',
    targetHospital: 'Hospital B',
    status: 'SUCCESS',
    bundleResourceCount: 4,
    timestamp: '2026-04-08T09:21:00Z',
    consentSnapshot: '{"granted":true}',
    failureReason: null,
  },
  {
    id: 2,
    patientId: 'P-1002',
    sourceHospital: 'Hospital A',
    targetHospital: 'Hospital B',
    status: 'PENDING',
    bundleResourceCount: 2,
    timestamp: '2026-04-08T11:05:00Z',
    consentSnapshot: '{"granted":true}',
    failureReason: null,
  },
  {
    id: 3,
    patientId: 'P-1003',
    sourceHospital: 'Hospital A',
    targetHospital: 'Hospital B',
    status: 'FAILED',
    bundleResourceCount: 0,
    timestamp: '2026-04-07T14:33:00Z',
    consentSnapshot: '{"granted":false}',
    failureReason: 'Consent DENIED for patient P-1003',
  },
  {
    id: 4,
    patientId: 'P-1004',
    sourceHospital: 'Hospital A',
    targetHospital: 'Hospital B',
    status: 'SUCCESS',
    bundleResourceCount: 6,
    timestamp: '2026-04-09T08:00:00Z',
    consentSnapshot: '{"granted":true}',
    failureReason: null,
  },
  {
    id: 5,
    patientId: 'P-1005',
    sourceHospital: 'Hospital A',
    targetHospital: 'Hospital B',
    status: 'PENDING',
    bundleResourceCount: 3,
    timestamp: '2026-04-09T10:15:00Z',
    consentSnapshot: '{"granted":true}',
    failureReason: null,
  },
];

const MOCK_AUDIT_LOGS = [
  { id: 1, timestamp: '2026-04-09T08:00:00Z', user: 'dr.chen', action: 'TRANSFER_INITIATED', resource: 'Patient P-1001', status: 'SUCCESS' },
  { id: 2, timestamp: '2026-04-09T08:01:00Z', user: 'system', action: 'FHIR_VALIDATED', resource: 'Bundle for P-1001', status: 'SUCCESS' },
  { id: 3, timestamp: '2026-04-09T09:00:00Z', user: 'dr.patel', action: 'CONSENT_GRANTED', resource: 'Patient P-1004', status: 'SUCCESS' },
  { id: 4, timestamp: '2026-04-09T10:00:00Z', user: 'admin', action: 'USER_REGISTERED', resource: 'dr.newuser', status: 'SUCCESS' },
  { id: 5, timestamp: '2026-04-09T10:15:00Z', user: 'dr.chen', action: 'TRANSFER_INITIATED', resource: 'Patient P-1005', status: 'PENDING' },
  { id: 6, timestamp: '2026-04-07T14:33:00Z', user: 'system', action: 'TRANSFER_FAILED', resource: 'Patient P-1003', status: 'FAILED' },
  { id: 7, timestamp: '2026-04-07T14:34:00Z', user: 'system', action: 'CONSENT_DENIED', resource: 'Patient P-1003', status: 'FAILED' },
  { id: 8, timestamp: '2026-04-06T16:00:00Z', user: 'admin', action: 'SYSTEM_HEALTH_CHECK', resource: 'All Services', status: 'SUCCESS' },
];

const MOCK_SYSTEM_HEALTH = [
  { name: 'Mon', transfers: 8, failures: 1 },
  { name: 'Tue', transfers: 12, failures: 0 },
  { name: 'Wed', transfers: 6, failures: 2 },
  { name: 'Thu', transfers: 15, failures: 1 },
  { name: 'Fri', transfers: 10, failures: 0 },
  { name: 'Sat', transfers: 4, failures: 0 },
  { name: 'Sun', transfers: 7, failures: 1 },
];

export const adminService = {
  getAllTransfers: async () => {
    try {
      const res = await api.get('/admin/transfers');
      return res.data;
    } catch {
      if (MOCK_ENABLED) return MOCK_TRANSFERS;
      throw new Error('Failed to fetch transfers');
    }
  },

  getAuditLogs: async () => {
    try {
      const res = await api.get('/admin/audit-logs');
      return res.data;
    } catch {
      if (MOCK_ENABLED) return MOCK_AUDIT_LOGS;
      throw new Error('Failed to fetch audit logs');
    }
  },

  getSystemHealth: async () => {
    try {
      const res = await api.get('/admin/system-health');
      return res.data;
    } catch {
      if (MOCK_ENABLED) return MOCK_SYSTEM_HEALTH;
      throw new Error('Failed to fetch system health');
    }
  },

  // ── User Management ───────────────────────────────────────────────────────
  getUsers: async () => {
    try {
      const res = await api.get('/admin/users');
      return res.data;
    } catch {
      if (MOCK_ENABLED) return [];
      throw new Error('Failed to fetch users');
    }
  },

  createUser: async (userData) => {
    try {
      const res = await api.post('/admin/users', userData);
      return res.data;
    } catch {
      if (MOCK_ENABLED) return { ...userData, id: Date.now() };
      throw new Error('Failed to create user');
    }
  },

  deleteUser: async (id) => {
    try {
      await api.delete(`/admin/users/${id}`);
    } catch {
      if (!MOCK_ENABLED) throw new Error('Failed to delete user');
    }
  },
};
