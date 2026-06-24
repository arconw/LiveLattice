FROM node:24-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
COPY test ./test
RUN npm test -- --test-timeout=10000
ENV NODE_OPTIONS="--require @opentelemetry/auto-instrumentations-node/register" \
    OTEL_SDK_DISABLED=true
EXPOSE 3000
CMD ["npx", "tsx", "src/start.ts"]
