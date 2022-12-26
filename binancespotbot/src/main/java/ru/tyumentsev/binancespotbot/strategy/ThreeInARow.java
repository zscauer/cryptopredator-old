package ru.tyumentsev.binancespotbot.strategy;

import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class ThreeInARow implements TradingStrategy {

    @Override
    public void handleBuying(OrderTradeUpdateEvent event) {

    }

    @Override
    public void handleSelling(OrderTradeUpdateEvent event) {

    }
}
