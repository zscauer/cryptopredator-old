package ru.tyumentsev.cryptopredator.indicatorvirginbot.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import ru.tyumentsev.cryptopredator.commons.service.AccountManager;
import ru.tyumentsev.cryptopredator.commons.service.CacheServiceClient;
import ru.tyumentsev.cryptopredator.commons.service.DataService;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.commons.service.SpotTrading;

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
    String dataKeeperURL;


    // ++++++++++ Binance functionality
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
    // ---------- Binance functionality

    // ++++++++++ Cryptopredator commons
    @Bean
    @DependsOn("binanceApiWebSocketClient")
    public MarketInfo marketInfo() {
        return new MarketInfo(binanceApiRestClient(), binanceApiWebSocketClient());
    }

    @Bean
    @DependsOn("binanceApiWebSocketClient")
    public AccountManager accountManager() {
        return new AccountManager(binanceApiRestClient(), binanceApiWebSocketClient());
    }

    @Bean
    @DependsOn("accountManager")
    public SpotTrading spotTrading() {
        return new SpotTrading(accountManager(), binanceApiAsyncRestClient(), marketInfo());
    }
    // ---------- Cryptopredator commons
}
