import api from './api';

// ─── Mock Data ───────────────────────────────────────────────────────────────
const MOCK_ENABLED = false;

export const doctorService = {
  /**
   * POST /hospitalA/op-consult
   * Body: HospitalAOPConsultRecordDTO
   * Fields: patientId, patientFirstName, patientLastName, doctorName,
   *         visitDate, symptoms, temperature (double), bloodPressure,
   *         prescriptionPdfBase64
   * Returns plain string success message.
   */
  submitPatientData: async (formData) => {
    try {
      // Only send fields that exist in HospitalAOPConsultRecordDTO
      const payload = {
        patientId:            formData.patientId,
        abhaId:               formData.abhaId || (formData.patientId?.startsWith('ABHA-') ? formData.patientId : ''),
        patientFirstName:     formData.patientFirstName,
        patientLastName:      formData.patientLastName,
        doctorName:           formData.doctorName,
        visitDate:            formData.visitDate,
        symptoms:             formData.symptoms,
        temperature:          formData.temperature,
        bloodPressure:        formData.bloodPressure,
        prescriptionPdfBase64: formData.prescriptionPdfBase64 || '',
      };
      const res = await api.post('/hospitalA/op-consult', payload);
      return res.data;
    } catch (err) {
      if (MOCK_ENABLED) {
        return 'OP Consult record stored and converted to FHIR successfully (mock)';
      }
      throw err;
    }
  },

  /**
   * POST /hospitalB/op-consult/native
   * Body: HospitalBOPConsultRecordDTO
   * Fields: patientId, abhaId, patientName, consultDate, doctor, clinicalNotes,
   *         vitals: { bp, temp }, prescriptionPdfBase64
   */
  submitHospitalBConsult: async (formData) => {
    try {
      const payload = {
        patientId: formData.patientId,
        abhaId: formData.abhaId || (formData.patientId?.startsWith('ABHA-') ? formData.patientId : ''),
        patientName: formData.patientName,
        consultDate: formData.consultDate,
        doctor: formData.doctor,
        clinicalNotes: formData.clinicalNotes,
        vitals: {
          bp: formData.bloodPressure,
          temp: formData.temperature,
        },
        prescriptionPdfBase64: formData.prescriptionPdfBase64 || '',
      };
      const res = await api.post('/hospitalB/op-consult/native', payload);
      return res.data;
    } catch (err) {
      if (MOCK_ENABLED) {
        return 'OP Consult record stored in Hospital B database successfully (mock)';
      }
      throw err;
    }
  },

  /**
   * POST /hospitalB/op-consult
   * Body: raw FHIR JSON string (Content-Type: text/plain)
   * Returns: HospitalBOPConsultRecordDTO {
   *   uhid, patientName, consultDate, doctor, clinicalNotes,
   *   vitals: { bp, temp }, prescriptionPdfBase64, consentVerified
   * }
   */
  receiveFhirAtHospitalB: async (fhirJson) => {
    try {
      const res = await api.post('/hospitalB/op-consult', fhirJson, {
        headers: { 'Content-Type': 'text/plain' },
      });
      return res.data;
    } catch (err) {
      if (MOCK_ENABLED) {
        return {
          uhid: 'UHID-001',
          patientName: 'Alice Johnson',
          consultDate: '2026-04-08',
          doctor: 'Dr. Chen',
          clinicalNotes: 'Viral fever — supportive care',
          vitals: { bp: '118/76', temp: '38.4' },
          prescriptionPdfBase64: '',
          consentVerified: true,
        };
      }
      throw err;
    }
  },

  receiveFhirAtHospitalA: async (fhirJson) => {
    try {
      const res = await api.post('/hospitalA/op-consult/receive', fhirJson, {
        headers: { 'Content-Type': 'text/plain' },
      });
      return res.data;
    } catch (err) {
      throw err;
    }
  },

  /**
   * POST /consent/initiate
   * Body: InitiateConsentDTO { patientId, purpose, requestedDataTypes }
   * Returns: ConsentRequestViewDTO
   * Requester ID is automatically taken from the logged-in doctor's JWT.
   */
  initiateConsent: async (abhaId, purpose, requestedDataTypes) => {
    try {
      const res = await api.post('/consent/initiate', {
        patientId: abhaId,
        purpose,
        requestedDataTypes,
      });
      return res.data;
    } catch (err) {
      if (MOCK_ENABLED) {
        return { id: Date.now(), patientId: abhaId, purpose, status: 'PENDING', requestedDataTypes };
      }
      throw err;
    }
  },

  /**
   * GET /auth/register/patient/{abhaId}
   * Fetch patient details by ABHA-ID from the central auth registry.
   * Note: endpoint lives in PatientRegistrationController, not DoctorPatientController.
   */
  getPatientByAbhaId: async (abhaId) => {
    try {
      const res = await api.get(`/auth/register/patient/${abhaId}`);
      return res.data;
    } catch (err) {
      throw err;
    }
  },

  /**
   * POST /doctor/patients/link/{abhaId}
   * Link an existing patient to the doctor's hospital
   */
  linkPatientByAbhaId: async (abhaId) => {
    try {
      const res = await api.post(`/doctor/patients/link/${abhaId}`);
      return res.data;
    } catch (err) {
      throw err;
    }
  },

  lookupPatient: async (identifier) => {
    try {
      const res = await api.get(`/doctor/patients/lookup/${encodeURIComponent(identifier)}`);
      return res.data;
    } catch (err) {
      throw err;
    }
  },

  /**
   * GET /hospitalB/op-consult
   * Returns list of HospitalBOPConsultEntity
   */
  getHospitalBConsults: async () => {
    try {
      const res = await api.get('/hospitalB/op-consult');
      return res.data;
    } catch {
      if (MOCK_ENABLED) return [];
      throw new Error('Failed to fetch Hospital B intake records');
    }
  },
};
