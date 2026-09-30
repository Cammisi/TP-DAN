import { createContext, useContext, useEffect, useState } from "react";
import { useRouter } from "next/router";
import * as api from "@/lib/api";
import { saveSession, loadSession, clearSession } from "@/lib/session";

const SessionContext = createContext(null);

export function SessionProvider({ children }) {
  const [session, setSession] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const router = useRouter();

  // La sesión vive en localStorage (solo existe en el browser), así que se carga
  // después del primer render para no desincronizar el HTML del servidor y del cliente.
  useEffect(() => {
    setSession(loadSession());
    setIsLoading(false);
  }, []);

  async function login(email, password) {
    const result = await api.login(email, password);
    // El contrato de POST /api/fans/auth/login no devuelve el email (solo
    // token/fanId/role): lo agregamos acá con el que el usuario ya tipeó.
    const session = { ...result, email };
    saveSession(session);
    setSession(session);
    return session;
  }

  function logout() {
    clearSession();
    setSession(null);
    router.push("/login");
  }

  return (
    <SessionContext.Provider value={{ session, isLoading, login, logout }}>
      {children}
    </SessionContext.Provider>
  );
}

export function useSession() {
  const context = useContext(SessionContext);
  if (!context) {
    throw new Error("useSession tiene que usarse dentro de <SessionProvider>");
  }
  return context;
}
