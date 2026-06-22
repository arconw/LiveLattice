FROM node:24-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
COPY test ./test
RUN npm test
EXPOSE 3000
CMD ["npx", "tsx", "src/start.ts"]
