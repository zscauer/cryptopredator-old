package ru.tyumentsev.cryptopredator.macsaw;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micronaut.context.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.ApplicationStartupEvent;
import io.micronaut.serde.annotation.SerdeImport;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.cryptopredator.commons.TradingStrategy;
import ru.tyumentsev.cryptopredator.commons.domain.SellRecord;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
@Slf4j
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@SerdeImport(SellRecord.class)
public class ApplicationInitializer implements ApplicationEventListener<ApplicationStartupEvent> {

//    @Inject
//    public ApplicationInitializer(MarketInfo marketInfo, List<TradingStrategy> tradingStrategies) {
//        this.marketInfo = marketInfo;
//        this.tradingStrategies = tradingStrategies;
//    }

//    final MarketInfo marketInfo;
//    final List<TradingStrategy> tradingStrategies;
//    @Value("${applicationconfig.testLaunch}")
//    boolean testLaunch;
//    @Value("${strategy.global.tradingAsset}")
//    String tradingAsset;
//    @Value("${strategy.global.maximalPairPrice}")
//    float maximalPairPrice;

    @Override
    public void onApplicationEvent(ApplicationStartupEvent event) {
        System.out.println("Server started up");
//        marketInfo.getAvailableTradePairs(tradingAsset);
//        marketInfo.fillCheapPairs(tradingAsset, maximalPairPrice);

//        Map<String, TradingStrategy> activeStrategies = tradingStrategies.stream()
//                .filter(TradingStrategy::isEnabled)
//                .collect(Collectors.toMap(value -> String.format("%s (id: %s)", value.getName(), value.getId()), value -> value));
//
//        activeStrategies.forEach((name, implementation) -> implementation.prepareData());
//
//        log.info("Application initialization complete. Active strategies: {}.", activeStrategies.keySet());

//        if (testLaunch) {
//            log.warn("Application launched in test mode. Deals functionality disabled.");
//        }
     }


}