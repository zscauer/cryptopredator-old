package ru.tyumentsev.cryptopredator.statekeeper.configuration;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.TimeUnit;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "applicationconfig")
@EnableScheduling
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApplicationConfig {

    final OkHttpClient sharedClient;

    String apiKey;
    String secret;
    boolean useTestnet;
    boolean useTestnetStreaming;

    {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequestsPerHost(300);
        dispatcher.setMaxRequests(300);
        sharedClient = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
//                .pingInterval(20, TimeUnit.SECONDS)
//                .connectionPool(new ConnectionPool(5, 3, TimeUnit.MINUTES))
                .callTimeout(60, TimeUnit.SECONDS)
//                .connectionPool(new ConnectionPool())
                .build();
    }

    // ++++++++++ Binance functionality
    @Bean(name = "binanceApiClientFactory")
    public BinanceApiClientFactory binanceApiClientFactory() {
        System.out.printf("Injecting listenKey %s and secret %s", apiKey, secret);
        return BinanceApiClientFactory.newInstance(apiKey, secret, useTestnet, useTestnetStreaming, sharedClient);
    }

    @Bean
    @DependsOn("binanceApiClientFactory")
    public BinanceApiRestClient binanceApiRestClient() {
        return binanceApiClientFactory().newRestClient();
    }

    @Bean
    @DependsOn("binanceApiClientFactory")
    public BinanceApiWebSocketClient binanceApiWebSocketClient() {
        return binanceApiClientFactory().newWebSocketClient();
    }
    // ---------- Binance functionality
}
