const STORAGE_KEY = "qj_session";

// Next.js renderiza primero en el servidor, donde no existe localStorage.
function isBrowser() {
  return typeof window !== "undefined";
}

export function saveSession(session) {
  if (!isBrowser()) return;
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function loadSession() {
  if (!isBrowser()) return null;
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function clearSession() {
  if (!isBrowser()) return;
  window.localStorage.removeItem(STORAGE_KEY);
}
