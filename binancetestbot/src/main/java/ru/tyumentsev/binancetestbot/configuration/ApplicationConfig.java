package ru.tyumentsev.binancetestbot.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "applicationconfig")
@EnableScheduling
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApplicationConfig {
    
    String apiKey;
    String secret;
    boolean useTestnet;
    boolean useTestnetStreaming;

    @Bean(name = "binanceApiClientFactory")
    public BinanceApiClientFactory binanceApiClientFactory() {
        return BinanceApiClientFactory.newInstance(apiKey, secret, useTestnet, useTestnetStreaming);
    }

    @Bean
    @DependsOn("binanceApiClientFactory")
    public BinanceApiRestClient binanceApiRestClient() {
        return binanceApiClientFactory().newRestClient();
    }

    @Bean
    @DependsOn("binanceApiClientFactory")
    public BinanceApiAsyncRestClient binanceApiAsyncRestClient() {
        return binanceApiClientFactory().newAsyncRestClient();
    }

    @Bean
    @DependsOn("binanceApiClientFactory")
    public BinanceApiWebSocketClient binanceApiWebSocketClient() {
        return binanceApiClientFactory().newWebSocketClient();
    }
}
