package ru.tyumentsev.cryptopredator.commons.service;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.event.CandlestickEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.general.SymbolStatus;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerPrice;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import ru.tyumentsev.cryptopredator.commons.domain.PlacedOrder;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
@Slf4j
@SuppressWarnings("unused")
public class MarketInfo implements TradingService {

    BinanceApiRestClient restClient;
    BinanceApiWebSocketClient binanceApiWebSocketClient;

    List<String> fiatAndStableCoins = List.of("EURUSDT", "AUDUSDT", "GBPUSDT", "BUSDUSDT", "USDCUSDT", "USDPUSDT", "BNBUSDT");
    /**
     * Store flags, which indicates that order already placed.
     */
    @Getter
    Map<String, PlacedOrder> placedOrders = new ConcurrentHashMap<>();
    Map<String, List<String>> availablePairs = new HashMap<>();
    @Getter
    Map<String, List<String>> cheapPairs = new ConcurrentHashMap<>();

    String QUERY_SYMBOLS_BEGIN = "[\"", DELIMITER = "\",\"", QUERY_SYMBOLS_END = "\"]"; // required format is "["BTCUSDT","BNBUSDT"]".

    public List<String> getAvailableTradePairs(final String quoteAsset) {
        List<String> pairs = restClient.getExchangeInfo().getSymbols().stream()
                .filter(symbolInfo -> SymbolStatus.TRADING.equals(symbolInfo.getStatus())
                        && symbolInfo.getQuoteAsset().equalsIgnoreCase(quoteAsset)
                        && symbolInfo.isSpotTradingAllowed()
                        && !fiatAndStableCoins.contains(symbolInfo.getSymbol()))
                .map(SymbolInfo::getSymbol)
                .collect(Collectors.toList());
        availablePairs.put(quoteAsset, pairs);
        return pairs;
    }

    public void fillCheapPairs(final String asset, final float maximalPairPrice) {
        List<String> filteredPairs = getLastTickersPrices(
                        combinePairsToRequestString(availablePairs.get(asset)))
                .stream().filter(tickerPrice -> Float.parseFloat(tickerPrice.getPrice()) < maximalPairPrice)
                .map(TickerPrice::getSymbol).collect(Collectors.toCollection(ArrayList::new));
        log.info("Filtered {} cheap tickers.", filteredPairs.size());
        cheapPairs.put(asset, filteredPairs);
    }

    public String combinePairsToRequestString(List<String> pairs) {
        return pairs.stream()
                .collect(Collectors.joining(DELIMITER, QUERY_SYMBOLS_BEGIN, QUERY_SYMBOLS_END));
    }

    public List<String> getCheapPairsExcludeOpenedPositions(String asset, Set<String> longPositions, Set<String> shortPositions) {
        List<String> pairs = cheapPairs.getOrDefault(asset, Collections.emptyList());
        pairs.removeAll(longPositions);
        pairs.removeAll(shortPositions);

        return pairs;
    }

    public TickerPrice getLastTickerPrice(String symbol) {
        return restClient.getPrice(symbol);
    }

    public List<TickerPrice> getLastTickersPrices(final String symbols) {
        return restClient.getPrices(symbols);
    }

    public boolean pairHadTradesInThePast(List<Candlestick> candleSticks, int qtyBarsToAnalize) {
        // pair should have history of trade for some days before.
        return candleSticks.size() == qtyBarsToAnalize;
    }

    public boolean pairHadTradesInThePast(String ticker, CandlestickInterval interval, Integer qtyBarsToAnalize) {
        // pair should have history of trade for some days before.
        return pairHadTradesInThePast(getCandleSticks(ticker, interval, qtyBarsToAnalize), qtyBarsToAnalize);
    }

    public List<Candlestick> getCandleSticks(String symbol, CandlestickInterval interval, Integer limit) {
        return restClient.getCandlestickBars(symbol, interval, limit);
    }

    public Closeable openCandleStickEventsStream(String asset, CandlestickInterval interval, BinanceApiCallback<CandlestickEvent> callback) {
        return binanceApiWebSocketClient.onCandlestickEvent(asset, interval, callback);
    }

    public boolean pairOrderIsProcessing(String symbol, Integer strategyId) {
        return Optional.ofNullable(placedOrders.get(symbol))
                .map(order -> order.strategyId().equals(strategyId))
                .orElse(false);
    }

    public void pairOrderPlaced(String symbol, final Integer strategyId, float qty, final OrderSide side) {
        placedOrders.put(symbol, new PlacedOrder(symbol, strategyId, qty, side));
        log.debug("Add {} to processed orders.", symbol);

    }

    public void pairOrderFilled(String symbol, Integer strategyId) {
        Optional.ofNullable(placedOrders.get(symbol)).ifPresent(order -> {
            if (order.strategyId().equals(strategyId)) {
                placedOrders.remove(symbol);
            }
        });
        log.debug("Remove {} from processed orders.", symbol);
    }
}