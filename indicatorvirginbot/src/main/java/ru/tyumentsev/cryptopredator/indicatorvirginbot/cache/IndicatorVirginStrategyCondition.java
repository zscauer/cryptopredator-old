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
import ru.tyumentsev.cryptopredator.commons.domain.SellRecord;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
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
    @Getter
    final Map<String, Boolean> pingPongs = new ConcurrentHashMap<>();

    @Value("${strategy.indicatorVirgin.workedOutSignalsIgnoringPeriod}")
    int workedOutSignalsIgnoringPeriod;
    @Value("${strategy.indicatorVirgin.monitoringExpirationTime}")
    long monitoringExpirationTime;

    public void ping(final String pair) {
        pingPongs.put(pair, Boolean.TRUE);
    }

    public boolean pong(final String pair) {
        return pingPongs.remove(pair, Boolean.TRUE);
    }

    @Override
    public boolean thisSignalWorkedOutBefore(final String pair) {
        AtomicBoolean ignoreSignal = new AtomicBoolean(false);

        if (sellJournal.containsKey(pair)) {
            SellRecord sellRecord = sellJournal.get(pair);
            if (sellRecord.sellTime().isAfter(LocalDateTime.now().minusHours(workedOutSignalsIgnoringPeriod))) {
                ignoreSignal.set(true);
            } else {
                log.debug("Period of signal ignoring for {} expired, remove pair from sell journal.", pair);
                sellJournal.remove(pair);
            }
        }

        return ignoreSignal.get();
    }

    public void addPairToMonitoring(final String symbol, final float price) {
        monitoredPositions.putIfAbsent(symbol, new MonitoredPosition(symbol, price, ZonedDateTime.now()));
    }

    public void setMonitoredPairWeight(final String symbol, final int percentageDiff) {
        monitoredPositions.get(symbol).setWeight(percentageDiff);
    }

    public boolean pairOnMonitoring(final String symbol, final BaseBarSeries series) {
        if (monitoredPositions.containsKey(symbol)) {
            MonitoredPosition monitoredPosition = monitoredPositions.get(symbol);
            if (monitoredPosition.getBeginMonitoringTime().isBefore(ZonedDateTime.now().minusHours(monitoringExpirationTime)) ||
                    monitoredPairPriceTurnedBack(series)) {
                monitoredPositions.remove(symbol);
            }
        }
        return monitoredPositions.containsKey(symbol);
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
        return Optional.ofNullable(monitoredPositions.get(symbol)).map(MonitoredPosition::getPrice);
    }

    public void removePositionFromMonitoring(final String symbol) {
        monitoredPositions.remove(symbol);
    }

    public boolean pairOnUptrend(String symbol, float currentPrice, CandlestickInterval interval, MarketInfo marketInfo) {
        if (upperTimeframeCandles.containsKey(symbol)) {
            List<Candlestick> candles = upperTimeframeCandles.get(symbol);
            if (candles.size() > 2) {
                if (ZonedDateTime.ofInstant(Instant.ofEpochMilli(candles.get(2).getCloseTime()), ZoneId.systemDefault()).isBefore(ZonedDateTime.now(ZoneId.systemDefault()))) {
                    upperTimeframeCandles.put(symbol, marketInfo.getCandleSticks(symbol, interval, 3));
                }
            }
        } else {
            upperTimeframeCandles.put(symbol, marketInfo.getCandleSticks(symbol, interval, 3));
        }

        var candles = upperTimeframeCandles.get(symbol);

        if (candles.size() > 2) {
            return currentPrice > Float.parseFloat(candles.get(1).getHigh()) && currentPrice > Float.parseFloat(candles.get(0).getHigh());
        } else {
            log.info("List of candles of {} is less then 3 and contains {} elements: {}.", symbol, candles.size(), candles);
            return false;
        }
    }

    public boolean itsHeaviestMonitoredPair(final String symbol) {
        return monitoredPositions.values().stream()
                .max(Comparator.comparing(MonitoredPosition::getWeight))
                .map(heaviestPair -> heaviestPair.getSymbol().equals(symbol))
                .orElse(false);
    }

}
