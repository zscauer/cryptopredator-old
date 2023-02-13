package ru.tyumentsev.cryptopredator.commons.domain;

import java.time.ZonedDateTime;

public record SellRecord (
        String symbol,
        ZonedDateTime sellTime,
        String strategy
){}
