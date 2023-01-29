package ru.tyumentsev.cryptopredator.multitimeframebot.strategy;

import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tyumentsev.cryptopredator.commons.TradingStrategy;
import ru.tyumentsev.cryptopredator.commons.service.DataService;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.commons.service.SpotTrading;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class Multitimeframe implements TradingStrategy {

    final MarketInfo marketInfo;
    final SpotTrading spotTrading;
    final DataService dataService;

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void prepareData() {

    }

    @Override
    public void handleBuying(OrderTradeUpdateEvent orderTradeUpdateEvent) {

    }

    @Override
    public void handleSelling(OrderTradeUpdateEvent orderTradeUpdateEvent) {

    }
}
