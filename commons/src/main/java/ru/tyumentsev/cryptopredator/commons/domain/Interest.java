package ru.tyumentsev.cryptopredator.commons.domain;

import com.binance.api.client.domain.market.OrderBookEntry;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.LinkedList;
import java.util.Queue;

@Getter
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class Interest {
    String symbol;
    Queue<OrderBookEntry> bids = new LinkedList<>(); // buy orders.
    Queue<OrderBookEntry> asks = new LinkedList<>(); // sell orders.

    public static Interest of(String symbol) {
        return new Interest(symbol);
    }

    private Interest(String symbol) {
        this.symbol = symbol;
    }
}
