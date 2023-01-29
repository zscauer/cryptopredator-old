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

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class MarketInfo implements TradingService {

    BinanceApiRestClient restClient;
    BinanceApiWebSocketClient binanceApiWebSocketClient;

    /**
     * Store flags, which indicates that order already placed.
     */
    @Getter
    Map<String, Boolean> processedOrders = new ConcurrentHashMap<>();
    Map<String, List<String>> availablePairs = new HashMap<>();
    @Getter
    Map<String, List<String>> cheapPairs = new ConcurrentHashMap<>();

    String QUERY_SYMBOLS_BEGIN = "[\"", DELIMITER = "\",\"", QUERY_SYMBOLS_END = "\"]"; // required format is "["BTCUSDT","BNBUSDT"]".

    public List<String> getAvailableTradePairs(final String quoteAsset) {
        List<String> pairs = restClient.getExchangeInfo().getSymbols().stream()
                .filter(symbolInfo -> symbolInfo.getStatus() == SymbolStatus.TRADING
                        && symbolInfo.getQuoteAsset().equalsIgnoreCase(quoteAsset)
                        && symbolInfo.isSpotTradingAllowed())
                .map(SymbolInfo::getSymbol)
                .collect(Collectors.toList());
        availablePairs.put(quoteAsset, pairs);
        return pairs;
    }

    /**
     * Get all pairs, that trades against asset and return only cheaper than maximalPairPrice.
     *
     * @param asset
     * @return
     */
    public void fillCheapPairs(String asset, float maximalPairPrice) {
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

    /**
     *
     * @param asset
     * @return list of cheap pairs, exclude pairs of opened positions.
     */
    public List<String> getCheapPairsExcludeOpenedPositions(String asset, Set<String> longPositions, Set<String> shortPositions) {
        List<String> pairs = cheapPairs.getOrDefault(asset, Collections.emptyList());
        pairs.removeAll(longPositions);
        pairs.removeAll(shortPositions);

        return pairs;
    }

    public TickerPrice getLastTickerPrice(String symbol) {
        return restClient.getPrice(symbol);
    }

    public List<TickerPrice> getLastTickersPrices(String symbols) {
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

    public boolean pairOrderIsProcessing(String symbol) {
        return Optional.ofNullable(processedOrders.get(symbol)).orElse(false);
    }

    public void pairOrderPlaced(String symbol) {
        processedOrders.put(symbol, true);
        log.debug("Add {} to processed orders.", symbol);

    }

    public void pairOrderFilled(String symbol) {
        processedOrders.remove(symbol);
        log.debug("Remove {} from processed orders.", symbol);
    }
}