package ru.tyumentsev.cryptopredator.commons.cache;

import com.binance.api.client.domain.event.CandlestickEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.commons.domain.SellRecord;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
@Slf4j
public abstract class StrategyCondition {
    @Getter
    Map<String, OpenedPosition> longPositions = new ConcurrentHashMap<>();
    @Getter
    Map<String, OpenedPosition> shortPositions = new ConcurrentHashMap<>();

    // stores time of last selling to avoid repeated buy signals.
    @Getter
    Map<String, SellRecord> sellJournal = new ConcurrentHashMap<>();

    public void addOpenedPosition(String pair, float price, float qty, float priceDecreaseFactor,
                                  boolean rocketCandidate, String strategy) {
        Optional.ofNullable(longPositions.get(pair)).ifPresentOrElse(pos -> {
            var newQty = pos.qty() + qty;
            pos.avgPrice((pos.avgPrice() * pos.qty() + price * qty) / newQty)
                    .qty(newQty);
        }, () -> {
            var pos = new OpenedPosition();
            pos.symbol(pair)
                    .maxPrice(price)
                    .avgPrice(price)
                    .qty(qty)
                    .priceDecreaseFactor(priceDecreaseFactor)
                    .rocketCandidate(rocketCandidate)
                    .strategy(strategy);
            log.debug("{} not found in opened long positions, adding new one - '{}'.", pair, pos);
            longPositions.put(pair, pos);
        });
        if (rocketCandidate) {
            log.info("{} added to opened positions as rocket candidate.", pair);
        }
    }

    public void updateOpenedPositionLastPrice(String pair, float lastPrice, Map<String, OpenedPosition> openedPositions) {
        Optional.ofNullable(openedPositions.get(pair)).ifPresent(pos -> {
            pos.lastPrice(lastPrice);
            if (lastPrice > pos.maxPrice()) {
                pos.maxPrice(lastPrice);
            }
            pos.updateStamp(LocalDateTime.now());
            pos.threadName(String.format("%s:%s", Thread.currentThread().getName(), Thread.currentThread().getId()));
        });
    }

    public OpenedPosition removeOpenedPosition(String pair) {
        return longPositions.remove(pair);
    }


    public void addSellRecordToJournal(String pair, String strategy) {
        sellJournal.put(pair, new SellRecord(pair, LocalDateTime.now(), strategy));
    }

    protected abstract boolean thisSignalWorkedOutBefore(final String pair);

    public void removeCandlestickEventsCacheForPair(String ticker, Map<String, Deque<CandlestickEvent>> cachedCandlestickEvents) {
        cachedCandlestickEvents.get(ticker).clear();
    }
}