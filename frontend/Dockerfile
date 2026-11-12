# syntax=docker/dockerfile:1
# Frontend image: built from this directory (docker build -t hourforge-frontend frontend).
# Static files only; the reverse proxy routes /api to the backend image.

FROM node:25-alpine@sha256:bdf2cca6fe3dabd014ea60163eca3f0f7015fbd5c7ee1b0e9ccb4ced6eb02ef4 AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine@sha256:0c79d56aee561a1d81c63f00eee5fb5fe29279560cdc55e91425133104c7fbe6
# The digest fixes the base; system packages are brought up to date at every build so known fixes are not left out.
USER root
RUN apk upgrade --no-cache
USER 101
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /src/dist/ /usr/share/nginx/html/
# The base image already runs as the unprivileged nginx user (uid 101) and listens on 8080.
EXPOSE 8080
