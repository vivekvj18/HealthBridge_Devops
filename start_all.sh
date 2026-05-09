#!/usr/bin/env bash

echo "Starting all services in the background..."

mkdir -p logs

# Kill existing processes if any (optional but helps keep it neat)
pkill -f "spring-boot:run" || true
pkill -f "vite" || true

echo "1. Starting Backend..."
(cd backend && ./mvnw spring-boot:run > ../logs/backend.log 2>&1 &)

echo "2. Starting Frontend (Main/Admin)..."
(cd frontend && npm install > /dev/null 2>&1 && npm run dev > ../logs/frontend.log 2>&1 &)

echo "3. Starting Frontend (Hospital A)..."
(cd frontend-hospital-a && npm install > /dev/null 2>&1 && npm run dev > ../logs/frontend-hospital-a.log 2>&1 &)

echo "4. Starting Frontend (Hospital B)..."
(cd frontend-hospital-b && npm install > /dev/null 2>&1 && npm run dev > ../logs/frontend-hospital-b.log 2>&1 &)

echo "All services have been started! You can check the logs in the 'logs' directory."
echo " - Backend: logs/backend.log"
echo " - Main Frontend: logs/frontend.log (Port 5173)"
echo " - Hospital A Frontend: logs/frontend-hospital-a.log (Port 5174)"
echo " - Hospital B Frontend: logs/frontend-hospital-b.log (Port 5175)"
