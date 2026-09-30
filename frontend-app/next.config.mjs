/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Imagen de runtime liviana en Docker: copia solo lo necesario para `node server.js`.
  output: "standalone",
  // El repo ya tiene su propio CLAUDE.md en la raíz: que Next no regenere uno acá.
  agentRules: false,
};

export default nextConfig;
