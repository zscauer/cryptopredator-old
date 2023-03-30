package ru.tyumentsev.cryptopredator.commons.domain;

import com.binance.api.client.domain.OrderSide;

public record PlacedOrder(
        String symbol,
        Integer strategyId,
        float qty,
        OrderSide side
) {}
