package ru.tyumentsev.binancespotbot.strategy;

import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tyumentsev.binancespotbot.cache.MarketData;
import ru.tyumentsev.binancespotbot.domain.OpenedPosition;
import ru.tyumentsev.binancespotbot.service.AccountManager;

import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class Averaging implements TradingStrategy {

    // усреднять открытые позиции, выросшие больше чем на 10%
    AccountManager accountManager;
    MarketData marketData;

    @Override
    public void handleBuying(OrderTradeUpdateEvent event) {

    }

    @Override
    public void handleSelling(OrderTradeUpdateEvent event) {

    }

    public void initializePriceChanges(){
        Map<String, OpenedPosition> openedPositions = marketData.getLongPositions();
    }

}
