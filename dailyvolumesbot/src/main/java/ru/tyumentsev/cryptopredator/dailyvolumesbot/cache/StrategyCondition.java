//package ru.tyumentsev.cryptopredator.dailyvolumesbot.cache;
//
//import java.time.LocalDateTime;
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//import lombok.experimental.NonFinal;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Repository;
//
//import com.binance.api.client.domain.event.CandlestickEvent;
//
//import lombok.AccessLevel;
//import lombok.Getter;
//import lombok.RequiredArgsConstructor;
//import lombok.experimental.FieldDefaults;
//import lombok.extern.slf4j.Slf4j;
//import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;
//import ru.tyumentsev.cryptopredator.commons.domain.SellRecord;
//import ru.tyumentsev.cryptopredator.commons.service.AccountManager;
//import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
//
//@RequiredArgsConstructor
//@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
//@Slf4j
//public class StrategyCondition {
//    // key - quote asset, value - available pairs to this asset.
////    Map<String, List<String>> availablePairs = new HashMap<>();
////    @Getter
////    Map<String, List<String>> cheapPairs = new ConcurrentHashMap<>();
////    @Getter
////    final Map<String, Deque<CandlestickEvent>> cachedCandlestickEvents = new ConcurrentHashMap<>();
//    // monitoring last maximum price of opened positions. key - pair, value - last price.
//    @Getter
//    Map<String, OpenedPosition> longPositions = new ConcurrentHashMap<>();
//    @Getter
//    Map<String, OpenedPosition> shortPositions = new ConcurrentHashMap<>();
//
//    // stores time of last selling to avoid repeated buy signals.
//    @Getter
//    Map<String, SellRecord> sellJournal = new ConcurrentHashMap<>();
//
//    @NonFinal
//    @Value("${strategy.global.tradingAsset}")
//    String tradingAsset;
//
////    public void fillCheapPairs(String asset, MarketInfo marketInfo) {
////        // get all pairs, that trades against USDT.
////        List<String> pairs = getAvailablePairs(asset);
////        // filter available pairs to get cheaper than maximalPairPrice.
////        List<String> filteredPairs = marketInfo
////                .getLastTickersPrices(
////                        combinePairsToRequestString(pairs))
////                .stream().filter(tickerPrice -> Float.parseFloat(tickerPrice.getPrice()) < maximalPairPrice)
////                .map(TickerPrice::getSymbol).collect(Collectors.toCollection(ArrayList::new));
////        log.info("Filtered {} cheap tickers.", filteredPairs.size());
////
////        // place filtered pairs to cache.
////        putCheapPairs(asset, filteredPairs);
////    }
//
////    public void constructCandleStickEventsCache(String asset) {
////        Optional.ofNullable(cheapPairs.get(asset)).ifPresentOrElse(list -> {
////            list.forEach(pair -> {
////                cachedCandlestickEvents.put(pair, new LinkedList<>());
////            });
////            log.debug("Cache of candle stick events constructed with {} elements.", cachedCandlestickEvents.size());
////        }, () -> {
////            log.warn("Can't construct queues of candlestick events cache for {} - list of cheap pairs for this asset is empty.", asset);
////        });
//////        cheapPairs.get(asset).forEach(pair -> {
//////            cachedCandlestickEvents.put(pair, new LinkedList<>());
//////        });
////    }
//
//    public void initializeOpenedLongPositionsFromMarket(MarketInfo marketInfo, AccountManager accountManager) {
//        longPositions.clear();
//        // fill cache of opened positions with last market price of each.
//        accountManager.refreshAccountBalances()
//                .getAccountBalances().stream()
//                .filter(balance -> !(balance.getAsset().equals("USDT") || balance.getAsset().equals("BNB")))
//                .forEach(balance -> putLongPositionToPriceMonitoring(balance.getAsset() + tradingAsset,
//                        Float.parseFloat(marketInfo.getLastTickerPrice(balance.getAsset() + tradingAsset).getPrice()),
//                        Float.parseFloat(balance.getFree()), 1F, false));
//
//        log.info("{} pair(s) initialized from account manager to opened long positions price monitoring: {}",
//                longPositions.size(), longPositions);
//    }
//
//    public void putLongPositionToPriceMonitoring(String pair, float price, float qty, float priceDecreaseFactor, boolean rocketCandidate) {
//        Optional.ofNullable(longPositions.get(pair)).ifPresentOrElse(pos -> {
//            var newQty = pos.qty() + qty;
//            pos.avgPrice((pos.avgPrice() * pos.qty() + price * qty) / newQty);
//            pos.qty(newQty);
//            pos.priceDecreaseFactor(priceDecreaseFactor);
//        }, () -> {
//            var pos = new OpenedPosition();
//            pos.symbol(pair)
//                .maxPrice(price)
//                .avgPrice(price)
//                .qty(qty)
//                .priceDecreaseFactor(priceDecreaseFactor)
//                .rocketCandidate(rocketCandidate);
//            log.debug("{} not found in opened long positions, adding new one - '{}'.", pair, pos);
//            longPositions.put(pair, pos);
//        });
//        if (rocketCandidate) {
//            log.info("{} added to opened positions as rocket candidate.", pair);
//        }
//    }
//
//    public void updateOpenedPositionLastPrice(String pair, float lastPrice, Map<String, OpenedPosition> openedPositions) {
//        Optional.ofNullable(openedPositions.get(pair)).ifPresent(pos -> {
//            pos.lastPrice(lastPrice);
//            if (lastPrice > pos.maxPrice()) {
//                pos.maxPrice(lastPrice);
//            }
//        });
//    }
//
//    public void updatePriceDecreaseFactor(final String pair, float priceDecreaseFactor, Map<String, OpenedPosition> openedPositions) {
//        Optional.ofNullable(openedPositions.get(pair)).ifPresent(pos -> {
//            pos.priceDecreaseFactor(priceDecreaseFactor);
//            log.info("Updating price decrease factor of {} to {}. Value after updating: {}.", pair, priceDecreaseFactor, openedPositions.get(pair).priceDecreaseFactor());
//        });
//    }
//
//    public void removeLongPositionFromPriceMonitoring(String pair) {
//        longPositions.remove(pair);
//    }
//
//
//    public void addSellRecordToJournal(String pair, String strategy) {
//        sellJournal.put(pair, new SellRecord(pair, LocalDateTime.now(), strategy));
//    }
//
//    public boolean thisSignalWorkedOutBefore(final String pair) {
//        AtomicBoolean ignoreSignal = new AtomicBoolean(false);
//
//        Optional.ofNullable(sellJournal.get(pair)).ifPresent(sellRecord -> {
//            if (sellRecord.sellTime().getDayOfYear() == LocalDateTime.now().getDayOfYear()) {
//                ignoreSignal.set(true);
//            } else {
//                log.debug("Period of signal ignoring for {} expired, remove pair from sell journal.", pair);
//                sellJournal.remove(pair);
//            }
//        });
//
//        return ignoreSignal.get();
//    }
//
//    public void removeCandlestickEventsCacheForPair(String ticker, Map<String, Deque<CandlestickEvent>> cachedCandlestickEvents) {
//        cachedCandlestickEvents.get(ticker).clear();
//    }
//}
