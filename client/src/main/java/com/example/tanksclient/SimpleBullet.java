package com.example.tanksclient;

import lombok.Value;

@Value
public class SimpleBullet {
    int owner;
    double x;
    double y;
    double r;
}
