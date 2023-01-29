package ru.tyumentsev.cryptopredator.commons.domain;

import java.time.LocalDateTime;

public record SellRecord(
        String symbol,
        LocalDateTime sellTime,
        String strategy
) {}
