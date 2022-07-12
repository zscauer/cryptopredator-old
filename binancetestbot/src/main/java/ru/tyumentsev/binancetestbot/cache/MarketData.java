package ru.tyumentsev.binancetestbot.cache;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Repository;

import com.binance.api.client.domain.market.TickerStatistics;

@Repository
public class MarketData {
    // key - quote asset, value - available pairs to this asset.
    Map<String, List<String>> availablePairs = new HashMap<>();
    Set<TickerStatistics> toBuy = new HashSet<>();
    // key - pair, value - last price.
    Map<String, Double> openedPositions = new HashMap<>();
    // key - pair, value - time of closing.
    Map<String, Long> closedPositions = new HashMap<>();

    public void addAvailablePairs(String asset, List<String> pairs) {
        availablePairs.put(asset.toUpperCase(), pairs);
    }

    public List<String> getAvailablePairs(String ticker) {
        return availablePairs.getOrDefault(ticker.toUpperCase(), Collections.emptyList());
    }

    // TODO change to get asset as a parameter.
    public void loadPairsToBuy(List<TickerStatistics> pairs) {
        toBuy.clear();
        toBuy.addAll(pairs);

        // need to remove all added pairs from available pairs:
        getAvailablePairs("USDT")
                .removeAll(pairs.stream().map(tickerStatistics -> tickerStatistics.getSymbol()).toList());
    }

    public Set<TickerStatistics> getPairsToBuy() {
        return toBuy;
    }

    public void putOpenedPosition(String pair, Double price) {
        openedPositions.put(pair, price);
    }

    public Map<String, Double> getOpenedPositions() {
        return openedPositions;
    }

    public void representClosingPositions(Map<String, Long> closedPairs) {
        closedPairs.entrySet().stream().forEach(entrySet -> {
            openedPositions.remove(entrySet.getKey());
        });

        closedPositions.putAll(closedPairs);
    }

}
