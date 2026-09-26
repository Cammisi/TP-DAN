package com.tpnube.match_ingestion_service.repository;

import com.tpnube.match_ingestion_service.domain.MatchEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MatchEventRepository extends MongoRepository<MatchEvent, String> {

    List<MatchEvent> findAllByMatchIdOrderByOccurredAtAsc(String matchId);
}
