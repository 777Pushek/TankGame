package com.example.projekt.repository;

import com.example.projekt.model.Game;
import com.example.projekt.model.Tank;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TankRepository extends JpaRepository<Tank,Integer> {
}
