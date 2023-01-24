package ru.tyumentsev.cryptopredator.datakeeper.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.tyumentsev.cryptopredator.datakeeper.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.datakeeper.domain.PreviousCandleData;
import ru.tyumentsev.cryptopredator.datakeeper.domain.SellRecord;
import ru.tyumentsev.cryptopredator.datakeeper.service.CacheService;

import java.util.List;

@RestController
@RequestMapping("/api/cache/v1")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class CacheController {

    CacheService cacheService;

    @GetMapping("/sellRecord")
    public List<SellRecord> getSellRecords() {
        return cacheService.findAllSellRecords();
    }

    @GetMapping("/sellRecord/{id}")
    public SellRecord getSellRecord(@PathVariable String id) {
        return cacheService.findSellRecord(id).orElseThrow();
    }

    @PostMapping("/sellRecord")
    public List<SellRecord> saveSellRecordsCache(@RequestBody List<SellRecord> records) {
        return cacheService.saveAllSellRecords(records);
    }

    @PostMapping("/sellRecord/delete")
    public void deleteAllSellRecordsById(@RequestBody List<String> ids) {
        cacheService.deleteAllSellRecordsById(ids);
    }

    @DeleteMapping("/sellRecord")
    public void deleteAllSellRecords() {
        cacheService.deleteAllSellRecords();
    }

    @GetMapping("/previousCandleData")
    public List<PreviousCandleData> getPreviousCandlesData() {
        return cacheService.findAllPreviousCandleData();
    }

    @GetMapping("/previousCandleData/{id}")
    public PreviousCandleData getPreviousCandleData(@PathVariable String id) {
        return cacheService.findPreviousCandleData(id).orElseThrow();
    }

    @PostMapping("/previousCandleData")
    public List<PreviousCandleData> savePreviousCandleDataCache(@RequestBody List<PreviousCandleData> previousCandleData) {
        return cacheService.saveAllPreviousCandleData(previousCandleData);
    }

    @PostMapping("/previousCandleData/delete")
    public void deleteAllPreviousCandlesDataById(@RequestBody List<String> ids) {
        cacheService.deleteAllPreviousCandleDataById(ids);
    }

    @GetMapping("/openedPosition")
    public List<OpenedPosition> getOpenedPositions() {
        return cacheService.findAllOpenedPositions();
    }

    @GetMapping("/openedPosition/{id}")
    public OpenedPosition getOpenedPosition(@PathVariable String id) {
        return cacheService.findOpenedPosition(id).orElseThrow();
    }

    @PostMapping("/openedPosition")
    public List<OpenedPosition> saveOpenedPositionsCache(@RequestBody List<OpenedPosition> openedPositions) {
        return cacheService.saveAllOpenedPositions(openedPositions);
    }

    @DeleteMapping("/openedPosition")
    public void deleteAllOpenedPositions() {
        cacheService.deleteAllOpenedPositions();
    }

    @PostMapping("/openedPosition/delete")
    public void deleteAllOpenedPositionsById(@RequestBody List<String> ids) {
        cacheService.deleteAllOpenedPositionsById(ids);
    }
}
