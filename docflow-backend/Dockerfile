FROM node:20-alpine

# Install curl for healthcheck
RUN apk add --no-cache curl

WORKDIR /app
COPY package*.json ./
RUN npm install --production
COPY . .
RUN mkdir -p uploads
EXPOSE 3000
CMD ["node", "server.js"]