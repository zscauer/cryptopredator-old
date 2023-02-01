package ru.tyumentsev.cryptopredator.commons.domain;

import com.binance.api.client.domain.OrderSide;

public record PlacedOrder(
        String symbol,
        String strategyName,
        float qty,
        OrderSide side
) {}
