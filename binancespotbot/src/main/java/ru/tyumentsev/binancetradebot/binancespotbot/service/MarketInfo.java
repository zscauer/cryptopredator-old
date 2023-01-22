package ru.tyumentsev.binancetradebot.binancespotbot.service;

import java.io.Closeable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.event.CandlestickEvent;
import lombok.Getter;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.general.SymbolStatus;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerPrice;
import com.binance.api.client.domain.market.TickerStatistics;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketInfo {

    BinanceApiRestClient restClient;
    BinanceApiWebSocketClient binanceApiWebSocketClient;

    /**
     * Store flags, which indicates that order already placed.
     */
    Map<String, Boolean> processedOrders = new ConcurrentHashMap<>();

    public List<String> getAvailableTradePairs(final String quoteAsset) {
        return restClient.getExchangeInfo().getSymbols().stream()
                .filter(symbolInfo -> symbolInfo.getStatus() == SymbolStatus.TRADING
                        && symbolInfo.getQuoteAsset().equalsIgnoreCase(quoteAsset)
                        && symbolInfo.isSpotTradingAllowed())
                .map(SymbolInfo::getSymbol)
                .collect(Collectors.toList());
    }

    public List<TickerStatistics> getTickers24HrPriceStatistics(String symbols) {
        return restClient.getVariousTicker24HrPriceStatistics(symbols);
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
    }

    public void pairOrderFilled(String symbol) {
        processedOrders.remove(symbol);
    }

}
