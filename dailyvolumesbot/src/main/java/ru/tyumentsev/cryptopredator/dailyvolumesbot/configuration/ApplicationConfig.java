package ru.tyumentsev.cryptopredator.dailyvolumesbot.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;

import io.prometheus.client.exporter.MetricsServlet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import ru.tyumentsev.cryptopredator.commons.service.AccountManager;
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

    @Bean
    public DataService dataService() {
        return new DataService();
    }
    // ---------- Cryptopredator commons

    @Bean
	public ServletRegistrationBean<MetricsServlet> metricsServlet() {
		ServletRegistrationBean<MetricsServlet> bean = new ServletRegistrationBean<>(new MetricsServlet(), "/metrics");
		bean.setLoadOnStartup(1);
		return bean;
	}

    @Bean
	public CommonsRequestLoggingFilter logFilter() {
		CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
		filter.setIncludeQueryString(true);
		filter.setIncludeHeaders(true);
		filter.setIncludeClientInfo(true);
		return filter;
	}
}
