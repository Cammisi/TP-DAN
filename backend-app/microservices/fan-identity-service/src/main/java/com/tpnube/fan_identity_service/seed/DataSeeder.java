package com.tpnube.fan_identity_service.seed;

import com.tpnube.fan_identity_service.domain.Fan;
import com.tpnube.fan_identity_service.domain.FanRole;
import com.tpnube.fan_identity_service.domain.League;
import com.tpnube.fan_identity_service.domain.Team;
import com.tpnube.fan_identity_service.repository.FanRepository;
import com.tpnube.fan_identity_service.repository.LeagueRepository;
import com.tpnube.fan_identity_service.repository.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final FanRepository fanRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(LeagueRepository leagueRepository,
                      TeamRepository teamRepository,
                      FanRepository fanRepository,
                      PasswordEncoder passwordEncoder) {
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
        this.fanRepository = fanRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedLeagues();
        seedTeams();
        seedFans();
        log.info("Datos semilla cargados correctamente");
    }

    private void seedLeagues() {
        saveLeagueIfAbsent("liga-profesional", "Liga Profesional de Fútbol");
        saveLeagueIfAbsent("premier-league", "Premier League");
    }

    private void seedTeams() {
        League ligaProfesional = leagueRepository.findById("liga-profesional").orElseThrow();
        League premierLeague = leagueRepository.findById("premier-league").orElseThrow();

        // Orden fijo: con la base recién creada, esto deja los ids 1..7 en este mismo orden
        // (T-77). match-ingestion-service los referencia tal cual en sus partidos semilla.
        saveTeamIfAbsent("Colón", ligaProfesional);
        saveTeamIfAbsent("Unión", ligaProfesional);
        saveTeamIfAbsent("River Plate", ligaProfesional);
        saveTeamIfAbsent("Boca Juniors", ligaProfesional);
        saveTeamIfAbsent("Racing Club", ligaProfesional);
        saveTeamIfAbsent("Liverpool FC", premierLeague);
        saveTeamIfAbsent("Manchester City", premierLeague);
    }

    private void seedFans() {
        saveFanIfAbsent("admin@quienjuega.com", "admin123", FanRole.ADMIN);
        saveFanIfAbsent("fan1@quienjuega.com", "fan123", FanRole.FAN);
        saveFanIfAbsent("fan2@quienjuega.com", "fan123", FanRole.FAN);
    }

    // --- Helpers idempotentes ---

    private void saveLeagueIfAbsent(String id, String name) {
        if (leagueRepository.existsById(id)) {
            return;
        }
        leagueRepository.save(new League(id, name));
        log.info("Liga sembrada: {}", name);
    }

    // Idempotente por nombre, no por id: el id ahora lo genera la base (T-77).
    private void saveTeamIfAbsent(String name, League league) {
        if (teamRepository.existsByName(name)) {
            return;
        }
        teamRepository.save(new Team(null, name, league));
        log.info("Equipo sembrado: {} ({})", name, league.getName());
    }

    private void saveFanIfAbsent(String email, String rawPassword, FanRole role) {
        if (fanRepository.findByEmail(email).isPresent()) {
            return;
        }
        Fan fan = new Fan();
        fan.setEmail(email);
        fan.setPassword(passwordEncoder.encode(rawPassword));
        fan.setRole(role);
        fanRepository.save(fan);
        log.info("Fan sembrado: {} ({})", email, role);
    }
}
