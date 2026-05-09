import { useAuth } from '../context/AuthContext.jsx';
import HospitalADashboard from './HospitalADashboard.jsx';
import HospitalBDashboard from './HospitalBDashboard.jsx';
import { Navigate } from 'react-router-dom';

const DoctorDashboard = () => {
  const { user } = useAuth();

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (user.hospitalId === 'HOSP-A') {
    return <HospitalADashboard />;
  } else if (user.hospitalId === 'HOSP-B') {
    return <HospitalBDashboard />;
  } else {
    // Fallback if no hospital ID
    return <HospitalADashboard />;
  }
};

export default DoctorDashboard;