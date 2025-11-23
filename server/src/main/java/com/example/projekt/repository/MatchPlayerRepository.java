package com.example.projekt.repository;

import com.example.projekt.model.MatchPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Repository
public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, Integer> {
    @Query("select mp from MatchPlayer mp where mp.userId = :userId order by mp.match.id desc")
    List<MatchPlayer> findByUserIdOrderByMatchStartDesc(@Param("userId") int userId);

    @Query("select mp from MatchPlayer mp where mp.userId = :userId order by mp.match.id desc")
    Page<MatchPlayer> findByUserIdOrderByMatchStartDesc(@Param("userId") int userId, Pageable pageable);
    List<MatchPlayer> findByMatch_Id(int matchId);

    @Query("select mp.tankType from MatchPlayer mp where mp.userId = :userId group by mp.tankType order by count(mp) desc")
    List<String> findMostUsedTanksByUser(@Param("userId") int userId, org.springframework.data.domain.Pageable pageable);
}
