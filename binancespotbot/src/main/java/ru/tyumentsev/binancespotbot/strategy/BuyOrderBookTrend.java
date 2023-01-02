package ru.tyumentsev.binancespotbot.strategy;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;

import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.market.OrderBookEntry;
import lombok.NonNull;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiWebSocketClient;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.binancespotbot.cache.MarketData;
import ru.tyumentsev.binancespotbot.domain.Interest;

import javax.annotation.PreDestroy;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BuyOrderBookTrend implements TradingStrategy {

    BinanceApiWebSocketClient binanceApiWebSocketClient;
    MarketData marketData;
    Map<String, Closeable> webSocketStreams;

    @NonFinal
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;

    private static Double parsedDouble(String stringToParse) {
        return Double.parseDouble(stringToParse);
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void handleBuying(final OrderTradeUpdateEvent event) {

    }

    @Override
    public void handleSelling(final OrderTradeUpdateEvent event) {

    }

    public void generateWebSocketStreams() {
        List<String> cheapPairs = marketData.getCheapPairsExcludeOpenedPositions(tradingAsset).subList(0, 5);
        // fill asks and bids to analize.
        Map<String, Interest> openInterest = marketData.getOpenInterest();
        for (String pair : cheapPairs) {
            openInterest.put(pair, Interest.of(pair));
            webSocketStreams.put(pair, getNewWebSocketStream(pair, openInterest.get(pair)));
        }
    }

    private Closeable getNewWebSocketStream(@NonNull String pair, Interest interest) {
        return binanceApiWebSocketClient.onDepthEvent(pair.toLowerCase(), response -> {
                    Queue<OrderBookEntry> asks = interest.getAsks();
                    if (asks.size() + response.getAsks().size() > 10) {
                        for (int counter = response.getAsks().size(); counter > 0; counter--) {
                            log.debug("OrderBookEntry asks '{}' removed from fully queue of {}, adding '{}'",
                                    asks.poll(), interest.getSymbol(), response.getAsks());
                        }
                    }
                    asks.addAll(response.getAsks());

                    Queue<OrderBookEntry> bids = interest.getBids();
                    if (bids.size() + response.getBids().size() > 10) {
                        for (int counter = response.getBids().size(); counter > 0; counter--) {
                            log.debug("OrderBookEntry bids '{}' removed from fully queue of {}, adding '{}'",
                                    bids.poll(), interest.getSymbol(), response.getBids());
                        }
                    }
                    bids.addAll(response.getBids());

                    log.debug("{} queue was updated and contains {} bids and {} asks.", interest.getSymbol(), bids.size(), asks.size());
                }
        );
    }

    // взять очередь заявок, отсортировать по цене
    // сопоставить объемы лучших асков с лучишими бидами
    // объем первых двух бидов должен превысить объем 10 басков
    public void analizeInterest() {
        Map<String, Interest> openInterest = marketData.getOpenInterest();
        for (Map.Entry<String, Interest> entry : openInterest.entrySet()) {
            Interest interest = entry.getValue();

            Double asksVolumeSummary = interest.getAsks().stream()
                    .map(orderBookEntry -> parsedDouble(orderBookEntry.getQty()))
                    .reduce(0D, Double::sum);
//            Double asksPriceAvg = interest.getAsks().stream()
//                    .map(orderBookEntry -> parsedDouble(orderBookEntry.getPrice()))
//                    .reduce(0D, Double::sum) / interest.getAsks().size();

            Double bestBidsVolume = interest.getBids().stream()
                    .sorted((e1, e2) -> Double.compare(parsedDouble(e2.getPrice()), parsedDouble(e1.getPrice())))
                    .limit(2)
                    .map(orderBookEntry -> parsedDouble(orderBookEntry.getQty()))
                    .reduce(0D, Double::sum);

            log.info("For {} volumes are: best bids - '{}, asks - '{}'", entry.getKey(), bestBidsVolume, asksVolumeSummary);

            if (bestBidsVolume > asksVolumeSummary) {
                log.info("!!!!! Volume of best bids ({}) of {} is bigger then summary of asks volume ({}).",
                        bestBidsVolume, entry.getKey(), asksVolumeSummary);
            }
        }
    }

    public void closeOpenedWebSocketStreams() {
        webSocketStreams.forEach((pair, stream) -> {
            try {
                stream.close();
                log.debug("WebStream of '{}' closed.", pair);
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
