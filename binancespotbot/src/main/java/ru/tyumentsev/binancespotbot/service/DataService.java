package ru.tyumentsev.binancespotbot.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import ru.tyumentsev.binancespotbot.cache.OpenedPositionRepository;
import ru.tyumentsev.binancespotbot.cache.PreviousCandleDataRepository;
import ru.tyumentsev.binancespotbot.cache.SellRecordRepository;
import ru.tyumentsev.binancespotbot.domain.OpenedPosition;
import ru.tyumentsev.binancespotbot.domain.PreviousCandleData;
import ru.tyumentsev.binancespotbot.domain.SellRecord;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DataService {

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
