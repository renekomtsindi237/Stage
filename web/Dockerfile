# ══════════════════════════════════════════════════════
# MicroRecouv Web — Dockerfile multi-stage
# Build : docker build --target production -t microrecouv/web .
# Dev   : docker build --target development -t microrecouv/web:dev .
# ══════════════════════════════════════════════════════

# ── Stage 1 : Dépendances ─────────────────────────────
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci --prefer-offline && npm install three @types/three

# ── Stage 2 : Build de production ────────────────────
FROM deps AS builder
ARG ENV=production
# CACHEBUST invalide uniquement cette couche et les suivantes,
# sans refaire npm ci. Passer --build-arg CACHEBUST=$(date +%s)
ARG CACHEBUST=1
COPY . .
RUN npx ng build --configuration=${ENV}

# ── Stage 3 : Image finale nginx ─────────────────────
FROM nginx:1.25-alpine AS production
COPY --from=builder /app/dist/imf-pipeline-web/browser /usr/share/nginx/html
COPY nginx.docker.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

# ── Stage dev : pour tests d'intégration ─────────────
FROM deps AS development
COPY . .
EXPOSE 80
CMD ["npx", "ng", "serve", "--host", "0.0.0.0", "--port", "80", "--disable-host-check"]
