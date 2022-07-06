package ru.tyumentsev.binancetestbot.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.binance.api.client.BinanceApiClientFactory;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "applicationconfig")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApplicationConfig {
    
    String apiKey;
    String secret;
    boolean useTestnet;
    boolean useTestnetStreaming;

    @Bean
    public BinanceApiClientFactory binanceApiClientFactory() {
        // System.out.println("apiKey = " + apiKey);
        // System.out.println("secret = " + secret);
        return BinanceApiClientFactory.newInstance(apiKey, secret, useTestnet, useTestnetStreaming);
    }
}
