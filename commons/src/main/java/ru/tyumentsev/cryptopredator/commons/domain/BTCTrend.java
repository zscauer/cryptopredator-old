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
    final String symbol = "BTCUSDT";
    @JsonProperty
    CandlestickInterval interval;
    @JsonProperty
    final List<Candlestick> lastCandles = new ArrayList<>();
    volatile boolean bullish = true;

    public BTCTrend(CandlestickInterval candlestickInterval) {
        interval = candlestickInterval;
    }

    public void setLastCandles(final List<Candlestick> candles) {
        lastCandles.clear();
        lastCandles.addAll(candles);
        lastCandles.forEach(candlestick -> {
            if (Float.parseFloat(candlestick.getClose()) < Float.parseFloat(candlestick.getOpen())) {
                bullish = false;
            }
        });
    }

    public boolean isBearish() {
        return !isBullish();
    }
}
