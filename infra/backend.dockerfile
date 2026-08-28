FROM node:20-alpine AS builder
WORKDIR /app
COPY backend/package.json backend/tsconfig.json ./
RUN npm ci
COPY backend/src ./src
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
COPY --from=builder /app/dist ./dist
COPY backend/package.json ./
RUN npm ci --omit=dev

RUN addgroup -S linko && adduser -S backend -G linko
USER backend

EXPOSE 8080
ENV NODE_ENV=production
ENV PORT=8080

CMD ["node", "dist/server.js"]
