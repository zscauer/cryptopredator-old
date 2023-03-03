package ru.tyumentsev.cryptopredator.commons.domain;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

public record SellRecord (
        String symbol,
        LocalDateTime sellTime,
        String strategy
){}
