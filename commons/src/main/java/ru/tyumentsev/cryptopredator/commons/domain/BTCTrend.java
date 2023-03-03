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
    volatile Candlestick lastCandle;

    public BTCTrend(CandlestickInterval candlestickInterval) {
        interval = candlestickInterval;
    }

    public boolean isBullish() {
        return Float.parseFloat(lastCandle.getClose()) > Float.parseFloat(lastCandle.getOpen());
    }

    public boolean isBearish() {
        return !isBullish();
    }
}
