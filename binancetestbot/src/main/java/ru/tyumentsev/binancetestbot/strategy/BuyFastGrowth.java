package ru.tyumentsev.binancetestbot.strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.api.client.domain.market.TickerStatistics;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;
import ru.tyumentsev.binancetestbot.cache.MarketData;
import ru.tyumentsev.binancetestbot.service.MarketInfo;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Log4j2
public class BuyFastGrowth {

    /*
     * this strategy will get all price changes of pairs to USDT
     * and buy this coin if price changes more then 10% for the last 3 hours
     */

    @Autowired
    MarketInfo marketInfo;
    @Autowired
    MarketData marketData;

    final String WINDOW_SIZE = "3h";
    final int QUERY_SYMBOLS_PART_SIZE = 40;

    public List<TickerStatistics> addPairsToBuy(String asset) {
        // get assets that paired to USDT.
        List<String> pairs = marketData.getAvailablePairs(asset);

        int qtyOfParts = 0; // quantity of groups of pairs, need to split because of binance API limits.
        if (pairs.size() > QUERY_SYMBOLS_PART_SIZE) {
            qtyOfParts = pairs.size() % QUERY_SYMBOLS_PART_SIZE == 0 ? pairs.size() / QUERY_SYMBOLS_PART_SIZE
                    : pairs.size() / QUERY_SYMBOLS_PART_SIZE + 1;
        } else if (!pairs.isEmpty()) {
            qtyOfParts = 1;
        }

        List<TickerStatistics> accumulatedResponses = new ArrayList<>(); // accumulate here responses from binance API.
        for (int i = 0, fromIndex = 0, toIndex = 0; i < qtyOfParts; i++) {
            if (i == qtyOfParts - 1) { // last iteration, need to define last index.
                fromIndex = toIndex;
                toIndex = pairs.size();
            } else {
                fromIndex = QUERY_SYMBOLS_PART_SIZE * i;
                toIndex = QUERY_SYMBOLS_PART_SIZE * i + QUERY_SYMBOLS_PART_SIZE;
            }
            accumulatedResponses.addAll(getPartOfTickerStatistics(pairs, fromIndex, toIndex));
        }

        marketData.loadPairsToBuy(accumulatedResponses); // pairs that growth.

        return new ArrayList<>(marketData.getPairsToBuy());
    }

    public void makeOrdersForSelectedPairsToBuy() {
        Set<TickerStatistics> pairsToBuy = marketData.getPairsToBuy();
        // place all pairs in another collection like it was bought:
        for (TickerStatistics tickerStatistics : pairsToBuy) {
            log.info("Buy " + tickerStatistics.getSymbol() + " for " + tickerStatistics.getLastPrice());
            marketData.putOpenedPositionToPriceMonitoring(tickerStatistics.getSymbol(),
                    Double.parseDouble(tickerStatistics.getLastPrice()));
        }
        // remove from list because they bought.
        pairsToBuy.clear();
    }

    public void closeOpenedPositions() {
        Map<String, Double> openedPositions = marketData.getOpenedPositionsLastPrices();
        Map<String, Double> positionsToClose = new HashMap<>();
        openedPositions.entrySet().stream().forEach(entrySet -> {
            Double lastPrice = Double.parseDouble(marketInfo.getLastTickerPrice(entrySet.getKey()).getPrice());
            if (lastPrice > entrySet.getValue() * 1.05) {
                log.info("Price of " + entrySet.getKey() + " growth more then 5% and now equals " + lastPrice);
                entrySet.setValue(lastPrice);
            } else if (lastPrice < entrySet.getValue() * 0.95) {
                log.info("Price of " + entrySet.getKey() + " decreased more then 5% and now equals " + lastPrice);
                positionsToClose.put(entrySet.getKey(), lastPrice);
            }
        });

        marketData.representClosingPositions(positionsToClose, "USDT");
    }

    // filter pairs that growth more then 5% in window (3h).
    private List<TickerStatistics> getPartOfTickerStatistics(List<String> pairs, int fromIndex, int toIndex) {
        List<TickerStatistics> statistics = marketInfo
                .getAllWindowPriceChange(marketData.getAvailablePairsSymbolsFormatted(pairs, fromIndex, toIndex), WINDOW_SIZE);

        return statistics.stream()
                .filter(tickerStatistics -> Double.parseDouble(tickerStatistics.getPriceChangePercent()) > 5)
                .collect(Collectors.toList());
    }
}
