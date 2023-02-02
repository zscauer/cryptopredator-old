package ru.tyumentsev.cryptopredator.dailyvolumesbot.cache;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tyumentsev.cryptopredator.commons.cache.StrategyCondition;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class DailyVolumesStrategyCondition extends StrategyCondition {

    @Override
    public boolean thisSignalWorkedOutBefore(final String pair) {
        AtomicBoolean ignoreSignal = new AtomicBoolean(false);

        Optional.ofNullable(sellJournal.get(pair)).ifPresent(sellRecord -> {
            if (sellRecord.sellTime().getDayOfYear() == LocalDateTime.now().getDayOfYear()) {
                ignoreSignal.set(true);
            } else {
                log.debug("Period of signal ignoring for {} expired, remove pair from sell journal.", pair);
                sellJournal.remove(pair);
            }
        });

        return ignoreSignal.get();
    }

    public void updatePriceDecreaseFactor(final String pair, float priceDecreaseFactor, Map<String, OpenedPosition> openedPositions) {
        Optional.ofNullable(openedPositions.get(pair)).ifPresent(pos -> {
            pos.priceDecreaseFactor(priceDecreaseFactor);
            log.info("Updating price decrease factor of {} to {}. Value after updating: {}.", pair, priceDecreaseFactor, openedPositions.get(pair).priceDecreaseFactor());
        });
    }

}
