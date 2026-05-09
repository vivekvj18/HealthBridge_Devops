import { createContext, useContext, useState, useCallback } from 'react';
import { authService } from '../services/authService';

const AuthContext = createContext(null);

/** Safely decode a JWT payload without a library. Returns {} on failure. */
const decodeJwtPayload = (token) => {
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(base64));
  } catch {
    return {};
  }
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => {
    try {
      const stored = localStorage.getItem('user');
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });

  const [token, setToken] = useState(() => localStorage.getItem('accessToken') || null);

  const login = useCallback(async (username, password) => {
    const data = await authService.login(username, password);
    // Decode JWT to get patientId claim embedded by the backend AuthService
    const claims = decodeJwtPayload(data.accessToken);
    const userObj = {
      username: data.username,
      role: data.role,
      patientId: claims.patientId || null,
      hospitalId: claims.hospitalId || null,
    };
    localStorage.setItem('accessToken', data.accessToken);
    localStorage.setItem('refreshToken', data.refreshToken);
    localStorage.setItem('user', JSON.stringify(userObj));
    setToken(data.accessToken);
    setUser(userObj);
    return userObj;
  }, []);

  const register = useCallback(async (username, password, role, patientId, hospitalId, fullName, specialization) => {
    const data = await authService.register(username, password, role, patientId, hospitalId, fullName, specialization);
    return data;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    setToken(null);
    setUser(null);
  }, []);

  const isAuthenticated = !!token && !!user;

  return (
    <AuthContext.Provider value={{ user, token, isAuthenticated, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
};

export default AuthContext;
