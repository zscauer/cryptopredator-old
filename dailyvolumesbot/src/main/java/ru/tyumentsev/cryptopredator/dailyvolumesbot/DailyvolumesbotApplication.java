package ru.tyumentsev.cryptopredator.dailyvolumesbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.prometheus.client.hotspot.DefaultExports;

@SpringBootApplication
public class DailyvolumesbotApplication {

	public static void main(String[] args) {
		DefaultExports.initialize();
		SpringApplication.run(DailyvolumesbotApplication.class, args);
	}

}
