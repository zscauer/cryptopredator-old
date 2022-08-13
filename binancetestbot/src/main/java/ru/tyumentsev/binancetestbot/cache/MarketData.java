package ru.tyumentsev.binancetestbot.cache;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.TickerStatistics;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import ru.tyumentsev.binancetestbot.service.AccountManager;
import ru.tyumentsev.binancetestbot.service.MarketInfo;

@Repository
@Log4j2
public class MarketData {
    // key - quote asset, value - available pairs to this asset.
    Map<String, List<String>> availablePairs = new HashMap<>();
    // + "Buy fast growth" strategy
    Set<TickerStatistics> toBuy = new HashSet<>();
    // monitoring last maximun price of opened positions. key - pair, value - last
    // price.
    @Getter
    Map<String, Double> openedPositionsLastPrices = new ConcurrentHashMap<>();
    // key - pair, value - price of closing.
    Map<String, Double> closedPositions = new HashMap<>();
    // - "Buy fast growth" strategy

    // + "Buy big volume changes"
    // stores candles, that have price < 1 USDT.
    Map<String, List<String>> cheapPairs = new HashMap<>();
    @Getter
    Map<String, List<Candlestick>> cachedCandles = new HashMap<>(); // slow method
    // stores current and previous candlestick events for each pair to compare them.
    // first element - previous, last element - current.
    Map<String, CandlestickEvent> cachedCandlestickEvents = new ConcurrentHashMap<>();
    @Getter
    Map<String, Double> testMapToBuy = new HashMap<>();
    // - "Buy big volume changes" strategy

    final StringBuilder symbolsParameterBuilder = new StringBuilder(); // build query in format, that accepts by binance
                                                                       // API.
    final String QUERY_SYMBOLS_BEGIN = "[", DELIMETER = "\"", QUERY_SYMBOLS_END = "]"; // required format is
                                                                                       // "["BTCUSDT","BNBUSDT"]".

    public void initializeOpenedPositionsFromMarket(MarketInfo marketInfo, AccountManager accountManager) {
        openedPositionsLastPrices.clear();
        // fill cache of opened positions with last market price of each.
        accountManager.getAccountBalances().stream()
                .filter(balance -> !(balance.getAsset().equals("USDT") || balance.getAsset().equals("BNB")))
                .forEach(balance -> {
                    putOpenedPositionToPriceMonitoring(balance.getAsset() + "USDT",
                            Double.parseDouble(marketInfo.getLastTickerPrice(balance.getAsset() + "USDT").getPrice()));
                });

        log.info("Next pairs initialized from account manager to opened positions price monitoring: "
                + getOpenedPositionsLastPrices());
    }

    public void addAvailablePairs(String asset, List<String> pairs) {
        availablePairs.put(asset.toUpperCase(), pairs);
    }

    public List<String> getAvailablePairs(String asset) {
        return availablePairs.getOrDefault(asset.toUpperCase(), Collections.emptyList());
    }

    // return string, that formatted to websocket stream requires.
    public String getAvailablePairsSymbols(String asset) {
        StringBuilder sb = new StringBuilder();

        availablePairs.get(asset).stream().forEach(pair -> {
            sb.append(pair.toLowerCase() + ",");
        });
        sb.deleteCharAt(sb.length() - 1);

        return sb.toString();
    }

    public String getAvailablePairsSymbolsFormatted(List<String> pairs, int fromIndex, int toIndex) {
        symbolsParameterBuilder.delete(0, symbolsParameterBuilder.capacity());
        symbolsParameterBuilder.append(QUERY_SYMBOLS_BEGIN);

        for (String pair : pairs.subList(fromIndex, toIndex)) {
            symbolsParameterBuilder.append(DELIMETER);
            symbolsParameterBuilder.append(pair);
            symbolsParameterBuilder.append(DELIMETER);
            symbolsParameterBuilder.append(",");
        }
        symbolsParameterBuilder.deleteCharAt(symbolsParameterBuilder.length() - 1); // delete "," in last line.
        symbolsParameterBuilder.append(QUERY_SYMBOLS_END);

        return symbolsParameterBuilder.toString();
    }

    public void putCheapPairs(String asset, List<String> pairs) {
        cheapPairs.put(asset, pairs);
    }

    /**
     * 
     * @param asset
     * @return list of cheap pairs, exclude pairs of opened positions.
     */
    public List<String> getCheapPairsWithoutOpenedPositions(String asset) {
        List<String> pairs = cheapPairs.getOrDefault(asset, Collections.emptyList());
        pairs.removeAll(openedPositionsLastPrices.keySet());
        return pairs;
    }

    // return string, that formatted to websocket stream requires.
    public String getCheapPairsSymbols(String asset) {
        StringBuilder sb = new StringBuilder();

        cheapPairs.get(asset).stream().forEach(pair -> {
            sb.append(pair.toLowerCase() + ",");
        });
        sb.deleteCharAt(sb.length() - 1);

        return sb.toString();
    }

    public String getCheapPairsSymbols(List<String> asset) {
        StringBuilder sb = new StringBuilder();

        asset.stream().forEach(pair -> {
            sb.append(pair.toLowerCase() + ",");
        });
        sb.deleteCharAt(sb.length() - 1);

        return sb.toString();
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

    public void putOpenedPositionToPriceMonitoring(String pair, Double price) {
        openedPositionsLastPrices.put(pair, price);
    }

    public void clearOpenedPositionsLastPrices() {
        openedPositionsLastPrices.clear();
    }

    public void removeClosedPositionFromPriceMonitoring(String pair) {
        openedPositionsLastPrices.remove(pair);
    }

    public void representClosingPositions(Map<String, Double> closedPairs, String asset) {
        closedPairs.entrySet().stream().forEach(entrySet -> {
            openedPositionsLastPrices.remove(entrySet.getKey());
            if (entrySet.getValue() < 1) {
                cheapPairs.get(asset).add(entrySet.getKey());
            }
        });
    }

    public void addCandlesticksToCache(String ticker, List<Candlestick> sticks) {
        cachedCandles.put(ticker, sticks);
    }

    public void clearCandleSticksCache() {
        cachedCandles.clear();
    }

    public void addCandlestickEventToMonitoring(String ticker, CandlestickEvent candlestickEvent) {
        cachedCandlestickEvents.put(ticker, candlestickEvent);
    }

    public Map<String, CandlestickEvent> getCachedCandleStickEvents() {
        return cachedCandlestickEvents;
    }

    public void addPairToTestBuy(String symbol, Double price) {
        testMapToBuy.put(symbol, price);
    }

}
