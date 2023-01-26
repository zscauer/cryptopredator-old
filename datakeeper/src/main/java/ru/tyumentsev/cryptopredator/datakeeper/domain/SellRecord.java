package ru.tyumentsev.cryptopredator.datakeeper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.time.LocalDateTime;

@RedisHash("SellRecord")
public record SellRecord(
        @Id String symbol,
        LocalDateTime sellTime
) {}
