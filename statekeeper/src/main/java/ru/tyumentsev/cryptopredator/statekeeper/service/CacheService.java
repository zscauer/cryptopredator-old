package ru.tyumentsev.cryptopredator.statekeeper.service;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.CandlestickInterval;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.tyumentsev.cryptopredator.commons.domain.BTCTrend;
import ru.tyumentsev.cryptopredator.statekeeper.cache.OpenedPositionRepository;
import ru.tyumentsev.cryptopredator.statekeeper.cache.PreviousCandleDataRepository;
import ru.tyumentsev.cryptopredator.statekeeper.cache.SellRecordRepository;
import ru.tyumentsev.cryptopredator.statekeeper.domain.OpenedPositionData;
import ru.tyumentsev.cryptopredator.statekeeper.domain.PreviousCandleData;
import ru.tyumentsev.cryptopredator.statekeeper.domain.SellRecordData;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
@Slf4j
public class CacheService {

    BinanceApiRestClient restClient;
    OpenedPositionRepository openedPositionRepository;
    SellRecordRepository sellRecordRepository;
    PreviousCandleDataRepository previousCandleDataRepository;

    final BTCTrend btcTrend = new BTCTrend(CandlestickInterval.DAILY);

    @Scheduled(fixedDelayString = "${monitoring.updateBtcTrend.fixedDelay}", initialDelayString = "${monitoring.updateBtcTrend.initialDelay}")
    public void stateKeeper_updateBTCTrend() {
            restClient.getCandlestickBars(btcTrend.getSymbol(), btcTrend.getInterval(), 1).stream()
                    .findAny()
                    .ifPresentOrElse(btcTrend::setLastCandle,
                            () -> log.warn("BTC trend wasn't updated, because market info returned no Candlestick."));
    }

    public BTCTrend getBTCTrend() {
        return btcTrend;
    }

    public List<SellRecordData> saveAllSellRecords(Collection<SellRecordData> sellRecords) {
        return StreamSupport.stream(sellRecordRepository.saveAll(sellRecords).spliterator(), false).toList();
    }

    public Optional<SellRecordData> findSellRecord(String id) {
        return sellRecordRepository.findById(id);
    }

    public List<SellRecordData> findAllSellRecords() {
        return StreamSupport.stream(sellRecordRepository.findAll().spliterator(), false).toList();
    }

    public void deleteAllSellRecordsById(Collection<String> id) {
        sellRecordRepository.deleteAllById(id);
    }

    public void deleteAllSellRecords(Collection<SellRecordData> sellRecords) {
        sellRecordRepository.deleteAll(sellRecords);
    }

    public List<OpenedPositionData> saveAllOpenedPositions(Collection<OpenedPositionData> openedPositions) {
        return StreamSupport.stream(openedPositionRepository.saveAll(openedPositions).spliterator(), false).toList();
    }

    public Optional<OpenedPositionData> findOpenedPositionsById(String id) {
        return openedPositionRepository.findById(id);
    }

    public List<OpenedPositionData> findAllOpenedPositions() {
        return StreamSupport.stream(openedPositionRepository.findAll().spliterator(), false).toList();
    }

    public void deleteAllOpenedPositionsById(Collection<String> ids) {
        openedPositionRepository.deleteAllById(ids);
    }

    public void deleteAllOpenedPositions(Collection<OpenedPositionData> openedPositions) {
        openedPositionRepository.deleteAll(openedPositions);
    }

    public List<PreviousCandleData> saveAllPreviousCandleData(Collection<PreviousCandleData> previousCandleData) {
        return StreamSupport.stream(previousCandleDataRepository.saveAll(previousCandleData).spliterator(), false).toList();
    }

    public Optional<PreviousCandleData> findPreviousCandleData(String id) {
        return previousCandleDataRepository.findById(id);
    }

    public List<PreviousCandleData> findAllPreviousCandleData() {
        return StreamSupport.stream(previousCandleDataRepository.findAll().spliterator(), false).toList();
    }

    public void deleteAllPreviousCandleDataById(Collection<String> ids) {
        previousCandleDataRepository.deleteAllById(ids);
    }
}
