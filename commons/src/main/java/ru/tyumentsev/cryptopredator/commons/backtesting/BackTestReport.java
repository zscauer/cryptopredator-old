package ru.tyumentsev.cryptopredator.commons.backtesting;

import com.binance.api.client.domain.market.CandlestickInterval;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;

@Getter
@ToString
@FieldDefaults(level = AccessLevel.PROTECTED)
@RequiredArgsConstructor
@Slf4j
public class BackTestReport {
    String strategy;
    CandlestickInterval candleInterval;

    Float finalBalance, maximalDeposit, minimalDeposit, winLossRatio;
    int buysQty, winsQty, lossQty;

    final float comissionFee = 1.0005F; // 0.05% comission.
    float comissionTotal = 0F;

    Duration avgDealDuration; // TODO: calculate average time in deals.

    final LocalTime initTime = LocalTime.now();
    LocalTime endTime;

    public BackTestReport getReport(Map<String, OpenedPosition> longPositions, String strategyName, CandlestickInterval interval) {
        strategy = strategyName;
        candleInterval = interval;
        float positionsBalance = longPositions.values().stream()
                .map(position -> position.avgPrice() * position.qty())
                .reduce(0F, Float::sum);
        finalBalance = finalBalance + positionsBalance;
        winLossRatio = lossQty != 0 ? (float) winsQty / lossQty : winsQty;
        endTime = LocalTime.now();

        log.info("BackTest report: {}. Opened {} positions ({}).", this, longPositions.size(), positionsBalance);
        return this;
    }

    public float updateBalances(final float currentBalance, final float dealSum, final boolean itsBuy) {
        float comissionSum = dealSum * comissionFee - dealSum;
        comissionTotal += comissionSum;
        if (itsBuy) {
            buysQty++;
            finalBalance = currentBalance - dealSum - comissionSum;
        } else {
            finalBalance = currentBalance + dealSum - comissionSum;
        }

        if (minimalDeposit == null) {
            minimalDeposit = finalBalance;
        } else if (finalBalance < minimalDeposit) {
            minimalDeposit = finalBalance;
        }
        if (maximalDeposit == null) {
            maximalDeposit = finalBalance;
        } else if (finalBalance > maximalDeposit) {
            maximalDeposit = finalBalance;
        }

        return finalBalance;
    }

    public void fixTradeResult(final OpenedPosition closedPosition, final float sellPrice) {
        if (sellPrice > closedPosition.avgPrice()) {
            winsQty++;
        } else {
            lossQty++;
        }
    }
}
