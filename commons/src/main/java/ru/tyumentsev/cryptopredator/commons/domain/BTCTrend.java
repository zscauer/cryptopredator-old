package ru.tyumentsev.cryptopredator.commons.domain;

import com.binance.api.client.domain.Candle;
import com.binance.api.client.domain.market.CandlestickInterval;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class BTCTrend {

    String symbol = "BTCUSDT";
    CandlestickInterval interval;
    @Setter
    @NonFinal
    volatile Candle lastCandle;

    public boolean isBullish() {
        return Float.parseFloat(lastCandle.getClose()) > Float.parseFloat(lastCandle.getOpen());
    }

    public boolean isBearish() {
        return !isBullish();
    }
}
