# --- Etapa 1: build con Node ---
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# NEXT_PUBLIC_* se inlinea en el bundle del browser en build-time, no en runtime:
# tiene que llegar acá como build arg, no como variable de entorno del contenedor.
ARG NEXT_PUBLIC_GATEWAY_URL
ENV NEXT_PUBLIC_GATEWAY_URL=$NEXT_PUBLIC_GATEWAY_URL
RUN npm run build

# --- Etapa 2: runtime liviano (output: "standalone") ---
FROM node:22-alpine AS runtime
RUN apk add --no-cache curl
WORKDIR /app
ENV NODE_ENV=production
# El server standalone de Next.js usa $HOSTNAME para el bind; Docker ya setea esa
# variable al id del contenedor, así que sin esto escucha solo en su IP interna
# de la red Docker y ni el healthcheck (curl a localhost) ni el host pueden llegar.
ENV HOSTNAME=0.0.0.0
COPY --from=build /app/public ./public
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
EXPOSE 3000
ENTRYPOINT ["node", "server.js"]
