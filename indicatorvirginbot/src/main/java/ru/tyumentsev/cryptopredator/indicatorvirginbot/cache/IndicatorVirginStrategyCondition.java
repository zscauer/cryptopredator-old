package ru.tyumentsev.cryptopredator.indicatorvirginbot.cache;

import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import ru.tyumentsev.cryptopredator.commons.cache.StrategyCondition;
import ru.tyumentsev.cryptopredator.commons.domain.MonitoredPosition;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
@Slf4j
public class IndicatorVirginStrategyCondition extends StrategyCondition {

    @Getter
    final Map<String, MonitoredPosition> monitoredPositions = new ConcurrentHashMap<>();
    @Getter
    final Map<String, List<Candlestick>> upperTimeframeCandles = new ConcurrentHashMap<>();

    @Value("${strategy.indicatorVirgin.workedOutSignalsIgnoringPeriod}")
    int workedOutSignalsIgnoringPeriod;
    @Value("${strategy.indicatorVirgin.monitoringExpirationTime}")
    long monitoringExpirationTime;

    @Override
    public boolean thisSignalWorkedOutBefore(final String pair) {
        AtomicBoolean ignoreSignal = new AtomicBoolean(false);

        Optional.ofNullable(sellJournal.get(pair)).ifPresent(sellRecord -> {
            if (sellRecord.sellTime().isAfter(LocalDateTime.now().minusHours(workedOutSignalsIgnoringPeriod))) {
                ignoreSignal.set(true);
            } else {
                log.debug("Period of signal ignoring for {} expired, remove pair from sell journal.", pair);
                sellJournal.remove(pair);
            }
        });

        return ignoreSignal.get();
    }

    public void addPairToMonitoring(final String symbol, final float price) {
        Optional.ofNullable(monitoredPositions.get(symbol)).ifPresentOrElse(monitoredPosition -> {

        }, () -> {
            monitoredPositions.put(symbol, new MonitoredPosition(symbol, price, ZonedDateTime.now()));
        });
    }

    public boolean pairOnMonitoring(final String symbol, final BaseBarSeries series) {
        Optional.ofNullable(monitoredPositions.get(symbol)).ifPresent(monitoredPosition -> {
            if (monitoredPosition.beginMonitoringTime().isBefore(ZonedDateTime.now().minusHours(monitoringExpirationTime)) ||
                    monitoredPairPriceTurnedBack(series)) {
                monitoredPositions.remove(symbol);
            }
        });
        return Optional.ofNullable(monitoredPositions.get(symbol)).isPresent();
    }

    private boolean monitoredPairPriceTurnedBack(final BaseBarSeries series) {
        if (series.getBarData().isEmpty()) {
            return false;
        }
        var endBarSeriesIndex = series.getEndIndex();

        EMAIndicator ema7 = new EMAIndicator(new ClosePriceIndicator(series), 7);
        EMAIndicator ema25 = new EMAIndicator(new ClosePriceIndicator(series), 25);

        return ema25.getValue(endBarSeriesIndex - 1).isGreaterThan(ema7.getValue(endBarSeriesIndex - 1));
    }

    public Optional<Float> getMonitoredPositionPrice(final String symbol) {
        return Optional.ofNullable(monitoredPositions.get(symbol)).map(MonitoredPosition::price);
    }

    public void removePositionFromMonitoring(final String symbol) {
        monitoredPositions.remove(symbol);
    }

    public boolean pairOnUptrend(String symbol, float currentPrice, CandlestickInterval interval, MarketInfo marketInfo) {
        Optional.ofNullable(upperTimeframeCandles.get(symbol)).ifPresentOrElse(candles -> {
            if (candles.size() < 3) {
                log.info("List of candles in lambda of {} is less then 3 and contains {} elements: {}.", symbol, candles.size(), candles);
                return;
            }
            if (ZonedDateTime.ofInstant(Instant.ofEpochMilli(candles.get(2).getCloseTime()), ZoneId.systemDefault()).isBefore(ZonedDateTime.now(ZoneId.systemDefault()))) {
                upperTimeframeCandles.put(symbol, marketInfo.getCandleSticks(symbol, interval, 3));
            }
        }, () -> {
            upperTimeframeCandles.put(symbol, marketInfo.getCandleSticks(symbol, interval, 3));
        });

        var candles = upperTimeframeCandles.get(symbol);

        if (candles.size() > 2) {
//            log.info("Prices of {}: currentPrice/get(1)/get(0)/: {}/{}/{}", symbol, currentPrice, candles.get(1).getClose(), candles.get(0).getClose());
            return currentPrice > Float.parseFloat(candles.get(1).getClose()) && currentPrice > Float.parseFloat(candles.get(0).getClose());
        } else {
            log.info("List of candles of {} is less then 3 and contains {} elements: {}.", symbol, candles.size(), candles);
            return false;
        }
    }
}
