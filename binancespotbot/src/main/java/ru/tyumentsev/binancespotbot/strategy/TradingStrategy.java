package ru.tyumentsev.binancespotbot.strategy;

import com.binance.api.client.domain.event.OrderTradeUpdateEvent;

public interface TradingStrategy {

    /**
     * Active status of strategy.
     * @return true if straregy enabled.
     */
    boolean isEnabled();

    /**
     * Additional logic when buy order successfully executed.
     */
    void handleBuying(final OrderTradeUpdateEvent event);

    /**
     * Additional logic when sell order successfully executed.
     */
    void handleSelling(final OrderTradeUpdateEvent event);

}
