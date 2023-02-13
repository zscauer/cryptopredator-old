package ru.tyumentsev.cryptopredator.indicatorvirginbot.cache;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tyumentsev.cryptopredator.commons.cache.StrategyCondition;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class IndicatorVirginStrategyCondition extends StrategyCondition {

    Map<String, MonitoredPosition> monitoredPositions = new ConcurrentHashMap<>();
    Long monitoringExpirationTime = 6L;

    @Override
    public boolean thisSignalWorkedOutBefore(final String pair) {
    //TODO: define parameter for signal ignoring period.
        AtomicBoolean ignoreSignal = new AtomicBoolean(false);

        Optional.ofNullable(sellJournal.get(pair)).ifPresent(sellRecord -> {
            if (sellRecord.sellTime().isAfter(LocalDateTime.now().minusHours(3))) {
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
            monitoredPositions.put(symbol, new MonitoredPosition(symbol, price, LocalDateTime.now()));
        });
    }

    public boolean pairOnMonitoring(final String symbol) {
        Optional.ofNullable(monitoredPositions.get(symbol)).ifPresent(monitoredPosition -> {
            if (monitoredPosition.beginMonitoringTime().isBefore(LocalDateTime.now().minusHours(monitoringExpirationTime))) {
                monitoredPositions.remove(symbol);
            }
        });
        return Optional.ofNullable(monitoredPositions.get(symbol)).isPresent();
    }

    public Optional<Float> getMonitoredPositionPrice(final String symbol) {
        return Optional.ofNullable(monitoredPositions.get(symbol)).map(MonitoredPosition::price);
    }

    public void removePositionFromMonitoring(final String symbol) {
        monitoredPositions.remove(symbol);
    }


    private record MonitoredPosition(
            String symbol,
            float price,
            LocalDateTime beginMonitoringTime
    ) {

    }

}
