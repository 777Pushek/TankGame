package com.example.projekt.controller;

import com.example.projekt.model.Game;
import com.example.projekt.model.Player;
import com.example.projekt.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/")
@AllArgsConstructor
public class HomeController {

    private final GameRepository gameRepository;
    private final ItemRepository itemRepository;
    private final MonsterRepository monsterRepository;
    private final PlayerRepository playerRepository;
    private final TankRepository tankRepository;
    private final UserRepository userRepository;

    @GetMapping("/game/{id}")
    public Game findGameById( @PathVariable int id){
        return gameRepository.findGameById(id);
    }

    @PostMapping("/player")
    public Player addPlayer(@RequestBody Player player){
        return playerRepository.save(player);
    }
    @PostMapping("/game")
    public Game addGame(@RequestBody Game game){
        return gameRepository.save(game);
    }



}
