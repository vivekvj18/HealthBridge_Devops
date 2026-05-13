FROM node:20-alpine

WORKDIR /app

# Dependency files copy karte hain
COPY package*.json ./

# Dependencies install karte hain
RUN npm install

# Baaki saara code copy karte hain
COPY . .

# Vite ka default port expose karte hain
EXPOSE 5173

# Dev server start karte hain (host flag ke saath taaki bahar se access ho sake)
CMD ["npm", "run", "dev", "--", "--host"]
