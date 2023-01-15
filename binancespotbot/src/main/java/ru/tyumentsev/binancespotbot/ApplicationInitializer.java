package ru.tyumentsev.binancespotbot;

import com.binance.api.client.domain.event.CandlestickEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.binancespotbot.cache.MarketData;
import ru.tyumentsev.binancespotbot.cache.SellRecordRepository;
import ru.tyumentsev.binancespotbot.domain.PreviousCandleData;
import ru.tyumentsev.binancespotbot.domain.SellRecord;
import ru.tyumentsev.binancespotbot.service.AccountManager;
import ru.tyumentsev.binancespotbot.service.DataService;
import ru.tyumentsev.binancespotbot.service.MarketInfo;
import ru.tyumentsev.binancespotbot.strategy.TradingStrategy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ApplicationInitializer implements ApplicationRunner {

    MarketData marketData;
    MarketInfo marketInfo;
    AccountManager accountManager;
    Map<String, TradingStrategy> tradingStrategies;
    DataService dataService;

    @NonFinal
    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    @NonFinal
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (testLaunch) {
            log.warn("Application launched in test mode. Deals functionality disabled.");
            LocalDateTime sellTime = LocalDateTime.now();
//            SellRecord record = new SellRecord("BTCUSDT", sellTime);
//            SellRecord record2 = new SellRecord("ETHUSDT", sellTime);
//            dataService.save(record);
//            dataService.save(record2);

//            PreviousCandleData d1 = new PreviousCandleData("Daily:" + "ETHUSDT", "ETHUSDT", sellTime.toEpochSecond(ZoneOffset.UTC), "5");
//            PreviousCandleData d2 = new PreviousCandleData("Daily:" + "BTCUSDT", "BTCUSDT", sellTime.toEpochSecond(ZoneOffset.UTC), "6");
//            PreviousCandleData d3 = new PreviousCandleData("Daily:" + "TUSDT", "TUSDT", sellTime.toEpochSecond(ZoneOffset.UTC), "7");
//            dataService.save(d1);
//            dataService.save(d2);
//            dataService.save(d3);

            CandlestickEvent e1 = new CandlestickEvent();
            e1.setSymbol("U3RUSDT");
            e1.setClose("2838383");
            PreviousCandleData d1 = new PreviousCandleData("DailyTest:" + e1.getSymbol(), e1);
            dataService.save(d1);

            Iterable<PreviousCandleData> r1 = dataService.findAllPreviousCandleData();
                        StreamSupport.stream(r1.spliterator(), false)
                    .filter(data -> data.id().startsWith("DailyTest"))
                    .forEach(e -> log.info(e.event().getSymbol() + e.event().getClose()));
//
//            Iterable<PreviousCandleData> r1 = dataService.findAllPreviousCandleData();
//            StreamSupport.stream(r1.spliterator(), false)
//                    .filter(data -> data.id().startsWith("Daily"))
//                    .forEach(e -> log.info(e.symbol()));
        }

        marketData.addAvailablePairs(tradingAsset, marketInfo.getAvailableTradePairs(tradingAsset));
        marketData.initializeOpenedLongPositionsFromMarket(marketInfo, accountManager);
        marketData.fillCheapPairs(tradingAsset, marketInfo);

        Map<String, TradingStrategy> activeStrategies = tradingStrategies.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        activeStrategies.forEach((name, implementation) -> implementation.prepareData());

        log.info("Application initialization complete.\nActive strategies: {}.", activeStrategies.keySet());
    }
}
