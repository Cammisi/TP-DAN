import { loadSession, clearSession } from "./session";

const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL;

// Shape fijo por el contrato (docs/explicacion_contratos_api.md, sección 1):
// { timestamp, status, error, message, path }. `code` es el campo `error` (UPPER_SNAKE_CASE).
export class ApiError extends Error {
  constructor(status, code, message) {
    super(message || code || `Error HTTP ${status}`);
    this.status = status;
    this.code = code;
  }
}

// Único punto de acceso a cloud-gateway: todas las funciones de este archivo pasan por acá.
// `auth: false` es solo para register/login (todavía no hay sesión que adjuntar).
async function apiFetch(path, { method = "GET", body, auth = true } = {}) {
  const session = auth ? loadSession() : null;

  const headers = { "Content-Type": "application/json" };
  if (session?.token) {
    headers.Authorization = `Bearer ${session.token}`;
  }

  const response = await fetch(`${GATEWAY_URL}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (response.status === 204) {
    return null;
  }

  const data = await response.json().catch(() => null);

  if (!response.ok) {
    // 401 con un token que mandamos nosotros = sesión vencida/inválida, no un simple
    // error de negocio: se limpia y se redirige, en vez de dejar que cada pantalla lo maneje.
    if (response.status === 401 && session?.token && typeof window !== "undefined") {
      clearSession();
      // apiFetch es un módulo plano, no un componente: no hay useRouter() disponible acá.
      // eslint-disable-next-line @next/next/no-location-assign-relative-destination
      window.location.href = "/login";
    }
    throw new ApiError(response.status, data?.error, data?.message);
  }

  return data;
}

// --- Auth (público) ---
export function register(email, password) {
  return apiFetch("/api/fans/auth/register", {
    method: "POST",
    body: { email, password },
    auth: false,
  });
}

export function login(email, password) {
  return apiFetch("/api/fans/auth/login", {
    method: "POST",
    body: { email, password },
    auth: false,
  });
}

// --- Suscripciones ---
export function getSubscriptions() {
  return apiFetch("/api/fans/subscriptions");
}

export function subscribe(teamId) {
  return apiFetch("/api/fans/subscriptions", { method: "POST", body: { teamId } });
}

export function unsubscribe(teamId) {
  return apiFetch(`/api/fans/subscriptions/${encodeURIComponent(teamId)}`, {
    method: "DELETE",
  });
}

// --- Notificaciones ---
export function getNotifications() {
  return apiFetch("/api/fans/me/notifications");
}

// --- Catálogo ---
export function getCatalogLeagues() {
  return apiFetch("/api/catalog/leagues");
}

export function getCatalogTeams({ name, leagueId } = {}) {
  const params = new URLSearchParams();
  if (name) params.set("name", name);
  if (leagueId) params.set("leagueId", leagueId);
  const query = params.toString();
  return apiFetch(`/api/catalog/teams${query ? `?${query}` : ""}`);
}

// --- Partidos y eventos ---
export function getMatches({ teamId } = {}) {
  const query = teamId ? `?teamId=${encodeURIComponent(teamId)}` : "";
  return apiFetch(`/api/matches${query}`);
}

export function getMatchEvents(matchId) {
  return apiFetch(`/api/matches/${encodeURIComponent(matchId)}/events`);
}

export function ingestMatchEvent(matchId, event) {
  return apiFetch(`/api/matches/${encodeURIComponent(matchId)}/events`, {
    method: "POST",
    body: event,
  });
}

export function createMatch(match) {
  return apiFetch("/api/matches", { method: "POST", body: match });
}

// --- Estado del despachador (admin) ---
export function getAlertsStatus() {
  return apiFetch("/api/alerts/status");
}
