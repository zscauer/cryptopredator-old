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

    public void addAvailablePairs(String asset, List<String> pairs) {
        availablePairs.put(asset.toUpperCase(), pairs);
    }

    public List<String> getAvailablePairs(String ticker) {
        return availablePairs.getOrDefault(ticker.toUpperCase(), Collections.emptyList());
    }

    public void loadPairsToBuy(List<TickerStatistics> pairs) {
        toBuy.clear();
        toBuy.addAll(pairs);
    }

    public Set<TickerStatistics> getPairsToBuy() {
        return toBuy;
    }

}
