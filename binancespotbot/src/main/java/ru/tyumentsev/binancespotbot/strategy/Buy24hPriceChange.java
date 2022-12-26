package ru.tyumentsev.binancespotbot.strategy;

import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.market.TickerPrice;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class Buy24hPriceChange implements TradingStrategy {

    final BinanceApiWebSocketClient binanceApiWebSocketClient;
    final MarketInfo marketInfo;
    final MarketData marketData;
    final Map<String, Closeable> webSocketStreams;

    @Value("${strategy.global.maximalPairPrice}")
    int maximalPairPrice;
    @Value("${strategy.buy24hPriceChange.percentOfGrowingFor24h}")
    int percentOfGrowingFor24h;

    final List<TickerStatistics> grownPairs = new ArrayList<>();

    private static Double parsedDouble(String stringToParse) {
        return Double.parseDouble(stringToParse);
    }

    public void defineGrowingPairs(String asset) {
        List<String> cheapPairsExcludeOpenedPositions = marketData.getCheapPairsExcludeOpenedPositions(asset);
        List<TickerStatistics> tickers24HrPriceStatistics = marketInfo.getTickers24HrPriceStatistics(
                marketData.combinePairsToRequestString(cheapPairsExcludeOpenedPositions));

        grownPairs.clear();
        tickers24HrPriceStatistics.stream()
                .filter(stats -> parsedDouble(stats.getPriceChangePercent()) > percentOfGrowingFor24h)
                .sorted((x1, x2) -> parsedDouble(x2.getPriceChangePercent()).compareTo(parsedDouble(x1.getPriceChangePercent())))
                .forEach(tickerStatistics -> {
                    grownPairs.add(tickerStatistics);
                    log.info("[Buy24hPriceChange] {} growth at {}%.", tickerStatistics.getSymbol(), tickerStatistics.getPriceChangePercent());
                });
//        log.info("There is {} pairs that grows more than {}%", grownPairs.size(), percentOfGrowingFor24h);
    }

    public void fillWebSocketStreams() {
        for (TickerStatistics pair : grownPairs.subList(0, 3)) {
            webSocketStreams.put(pair.getSymbol(), getNewWebSocketStream(pair.getSymbol()));
        }
    }

    private Closeable getNewWebSocketStream(String pair) {
        return binanceApiWebSocketClient.onTickerEvent(pair.toLowerCase(), response -> {
            if (parsedDouble(response.getPriceChange()) > 20) {
                log.info("Price change of {} is more than 20% and equals '{}'.", response.getSymbol(), response.getPriceChangePercent());
            }
        });
    }

    public void closeOpenedWebSocketStreams() {
        webSocketStreams.forEach((key, value) -> {
            try {
                value.close();
                log.info("WebStream of '{}' closed.", key);
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
