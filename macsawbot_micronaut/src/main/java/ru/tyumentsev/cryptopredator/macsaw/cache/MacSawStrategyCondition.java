package ru.tyumentsev.cryptopredator.macsaw.cache;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.cryptopredator.commons.cache.StrategyCondition;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
@Slf4j
public class MacSawStrategyCondition extends StrategyCondition {

    @Override
    protected boolean thisSignalWorkedOutBefore(final String pair) {
        //TODO: define parameter for signal ignoring period.
        AtomicBoolean ignoreSignal = new AtomicBoolean(false);

        Optional.ofNullable(sellJournal.get(pair)).ifPresent(sellRecord -> {
            if (sellRecord.sellTime().isAfter(LocalDateTime.now().minusHours(1))) {
                ignoreSignal.set(true);
            } else {
                log.debug("Period of signal ignoring for {} expired, remove pair from sell journal.", pair);
                sellJournal.remove(pair);
            }
        });

        return ignoreSignal.get();
    }
}
