package ru.tyumentsev.binancespotbot.strategy;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.market.CandlestickInterval;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.tyumentsev.binancespotbot.cache.MarketData;
import ru.tyumentsev.binancespotbot.domain.OpenedPosition;
import ru.tyumentsev.binancespotbot.service.AccountManager;
import ru.tyumentsev.binancespotbot.service.MarketInfo;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class Daily implements TradingStrategy {

    final MarketInfo marketInfo;
    final MarketData marketData;
    final AccountManager accountManager;

    CandlestickInterval candlestickInterval;
    @Getter
    final Map<String, Closeable> candleStickEventsStreams = new ConcurrentHashMap<>();
    @Getter
    final Map<String, Deque<CandlestickEvent>> cachedCandlestickEvents = new ConcurrentHashMap<>();
    final Map<String, OpenedPosition> longPositions = new ConcurrentHashMap<>();
    Map<String, LocalDateTime> sellJournal = new ConcurrentHashMap<>();


    @Value("${strategy.daily.enabled}")
    boolean dailyEnabled;
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;
    @Value("${strategy.global.candlestickEventsCacheSize}")
    int candlestickEventsCacheSize;
    @Value("${strategy.daily.volumeGrowthFactor}")
    int volumeGrowthFactor;
    @Value("${strategy.daily.priceGrowthFactor}")
    double priceGrowthFactor;

    @Override
    public boolean isEnabled() {
        return dailyEnabled;
    }

    @Override
    public void handleBuying(final OrderTradeUpdateEvent event) {

    }

    @Override
    public void handleSelling(final OrderTradeUpdateEvent event) {

    }

    public void startCandlstickEventsCacheUpdating(String asset, CandlestickInterval interval) {
        log.info("Start canlde stick events cache updataing in DAILY. Interval is {}.", interval);
        candlestickInterval = interval;
        closeOpenedWebSocketStreams();

        marketData.getCheapPairsExcludeOpenedPositions(asset)
            .forEach(ticker -> {
                candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
                    marketMonitoringCallback(ticker)));
            });
//        marketData.getLongPositions().forEach((ticker, openedPosition) -> {
//            candleStickEventsStreams.put(ticker, marketInfo.openCandleStickEventsStream(ticker.toLowerCase(), candlestickInterval,
//                    longPositionMonitoringCallback(ticker)));
//        });
    }

    private BinanceApiCallback<CandlestickEvent> marketMonitoringCallback(String ticker) {
//        log.info("[DAILY] request of callback logic for {}.", ticker);
        return event -> {
//            log.info("Callback in Daily of {}, close = {}, open = {}, priceGrowthFactor = {}."
//                    , ticker, event.getClose(), event.getOpen(), priceGrowthFactor);
            addCandlestickEventToCache(ticker, event);
//
//            var currentEvent = cachedCandlestickEvents.get(ticker).getLast();
//            var previousEvent = cachedCandlestickEvents.get(ticker).getFirst();

            if (//parsedDouble(currentEvent.getVolume()) > parsedDouble(previousEvent.getVolume()) * volumeGrowthFactor &&
                    parsedDouble(event.getClose()) > parsedDouble(event.getOpen()) * priceGrowthFactor) {
                buyFast(ticker, parsedDouble(event.getClose()), tradingAsset);
            }
        };
    }

    private BinanceApiCallback<CandlestickEvent> longPositionMonitoringCallback(String ticker) {
        return event -> {
//            addCandlestickEventToCache(ticker, event);
//
//            List<AssetBalance> currentBalances = accountManager.getAccountBalances().stream()
//                    .filter(balance -> !(balance.getAsset().equals("USDT") || balance.getAsset().equals("BNB"))).toList();
//
//            if (currentBalances.isEmpty()) {
//                log.warn("No available trading assets found on binance account, but long position monitoring for '{}' is still executing.", ticker);
//                return;
//            }
//
//            Optional.ofNullable(longPositions.get(ticker)).ifPresent(openedPosition -> {
//                var assetPrice = parsedDouble(event.getClose());
//
////                if (assetPrice > openedPosition.maxPrice()) { // update current price if it's growth.
//                marketData.updateOpenedPosition(ticker, assetPrice, longPositions);
////                }
//                if (assetPrice < openedPosition.maxPrice() * priceDecreaseFactor) {
//                    log.debug("PRICE of {} DECREASED and now equals {}.", ticker, assetPrice);
//                    sellFast(ticker, openedPosition.qty(), tradingAsset);
////                } else if (averagingEnabled && assetPrice > openedPosition.avgPrice() * averagingTriggerFactor) {
////                    log.debug("PRICE of {} GROWTH more than avg and now equals {}.", ticker, assetPrice);
////                    buyFast(ticker, assetPrice, tradingAsset);
//                }
//            });
        };
    }

    private void addCandlestickEventToCache(String ticker, CandlestickEvent candlestickEvent) {
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

    private void buyFast(String symbol, Double price, String quoteAsset) {
        if (!(marketInfo.pairOrderIsProcessing(symbol)
                || thisSignalWorkedOutBefore(symbol)
        )) {
            log.info("[DAILY] price of {} growth more than {}%, and now equals {}.", symbol, 100 * priceGrowthFactor - 100, price);
            marketInfo.pairOrderPlaced(symbol);
            marketData.putLongPositionToPriceMonitoring(symbol, price, 1.0);

//            spotTrading.placeBuyOrderFast(symbol, price, quoteAsset, accountManager);
        }
    }

    public void addSellRecordToJournal(String pair) {
        sellJournal.put(pair, LocalDateTime.now());
    }

    public boolean thisSignalWorkedOutBefore (String pair) {
        AtomicBoolean ignoreSignal = new AtomicBoolean(false);

        Optional.ofNullable(sellJournal.get(pair)).ifPresent(dealTime -> {
            if (dealTime.getDayOfYear() <= LocalDateTime.now().getDayOfYear()) {
                ignoreSignal.set(true);
            } else {
//                log.info("Period of signal ignoring for {} expired, remove pair from sell journal.", pair);
//                sellJournal.remove(pair);
                marketInfo.pairOrderFilled(pair);
            }
        });

        return ignoreSignal.get();
    }

    public void closeOpenedWebSocketStreams() {
        candleStickEventsStreams.forEach((pair, stream) -> {
            try {
                stream.close();
                log.debug("WebStream of '{}' closed.", pair);
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        });
        candleStickEventsStreams.clear();
    }

    @PreDestroy
    public void destroy() {
        closeOpenedWebSocketStreams();
    }
}
