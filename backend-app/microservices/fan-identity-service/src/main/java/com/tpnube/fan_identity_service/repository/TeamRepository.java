package com.tpnube.fan_identity_service.repository;

import com.tpnube.fan_identity_service.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, String> {

    // El CAST de :name a string es necesario: Postgres no puede inferir el tipo
    // de un parámetro null dentro de LOWER(CONCAT(...)) y falla con
    // "function lower(bytea) does not exist" cuando no se filtra por nombre.
    @Query("SELECT t FROM Team t JOIN FETCH t.league l " +
           "WHERE (:name IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) " +
           "AND (:leagueId IS NULL OR l.id = :leagueId) " +
           "ORDER BY t.name")
    List<Team> search(@Param("name") String name, @Param("leagueId") String leagueId);
}
