package ru.tyumentsev.cryptopredator.macsaw.configuration;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import ru.tyumentsev.cryptopredator.commons.service.AccountInfo;
import ru.tyumentsev.cryptopredator.commons.service.AccountServiceClient;
import ru.tyumentsev.cryptopredator.commons.service.BotStateService;
import ru.tyumentsev.cryptopredator.commons.service.BotStateServiceClient;
import ru.tyumentsev.cryptopredator.commons.service.CacheServiceClient;
import ru.tyumentsev.cryptopredator.commons.service.DataService;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.commons.service.SpotTrading;

import java.util.concurrent.TimeUnit;

@Context
@Factory
@ConfigurationProperties("applicationconfig")
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
                .callTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    // ++++++++++ Binance functionality
    @Singleton
    public BinanceApiClientFactory binanceApiClientFactory() {
        return BinanceApiClientFactory.newInstance(apiKey, secret, useTestnet, useTestnetStreaming, sharedClient);
    }

    @Singleton
    @Requires(classes = BinanceApiClientFactory.class)
    @Inject
    public BinanceApiRestClient binanceApiRestClient(BinanceApiClientFactory binanceApiClientFactory) {
        return binanceApiClientFactory.newRestClient();
    }

    @Singleton
    @Requires(classes = BinanceApiClientFactory.class)
    @Inject
    public BinanceApiAsyncRestClient binanceApiAsyncRestClient(BinanceApiClientFactory binanceApiClientFactory) {
        return binanceApiClientFactory.newAsyncRestClient();
    }

    @Singleton
    @Requires(classes = BinanceApiClientFactory.class)
    @Inject
    public BinanceApiWebSocketClient binanceApiWebSocketClient(BinanceApiClientFactory binanceApiClientFactory) {
        return binanceApiClientFactory.newWebSocketClient();
    }
    // ---------- Binance functionality

    // ++++++++++ Cryptopredator commons
    @Singleton
    @Requires(classes = {BinanceApiWebSocketClient.class, BinanceApiRestClient.class})
    @Inject
    public MarketInfo marketInfo(BinanceApiRestClient binanceApiRestClient, BinanceApiWebSocketClient binanceApiWebSocketClient) {
        return new MarketInfo(binanceApiRestClient, binanceApiWebSocketClient);
    }

    @Singleton
    @Requires(classes = {AccountInfo.class, BinanceApiAsyncRestClient.class, MarketInfo.class, BotStateService.class})
    @Inject
    public SpotTrading spotTrading(AccountInfo accountInfo, BinanceApiAsyncRestClient binanceApiAsyncRestClient, MarketInfo marketInfo, BotStateService botStateService) {
        return new SpotTrading(accountInfo, binanceApiAsyncRestClient, marketInfo, botStateService);
    }
    // ---------- Cryptopredator commons

    @Singleton
    public DataService dataService() {
        return new DataService(new Retrofit.Builder()
                .baseUrl(String.format(stateKeeperURL))
                .addConverterFactory(JacksonConverterFactory.create(new ObjectMapper().registerModule(new JavaTimeModule())))
                .client(sharedClient)
                .build().create(CacheServiceClient.class)
        );
    }

    @Singleton
    public AccountInfo accountInfo() {
        return new AccountInfo(new Retrofit.Builder()
                .baseUrl(String.format(stateKeeperURL))
                .addConverterFactory(JacksonConverterFactory.create())
                .client(sharedClient)
                .build().create(AccountServiceClient.class)
        );
    }

    @Singleton
    public BotStateService botStateService() {
        return new BotStateService(new Retrofit.Builder()
                .baseUrl(String.format(stateKeeperURL))
                .addConverterFactory(JacksonConverterFactory.create())
                .client(sharedClient)
                .build().create(BotStateServiceClient.class)
        );
    }
}
