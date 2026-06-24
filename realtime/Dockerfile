FROM node:24-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
COPY test ./test
RUN npm test
RUN npm run build
ENV NODE_OPTIONS="--require @opentelemetry/auto-instrumentations-node/register" \
    OTEL_SDK_DISABLED=true
EXPOSE 3002
CMD ["npx", "tsx", "src/start.ts"]
