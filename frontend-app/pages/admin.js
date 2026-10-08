import { useCallback, useEffect, useMemo, useState } from "react";
import Head from "next/head";
import RequireAuth from "@/components/RequireAuth";
import AppLayout from "@/components/AppLayout";
import {
  getMatches,
  getCatalogTeams,
  getCatalogLeagues,
  ingestMatchEvent,
  createMatch,
  createCatalogTeam,
  createCatalogLeague,
  getAlertsStatus,
} from "@/lib/api";
import styles from "@/styles/Admin.module.css";

const CUSTOM_TYPE = "CUSTOM";
// Sentinels para "escribir nuevo" en vez de elegir del catálogo (T-76), en equipo y en liga.
const NEW_TEAM = "__NEW__";
const NEW_LEAGUE = "__NEW__";

const EVENT_TYPES = [
  { value: "MATCH_START", label: "Inicio del partido" },
  { value: "GOAL", label: "Gol" },
  { value: "CARD", label: "Tarjeta" },
  { value: "MATCH_END", label: "Fin del partido" },
  { value: CUSTOM_TYPE, label: "Otro (texto libre)" },
];

// Minúsculas y sin tildes, para que "colon" encuentre "Colón".
function normalize(text) {
  return text.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

const MONITORING_LINKS = [
  { href: "http://localhost:8761", label: "Eureka" },
  { href: "http://localhost:8090", label: "Kafka UI" },
  { href: "http://localhost:3001", label: "Grafana" },
];

function AdminContent() {
  const [matches, setMatches] = useState([]);
  const [teamNames, setTeamNames] = useState({});
  const [catalogTeams, setCatalogTeams] = useState([]);
  const [leagues, setLeagues] = useState([]);
  const [matchId, setMatchId] = useState("");
  const [matchQuery, setMatchQuery] = useState("");

  const [newHomeTeamId, setNewHomeTeamId] = useState("");
  const [newHomeTeamName, setNewHomeTeamName] = useState("");
  const [newHomeLeagueId, setNewHomeLeagueId] = useState("");
  const [newHomeLeagueName, setNewHomeLeagueName] = useState("");
  const [newAwayTeamId, setNewAwayTeamId] = useState("");
  const [newAwayTeamName, setNewAwayTeamName] = useState("");
  const [newAwayLeagueId, setNewAwayLeagueId] = useState("");
  const [newAwayLeagueName, setNewAwayLeagueName] = useState("");
  const [createMatchError, setCreateMatchError] = useState(null);
  const [createMatchResult, setCreateMatchResult] = useState(null);
  const [isCreatingMatch, setIsCreatingMatch] = useState(false);
  const [type, setType] = useState("GOAL");
  const [customType, setCustomType] = useState("");
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

  const loadMatches = useCallback(() => getMatches().then(setMatches), []);

  // Separado de loadMatches: crear un equipo nuevo (T-76) solo necesita refrescar esto.
  const loadCatalogTeams = useCallback(
    () =>
      getCatalogTeams().then((teamsResult) => {
        setCatalogTeams(teamsResult);
        const names = {};
        for (const team of teamsResult) names[team.id] = team.name;
        setTeamNames(names);
      }),
    []
  );

  useEffect(() => {
    Promise.all([getMatches(), loadCatalogTeams(), getCatalogLeagues()])
      .then(([matchesResult, , leaguesResult]) => {
        setMatches(matchesResult);
        if (matchesResult.length > 0) setMatchId(matchesResult[0].id);
        setLeagues(leaguesResult);
        if (leaguesResult.length > 0) {
          setNewHomeLeagueId(leaguesResult[0].id);
          setNewAwayLeagueId(leaguesResult[0].id);
        }
      })
      .catch((err) => setSubmitError(err.message));
  }, [loadCatalogTeams]);

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

  const matchLabel = (match) =>
    `${teamNames[match.homeTeamId] ?? match.homeTeamId} vs ${teamNames[match.awayTeamId] ?? match.awayTeamId} (${match.status})`;

  function filterMatches(query) {
    const q = normalize(query.trim());
    if (!q) return matches;
    return matches.filter(
      (match) => normalize(matchLabel(match)).includes(q) || match.id.toLowerCase().includes(q)
    );
  }

  const filteredMatches = filterMatches(matchQuery);

  function handleMatchSearch(query) {
    setMatchQuery(query);
    const visible = filterMatches(query);
    // Si el partido elegido quedó oculto, no dejar que el formulario lo siga enviando.
    if (!visible.some((match) => match.id === matchId)) {
      setMatchId(visible[0]?.id ?? "");
    }
  }

  const teamOptions = useMemo(() => {
    if (!selectedMatch) return [];
    return [selectedMatch.homeTeamId, selectedMatch.awayTeamId].map((id) => ({
      id,
      name: teamNames[id] ?? id,
    }));
  }, [selectedMatch, teamNames]);

  // Si eligieron "Otro" en la liga, primero hay que crearla para tener un leagueId real.
  async function resolveLeagueId(selectedLeagueId, typedLeagueName) {
    if (selectedLeagueId !== NEW_LEAGUE) return selectedLeagueId;
    const league = await createCatalogLeague({ name: typedLeagueName.trim() });
    return league.id;
  }

  // Si eligieron "Otro" en el equipo, primero hay que crearlo en el catálogo para tener su id
  // numérico (T-77) — match-ingestion no tiene catálogo propio (D-1), no puede crearlo él.
  async function resolveTeam(selectedId, typedName, leagueId, typedLeagueName) {
    if (selectedId !== NEW_TEAM) {
      return { id: selectedId, name: teamNames[selectedId] ?? selectedId };
    }
    const resolvedLeagueId = await resolveLeagueId(leagueId, typedLeagueName);
    const team = await createCatalogTeam({ name: typedName.trim(), leagueId: resolvedLeagueId });
    return { id: team.id, name: team.name };
  }

  async function handleCreateMatch(event) {
    event.preventDefault();
    setCreateMatchError(null);
    setCreateMatchResult(null);
    setIsCreatingMatch(true);
    try {
      const home = await resolveTeam(newHomeTeamId, newHomeTeamName, newHomeLeagueId, newHomeLeagueName);
      const away = await resolveTeam(newAwayTeamId, newAwayTeamName, newAwayLeagueId, newAwayLeagueName);
      if (home.id === away.id) {
        throw new Error("El equipo local y el visitante no pueden ser el mismo");
      }
      const created = await createMatch({ homeTeamId: home.id, awayTeamId: away.id });
      await Promise.all([loadMatches(), loadCatalogTeams()]);
      setMatchId(created.id);
      setMatchQuery("");
      setCreateMatchResult(`${home.name} vs ${away.name}`);
      setNewHomeTeamId("");
      setNewHomeTeamName("");
      setNewHomeLeagueName("");
      setNewAwayTeamId("");
      setNewAwayTeamName("");
      setNewAwayLeagueName("");
    } catch (err) {
      setCreateMatchError(err.message);
    } finally {
      setIsCreatingMatch(false);
    }
  }

  // Qué campos pide cada tipo (contrato T-01, sección 3.1). "Otro" es solo de la UI:
  // se manda como texto libre en el campo type.
  const isCustom = type === CUSTOM_TYPE;
  const needsTeam = type !== "MATCH_START" && type !== "MATCH_END";
  const needsPlayer = type === "GOAL" || type === "CARD";
  const needsScores = type === "GOAL" || type === "MATCH_END";

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitError(null);
    setSubmitResult(null);
    setIsSubmitting(true);
    try {
      const dto = {
        matchId,
        teamId: needsTeam ? teamId : null,
        type: isCustom ? customType.trim() : type,
        minute: Number(minute),
        description,
        playerName: needsPlayer ? playerName : null,
        cardType: type === "CARD" ? cardType : null,
        homeScore: needsScores ? Number(homeScore) : null,
        awayScore: needsScores ? Number(awayScore) : null,
        occurredAt: new Date().toISOString(),
      };
      const result = await ingestMatchEvent(matchId, dto);
      setSubmitResult(result.eventId);
      loadStatus();
      // El evento cambia el status y el marcador del partido, que se muestran en el selector.
      await loadMatches();
    } catch (err) {
      setSubmitError(err.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  // Si es "Otro", alcanza con el nombre tipeado (y, si la liga también es "Otro", su nombre).
  // Si los dos lados son "Otro", no se puede comparar el id todavía (se crea al enviar):
  // ese caso lo valida handleCreateMatch después de resolver ambos equipos.
  const homeLeagueReady = newHomeLeagueId === NEW_LEAGUE ? newHomeLeagueName.trim() : newHomeLeagueId;
  const awayLeagueReady = newAwayLeagueId === NEW_LEAGUE ? newAwayLeagueName.trim() : newAwayLeagueId;
  const homeTeamReady =
    newHomeTeamId === NEW_TEAM ? newHomeTeamName.trim() && homeLeagueReady : newHomeTeamId;
  const awayTeamReady =
    newAwayTeamId === NEW_TEAM ? newAwayTeamName.trim() && awayLeagueReady : newAwayTeamId;
  const sameExistingTeam =
    newHomeTeamId !== NEW_TEAM && newAwayTeamId !== NEW_TEAM && newHomeTeamId === newAwayTeamId;
  const canCreateMatch = homeTeamReady && awayTeamReady && !sameExistingTeam;

  return (
    <AppLayout title="Panel de administrador">
      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Crear partido</h2>
        <form onSubmit={handleCreateMatch} className={styles.form}>
          <div className={styles.field}>
            <label htmlFor="newHomeTeamId">Equipo local</label>
            <select
              id="newHomeTeamId"
              value={newHomeTeamId}
              onChange={(e) => setNewHomeTeamId(e.target.value)}
              required
            >
              <option value="">Elegir…</option>
              {catalogTeams.map((team) => (
                <option key={team.id} value={team.id}>
                  {team.name}
                </option>
              ))}
              <option value={NEW_TEAM}>Otro (escribir nombre)</option>
            </select>
          </div>

          {newHomeTeamId === NEW_TEAM && (
            <>
              <div className={styles.field}>
                <label htmlFor="newHomeTeamName">Nombre del equipo local</label>
                <input
                  id="newHomeTeamName"
                  type="text"
                  placeholder="Ej: PSG"
                  required
                  value={newHomeTeamName}
                  onChange={(e) => setNewHomeTeamName(e.target.value)}
                />
              </div>
              <div className={styles.field}>
                <label htmlFor="newHomeLeagueId">Liga del equipo local</label>
                <select
                  id="newHomeLeagueId"
                  value={newHomeLeagueId}
                  onChange={(e) => setNewHomeLeagueId(e.target.value)}
                  required
                >
                  {leagues.map((league) => (
                    <option key={league.id} value={league.id}>
                      {league.name}
                    </option>
                  ))}
                  <option value={NEW_LEAGUE}>Otro (escribir nombre)</option>
                </select>
              </div>
              {newHomeLeagueId === NEW_LEAGUE && (
                <div className={styles.field}>
                  <label htmlFor="newHomeLeagueName">Nombre de la liga local</label>
                  <input
                    id="newHomeLeagueName"
                    type="text"
                    placeholder="Ej: Ligue 1 Francia"
                    required
                    value={newHomeLeagueName}
                    onChange={(e) => setNewHomeLeagueName(e.target.value)}
                  />
                </div>
              )}
            </>
          )}

          <div className={styles.field}>
            <label htmlFor="newAwayTeamId">Equipo visitante</label>
            <select
              id="newAwayTeamId"
              value={newAwayTeamId}
              onChange={(e) => setNewAwayTeamId(e.target.value)}
              required
            >
              <option value="">Elegir…</option>
              {catalogTeams.map((team) => (
                <option key={team.id} value={team.id}>
                  {team.name}
                </option>
              ))}
              <option value={NEW_TEAM}>Otro (escribir nombre)</option>
            </select>
          </div>

          {newAwayTeamId === NEW_TEAM && (
            <>
              <div className={styles.field}>
                <label htmlFor="newAwayTeamName">Nombre del equipo visitante</label>
                <input
                  id="newAwayTeamName"
                  type="text"
                  placeholder="Ej: Real Madrid"
                  required
                  value={newAwayTeamName}
                  onChange={(e) => setNewAwayTeamName(e.target.value)}
                />
              </div>
              <div className={styles.field}>
                <label htmlFor="newAwayLeagueId">Liga del equipo visitante</label>
                <select
                  id="newAwayLeagueId"
                  value={newAwayLeagueId}
                  onChange={(e) => setNewAwayLeagueId(e.target.value)}
                  required
                >
                  {leagues.map((league) => (
                    <option key={league.id} value={league.id}>
                      {league.name}
                    </option>
                  ))}
                  <option value={NEW_LEAGUE}>Otro (escribir nombre)</option>
                </select>
              </div>
              {newAwayLeagueId === NEW_LEAGUE && (
                <div className={styles.field}>
                  <label htmlFor="newAwayLeagueName">Nombre de la liga visitante</label>
                  <input
                    id="newAwayLeagueName"
                    type="text"
                    placeholder="Ej: Ligue 1 Francia"
                    required
                    value={newAwayLeagueName}
                    onChange={(e) => setNewAwayLeagueName(e.target.value)}
                  />
                </div>
              )}
            </>
          )}

          <button type="submit" className={styles.submit} disabled={isCreatingMatch || !canCreateMatch}>
            {isCreatingMatch ? "Creando…" : "Crear partido"}
          </button>
        </form>

        {createMatchError && <p className={styles.error}>{createMatchError}</p>}
        {createMatchResult && <p className={styles.success}>Partido creado: {createMatchResult}</p>}
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Simular evento</h2>
        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.fieldWide}>
            <label htmlFor="matchSearch">Partido</label>
            <input
              id="matchSearch"
              type="search"
              placeholder="Buscar por equipo o id…"
              value={matchQuery}
              onChange={(e) => handleMatchSearch(e.target.value)}
            />
            <select
              id="match"
              size={Math.min(Math.max(filteredMatches.length, 2), 6)}
              value={matchId}
              onChange={(e) => setMatchId(e.target.value)}
              required
            >
              {filteredMatches.map((match) => (
                <option key={match.id} value={match.id}>
                  {matchLabel(match)}
                </option>
              ))}
            </select>
            {filteredMatches.length === 0 && (
              <span className={styles.muted}>Ningún partido coincide con la búsqueda.</span>
            )}
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

          {isCustom && (
            <div className={styles.field}>
              <label htmlFor="customType">Nombre del evento</label>
              <input
                id="customType"
                type="text"
                maxLength={40}
                placeholder="Ej: Penal, Suspensión"
                required
                value={customType}
                onChange={(e) => setCustomType(e.target.value)}
              />
            </div>
          )}

          {needsTeam && (
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

          {needsPlayer && (
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

          {needsScores && (
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
                  <li key={`${e.eventId}-${e.teamId}`}>
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
                  <li key={`${e.eventId}-${e.teamId}`}>
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
