package ru.tyumentsev.cryptopredator.bigasscandlesbot.cache;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.tyumentsev.cryptopredator.commons.cache.StrategyCondition;

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
public class BigAssCandlesStrategyCondition extends StrategyCondition {

    @Getter
    final Map<String, MonitoredPosition> monitoredPositions = new ConcurrentHashMap<>();
    @Value("${strategy.bigAssCandles.workedOutSignalsIgnoringPeriod}")
    int workedOutSignalsIgnoringPeriod;
//    @Value("${strategy.bigAssCandles.monitoringExpirationTime}")
//    long monitoringExpirationTime;

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

//    public void addPairToMonitoring(final String symbol, final float price) {
//        Optional.ofNullable(monitoredPositions.get(symbol)).ifPresentOrElse(monitoredPosition -> {
//
//        }, () -> {
//            monitoredPositions.put(symbol, new MonitoredPosition(symbol, price, ZonedDateTime.now()));
//        });
//    }

//    public boolean pairOnMonitoring(final String symbol) {
//        Optional.ofNullable(monitoredPositions.get(symbol)).ifPresent(monitoredPosition -> {
//            if (monitoredPosition.beginMonitoringTime().isBefore(ZonedDateTime.now().minusMinutes(monitoringExpirationTime))) {
//                monitoredPositions.remove(symbol);
//            }
//        });
//        return Optional.ofNullable(monitoredPositions.get(symbol)).isPresent();
//    }

//    public Optional<Float> getMonitoredPositionPrice(final String symbol) {
//        return Optional.ofNullable(monitoredPositions.get(symbol)).map(MonitoredPosition::price);
//    }

    public void removePositionFromMonitoring(final String symbol) {
        monitoredPositions.remove(symbol);
    }

    public record MonitoredPosition(
            String symbol,
            float price,
            ZonedDateTime beginMonitoringTime
    ) {}
}
