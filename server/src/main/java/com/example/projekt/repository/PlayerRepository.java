package com.example.projekt.repository;

import com.example.projekt.model.Game;
import com.example.projekt.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player,Integer> {
}
