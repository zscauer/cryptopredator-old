package ru.tyumentsev.cryptopredator.commons.domain;

import com.binance.api.client.domain.OrderSide;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@Getter
@Setter
@ToString
@Accessors(fluent = true, chain = true)
@FieldDefaults(level = AccessLevel.PROTECTED)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenedPosition implements Serializable {

    @JsonProperty
    String symbol;
    @JsonProperty
    String strategy;
    @JsonProperty
    boolean rocketCandidate;
    @JsonProperty
    volatile Float qty;
    @JsonProperty
    volatile Float avgPrice;
    @JsonProperty
    volatile Float lastPrice;
    @JsonProperty
    volatile Float maxPrice;
    @JsonProperty
    volatile Float priceDecreaseFactor; // indicatorvirginbot
    @JsonProperty
    volatile Float stopPrice; //bigasscandlesbot
    @JsonProperty
    volatile Float takePrice; //bigasscandlesbot
    @JsonProperty
    volatile Float trendPriceStep; //bigasscandlesbot
    volatile int lastBarSeriesIndex; //bigasscandlesbot
    @JsonIgnore
    volatile LocalDateTime updateStamp; //threads debug
    @JsonProperty
    volatile String threadStatus; //threads debug

    @JsonIgnore
    public boolean isProfitable() {
        return stopPrice > avgPrice && lastPrice > avgPrice;
    }

    public void updateLastPrice(final float lastPrice) {
        lastPrice(lastPrice);
        if (lastPrice > maxPrice) {
            maxPrice(lastPrice);
        }
        updateStamp(LocalDateTime.now());
        threadStatus(String.format("[%s] %s:%s", updateStamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), Thread.currentThread().getName(), Thread.currentThread().getId()));
    }

    public float calculateFutureAvgPrice(int orderVolume, final OrderSide side) {
        var dealQty = Math.ceil(orderVolume / lastPrice);
        float futureAvgPrice = 0;
        switch (side) {
            case BUY -> futureAvgPrice = (float) ((avgPrice * qty + lastPrice * dealQty) / (qty + dealQty));
            case SELL -> futureAvgPrice = (float) ((avgPrice * qty - lastPrice * dealQty) / (qty - dealQty));
        }
        return futureAvgPrice;
    }

}
