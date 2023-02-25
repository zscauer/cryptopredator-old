package ru.tyumentsev.cryptopredator.commons;

import com.binance.api.client.domain.event.OrderTradeUpdateEvent;

public interface TradingStrategy {

    default float parsedFloat(final String stringToParse) {
        return Float.parseFloat(stringToParse);
    }

    default float percentageDifference(float bigger, float smaller) {
        return 100 * (bigger - smaller) / bigger;
    }

    String getName();

    Integer getId();

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

