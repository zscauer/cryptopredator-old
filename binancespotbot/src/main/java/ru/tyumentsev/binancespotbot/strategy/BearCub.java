package ru.tyumentsev.binancespotbot.strategy;

import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.market.TickerStatistics;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.tyumentsev.binancespotbot.cache.MarketData;
import ru.tyumentsev.binancespotbot.service.MarketInfo;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class BearCub implements TradingStrategy {

    /*
     * Strategy find pairs, that growth more than % in 24hr, and margin sell them.
     */
    final BinanceApiWebSocketClient binanceApiWebSocketClient;
    final MarketInfo marketInfo;
    final MarketData marketData;
    final Map<String, Closeable> webSocketStreams;
    final List<TickerStatistics> grownPairs = new ArrayList<>();

    @Value("${strategy.bearCub.percentOfGrowingFor24h}")
    int percentOfGrowingFor24h;

    private static Double parsedDouble(String stringToParse) {
        return Double.parseDouble(stringToParse);
    }

    public void defineGrowingPairs(String asset) {
        List<String> cheapPairsExcludeOpenedPositions = marketData.getCheapPairsExcludeOpenedPositions(asset);
        List<TickerStatistics> tickers24HrPriceStatistics = marketInfo.getTickers24HrPriceStatistics(
                marketData.combinePairsToRequestString(cheapPairsExcludeOpenedPositions));

//        grownPairs.clear();
        tickers24HrPriceStatistics.stream()
                .filter(stats -> parsedDouble(stats.getPriceChangePercent()) > percentOfGrowingFor24h)
                .sorted((x1, x2) -> parsedDouble(x2.getPriceChangePercent()).compareTo(parsedDouble(x1.getPriceChangePercent())))
                .forEach(tickerStatistics -> {
                    grownPairs.add(tickerStatistics);
//                    log.info("[BearCub] {} growth at {}%.", tickerStatistics.getSymbol(), tickerStatistics.getPriceChangePercent());
                });
//        log.info("There is {} pairs that grows more than {}%", grownPairs.size(), percentOfGrowingFor24h);
    }

    public void openShortsForGrownPairs() {
        grownPairs.forEach(pos -> {
            marketData.putShortPositionToPriceMonitoring(pos.getSymbol(),
                    parsedDouble(pos.getLastPrice()), parsedDouble(pos.getPriceChangePercent()));
        });
        grownPairs.clear();
    }

    public void closeOpenedWebSocketStreams() {
        webSocketStreams.forEach((pair, stream) -> {
            try {
                stream.close();
                log.info("WebStream of '{}' closed.", pair);
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        });
    }

    @PreDestroy
    public void destroy() {
        closeOpenedWebSocketStreams();
    }
}
