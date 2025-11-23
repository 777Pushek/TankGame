package com.example.projekt;

import com.example.projekt.model.Game;
import com.example.projekt.model.Tank;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProjektApplication {

    public static void main(String[] args) {
        Tank tank1 = new Tank(1, "lekki", 80, 120, 80, 120, 80);

        SpringApplication.run(ProjektApplication.class, args);
    }

}
