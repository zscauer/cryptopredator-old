package ru.tyumentsev.cryptopredator.indicatorvirginbot;

import com.binance.api.client.domain.ExecutionType;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.event.UserDataUpdateEvent;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DoubleNum;
import ru.tyumentsev.cryptopredator.commons.TradingStrategy;
import ru.tyumentsev.cryptopredator.commons.mapping.CandlestickToBaseBarMapper;
import ru.tyumentsev.cryptopredator.commons.service.AccountManager;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class ApplicationInitializer implements ApplicationRunner {

    final MarketInfo marketInfo;
    final Map<String, TradingStrategy> tradingStrategies;

    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    @Value("${strategy.global.tradingAsset}")
    String tradingAsset;
    @Value("${strategy.global.maximalPairPrice}")
    float maximalPairPrice;

    @Override
    public void run(ApplicationArguments args) throws Exception {

        marketInfo.getAvailableTradePairs(tradingAsset);
        marketInfo.fillCheapPairs(tradingAsset, maximalPairPrice);

        Map<String, TradingStrategy> activeStrategies = tradingStrategies.values().stream()
                .filter(TradingStrategy::isEnabled)
                .collect(Collectors.toMap(value -> String.format("%s (id: %s)", value.getName(), value.getId()), value -> value));

        activeStrategies.forEach((name, implementation) -> implementation.prepareData());

        log.info("Application initialization complete. Active strategies: {}.", activeStrategies.keySet());

        if (testLaunch) {
            log.warn("Application launched in test mode. Deals functionality disabled.");
//            runTest();
        }
    }

    private void runTest() {
        CandlestickInterval interval = CandlestickInterval.FIVE_MINUTES;
        List<Candlestick> candlestickList = marketInfo.getCandleSticks("TUSDT", interval, 20);
        log.info("Candlesticks: {}", candlestickList);
        List<Bar> barsList = CandlestickToBaseBarMapper.map(candlestickList, interval);
        log.info("Bars list: {}", barsList);
        BaseBarSeriesBuilder barSeriesBuilder = new BaseBarSeriesBuilder();
        barSeriesBuilder.withBars(barsList);
        barSeriesBuilder.withMaxBarCount(26);
        barSeriesBuilder.withNumTypeOf(DoubleNum::valueOf);
        BaseBarSeries series = barSeriesBuilder.build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(closePrice);

        log.info(macd.getLongTermEma().toString());
        log.info(macd.getShortTermEma().toString());
        log.info(macd.getValue(5).toString());

    }
}
