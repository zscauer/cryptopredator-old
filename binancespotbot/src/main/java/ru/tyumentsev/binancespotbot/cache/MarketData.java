package ru.tyumentsev.binancespotbot.cache;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.market.Candlestick;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.binancespotbot.domain.Interest;
import ru.tyumentsev.binancespotbot.domain.OpenedPosition;
import ru.tyumentsev.binancespotbot.service.AccountManager;
import ru.tyumentsev.binancespotbot.service.MarketInfo;

@Repository
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class MarketData {
    // key - quote asset, value - available pairs to this asset.
    Map<String, List<String>> availablePairs = new HashMap<>();
    // monitoring last maximum price of opened positions. key - pair, value - last price.
    @Getter
    Map<String, OpenedPosition> longPositions = new ConcurrentHashMap<>();
    @Getter
    Map<String, OpenedPosition> shortPositions = new ConcurrentHashMap<>();

    // + "Buy big volume growth"
    // stores candles, that have price < 1 USDT.
    Map<String, List<String>> cheapPairs = new ConcurrentHashMap<>();
    @Getter
    Map<String, List<Candlestick>> cachedCandles = new ConcurrentHashMap<>();
    // stores current and previous candlestick events for each pair to compare them.
    // first element - previous, last element - current.
    Map<String, CandlestickEvent> cachedCandlestickEvents = new ConcurrentHashMap<>();
    @Getter
    Map<String, Double> pairsToBuy = new ConcurrentHashMap<>();
    // - "Buy big volume growth" strategy

    // + "Buy order book trend"
    @Getter
    Map<String, Interest> openInterest = new ConcurrentHashMap<>();
    // - "Buy order book trend"

    StringBuilder symbolsParameterBuilder = new StringBuilder(); // build query in format, that accepts by binance API.
    String QUERY_SYMBOLS_BEGIN = "[", DELIMETER = "\"", QUERY_SYMBOLS_END = "]"; // required format is
                                                                                       // "["BTCUSDT","BNBUSDT"]".

    public void initializeOpenedLongPositionsFromMarket(MarketInfo marketInfo, AccountManager accountManager) {
        longPositions.clear();
        // fill cache of opened positions with last market price of each.
        accountManager.getAccountBalances().stream()
                .filter(balance -> !(balance.getAsset().equals("USDT") || balance.getAsset().equals("BNB")))
                .forEach(balance -> putLongPositionToPriceMonitoring(balance.getAsset() + "USDT",
                        Double.parseDouble(marketInfo.getLastTickerPrice(balance.getAsset() + "USDT").getPrice()),
                        Double.parseDouble(balance.getFree())));

        log.info("{} pair(s) initialized from account manager to opened long positions price monitoring: {}",
                longPositions.size(), longPositions);
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

        availablePairs.get(asset).forEach(pair -> sb.append(pair.toLowerCase()).append(","));
        sb.deleteCharAt(sb.length() - 1);

        return sb.toString();
    }

    public String combinePairsToRequestString(List<String> pairs) {
        symbolsParameterBuilder.delete(0, symbolsParameterBuilder.capacity());
        symbolsParameterBuilder.append(QUERY_SYMBOLS_BEGIN);

        for (var pair : pairs) {
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
    public List<String> getCheapPairsExcludeOpenedPositions(String asset) {
        List<String> pairs = cheapPairs.getOrDefault(asset, Collections.emptyList());
        pairs.removeAll(longPositions.keySet());
        pairs.removeAll(shortPositions.keySet());
        
        return pairs;
    }

    // return string, that formatted to websocket stream requires.
    public String getCheapPairsSymbols(String asset) {
        StringBuilder sb = new StringBuilder();

        cheapPairs.get(asset).forEach(pair -> sb.append(pair.toLowerCase()).append(","));
        sb.deleteCharAt(sb.length() - 1);

        return sb.toString();
    }

    public String getCheapPairsSymbols(List<String> asset) {
        StringBuilder sb = new StringBuilder();

        asset.forEach(pair -> sb.append(pair.toLowerCase()).append(","));
        sb.deleteCharAt(sb.length() - 1);

        return sb.toString();
    }

    public void putLongPositionToPriceMonitoring(String pair, Double price, Double qty) {
        Optional.ofNullable(longPositions.get(pair)).ifPresentOrElse(pos -> {
            var newQty = pos.qty() + qty;
            pos.avgPrice((pos.avgPrice() * pos.qty() + price * qty) / newQty);
            pos.qty(newQty);
        }, () -> {
            var pos = OpenedPosition.of(pair);
            pos.maxPrice(price);
            pos.avgPrice(price); // TODO: how to define avg at application initializing? connect db?
            pos.qty(qty);
            log.info("{} not found in opened long positions, adding new one - '{}'.", pair, pos);
            longPositions.put(pair, pos);
        });
    }

    public void putShortPositionToPriceMonitoring(String pair, Double price, Double qty) {
        Optional.ofNullable(shortPositions.get(pair)).ifPresentOrElse(pos -> {
            var newQty = pos.qty() + qty;
            pos.avgPrice((pos.avgPrice() * pos.qty() + price * qty) / newQty);
            pos.qty(newQty);
        }, () -> {
            var pos = OpenedPosition.of(pair);
            pos.maxPrice(price);
            pos.avgPrice(price); // TODO: how to define avg at application initializing? connect db?
            pos.qty(qty);
            log.info("{} not found in opened short positions, adding new one - '{}'.", pair, pos);
            shortPositions.put(pair, pos);
        });
    }

    public void updateOpenedPositionMaxPrice(String pair, Double price, Map<String, OpenedPosition> openedPositions) {
        Optional.ofNullable(openedPositions.get(pair)).ifPresent(pos -> pos.maxPrice(price));
    }

    public void removeLongPositionFromPriceMonitoring(String pair) {
        longPositions.remove(pair);
    }

    public void addCandlesticksToCache(String ticker, List<Candlestick> sticks) {
        cachedCandles.put(ticker, sticks);
    }

    public void clearCandleSticksCache() {
        cachedCandles.clear();
        log.debug("CandleStickCache cleared.");
    }

    public void addCandlestickEventToMonitoring(String ticker, CandlestickEvent candlestickEvent) {
        cachedCandlestickEvents.put(ticker, candlestickEvent);
    }

    public Map<String, CandlestickEvent> getCachedCandleStickEvents() {
        return cachedCandlestickEvents;
    }

    public void putPairToBuy(String symbol, Double price) {
        log.debug("Put {} into pairs to buy.", symbol);
        pairsToBuy.put(symbol, price);
    }
}
