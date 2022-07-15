package ru.tyumentsev.binancetestbot.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Repository;

import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.market.TickerStatistics;

import lombok.Getter;
import lombok.Setter;

@Repository
public class MarketData {
    // key - quote asset, value - available pairs to this asset.
    Map<String, List<String>> availablePairs = new HashMap<>();
    // + "Buy fast growth" strategy
    Set<TickerStatistics> toBuy = new HashSet<>();
    // key - pair, value - last price.
    Map<String, Double> openedPositions = new HashMap<>();
    // key - pair, value - time of closing.
    Map<String, Long> closedPositions = new HashMap<>();
    // - "Buy fast growth" strategy

    // + "Buy big volume changes" 
    // stores candles, that have price < 1 USDT.
    @Getter @Setter
    List<String> cheapPairs = new ArrayList<>();
    // Set<Candlestick> monitoredCandles = new HashSet<>(); // slow method
    Map<String, CandlestickEvent> monitoredCandlesticks = new HashMap<>();
    @Getter
    Map<String, Double> testMapToBuy = new HashMap<>();
    // - "Buy big volume changes" strategy

    final StringBuilder symbolsParameterBuilder = new StringBuilder(); // build query in format, that accepts by binance API.
    final String QUERY_SYMBOLS_BEGIN = "[", DELIMETER = "\"", QUERY_SYMBOLS_END = "]"; // required format is "["BTCUSDT","BNBUSDT"]".

    public void addAvailablePairs(String asset, List<String> pairs) {
        availablePairs.put(asset.toUpperCase(), pairs);
    }

    public List<String> getAvailablePairs(String asset) {
        return availablePairs.getOrDefault(asset.toUpperCase(), Collections.emptyList());
    }

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

    // public void fillMonitoredCandles(Set<Candlestick> sticks) {
    //     monitoredCandles.clear();
    //     monitoredCandles.addAll(sticks);
    // }

    // public Set<Candlestick> getMonitoredCandles() {
    //     return monitoredCandles;
    // }

    public void addCandlestickEventToMonitoring(String ticker, CandlestickEvent candlestickEvent) {
        monitoredCandlesticks.put(ticker, candlestickEvent);
    }

    public Map<String, CandlestickEvent> getMonitoredCandleSticks() {
        return monitoredCandlesticks;
    }

    public void addPairToTestBuy(String symbol, Double price) {
        testMapToBuy.put(symbol, price);
    }
}
