package ru.tyumentsev.cryptopredator.datakeeper.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import ru.tyumentsev.cryptopredator.datakeeper.cache.OpenedPositionRepository;
import ru.tyumentsev.cryptopredator.datakeeper.cache.PreviousCandleDataRepository;
import ru.tyumentsev.cryptopredator.datakeeper.cache.SellRecordRepository;
import ru.tyumentsev.cryptopredator.datakeeper.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.datakeeper.domain.PreviousCandleData;
import ru.tyumentsev.cryptopredator.datakeeper.domain.SellRecord;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CacheService {

    OpenedPositionRepository openedPositionRepository;
    SellRecordRepository sellRecordRepository;
    PreviousCandleDataRepository previousCandleDataRepository;

    public void save(SellRecord sellRecord) {sellRecordRepository.save(sellRecord);}

    public void save(OpenedPosition openedPosition) {openedPositionRepository.save(openedPosition);}

    public void save(PreviousCandleData previousCandleData) {previousCandleDataRepository.save(previousCandleData);}

    public void saveAllSellRecords(Iterable<SellRecord> sellRecords) {
        sellRecordRepository.saveAll(sellRecords);
    }

    public void saveAllOpenedPositions(Iterable<OpenedPosition> openedPositions) {
        openedPositionRepository.saveAll(openedPositions);
    }

    public void saveAllPreviousCandleData(Iterable<PreviousCandleData> previousCandleData) {
        previousCandleDataRepository.saveAll(previousCandleData);
    }

    public Iterable<SellRecord> findAllSellRecords() {
        return sellRecordRepository.findAll();
    }

    public Iterable<OpenedPosition> findAllOpenedPositions() {
        return openedPositionRepository.findAll();
    }

    public Iterable<PreviousCandleData> findAllPreviousCandleData() {
        return previousCandleDataRepository.findAll();
    }

    public void deleteAllSellRecords() {
        sellRecordRepository.deleteAll();
    }

    public void deleteAllOpenedPositions() {
        openedPositionRepository.deleteAll();
    }

    public void deleteAllPreviousCandleData(Iterable<PreviousCandleData> entities) {
        previousCandleDataRepository.deleteAll(entities);
    }

}
