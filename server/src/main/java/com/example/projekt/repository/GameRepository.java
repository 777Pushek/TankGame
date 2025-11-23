package com.example.projekt.repository;

import com.example.projekt.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, Integer> {
    Game findGameById(int id);


}
