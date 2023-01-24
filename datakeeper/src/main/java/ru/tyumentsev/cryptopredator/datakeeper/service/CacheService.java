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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CacheService {

    OpenedPositionRepository openedPositionRepository;
    SellRecordRepository sellRecordRepository;
    PreviousCandleDataRepository previousCandleDataRepository;

    public List<SellRecord> saveAllSellRecords(Collection<SellRecord> sellRecords) {
        return StreamSupport.stream(sellRecordRepository.saveAll(sellRecords).spliterator(), false).toList();
    }

    public Optional<SellRecord> findSellRecord(String id) {
        return sellRecordRepository.findById(id);
    }

    public List<SellRecord> findAllSellRecords() {
        return StreamSupport.stream(sellRecordRepository.findAll().spliterator(), false).toList();
    }

    public void deleteAllSellRecordsById(Collection<String> id) {
        sellRecordRepository.deleteAllById(id);
    }

    public void deleteAllSellRecords() {
        sellRecordRepository.deleteAll();
    }

    public List<OpenedPosition> saveAllOpenedPositions(Collection<OpenedPosition> openedPositions) {
        return StreamSupport.stream(openedPositionRepository.saveAll(openedPositions).spliterator(), false).toList();
    }

    public Optional<OpenedPosition> findOpenedPosition(String id) {
        return openedPositionRepository.findById(id);
    }

    public List<OpenedPosition> findAllOpenedPositions() {
        return StreamSupport.stream(openedPositionRepository.findAll().spliterator(), false).toList();
    }

    public void deleteAllOpenedPositionsById(Collection<String> ids) {
        openedPositionRepository.deleteAllById(ids);
    }

    public void deleteAllOpenedPositions() {
        openedPositionRepository.deleteAll();
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
