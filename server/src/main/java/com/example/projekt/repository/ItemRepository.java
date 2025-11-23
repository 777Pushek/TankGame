package com.example.projekt.repository;

import com.example.projekt.model.Game;
import com.example.projekt.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item,Integer> {

}
