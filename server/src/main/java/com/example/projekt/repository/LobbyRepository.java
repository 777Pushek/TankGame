package com.example.projekt.repository;

import com.example.projekt.model.Lobby;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LobbyRepository extends JpaRepository<Lobby, Integer> {
    
    List<Lobby> findAllByHostIdIsNotNull();

    
    boolean existsByName(String name);
}
