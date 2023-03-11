package ru.tyumentsev.cryptopredator.commons.domain;

import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
@ToString
@FieldDefaults(level = AccessLevel.PROTECTED)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings("unused")
public class BTCTrend implements Serializable {

    @JsonProperty
    String symbol = "BTCUSDT";
    @JsonProperty
    CandlestickInterval interval;
    @JsonProperty
    List<Candlestick> lastCandles = new ArrayList<>();

    public BTCTrend(CandlestickInterval candlestickInterval) {
        interval = candlestickInterval;
    }

    public void setLastCandles (final List<Candlestick> candles) {
        lastCandles.clear();
        lastCandles.addAll(candles);
    }

    public boolean isBullish() {
        var bullish = new AtomicBoolean(true);
        lastCandles.forEach(candlestick -> {
            if (Float.parseFloat(candlestick.getClose()) < Float.parseFloat(candlestick.getOpen())) {
                bullish.set(false);
            }
        });

        return bullish.get();
    }

    public boolean isBearish() {
        return !isBullish();
    }
}
