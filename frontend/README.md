# health-bridge — FHIR Health Record Interoperability System
### Frontend Application

A modern React application providing role-based dashboards for Admins, Doctors, and Patients in a FHIR-powered health data interoperability system.

---

## 🚀 Quick Start

### Prerequisites
- Node.js v18+ (tested on v24)
- npm v9+
- Backend running at `http://localhost:8085` (optional — mock data is enabled by default)

### 1. Install Dependencies
```bash
cd frontend
npm install
```

### 2. Configure Environment
```bash
cp .env.example .env
# Edit .env if your backend runs on a different port:
# VITE_API_BASE_URL=http://localhost:8085
```

### 3. Start Development Server
```bash
npm run dev
```
Open **http://localhost:5173** in your browser.

---

## 🏗️ Project Structure

```
frontend/
├── src/
│   ├── assets/
│   ├── components/
│   │   ├── ErrorBoundary.jsx
│   │   ├── LoadingSpinner.jsx
│   │   ├── ProtectedRoute.jsx
│   │   ├── Sidebar.jsx
│   │   └── StatusBadge.jsx
│   ├── context/
│   │   └── AuthContext.jsx
│   ├── pages/
│   │   ├── LoginPage.jsx
│   │   ├── AdminDashboard.jsx
│   │   ├── DoctorDashboard.jsx
│   │   └── PatientDashboard.jsx
│   ├── services/
│   │   ├── api.js
│   │   ├── authService.js
│   │   ├── adminService.js
│   │   ├── doctorService.js
│   │   └── patientService.js
│   ├── App.jsx
│   ├── main.jsx
│   └── index.css
├── .env
├── .env.example
└── package.json
```

---

## 🔐 Authentication and Routing

| Route | Page | Allowed Roles |
|---|---|---|
| `/login` | Login / Register | Public |
| `/admin/dashboard` | Admin Dashboard | ADMIN |
| `/doctor/dashboard` | Doctor Dashboard | DOCTOR |
| `/patient/dashboard` | Patient Dashboard | PATIENT |

---

## 🌐 API Endpoints (Backend at port 8085)

| Method | URL | Used By |
|---|---|---|
| POST | `/auth/register` | Login page |
| POST | `/auth/login` | Login page |
| POST | `/hospitalA/op-consult` | Doctor dashboard |
| POST | `/consent` | Patient dashboard |
| POST | `/consent/revoke/{patientId}` | Patient dashboard |

> Mock mode is ON by default. UI renders with sample data if backend is offline.

---

## 🎨 Design System

- Palette: Lavender #7C6FCD / #A89EE0 primary, white cards
- Font: Inter (Google Fonts)
- Charts: Recharts AreaChart for admin health view
- Animations: Framer Motion transitions

---

## 📦 Key Dependencies

- react-router-dom — routing
- axios — HTTP client
- recharts — charts
- framer-motion — animations

---

## 📝 Scripts

```bash
npm run dev      # Start dev server
npm run build    # Production build
npm run preview  # Preview build
```

---

## 🔧 Environment Variables

| Variable | Default | Description |
|---|---|---|
| VITE_API_BASE_URL | http://localhost:8085 | Spring Boot backend URL |
