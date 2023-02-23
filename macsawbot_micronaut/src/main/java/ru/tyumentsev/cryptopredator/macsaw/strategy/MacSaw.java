package ru.tyumentsev.cryptopredator.macsaw.strategy;

import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import ru.tyumentsev.cryptopredator.commons.TradingStrategy;
import ru.tyumentsev.cryptopredator.commons.service.BotStateService;
import ru.tyumentsev.cryptopredator.commons.service.DataService;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.commons.service.SpotTrading;
import ru.tyumentsev.cryptopredator.macsaw.cache.MacSawStrategyCondition;

@Singleton
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MacSaw implements TradingStrategy {

    final MarketInfo marketInfo;
    final MacSawStrategyCondition macSawStrategyCondition;
    final SpotTrading spotTrading;
    final DataService dataService;
    final BotStateService botStateService;


    final static String STRATEGY_NAME = "macsaw";
    final static Integer STRATEGY_ID = 1003;
    final static String USER_DATA_UPDATE_ENDPOINT = "http://macsawbot:8080/state/userDataUpdateEvent";

    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    @Value("${strategy.macSaw.enabled}")
    boolean macSawEnabled;

    @Inject
    public MacSaw(MarketInfo marketInfo, MacSawStrategyCondition macSawStrategyCondition, SpotTrading spotTrading, DataService dataService, BotStateService botStateService) {
        this.marketInfo = marketInfo;
        this.macSawStrategyCondition = macSawStrategyCondition;
        this.spotTrading = spotTrading;
        this.dataService = dataService;
        this.botStateService = botStateService;
    }

    @Override
    public String getName() {
        return STRATEGY_NAME;
    }

    @Override
    public Integer getId() {
        return STRATEGY_ID;
    }

    @Override
    public boolean isEnabled() {
        return macSawEnabled;
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
