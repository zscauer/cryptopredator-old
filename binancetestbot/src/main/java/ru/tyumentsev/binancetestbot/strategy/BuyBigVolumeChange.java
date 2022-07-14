package ru.tyumentsev.binancetestbot.strategy;

import java.io.Closeable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.market.CandlestickInterval;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import ru.tyumentsev.binancetestbot.cache.MarketData;
import ru.tyumentsev.binancetestbot.service.MarketInfo;

@Service
@RequiredArgsConstructor
@Log4j2
public class BuyBigVolumeChange {

    @Autowired
    MarketInfo marketInfo;
    @Autowired
    MarketData marketData;
    @Autowired
    BinanceApiWebSocketClient binanceApiWebSocketClient;

    public void fillMonitoredCandleSticks() {

        marketData.fillMonitoredTicks(
                marketInfo.getCandleSticks(marketData.getAvailablePairs("USDT"), CandlestickInterval.HOURLY, 2));
        System.out.println(marketData.getMonitoredCandleTicks());
    }

    public void findBigVolumeChanges() {
        // Closeable ws =
        // binanceApiWebSocketClient.onCandlestickEvent(marketData.getAvailablePairsSymbols("USDT"),
        // CandlestickInterval.FIFTEEN_MINUTES, callback -> {
        // log.info(callback);
        // });
        // System.out.println(marketData.getAvailablePairsSymbols("USDT"));
    }
}
