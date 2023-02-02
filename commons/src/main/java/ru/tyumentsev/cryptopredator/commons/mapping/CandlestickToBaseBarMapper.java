package ru.tyumentsev.cryptopredator.commons.mapping;

import com.binance.api.client.domain.Candle;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarBuilder;
import org.ta4j.core.ConvertibleBaseBarBuilder;
import org.ta4j.core.num.DoubleNum;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class CandlestickToBaseBarMapper {

    private static final BaseBarBuilder baseBarBuilder = BaseBar.builder();

    public static Bar map(final Candle candle, final CandlestickInterval interval) {
        final ConvertibleBaseBarBuilder<String> convertibleBaseBarBuilder = BaseBar.builder(DoubleNum::valueOf, String.class);

        convertibleBaseBarBuilder.timePeriod(Duration.parse(interval.getDurationIntervalId()))
                .endTime(ZonedDateTime.ofInstant(Instant.ofEpochMilli(candle.getCloseTime()), ZoneId.systemDefault()))
                .openPrice(candle.getOpen())
                .closePrice(candle.getClose())
                .highPrice(candle.getHigh())
                .lowPrice(candle.getLow())
                .amount(candle.getQuoteAssetVolume())
                .volume(candle.getVolume())
                .trades(candle.getNumberOfTrades());

        return convertibleBaseBarBuilder.build();
    }

    public static List<Bar> map(final Collection<? extends Candle> candlesticks, final CandlestickInterval interval) {
        final ConvertibleBaseBarBuilder<String> convertibleBaseBarBuilder = BaseBar.builder(DoubleNum::valueOf, String.class);

        return candlesticks.stream().map(candle ->
                        convertibleBaseBarBuilder.timePeriod(Duration.parse(interval.getDurationIntervalId()))
                                .endTime(ZonedDateTime.ofInstant(Instant.ofEpochMilli(candle.getCloseTime()), ZoneId.systemDefault()))
                                .openPrice(candle.getOpen())
                                .closePrice(candle.getClose())
                                .highPrice(candle.getHigh())
                                .lowPrice(candle.getLow())
                                .amount(candle.getQuoteAssetVolume())
                                .volume(candle.getVolume())
                                .trades(candle.getNumberOfTrades())
                                .build())
                .collect(Collectors.toList());
    }

    public static Candlestick deMap(final Bar bar) {
        Candlestick candlestick = new Candlestick();

        candlestick.setOpenTime(bar.getBeginTime().toEpochSecond());
        candlestick.setCloseTime(bar.getEndTime().toEpochSecond());
        candlestick.setOpen(bar.getOpenPrice().toString());
        candlestick.setClose(bar.getClosePrice().toString());
        candlestick.setHigh(bar.getHighPrice().toString());
        candlestick.setLow(bar.getLowPrice().toString());
        candlestick.setQuoteAssetVolume(bar.getVolume().toString());
        candlestick.setVolume(bar.getVolume().toString());
        candlestick.setNumberOfTrades(bar.getTrades());

        return candlestick;
    }

    public static List<? extends Candle> deMap(final Collection<Bar> bars) {
        return bars.stream().map(bar -> {
            Candlestick candlestick = new Candlestick();
            candlestick.setOpenTime(bar.getBeginTime().toEpochSecond());
            candlestick.setCloseTime(bar.getEndTime().toEpochSecond());
            candlestick.setOpen(bar.getOpenPrice().toString());
            candlestick.setClose(bar.getClosePrice().toString());
            candlestick.setHigh(bar.getHighPrice().toString());
            candlestick.setLow(bar.getLowPrice().toString());
            candlestick.setQuoteAssetVolume(bar.getVolume().toString());
            candlestick.setVolume(bar.getVolume().toString());
            candlestick.setNumberOfTrades(bar.getTrades());

            return candlestick;
        }).collect(Collectors.toList());
    }
}
