package ru.tyumentsev.cryptopredator.indicatorvirginbot.cache;

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

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
@Slf4j
public class LevelsStrategyCondition extends StrategyCondition {

    @Getter
    final Map<String, MonitoredPosition> monitoredPositions = new ConcurrentHashMap<>();
    @Value("${strategy.levels.workedOutSignalsIgnoringPeriod}")
    int workedOutSignalsIgnoringPeriod;
    @Value("${strategy.levels.monitoringExpirationTime}")
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
            if ((monitoredPosition.beginMonitoringTime().isBefore(ZonedDateTime.now().minusMinutes(monitoringExpirationTime))) ||
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

}
