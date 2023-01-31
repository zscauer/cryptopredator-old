package ru.tyumentsev.cryptopredator.commons.domain;

import com.binance.api.client.domain.OrderSide;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

public record PlacedOrder(
        String symbol,
        String strategyName,
        float qty,
        OrderSide side
) {}
