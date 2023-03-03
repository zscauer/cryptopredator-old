package ru.tyumentsev.cryptopredator.macsawbot;
// import what we need
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MacsawbotApplication {

	public static void main(String[] args) {
		SpringApplication.run(MacsawbotApplication.class, args);
		System.out.println("Startedddddd");

// get a RuntimeMXBean reference
		var runtimeMxBean = ManagementFactory.getRuntimeMXBean();

// get the jvm's input arguments as a list of strings
		var listOfArguments = runtimeMxBean.getInputArguments();

// print the arguments using my logger
		listOfArguments.forEach(System.out::printf);
	}

}
