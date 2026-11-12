# syntax=docker/dockerfile:1
# Frontend image: built from this directory (docker build -t hourforge-frontend frontend).
# Static files only; the reverse proxy routes /api to the backend image.

FROM node:24-alpine@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5 AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.31-alpine@sha256:b5047b9b76845eac124358373c6852377c52e4b05834a47647e955dd91cb8f1e
# The digest fixes the base; system packages are brought up to date at every build so known fixes are not left out.
USER root
RUN apk upgrade --no-cache
USER 101
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /src/dist/ /usr/share/nginx/html/
# The base image already runs as the unprivileged nginx user (uid 101) and listens on 8080.
EXPOSE 8080
