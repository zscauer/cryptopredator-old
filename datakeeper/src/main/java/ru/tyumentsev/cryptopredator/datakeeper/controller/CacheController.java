package ru.tyumentsev.cryptopredator.datakeeper.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.tyumentsev.cryptopredator.datakeeper.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.datakeeper.domain.PreviousCandleData;
import ru.tyumentsev.cryptopredator.datakeeper.domain.SellRecord;
import ru.tyumentsev.cryptopredator.datakeeper.service.CacheService;

import java.util.List;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping("/api/cache/v1")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class CacheController {

    CacheService cacheService;

    @GetMapping("/sellRecord")
    public List<SellRecord> getSellRecords() {
        return StreamSupport.stream(cacheService.findAllSellRecords().spliterator(), false).toList();
//        Map<String, SellRecord> result = new HashMap<>();
//        cacheService.findAllSellRecords().forEach(sellRecord -> result.put(sellRecord.symbol(), sellRecord));
//        return result;
    }

    @PostMapping("/sellRecord")
    public void saveSellRecordsCache(@RequestBody List<SellRecord> records) {
        cacheService.saveAllSellRecords(records);
    }

    @GetMapping("/previousCandleData")
    public List<PreviousCandleData> getPreviousCandleData() {
        return StreamSupport.stream(cacheService.findAllPreviousCandleData().spliterator(), false).toList();
//        Map<String, PreviousCandleData> result = new HashMap<>();
//        cacheService.findAllPreviousCandleData().forEach(previousCandleData -> result.put(previousCandleData.id(), previousCandleData));
//        return result;
    }

    @PostMapping("/previousCandleData")
    public void savePreviousCandleDataCache(@RequestBody List<PreviousCandleData> previousCandleData) {
        cacheService.saveAllPreviousCandleData(previousCandleData);
    }

    @GetMapping("/openedPosition")
    public List<OpenedPosition> getOpenedPositions() {
        return StreamSupport.stream(cacheService.findAllOpenedPositions().spliterator(), false).toList();
//        Map<String, OpenedPosition> result = new HashMap<>();
//        cacheService.findAllOpenedPositions().forEach(openedPosition -> result.put(openedPosition.symbol(), openedPosition));
//        return result;
    }

    @PostMapping("/openedPosition")
    public void saveOpenedPositionsCache(@RequestBody List<OpenedPosition> openedPositions) {
        cacheService.saveAllOpenedPositions(openedPositions);
    }

}
