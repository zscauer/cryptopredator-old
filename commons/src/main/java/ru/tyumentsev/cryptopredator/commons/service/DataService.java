package ru.tyumentsev.cryptopredator.commons.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.cryptopredator.commons.TradingStrategy;
import ru.tyumentsev.cryptopredator.commons.domain.BTCTrend;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPositionContainer;
import ru.tyumentsev.cryptopredator.commons.domain.PreviousCandleContainer;
import ru.tyumentsev.cryptopredator.commons.domain.SellRecord;
import ru.tyumentsev.cryptopredator.commons.domain.SellRecordContainer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Unite cache and data clients.
 */
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class DataService {

    final CacheServiceClient cacheServiceClient;

    public BTCTrend getBTCTrend() {
        try {
            return cacheServiceClient.getBTCTrend().execute().body();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<SellRecord> saveAllSellRecords(Collection<SellRecord> sellRecords, TradingStrategy strategy) {
        try {
            return Optional.ofNullable(cacheServiceClient.saveAllSellRecords(sellRecords.stream()
                                    .map(record -> new SellRecordContainer(String.format("%s:%s", strategy.getId(), record.symbol()), record))
                                    .collect(Collectors.toList()))
                            .execute().body())
                    .map(containers -> containers.stream()
                            .map(SellRecordContainer::sellRecord)
                            .collect(Collectors.toList()))
                    .orElseGet(ArrayList::new);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<SellRecord> findAllSellRecords(TradingStrategy strategy) {
        try {
            return Optional.ofNullable(cacheServiceClient.findAllSellRecords()
                            .execute().body())
                    .map(containers -> containers.stream()
                            .map(SellRecordContainer::sellRecord)
                            .filter(sellRecord -> sellRecord.strategy().equals(strategy.getName()))
                            .collect(Collectors.toList()))
                    .orElseGet(ArrayList::new);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteAllSellRecords(Collection<SellRecord> sellRecords, TradingStrategy strategy) {
        try {
            cacheServiceClient.deleteAllSellRecordsById(sellRecords.stream()
                    .map(record -> String.format("%s:%s", strategy.getId(), record.symbol()))
                    .collect(Collectors.toList())).execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<PreviousCandleContainer> saveAllPreviousCandleContainers(Collection<PreviousCandleContainer> previousCandleContainers) {
        try {
            return Optional.ofNullable(cacheServiceClient.saveAllPreviousCandleContainers(previousCandleContainers).execute().body())
                    .orElseGet(ArrayList::new);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<PreviousCandleContainer> findAllPreviousCandleContainers() {
        try {
            return Optional.ofNullable(cacheServiceClient.findAllPreviousCandleContainers().execute().body())
                    .orElseGet(ArrayList::new);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteAllPreviousCandleContainers(Collection<PreviousCandleContainer> previousCandleContainers) {
        try {
            cacheServiceClient.deleteAllPreviousCandleContainersById(previousCandleContainers.stream()
                            .map(PreviousCandleContainer::id)
                            .collect(Collectors.toList()))
                    .execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<OpenedPosition> saveAllOpenedPositions(Collection<OpenedPosition> openedPositions, TradingStrategy strategy) {
        try {
            return Optional.ofNullable(cacheServiceClient.saveAllOpenedPositions(openedPositions.stream()
                                    .map(record -> new OpenedPositionContainer(String.format("%s:%s", strategy.getId(), record.symbol()), record))
                                    .collect(Collectors.toList()))
                            .execute().body())
                    .map(containers -> containers.stream()
                            .map(OpenedPositionContainer::openedPosition)
                            .collect(Collectors.toList()))
                    .orElseGet(ArrayList::new);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<OpenedPosition> findAllOpenedPositions(TradingStrategy tradingStrategy) {
        try {
            return Optional.ofNullable(cacheServiceClient.findAllOpenedPositions()
                            .execute().body())
                    .map(containers -> containers.stream()
                            .map(OpenedPositionContainer::openedPosition)
                            .filter(openedPosition -> openedPosition.strategy().equals(tradingStrategy.getName()))
                            .collect(Collectors.toList()))
                    .orElseGet(ArrayList::new);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteAllOpenedPositions(Collection<OpenedPosition> openedPositions, TradingStrategy strategy) {
        try {
            cacheServiceClient.deleteAllOpenedPositionsById(openedPositions.stream()
                    .map(record -> String.format("%s:%s", strategy.getId(), record.symbol()))
                    .collect(Collectors.toList())).execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}