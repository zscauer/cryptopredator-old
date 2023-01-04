package ru.tyumentsev.binancespotbot.strategy;

import com.binance.api.client.domain.event.OrderTradeUpdateEvent;

public interface TradingStrategy {

    default Double parsedDouble(String stringToParse) {
        return Double.parseDouble(stringToParse);
    }

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
