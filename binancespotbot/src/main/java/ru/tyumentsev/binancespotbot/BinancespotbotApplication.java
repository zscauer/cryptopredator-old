package ru.tyumentsev.binancespotbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.prometheus.client.hotspot.DefaultExports;

@SpringBootApplication
public class BinancespotbotApplication {

	public static void main(String[] args) {
		DefaultExports.initialize();
		SpringApplication.run(BinancespotbotApplication.class, args);
	}

}
