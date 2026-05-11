#!/usr/bin/env bash

echo "Starting all microservices and frontends in the background..."

mkdir -p logs

# Kill existing processes if any
pkill -f "spring-boot:run" || true
pkill -f "vite" || true

echo "1. Starting Discovery Server (Port 9095)..."
(./mvnw -pl services/discovery-server spring-boot:run > logs/discovery-server.log 2>&1 &)

echo "Waiting 15 seconds for Discovery Server to be ready..."
sleep 15

echo "2. Starting API Gateway (Port 9091)..."
(./mvnw -pl services/api-gateway-service spring-boot:run > logs/api-gateway.log 2>&1 &)

echo "3. Starting Auth Identity Service (Port 9092)..."
(./mvnw -pl services/auth-identity-service spring-boot:run > logs/auth-identity.log 2>&1 &)

echo "4. Starting Consent Service (Port 9094)..."
(./mvnw -pl services/consent-service spring-boot:run > logs/consent.log 2>&1 &)

echo "5. Starting Hospital A Service (Port 9097)..."
(./mvnw -pl services/hospital-a-service spring-boot:run > logs/hospital-a.log 2>&1 &)

echo "6. Starting Hospital B Service (Port 9098)..."
(./mvnw -pl services/hospital-b-service spring-boot:run > logs/hospital-b.log 2>&1 &)

echo "7. Starting HIE FHIR Exchange Service (Port 9096)..."
(./mvnw -pl services/hie-fhir-exchange-service spring-boot:run > logs/hie-fhir-exchange.log 2>&1 &)

echo "8. Starting Notification Audit Service (Port 9099)..."
(./mvnw -pl services/notification-audit-service spring-boot:run > logs/notification-audit.log 2>&1 &)

echo "9. Starting Admin Reporting Service (Port 9090)..."
(./mvnw -pl services/admin-reporting-service spring-boot:run > logs/admin-reporting.log 2>&1 &)

echo "10. Starting Frontends..."
(cd frontend && npm install > /dev/null 2>&1 && npm run dev > ../logs/frontend.log 2>&1 &)
(cd frontend-hospital-a && npm install > /dev/null 2>&1 && npm run dev > ../logs/frontend-hospital-a.log 2>&1 &)
(cd frontend-hospital-b && npm install > /dev/null 2>&1 && npm run dev > ../logs/frontend-hospital-b.log 2>&1 &)

echo "All services have been started! You can check the logs in the 'logs' directory."
echo " - Discovery Server: logs/discovery-server.log"
echo " - API Gateway: logs/api-gateway.log"
echo " - Auth Identity: logs/auth-identity.log"
echo " - Consent: logs/consent.log"
echo " - Hospital A: logs/hospital-a.log"
echo " - Hospital B: logs/hospital-b.log"
echo " - HIE FHIR Exchange: logs/hie-fhir-exchange.log"
echo " - Notification Audit: logs/notification-audit.log"
echo " - Admin Reporting: logs/admin-reporting.log"
echo " - Main Frontend: logs/frontend.log"
echo " - Hospital A Frontend: logs/frontend-hospital-a.log"
echo " - Hospital B Frontend: logs/frontend-hospital-b.log"
