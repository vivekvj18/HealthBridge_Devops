import api from './api';

// ─── Mock Data ───────────────────────────────────────────────────────────────
// MOCK_ENABLED = true means data loads even when backend is unreachable.
// Set to false in production to force real API calls.
const MOCK_ENABLED = false;

// Mock consent requests matching ConsentRequestViewDTO structure
const MOCK_CONSENTS = [
  {
    id: 1,
    patientId: 'ABHA-2233-4455-6677-88',
    requesterId: 'dr_chen',
    purpose: 'Routine checkup and lab review',
    status: 'PENDING',
    requestedDataTypes: ['OP_CONSULT', 'LAB_RESULT'],
    grantedDataTypes: [],
    createdAt: '2026-03-01T12:00:00Z',
    updatedAt: '2026-03-01T12:00:00Z',
  },
  {
    id: 2,
    patientId: 'ABHA-2233-4455-6677-88',
    requesterId: 'dr_sharma',
    purpose: 'Emergency consultation',
    status: 'GRANTED',
    requestedDataTypes: ['OP_CONSULT', 'PRESCRIPTION'],
    grantedDataTypes: ['OP_CONSULT'],
    createdAt: '2026-02-15T09:00:00Z',
    updatedAt: '2026-02-16T11:00:00Z',
  },
];

export const patientService = {

  /**
   * GET /consent/pending/{patientId}
   * Returns list of ConsentRequestViewDTO for this patient.
   * Backend enum statuses: PENDING, GRANTED, DENIED, REVOKED
   */
  getConsents: async (abhaId) => {
    try {
      const res = await api.get(`/consent/pending/${abhaId}`);
      return res.data;
    } catch {
      if (MOCK_ENABLED) return MOCK_CONSENTS;
      throw new Error('Failed to fetch consent requests');
    }
  },

  /**
   * POST /consent/respond/{requestId}
   * Body: ConsentDecisionDTO { decision: 'GRANTED'|'DENIED', grantedDataTypes: Set<String> }
   * Returns updated ConsentRequestViewDTO.
   */
  respondToConsent: async (requestId, granted, grantedDataTypes = ['OP_CONSULT']) => {
    try {
      const res = await api.post(`/consent/respond/${requestId}`, {
        decision: granted ? 'GRANTED' : 'DENIED',
        grantedDataTypes,
      });
      return res.data;
    } catch (err) {
      if (MOCK_ENABLED) {
        console.warn('[Mock] respondToConsent fallback');
        return { id: requestId, status: granted ? 'GRANTED' : 'DENIED', grantedDataTypes };
      }
      throw err;
    }
  },

  /**
   * POST /consent/revoke/{requestId}
   * Returns plain string "Consent REVOKED for request ID: {id}"
   */
  revokeConsent: async (requestId) => {
    try {
      const res = await api.post(`/consent/revoke/${requestId}`);
      return res.data;
    } catch (err) {
      if (MOCK_ENABLED) {
        console.warn('[Mock] revokeConsent fallback');
        return `Consent REVOKED for request ID: ${requestId}`;
      }
      throw err;
    }
  },

  /**
   * POST /hospitalA/op-consult/push
   * Body: PatientPushRequestDTO { targetRequesterId: String, dataTypes: Set<String> }
   * Backend uses JWT claim to identify which patient is pushing.
   * Returns plain string success message.
   */
  pushRecords: async (targetRequesterId, dataTypes) => {
    try {
      const res = await api.post('/hospitalA/op-consult/push', {
        targetRequesterId,
        dataTypes,
      });
      return res.data;
    } catch (err) {
      if (MOCK_ENABLED) {
        console.warn('[Mock] pushRecords fallback');
        return 'Records pushed successfully (mock)';
      }
      throw err;
    }
  },

  /**
   * GET /patient/audit/{patientId}
   * Returns list of TransferAuditLog for this patient.
   */
  getAuditLogs: async (patientId) => {
    try {
      const res = await api.get(`/patient/audit/${patientId}`);
      return res.data;
    } catch {
      if (MOCK_ENABLED) return [];
      throw new Error('Failed to fetch activity logs');
    }
  },
};
