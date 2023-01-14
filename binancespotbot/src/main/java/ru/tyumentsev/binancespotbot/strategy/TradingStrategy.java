package ru.tyumentsev.binancespotbot.strategy;

import com.binance.api.client.domain.event.OrderTradeUpdateEvent;

public interface TradingStrategy {

    default double parsedDouble(String stringToParse) {
        return Double.parseDouble(stringToParse);
    }

    default double percentageDifference(double bigger, double smaller) {
        return 100 * (bigger - smaller) / bigger;
    }

    /**
     * Active status of strategy.
     * @return true if straregy enabled.
     */
    boolean isEnabled();

    void prepareData();

    /**
     * Additional logic when buy order successfully executed.
     */
    void handleBuying(final OrderTradeUpdateEvent buyEvent);

    /**
     * Additional logic when sell order successfully executed.
     */
    void handleSelling(final OrderTradeUpdateEvent sellEvent);

}
