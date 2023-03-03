package ru.tyumentsev.cryptopredator.macsaw;

import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.event.ApplicationStartupEvent;
import io.micronaut.serde.annotation.SerdeImport;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DoubleNum;
import ru.tyumentsev.cryptopredator.commons.TradingStrategy;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPositionContainer;
import ru.tyumentsev.cryptopredator.commons.domain.PlacedOrder;
import ru.tyumentsev.cryptopredator.commons.domain.SellRecord;
import ru.tyumentsev.cryptopredator.commons.domain.SellRecordContainer;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
@Slf4j
@FieldDefaults(level = AccessLevel.PROTECTED)
@SerdeImport(SellRecord.class)
@SerdeImport(BarSeries.class)
@SerdeImport(BaseBar.class)
@SerdeImport(Bar.class)
@SerdeImport(DoubleNum.class)
@SerdeImport(PlacedOrder.class)
@SerdeImport(OpenedPosition.class)
@SerdeImport(OrderTradeUpdateEvent.class)
@SerdeImport(OpenedPositionContainer.class)
@SerdeImport(SellRecordContainer.class)
public class ApplicationInitializer implements ApplicationEventListener<ApplicationStartupEvent> {

    @Inject
    public ApplicationInitializer(MarketInfo marketInfo, List<TradingStrategy> tradingStrategies) {
        this.marketInfo = marketInfo;
        this.tradingStrategies = tradingStrategies;
    }

    final MarketInfo marketInfo;
    List<TradingStrategy> tradingStrategies;
    @Value("${applicationconfig.testLaunch}")
    public boolean testLaunch;
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;
    @Value("${strategy.global.maximalPairPrice}")
    float maximalPairPrice;

    @Override
    public void onApplicationEvent(ApplicationStartupEvent event) {
        System.out.println("Server started up");
        marketInfo.getAvailableTradePairs(tradingAsset);
        marketInfo.fillCheapPairs(tradingAsset, maximalPairPrice);

        Map<String, TradingStrategy> activeStrategies = tradingStrategies.stream()
                .filter(TradingStrategy::isEnabled)
                .collect(Collectors.toMap(value -> String.format("%s (id: %s)", value.getName(), value.getId()), value -> value));

        activeStrategies.forEach((name, implementation) -> implementation.prepareData());
//
        log.info("Application initialization complete. Active strategies: {}.", activeStrategies.keySet());

        if (testLaunch) {
            log.warn("Application launched in test mode. Deals functionality disabled.");
        }
     }

}
