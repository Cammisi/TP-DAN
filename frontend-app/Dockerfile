# --- Etapa 1: build con Node ---
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Build arg y no variable de runtime: ver explicacion_docker_compose.md (frontend-app)
ARG NEXT_PUBLIC_GATEWAY_URL
ENV NEXT_PUBLIC_GATEWAY_URL=$NEXT_PUBLIC_GATEWAY_URL
RUN npm run build

# --- Etapa 2: runtime liviano (output: "standalone") ---
FROM node:22-alpine AS runtime
RUN apk add --no-cache curl
WORKDIR /app
ENV NODE_ENV=production
# ver gotcha de HOSTNAME en CLAUDE.md
ENV HOSTNAME=0.0.0.0
COPY --from=build /app/public ./public
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
EXPOSE 3000
ENTRYPOINT ["node", "server.js"]
