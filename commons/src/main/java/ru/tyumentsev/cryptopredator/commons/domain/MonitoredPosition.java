package ru.tyumentsev.cryptopredator.commons.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Accessors(fluent = true)
@Getter
@ToString
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MonitoredPosition {

    @JsonProperty
    String symbol;

    @JsonProperty
    float price;

    @JsonProperty
    ZonedDateTime beginMonitoringTime;

    @JsonProperty
    @NonFinal
    @Setter
    volatile int weight;

}
