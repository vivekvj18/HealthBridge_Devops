import api from './api';

const MOCK_ENABLED = false;

export const hieService = {

  /**
   * POST /hie/exchange
   * Body: { patientId, hip, hiu, scope: [...], purpose }
   * Returns ExchangeResponseDTO:
   *   status: "CONSENT_PENDING" | "SUCCESS" | "IDENTITY_NOT_FOUND"
   *   consentRequestId: number (when PENDING)
   *   fhirBundle: string (when SUCCESS)
   *   message: string
   */
  requestExchange: async (abhaId, scope, purpose) => {
    try {
      const res = await api.post('/hie/exchange', {
        patientId: abhaId,
        hip: 'HospitalA',
        hiu: 'HospitalB',
        scope,
        purpose: purpose || 'Clinical data request via HIE',
      });
      return res.data;
    } catch (err) {
      if (MOCK_ENABLED) {
        return {
          status: 'CONSENT_PENDING',
          consentRequestId: 99,
          message: 'Consent request #99 sent to patient. Poll for status.',
        };
      }
      throw err;
    }
  },

  initiateConsentOnly: async (abhaId, scope, purpose) => {
    const res = await api.post('/hie/consent-only', {
      patientId: abhaId,
      hip: 'HospitalA',
      hiu: 'HospitalB',
      scope,
      purpose: purpose || 'Manual HIE Consent Request',
    });
    return res.data;
  },

  pullOnly: async (abhaId, scope) => {
    const res = await api.post('/hie/pull-only', {
      patientId: abhaId,
      hip: 'HospitalA',
      hiu: 'HospitalB',
      scope,
    });
    return res.data;
  },

  /**
   * GET /hie/exchange/status/{consentId}
   * Returns ExchangeResponseDTO with current status
   */
  pollStatus: async (consentId) => {
    try {
      const res = await api.get(`/hie/exchange/status/${consentId}`);
      return res.data;
    } catch (err) {
      if (MOCK_ENABLED) {
        return { status: 'SUCCESS', fhirBundle: '{"resourceType":"Bundle"}' };
      }
      throw err;
    }
  },
};
