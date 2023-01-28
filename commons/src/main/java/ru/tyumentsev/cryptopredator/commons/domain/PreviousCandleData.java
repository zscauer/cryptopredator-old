package ru.tyumentsev.cryptopredator.commons.domain;

import com.binance.api.client.domain.event.CandlestickEvent;

public record PreviousCandleData(
        String id,
        CandlestickEvent event
) {}
