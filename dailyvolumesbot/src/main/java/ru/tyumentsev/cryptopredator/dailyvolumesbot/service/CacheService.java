package ru.tyumentsev.cryptopredator.dailyvolumesbot.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.cache.OpenedPositionRepository;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.cache.PreviousCandleDataRepository;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.cache.SellRecordRepository;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.domain.PreviousCandleData;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.domain.SellRecord;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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

    public void saveAllSellRecords(Collection<SellRecord> sellRecords) {
        sellRecordRepository.saveAll(sellRecords);
    }

    public void saveAllOpenedPositions(Iterable<OpenedPosition> openedPositions) {
        openedPositionRepository.saveAll(openedPositions);
    }

    public void saveAllPreviousCandleData(Iterable<PreviousCandleData> previousCandleData) {
        previousCandleDataRepository.saveAll(previousCandleData);
    }

    public List<SellRecord> findAllSellRecords() {
        return StreamSupport.stream(sellRecordRepository.findAll().spliterator(), false).collect(Collectors.toList());
    }

    public List<OpenedPosition> findAllOpenedPositions() {
        return StreamSupport.stream(openedPositionRepository.findAll().spliterator(), false).collect(Collectors.toList());
    }

    public List<PreviousCandleData> findAllPreviousCandleData() {
        return StreamSupport.stream(previousCandleDataRepository.findAll().spliterator(), false).collect(Collectors.toList());
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
