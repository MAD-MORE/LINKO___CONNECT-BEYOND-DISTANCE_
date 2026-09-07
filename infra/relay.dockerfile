FROM node:20-alpine AS build
WORKDIR /app
COPY relay/package.json relay/package-lock.json* ./
RUN npm install
COPY relay/ ./
RUN npm run build

FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/package.json ./package.json
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
EXPOSE 3479/udp
EXPOSE 8080/tcp
USER node
CMD ["node", "dist/relay-server.js"]
