import { useEffect, useMemo, useRef, useState } from "react";
import Head from "next/head";
import RequireAuth from "@/components/RequireAuth";
import AppLayout from "@/components/AppLayout";
import { getSubscriptions, getCatalogTeams, subscribe, unsubscribe } from "@/lib/api";
import styles from "@/styles/Subscriptions.module.css";

function groupByLeague(subscriptions) {
  const groups = new Map();
  for (const sub of subscriptions) {
    if (!groups.has(sub.leagueName)) groups.set(sub.leagueName, []);
    groups.get(sub.leagueName).push(sub);
  }
  return Array.from(groups.entries());
}

function SubscriptionsContent() {
  const [subscriptions, setSubscriptions] = useState([]);
  const [teams, setTeams] = useState([]);
  const [searchTerm, setSearchTerm] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [pendingTeamId, setPendingTeamId] = useState(null);
  const debounceRef = useRef(null);

  useEffect(() => {
    async function loadInitial() {
      try {
        const [subsResult, teamsResult] = await Promise.all([
          getSubscriptions(),
          getCatalogTeams(),
        ]);
        setSubscriptions(subsResult);
        setTeams(teamsResult);
      } catch (err) {
        setError(err.message);
      } finally {
        setIsLoading(false);
      }
    }
    loadInitial();
  }, []);

  useEffect(() => {
    // Búsqueda "en vivo" sin pegarle al gateway en cada tecla.
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(async () => {
      try {
        const result = await getCatalogTeams({ name: searchTerm || undefined });
        setTeams(result);
      } catch (err) {
        setError(err.message);
      }
    }, 300);
    return () => clearTimeout(debounceRef.current);
  }, [searchTerm]);

  const subscribedIds = useMemo(
    () => new Set(subscriptions.map((sub) => sub.teamId)),
    [subscriptions]
  );

  async function handleToggle(team) {
    setError(null);
    setPendingTeamId(team.id);
    try {
      if (subscribedIds.has(team.id)) {
        await unsubscribe(team.id);
        setSubscriptions((prev) => prev.filter((sub) => sub.teamId !== team.id));
      } else {
        await subscribe(team.id);
        setSubscriptions((prev) => [
          ...prev,
          {
            teamId: team.id,
            teamName: team.name,
            leagueId: team.leagueId,
            leagueName: team.leagueName,
          },
        ]);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setPendingTeamId(null);
    }
  }

  const leagueGroups = groupByLeague(subscriptions);

  return (
    <AppLayout title="Suscripciones">
      {error && <p className={styles.error}>{error}</p>}

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Buscar equipos</h2>
        <input
          type="search"
          placeholder="Buscar por nombre…"
          value={searchTerm}
          onChange={(event) => setSearchTerm(event.target.value)}
          className={styles.search}
        />
        {isLoading ? (
          <p className={styles.muted}>Cargando…</p>
        ) : teams.length === 0 ? (
          <p className={styles.muted}>No se encontraron equipos.</p>
        ) : (
          <ul className={styles.teamList}>
            {teams.map((team) => {
              const isSubscribed = subscribedIds.has(team.id);
              return (
                <li key={team.id} className={styles.teamRow}>
                  <div>
                    <div className={styles.teamName}>{team.name}</div>
                    <div className={styles.leagueName}>{team.leagueName}</div>
                  </div>
                  <button
                    type="button"
                    className={isSubscribed ? styles.toggleOn : styles.toggleOff}
                    disabled={pendingTeamId === team.id}
                    onClick={() => handleToggle(team)}
                  >
                    {isSubscribed ? "Siguiendo" : "Seguir"}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Mis suscripciones</h2>
        {!isLoading && subscriptions.length === 0 && (
          <p className={styles.muted}>Todavía no seguís ningún equipo.</p>
        )}
        {leagueGroups.map(([leagueName, subs]) => (
          <div key={leagueName} className={styles.leagueGroup}>
            <h3 className={styles.leagueHeading}>{leagueName}</h3>
            <ul className={styles.teamList}>
              {subs.map((sub) => (
                <li key={sub.teamId} className={styles.teamRow}>
                  <div className={styles.teamName}>{sub.teamName}</div>
                  <button
                    type="button"
                    className={styles.toggleOn}
                    disabled={pendingTeamId === sub.teamId}
                    onClick={() =>
                      handleToggle({
                        id: sub.teamId,
                        name: sub.teamName,
                        leagueId: sub.leagueId,
                        leagueName: sub.leagueName,
                      })
                    }
                  >
                    Dejar de seguir
                  </button>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </section>
    </AppLayout>
  );
}

export default function Subscriptions() {
  return (
    <RequireAuth>
      <Head>
        <title>Suscripciones — ¿Quién Juega?</title>
      </Head>
      <SubscriptionsContent />
    </RequireAuth>
  );
}
