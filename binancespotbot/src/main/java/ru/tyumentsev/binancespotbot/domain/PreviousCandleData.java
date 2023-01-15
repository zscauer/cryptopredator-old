package ru.tyumentsev.binancespotbot.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

@RedisHash("PreviousCandleData")
public record PreviousCandleData(
        @Id String id,
        String symbol,
        Long openTime,
        String volume) {
}
