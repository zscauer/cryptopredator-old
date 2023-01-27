package ru.tyumentsev.cryptopredator.dailyvolumesbot.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serializable;

@Getter
@Setter
@ToString
@Accessors(fluent = true, chain = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@RedisHash("OpenedPosition")
public class OpenedPosition implements Serializable {

    @JsonProperty
    @Id
    String symbol;
    @JsonProperty
    boolean rocketCandidate;
    @JsonProperty
    Float qty;
    @JsonProperty
    Float avgPrice;
    @JsonProperty
    Float lastPrice;
    @JsonProperty
    Float maxPrice;
    @JsonProperty
    Float priceDecreaseFactor;

}
