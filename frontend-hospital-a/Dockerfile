FROM node:20-alpine

WORKDIR /app

# Dependency files copy karte hain
COPY package*.json ./

# Dependencies install karte hain
RUN npm install

# Baaki saara code copy karte hain
COPY . .

# Is project me Vite 5174 port par chal raha hai
EXPOSE 5174

# Dev server start karte hain
CMD ["npm", "run", "dev", "--", "--host"]
