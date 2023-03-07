package ru.tyumentsev.cryptopredator.commons.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.time.LocalDateTime;

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
    @JsonProperty
    volatile LocalDateTime updateStamp; //threads debug
    @JsonProperty
    volatile String threadName; //threads debug

}
