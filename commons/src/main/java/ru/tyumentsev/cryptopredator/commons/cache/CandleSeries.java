package ru.tyumentsev.cryptopredator.commons.cache;

import com.binance.api.client.domain.Candle;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.Deque;
import java.util.LinkedList;

@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class CandleSeries {

    @Getter
    @JsonProperty
    int seriesSize;

    @Getter
    @JsonProperty
    String symbol;

    @JsonProperty
    Deque<Candle> candlestickSeries = new LinkedList<>();

    public void addCandleToSeries(Candle candle) {
        Candle lastCandle = candlestickSeries.peekLast();
        if (lastCandle != null && lastCandle.getOpenTime().equals(candle.getOpenTime())) {
            candlestickSeries.remove(lastCandle); // remove previous version of this event.
        }
        candlestickSeries.addLast(candle);

        if (candlestickSeries.size() > seriesSize) {
            candlestickSeries.removeFirst();
        }
    }

    public Candle getFirst() {
        return candlestickSeries.getFirst();
    }
}
