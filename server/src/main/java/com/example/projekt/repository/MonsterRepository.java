package com.example.projekt.repository;

import com.example.projekt.model.Game;
import com.example.projekt.model.Monster;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonsterRepository extends JpaRepository<Monster,Integer> {
}
