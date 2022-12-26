package ru.tyumentsev.binancespotbot.strategy;

import com.binance.api.client.domain.event.OrderTradeUpdateEvent;

public interface TradingStrategy {

    /**
     * Additional logic when buy order successfully executed.
     */
    void handleBuying(OrderTradeUpdateEvent event);
    /**
     * Additional logic when sell order successfully executed.
     */
    void handleSelling(OrderTradeUpdateEvent event);

}
