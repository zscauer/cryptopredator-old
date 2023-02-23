package ru.tyumentsev.cryptopredator.indicatorvirginbot.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
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
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import ru.tyumentsev.cryptopredator.commons.service.AccountInfo;
import ru.tyumentsev.cryptopredator.commons.service.AccountManager;
import ru.tyumentsev.cryptopredator.commons.service.AccountServiceClient;
import ru.tyumentsev.cryptopredator.commons.service.BotStateService;
import ru.tyumentsev.cryptopredator.commons.service.BotStateServiceClient;
import ru.tyumentsev.cryptopredator.commons.service.CacheServiceClient;
import ru.tyumentsev.cryptopredator.commons.service.DataService;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.commons.service.SpotTrading;

import java.util.concurrent.TimeUnit;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "applicationconfig")
@EnableScheduling
@FieldDefaults(level = AccessLevel.PRIVATE)
@SuppressWarnings("unused")
public class ApplicationConfig {

    final OkHttpClient sharedClient;

    String apiKey;
    String secret;
    boolean useTestnet;
    boolean useTestnetStreaming;
    String stateKeeperURL;

    {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequestsPerHost(400);
        dispatcher.setMaxRequests(400);
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
        return BinanceApiClientFactory.newInstance(apiKey, secret, useTestnet, useTestnetStreaming, sharedClient);
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
    // ---------- Binance functionality

    // ++++++++++ Cryptopredator commons
    @Bean
    @DependsOn("binanceApiWebSocketClient")
    public MarketInfo marketInfo() {
        return new MarketInfo(binanceApiRestClient(), binanceApiWebSocketClient());
    }

    @Bean
    @DependsOn({"accountInfo", "botStateService"})
    public SpotTrading spotTrading() {
        return new SpotTrading(accountInfo(), binanceApiAsyncRestClient(), marketInfo(), botStateService());
    }
    // ---------- Cryptopredator commons

    @Bean
    public DataService dataService() {
        return new DataService(new Retrofit.Builder()
                .baseUrl(String.format(stateKeeperURL))
                .addConverterFactory(JacksonConverterFactory.create(new ObjectMapper().registerModule(new JavaTimeModule())))
                .client(sharedClient)
//                .client(new OkHttpClient.Builder().build())
                .build().create(CacheServiceClient.class)
        );
    }

    @Bean
    public AccountInfo accountInfo() {
        return new AccountInfo(new Retrofit.Builder()
                .baseUrl(String.format(stateKeeperURL))
                .addConverterFactory(JacksonConverterFactory.create())
                .client(sharedClient)
//                .client(new OkHttpClient.Builder().build())
                .build().create(AccountServiceClient.class)
        );
    }

    @Bean
    public BotStateService botStateService() {
        return new BotStateService(new Retrofit.Builder()
                .baseUrl(String.format(stateKeeperURL))
                .addConverterFactory(JacksonConverterFactory.create())
                .client(sharedClient)
//                .client(new OkHttpClient.Builder().build())
                .build().create(BotStateServiceClient.class)
        );
    }
}
