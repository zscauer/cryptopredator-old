package ru.tyumentsev.cryptopredator.macsaw.strategy;

import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import jakarta.inject.Singleton;
import ru.tyumentsev.cryptopredator.commons.TradingStrategy;

@Singleton
public class MacSaw implements TradingStrategy {
    @Override
    public String getName() {
        return "Mac saw";
    }

    @Override
    public Integer getId() {
        return null;
    }

    @Override
    public boolean isEnabled() {
        return true;
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
