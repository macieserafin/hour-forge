# syntax=docker/dockerfile:1
# Frontend image: built from this directory (docker build -t hourforge-frontend frontend).
# Static files only; the reverse proxy routes /api to the backend image.

FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /src/dist/ /usr/share/nginx/html/
# The base image already runs as the unprivileged nginx user (uid 101) and listens on 8080.
EXPOSE 8080
