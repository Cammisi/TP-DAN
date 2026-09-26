package com.tpnube.match_ingestion_service.repository;

import com.tpnube.match_ingestion_service.domain.Match;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MatchRepository extends MongoRepository<Match, String> {

    List<Match> findAllByOrderByScheduledAtAsc();

    List<Match> findByHomeTeamIdOrAwayTeamIdOrderByScheduledAtAsc(String homeTeamId, String awayTeamId);
}
