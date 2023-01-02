package ru.tyumentsev.binancespotbot.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@ToString
@Accessors(fluent = true, chain = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor(staticName = "of")
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenedPosition {

    @JsonProperty
    final String symbol;
    @JsonProperty
    Double qty;
    @JsonProperty
    Double lastPrice;
    @JsonProperty
    Double maxPrice;
    @JsonProperty
    Double avgPrice;

}
