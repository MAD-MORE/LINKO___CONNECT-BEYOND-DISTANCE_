FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json tsconfig.json ./
RUN npm install
COPY src ./src
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
COPY --from=builder /app/dist ./dist
COPY package.json ./
RUN npm install --omit=dev

# Relay runs as non-root.
RUN addgroup -S linko && adduser -S relay -G linko
USER relay

EXPOSE 7000/udp
EXPOSE 7001/tcp

ENV NODE_ENV=production
ENV UDP_PORT=7000
ENV PORT=7001

# Production entrypoint starts the UDP listener first, then registers and heartbeats
# the node with Supabase. It exits closed when registration is not configured.
CMD ["node", "dist/production-entry.js"]
