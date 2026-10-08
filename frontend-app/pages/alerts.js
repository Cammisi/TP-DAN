import { useCallback, useEffect, useState } from "react";
import Head from "next/head";
import Link from "next/link";
import RequireAuth from "@/components/RequireAuth";
import AppLayout from "@/components/AppLayout";
import { getNotifications, getCatalogTeams, getMatches } from "@/lib/api";
import styles from "@/styles/Alerts.module.css";

const dateFormatter = new Intl.DateTimeFormat("es-AR", {
  dateStyle: "short",
  timeStyle: "short",
});

function AlertsContent() {
  const [notifications, setNotifications] = useState([]);
  const [teamNames, setTeamNames] = useState({});
  const [matchesById, setMatchesById] = useState({});
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      // El backend guarda solo ids: los nombres se resuelven acá con el catálogo y los partidos.
      const [notificationsResult, teamsResult, matchesResult] = await Promise.all([
        getNotifications(),
        getCatalogTeams(),
        getMatches(),
      ]);
      setNotifications(notificationsResult);
      setTeamNames(Object.fromEntries(teamsResult.map((team) => [team.id, team.name])));
      setMatchesById(Object.fromEntries(matchesResult.map((match) => [match.id, match])));
    } catch (err) {
      setError(err.message);
    } finally {
      setIsLoading(false);
    }
  }, []);

  function describeMatch(matchId) {
    const match = matchesById[matchId];
    if (!match) return null;
    return `${teamNames[match.homeTeamId] ?? match.homeTeamId} vs ${teamNames[match.awayTeamId] ?? match.awayTeamId}`;
  }

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
              <span className={styles.team}>
                {teamNames[notification.teamId] ?? notification.teamId}
              </span>
              <span className={styles.date}>
                {dateFormatter.format(new Date(notification.createdAt))}
              </span>
            </div>
            {describeMatch(notification.matchId) && (
              <p className={styles.match}>{describeMatch(notification.matchId)}</p>
            )}
            <p className={styles.message}>
              {/* El badge de arriba es el equipo que seguís; este, el que hizo el evento (D-7). */}
              {notification.eventTeamId && (
                <strong>{teamNames[notification.eventTeamId] ?? notification.eventTeamId}: </strong>
              )}
              {notification.message}
            </p>
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
