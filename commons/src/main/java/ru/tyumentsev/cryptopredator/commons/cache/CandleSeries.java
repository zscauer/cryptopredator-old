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
import java.util.Optional;

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
        Optional.ofNullable(candlestickSeries.peekLast()).ifPresentOrElse(lastCandle -> {
            if (lastCandle.getOpenTime().equals(candle.getOpenTime())) { // refreshed candle event.
                candlestickSeries.remove(lastCandle); // remove previous version of this event.
            }
            candlestickSeries.addLast(candle);
        }, () -> candlestickSeries.addLast(candle));

        if (candlestickSeries.size() > seriesSize) {
            candlestickSeries.removeFirst();
        }
    }

    public Candle getFirst() {
        return candlestickSeries.getFirst();
    }
}
