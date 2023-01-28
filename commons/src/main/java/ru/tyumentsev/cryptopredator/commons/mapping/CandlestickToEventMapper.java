package ru.tyumentsev.cryptopredator.commons.mapping;

import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.market.Candlestick;

import java.util.Optional;

public class CandlestickToEventMapper {

    public static Optional<CandlestickEvent> map(String symbol, Candlestick candlestick) {
        return Optional.ofNullable(candlestick).map(candle -> {
            CandlestickEvent event = new CandlestickEvent();

            // null values:
            event.setEventTime(0L);
            event.setFirstTradeId(0L);
            event.setLastTradeId(0L);
            event.setBarFinal(false);
            //

            event.setSymbol(symbol);
            event.setOpenTime(candlestick.getOpenTime());
            event.setOpen(candlestick.getOpen());
            event.setHigh(candlestick.getHigh());
            event.setLow(candlestick.getLow());
            event.setClose(candlestick.getClose());
            event.setVolume(candlestick.getVolume());
            event.setCloseTime(candlestick.getCloseTime());
            event.setQuoteAssetVolume(candlestick.getQuoteAssetVolume());
            event.setNumberOfTrades(candlestick.getNumberOfTrades());
            event.setTakerBuyBaseAssetVolume(candlestick.getTakerBuyBaseAssetVolume());
            event.setTakerBuyQuoteAssetVolume(candlestick.getTakerBuyQuoteAssetVolume());

            return event;
        });

    }

    public static Optional<Candlestick> deMap(CandlestickEvent candlestickEvent) {
        return Optional.ofNullable(candlestickEvent).map(event -> {
            Candlestick candlestick = new Candlestick();

            candlestick.setOpenTime(candlestickEvent.getOpenTime());
            candlestick.setOpen(candlestickEvent.getOpen());
            candlestick.setHigh(candlestickEvent.getHigh());
            candlestick.setLow(candlestickEvent.getLow());
            candlestick.setClose(candlestickEvent.getClose());
            candlestick.setVolume(candlestickEvent.getVolume());
            candlestick.setCloseTime(candlestickEvent.getCloseTime());
            candlestick.setQuoteAssetVolume(candlestickEvent.getQuoteAssetVolume());
            candlestick.setNumberOfTrades(candlestickEvent.getNumberOfTrades());
            candlestick.setTakerBuyBaseAssetVolume(candlestickEvent.getTakerBuyBaseAssetVolume());
            candlestick.setTakerBuyQuoteAssetVolume(candlestickEvent.getTakerBuyQuoteAssetVolume());

            return candlestick;
        });
    }
}