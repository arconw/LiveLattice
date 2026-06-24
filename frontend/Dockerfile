FROM node:24-alpine AS build
WORKDIR /app
ARG VITE_REALTIME_URL=
ENV VITE_REALTIME_URL=${VITE_REALTIME_URL}
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.29-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
