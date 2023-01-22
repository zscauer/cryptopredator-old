package ru.tyumentsev.cryptopredator.datakeeper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DatakeeperApplication {
    public static void main(String[] args) {
        SpringApplication.run(DatakeeperApplication.class, args);

        System.out.println("Hello world!");
    }
}