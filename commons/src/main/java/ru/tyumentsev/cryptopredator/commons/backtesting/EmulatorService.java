package ru.tyumentsev.cryptopredator.commons.backtesting;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.cryptopredator.commons.TradingStrategy;
import ru.tyumentsev.cryptopredator.commons.cache.StrategyCondition;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
@Setter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
@Slf4j
public class EmulatorService {

    final BackTestReport backTestReport = new BackTestReport();
    final TradingStrategy strategy;

    final Map<String, AtomicBoolean> emulatedPositions = new ConcurrentHashMap<>(); // key - uid

    // need to inject positions from init block of strategy, which owns emulator.
    final StrategyCondition strategyCondition;

    float accountBalance = 100000F;

    public void emulateBuy(String ticker, float buyPrice) {
        if (accountBalance - buyPrice < 10F) {
            return;
        }

        if (emulatedPositions.containsKey(ticker)) {
            AtomicBoolean inPosition = emulatedPositions.get(ticker);
            if (!inPosition.get()) {
                inPosition.set(true);
//                log.info("BUY {} at {}.", ticker, currentPrice);
                strategyCondition.addOpenedPosition(ticker, buyPrice, 1F, 1F,
                        false, strategy.getName()
                );
            }
        } else {
//            log.info("BUY {} at {}.", ticker, currentPrice);
            strategyCondition.addOpenedPosition(ticker, buyPrice, 1F, 1F,
                    false, strategy.getName()
            );
            emulatedPositions.put(ticker, new AtomicBoolean(true));
        }

//        accountBalance = accountBalance - currentPrice * share.getLot();
        accountBalance = backTestReport.updateBalances(accountBalance, buyPrice, true);
    }

    public void emulateSell(String ticker, float sellPrice) {

        if (emulatedPositions.containsKey(ticker)) {
            AtomicBoolean inPosition = emulatedPositions.get(ticker);
            if (inPosition.get()) {
                inPosition.set(false);
//                log.info("SELL {} at {}, buy price was {} (profit {}%).",
//                        ticker,
//                        currentPrice,
//                        Optional.ofNullable(longPositions.get(instrumentUid)).map(OpenedPosition::avgPrice).orElse(null),
//                        percentageDifference(currentPrice, Optional.ofNullable(longPositions.get(instrumentUid)).map(OpenedPosition::avgPrice).orElse(0F))
//                );
                OpenedPosition closedPosition = removeOpenedPosition(ticker);

//                accountBalance = accountBalance + currentPrice * share.getLot();
                accountBalance = backTestReport.updateBalances(accountBalance, sellPrice,false);
                backTestReport.fixTradeResult(closedPosition, sellPrice);
            }
        }
    }

    public OpenedPosition removeOpenedPosition(final String uid) {
        return strategyCondition.getLongPositions().remove(uid);
    }

    private float percentageDifference(final float bigger, final float smaller) {
        return 100 * (bigger - smaller) / bigger;
    }
}
