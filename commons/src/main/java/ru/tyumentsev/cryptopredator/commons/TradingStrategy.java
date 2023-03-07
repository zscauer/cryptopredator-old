package ru.tyumentsev.cryptopredator.commons;

import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBarSeries;

import java.util.Comparator;
import java.util.Optional;

public interface TradingStrategy {

    default float parsedFloat(final String stringToParse) {
        return Float.parseFloat(stringToParse);
    }

    default float percentageDifference(final float bigger, final float smaller) {
        return 100 * (bigger - smaller) / bigger;
    }

    /**
     * Search bar with lowest close price to define initial stop price.
     * @param series source where to search bar with lowest price.
     * @return {@link Bar} with lowest close price.
     */
    default Optional<Bar> lowestClosePrice(final BaseBarSeries series) {
        return series.getBarData().stream().min(Comparator.comparing(Bar::getClosePrice));
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

