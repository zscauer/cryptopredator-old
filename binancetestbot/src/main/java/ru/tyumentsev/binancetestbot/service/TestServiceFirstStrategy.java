package ru.tyumentsev.binancetestbot.service;

import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Service
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TestServiceFirstStrategy {
    /* this strategy will get all price changes of pairs to USDT
     * and buy this coin if price changes more then 10% for the last 3 hours
    */

    final BinanceApiRestClient restClient;
    final BinanceApiWebSocketClient webSocketClient;

    public TestServiceFirstStrategy(BinanceApiClientFactory factory) {
        this.restClient = factory.newRestClient();
        this.webSocketClient = factory.newWebSocketClient();
    }

    public void getTickerStatistic() {
        restClient.get24HrPriceStatistics("ETHUSDT");
    }

}
