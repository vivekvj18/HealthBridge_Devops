import api from './api';

export const hospitalService = {
  getHospitals: async () => {
    try {
      const res = await api.get('/hospitals');
      return res.data;
    } catch (err) {
      console.error('Failed to load hospitals', err);
      // Fallback for mock if dev
      return [
        { id: 'HOSP-A', name: 'City General Hospital' },
        { id: 'HOSP-B', name: 'Metro Medical Center' }
      ];
    }
  },
  getDoctors: async (hospitalId) => {
    try {
      const res = await api.get(`/auth/doctors?hospitalId=${hospitalId}`);
      return res.data;
    } catch (err) {
      console.error('Failed to load doctors', err);
      return [];
    }
  }
};
