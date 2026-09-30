import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";

const eslintConfig = defineConfig([
  ...nextVitals,
  {
    rules: {
      // El proyecto usa "cargar datos en useEffect al montar" a propósito (CLAUDE.md:
      // no sumar SWR/React Query sin justificarlo). Esta regla marca ese patrón como
      // error por defecto; acá queda como warning en vez de deshabilitarla del todo.
      "react-hooks/set-state-in-effect": "warn",
    },
  },
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
]);

export default eslintConfig;
