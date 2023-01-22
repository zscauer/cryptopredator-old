package ru.tyumentsev.binancetradebot.binancespotbot.domain;

import com.binance.api.client.domain.event.CandlestickEvent;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

@RedisHash("PreviousCandleData")
public record PreviousCandleData(
        @Id String id,
        CandlestickEvent event
) {}
