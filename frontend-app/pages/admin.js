import { useCallback, useEffect, useMemo, useState } from "react";
import Head from "next/head";
import RequireAuth from "@/components/RequireAuth";
import AppLayout from "@/components/AppLayout";
import { getMatches, getCatalogTeams, ingestMatchEvent, getAlertsStatus } from "@/lib/api";
import styles from "@/styles/Admin.module.css";

const EVENT_TYPES = [
  { value: "GOAL", label: "Gol" },
  { value: "CARD", label: "Tarjeta" },
  { value: "MATCH_END", label: "Fin del partido" },
];

const MONITORING_LINKS = [
  { href: "http://localhost:8761", label: "Eureka" },
  { href: "http://localhost:8090", label: "Kafka UI" },
  { href: "http://localhost:3001", label: "Grafana" },
];

function AdminContent() {
  const [matches, setMatches] = useState([]);
  const [teamNames, setTeamNames] = useState({});
  const [matchId, setMatchId] = useState("");
  const [type, setType] = useState("GOAL");
  const [teamId, setTeamId] = useState("");
  const [minute, setMinute] = useState("");
  const [description, setDescription] = useState("");
  const [playerName, setPlayerName] = useState("");
  const [cardType, setCardType] = useState("YELLOW");
  const [homeScore, setHomeScore] = useState("");
  const [awayScore, setAwayScore] = useState("");
  const [submitError, setSubmitError] = useState(null);
  const [submitResult, setSubmitResult] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [status, setStatus] = useState(null);
  const [statusError, setStatusError] = useState(null);
  const [isStatusLoading, setIsStatusLoading] = useState(true);

  const selectedMatch = matches.find((match) => match.id === matchId);

  useEffect(() => {
    Promise.all([getMatches(), getCatalogTeams()])
      .then(([matchesResult, teamsResult]) => {
        setMatches(matchesResult);
        if (matchesResult.length > 0) setMatchId(matchesResult[0].id);
        const names = {};
        for (const team of teamsResult) names[team.id] = team.name;
        setTeamNames(names);
      })
      .catch((err) => setSubmitError(err.message));
  }, []);

  const loadStatus = useCallback(async () => {
    setIsStatusLoading(true);
    setStatusError(null);
    try {
      setStatus(await getAlertsStatus());
    } catch (err) {
      setStatusError(err.message);
    } finally {
      setIsStatusLoading(false);
    }
  }, []);

  useEffect(() => {
    loadStatus();
  }, [loadStatus]);

  const teamOptions = useMemo(() => {
    if (!selectedMatch) return [];
    return [selectedMatch.homeTeamId, selectedMatch.awayTeamId].map((id) => ({
      id,
      name: teamNames[id] ?? id,
    }));
  }, [selectedMatch, teamNames]);

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitError(null);
    setSubmitResult(null);
    setIsSubmitting(true);
    try {
      const dto = {
        matchId,
        teamId: type === "MATCH_END" ? null : teamId,
        type,
        minute: Number(minute),
        description,
        playerName: type === "MATCH_END" ? null : playerName,
        cardType: type === "CARD" ? cardType : null,
        homeScore: type !== "CARD" ? Number(homeScore) : null,
        awayScore: type !== "CARD" ? Number(awayScore) : null,
        occurredAt: new Date().toISOString(),
      };
      const result = await ingestMatchEvent(matchId, dto);
      setSubmitResult(result.eventId);
      loadStatus();
    } catch (err) {
      setSubmitError(err.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AppLayout title="Panel de administrador">
      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Simular evento</h2>
        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.field}>
            <label htmlFor="match">Partido</label>
            <select id="match" value={matchId} onChange={(e) => setMatchId(e.target.value)}>
              {matches.map((match) => (
                <option key={match.id} value={match.id}>
                  {teamNames[match.homeTeamId] ?? match.homeTeamId} vs{" "}
                  {teamNames[match.awayTeamId] ?? match.awayTeamId} ({match.status})
                </option>
              ))}
            </select>
          </div>

          <div className={styles.field}>
            <label htmlFor="type">Tipo de evento</label>
            <select id="type" value={type} onChange={(e) => setType(e.target.value)}>
              {EVENT_TYPES.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>

          {type !== "MATCH_END" && (
            <div className={styles.field}>
              <label htmlFor="team">Equipo</label>
              <select id="team" value={teamId} onChange={(e) => setTeamId(e.target.value)} required>
                <option value="">Elegir…</option>
                {teamOptions.map((option) => (
                  <option key={option.id} value={option.id}>
                    {option.name}
                  </option>
                ))}
              </select>
            </div>
          )}

          <div className={styles.field}>
            <label htmlFor="minute">Minuto</label>
            <input
              id="minute"
              type="number"
              min="0"
              required
              value={minute}
              onChange={(e) => setMinute(e.target.value)}
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="description">Descripción</label>
            <input
              id="description"
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>

          {type !== "MATCH_END" && (
            <div className={styles.field}>
              <label htmlFor="playerName">Jugador</label>
              <input
                id="playerName"
                type="text"
                required
                value={playerName}
                onChange={(e) => setPlayerName(e.target.value)}
              />
            </div>
          )}

          {type === "CARD" && (
            <div className={styles.field}>
              <label htmlFor="cardType">Color</label>
              <select id="cardType" value={cardType} onChange={(e) => setCardType(e.target.value)}>
                <option value="YELLOW">Amarilla</option>
                <option value="RED">Roja</option>
              </select>
            </div>
          )}

          {type !== "CARD" && (
            <>
              <div className={styles.field}>
                <label htmlFor="homeScore">Marcador local</label>
                <input
                  id="homeScore"
                  type="number"
                  min="0"
                  required
                  value={homeScore}
                  onChange={(e) => setHomeScore(e.target.value)}
                />
              </div>
              <div className={styles.field}>
                <label htmlFor="awayScore">Marcador visitante</label>
                <input
                  id="awayScore"
                  type="number"
                  min="0"
                  required
                  value={awayScore}
                  onChange={(e) => setAwayScore(e.target.value)}
                />
              </div>
            </>
          )}

          <button type="submit" className={styles.submit} disabled={isSubmitting || !matchId}>
            {isSubmitting ? "Enviando…" : "Ingestar evento"}
          </button>
        </form>

        {submitError && <p className={styles.error}>{submitError}</p>}
        {submitResult && (
          <p className={styles.success}>Evento creado: {submitResult}</p>
        )}
      </section>

      <section className={styles.section}>
        <div className={styles.statusHeader}>
          <h2 className={styles.sectionTitle}>Estado del despachador</h2>
          <button type="button" className={styles.refresh} onClick={loadStatus} disabled={isStatusLoading}>
            {isStatusLoading ? "Actualizando…" : "Actualizar"}
          </button>
        </div>

        {statusError && <p className={styles.error}>{statusError}</p>}

        {status && (
          <>
            <p>
              Circuito: <span className={styles.badge}>{status.circuitState}</span>
            </p>
            <h3 className={styles.subTitle}>Últimos eventos despachados</h3>
            {status.lastProcessedEvents.length === 0 ? (
              <p className={styles.muted}>Sin eventos procesados todavía.</p>
            ) : (
              <ul className={styles.eventList}>
                {status.lastProcessedEvents.map((e) => (
                  <li key={e.eventId}>
                    {teamNames[e.teamId] ?? e.teamId} — {e.subscriberCount} fan(s) notificados
                  </li>
                ))}
              </ul>
            )}
            <h3 className={styles.subTitle}>No despachados</h3>
            {status.undispatchedEvents.length === 0 ? (
              <p className={styles.muted}>Ninguno.</p>
            ) : (
              <ul className={styles.eventList}>
                {status.undispatchedEvents.map((e) => (
                  <li key={e.eventId}>
                    {teamNames[e.teamId] ?? e.teamId} — {e.reason}
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Monitoreo</h2>
        <div className={styles.links}>
          {MONITORING_LINKS.map((link) => (
            <a key={link.href} href={link.href} target="_blank" rel="noreferrer" className={styles.link}>
              {link.label} ↗
            </a>
          ))}
        </div>
      </section>
    </AppLayout>
  );
}

export default function Admin() {
  return (
    <RequireAuth adminOnly>
      <Head>
        <title>Admin — ¿Quién Juega?</title>
      </Head>
      <AdminContent />
    </RequireAuth>
  );
}
