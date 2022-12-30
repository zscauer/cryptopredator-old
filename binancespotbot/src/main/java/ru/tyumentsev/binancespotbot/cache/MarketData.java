package ru.tyumentsev.binancespotbot.cache;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.binance.api.client.domain.market.TickerPrice;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
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

    // + "VolumeCatcher" strategy
    // stores candles, that have price < 1 USDT.
    Map<String, List<String>> cheapPairs = new ConcurrentHashMap<>();
    @Getter
    Map<String, List<Candlestick>> cachedCandles = new ConcurrentHashMap<>();
    // stores current and previous candlestick events for each pair to compare them.
    // first element - previous, last element - current.
    @Getter
    Map<String, Deque<CandlestickEvent>> cachedCandlestickEvents = new ConcurrentHashMap<>();
    @Getter
    Map<String, Double> pairsToBuy = new ConcurrentHashMap<>();
    // stores time of last selling to avoid repeated buy signals.
    Map<String, LocalDateTime> sellJournal = new ConcurrentHashMap<>();
    // - "VolumeCatcher" strategy

    // + "Buy order book trend"
    @Getter
    Map<String, Interest> openInterest = new ConcurrentHashMap<>();
    // - "Buy order book trend"

    StringBuilder symbolsParameterBuilder = new StringBuilder(); // build query in format, that accepts by binance API.
    String QUERY_SYMBOLS_BEGIN = "[", DELIMETER = "\"", QUERY_SYMBOLS_END = "]"; // required format is "["BTCUSDT","BNBUSDT"]".

    @NonFinal
    @Value("${strategy.global.maximalPairPrice}")
    int maximalPairPrice;
    @NonFinal
    @Value("${strategy.global.candlestickEventsCacheSize}")
    int candlestickEventsCacheSize;
    @NonFinal
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;

    public void fillCheapPairs(String asset, MarketInfo marketInfo) {
        // get all pairs, that trades against USDT.
        List<String> pairs = getAvailablePairs(asset);
        // filter available pairs to get cheaper than maximalPairPrice.
        List<String> filteredPairs = marketInfo
                .getLastTickersPrices(
                        combinePairsToRequestString(pairs))
                .stream().filter(tickerPrice -> Double.parseDouble(tickerPrice.getPrice()) < maximalPairPrice)
                .map(TickerPrice::getSymbol).collect(Collectors.toCollection(ArrayList::new));
        log.info("Filtered {} cheap tickers.", filteredPairs.size());

        // place filtered pairs to cache.
        putCheapPairs(asset, filteredPairs);
    }

    public void constructCandleStickEventsCache(String asset) {
        cheapPairs.get(asset).forEach(pair -> {
            cachedCandlestickEvents.put(pair, new LinkedList<>());
        });
        log.debug("Cache of candle stick events constructed with {} elements.", cachedCandlestickEvents.size());
    }

    public void initializeOpenedLongPositionsFromMarket(MarketInfo marketInfo, AccountManager accountManager) {
        longPositions.clear();
        // fill cache of opened positions with last market price of each.
        accountManager.refreshAccountBalances()
                .getAccountBalances().stream()
                .filter(balance -> !(balance.getAsset().equals("USDT") || balance.getAsset().equals("BNB")))
                .forEach(balance -> putLongPositionToPriceMonitoring(balance.getAsset() + tradingAsset,
                        Double.parseDouble(marketInfo.getLastTickerPrice(balance.getAsset() + tradingAsset).getPrice()),
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

    public String combinePairsToRequestString(List<String> pairs) {
//        return pairs.stream()
//                .collect(Collectors.joining(DELIMETER, QUERY_SYMBOLS_BEGIN, QUERY_SYMBOLS_END));

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
            log.debug("{} not found in opened long positions, adding new one - '{}'.", pair, pos);
            longPositions.put(pair, pos);
        });
    }

    public void putShortPositionToPriceMonitoring(String pair, Double price, Double qty) {
        // in test mode avgPrice - price in first time of signal
        // qty - % of change in first time of signal
        // maxPrice - price moving
        Optional.ofNullable(shortPositions.get(pair)).ifPresentOrElse(pos -> {
//            var newQty = pos.qty() + qty;
//            pos.avgPrice((pos.avgPrice() * pos.qty() + price * qty) / qty);
            if (price > pos.maxPrice()) {
                pos.maxPrice(price);
            }
            //pos.qty(qty);
        }, () -> {
            var pos = OpenedPosition.of(pair);
//            pos.maxPrice(price);
            pos.avgPrice(price); // TODO: how to define avg at application initializing? connect db?
            pos.qty(qty);
            log.info("{} not found in opened short positions, adding new one - '{}'.", pair, pos);
            shortPositions.put(pair, pos);
        });
    }

    public void updateOpenedPosition(String pair, Double price, Map<String, OpenedPosition> openedPositions) {
        Optional.ofNullable(openedPositions.get(pair)).ifPresent(pos -> pos.maxPrice(price));
    }

    public void removeLongPositionFromPriceMonitoring(String pair) {
        longPositions.remove(pair);
    }

    public void addCandlestickEventToCache(String ticker, CandlestickEvent candlestickEvent) {
        Deque<CandlestickEvent> eventsQueue = cachedCandlestickEvents.get(ticker);
        Optional.ofNullable(eventsQueue.peekLast()).ifPresentOrElse(lastCachedEvent -> {
            if (lastCachedEvent.getOpenTime().equals(candlestickEvent.getOpenTime())) { // refreshed candle event.
                eventsQueue.remove(lastCachedEvent); // remove previous version of this event.
            }
            eventsQueue.addLast(candlestickEvent);
        }, () -> eventsQueue.addLast(candlestickEvent));

        if (eventsQueue.size() > candlestickEventsCacheSize) {
            eventsQueue.removeFirst();
        }
    }

//    public boolean signalWorkedOutBefore (String pair, long timeDifference) {
//        Optional.ofNullable(sellJournal.get(pair)).ifPresent(dealTime -> {
//            if (dealTime.getLong() < LocalDateTime.now().getLong() + timeDifference) {
//                sellJournal.remove(pair);
//                return false;
//            }
//        });
//        return true;
//    }

    public void removeCandlestickEventsCacheForPair(String ticker) {
        cachedCandlestickEvents.get(ticker).clear();
    }

    public void putPairToBuy(String symbol, Double price) {
        log.debug("Put {} into pairs to buy.", symbol);
        pairsToBuy.put(symbol, price);
    }
}
