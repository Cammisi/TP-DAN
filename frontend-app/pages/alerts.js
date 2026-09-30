import { useCallback, useEffect, useState } from "react";
import Head from "next/head";
import Link from "next/link";
import RequireAuth from "@/components/RequireAuth";
import AppLayout from "@/components/AppLayout";
import { getNotifications } from "@/lib/api";
import styles from "@/styles/Alerts.module.css";

const dateFormatter = new Intl.DateTimeFormat("es-AR", {
  dateStyle: "short",
  timeStyle: "short",
});

function AlertsContent() {
  const [notifications, setNotifications] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await getNotifications();
      setNotifications(result);
    } catch (err) {
      setError(err.message);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <AppLayout title="Alertas">
      <div className={styles.toolbar}>
        <button type="button" className={styles.refresh} onClick={load} disabled={isLoading}>
          {isLoading ? "Actualizando…" : "Actualizar"}
        </button>
      </div>

      {error && <p className={styles.error}>{error}</p>}

      {!isLoading && notifications.length === 0 && !error && (
        <p className={styles.empty}>
          Todavía no tenés alertas. Seguí equipos desde{" "}
          <Link href="/subscriptions">Suscripciones</Link> para empezar a recibirlas.
        </p>
      )}

      <ul className={styles.list}>
        {notifications.map((notification) => (
          <li key={notification.id} className={styles.item}>
            <div className={styles.itemHeader}>
              <span className={styles.date}>
                {dateFormatter.format(new Date(notification.createdAt))}
              </span>
            </div>
            <p className={styles.message}>{notification.message}</p>
          </li>
        ))}
      </ul>
    </AppLayout>
  );
}

export default function Alerts() {
  return (
    <RequireAuth>
      <Head>
        <title>Alertas — ¿Quién Juega?</title>
      </Head>
      <AlertsContent />
    </RequireAuth>
  );
}
