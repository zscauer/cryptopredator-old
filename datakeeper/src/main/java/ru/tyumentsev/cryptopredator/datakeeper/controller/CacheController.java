package ru.tyumentsev.cryptopredator.datakeeper.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.tyumentsev.cryptopredator.datakeeper.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.datakeeper.domain.PreviousCandleData;
import ru.tyumentsev.cryptopredator.datakeeper.domain.SellRecord;
import ru.tyumentsev.cryptopredator.datakeeper.service.CacheService;

import java.net.http.HttpHeaders;
import java.util.List;

@RestController
@RequestMapping("/api/cache/v1")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
public class CacheController {

    CacheService cacheService;

    @GetMapping("/sellRecord")
    public List<SellRecord> getSellRecords() {
//        log.info("getSellRecords() call from {}.", headers.map().get("bot-id"));
        return cacheService.findAllSellRecords();
    }

    @GetMapping("/sellRecord/{id}")
    public SellRecord getSellRecord(@PathVariable String id) {
//        log.info("getSellRecord({}) call from {}.", id, headers.map().get("bot-id"));
        return cacheService.findSellRecord(id).orElseThrow();
    }

    @PostMapping("/sellRecord")
    public List<SellRecord> saveSellRecordsCache(@RequestBody List<SellRecord> records) {
//        log.info("saveSellRecordsCache({}) call from {}.", records, headers.map().get("bot-id"));
        return cacheService.saveAllSellRecords(records);
    }

//    @PostMapping("/sellRecord/delete")
//    public void deleteAllSellRecordsById(@RequestBody List<String> ids) {
////        log.info("deleteAllSellRecordsById({}) call from {}.", ids, headers.map().get("bot-id"));
//        cacheService.deleteAllSellRecordsById(ids);
//    }

    @PostMapping("/sellRecord/delete")
    public void deleteAllSellRecords(@RequestBody List<SellRecord> records) {
//        log.info("deleteAllSellRecords() call from {}.", headers.map().get("bot-id"));
        cacheService.deleteAllSellRecords(records);
    }

    @GetMapping("/previousCandleData")
    public List<PreviousCandleData> getPreviousCandlesData() {
//        log.info("getPreviousCandlesData() call from {}.", headers.map().get("bot-id"));
        return cacheService.findAllPreviousCandleData();
    }

    @GetMapping("/previousCandleData/{id}")
    public PreviousCandleData getPreviousCandleData(@PathVariable String id) {
//        log.info("getPreviousCandleData({}) call from {}.", id, headers.map().get("bot-id"));
        return cacheService.findPreviousCandleData(id).orElseThrow();
    }

    @PostMapping("/previousCandleData")
    public List<PreviousCandleData> savePreviousCandleDataCache(@RequestBody List<PreviousCandleData> previousCandleData) {
//        log.info("savePreviousCandleDataCache({}) call from {}.", previousCandleData, headers.map().get("bot-id"));
        return cacheService.saveAllPreviousCandleData(previousCandleData);
    }

    @PostMapping("/previousCandleData/delete")
    public void deleteAllPreviousCandlesDataById(@RequestBody List<String> ids) {
//        log.info("deleteAllPreviousCandlesDataById({}) call from {}.", ids, headers.map().get("bot-id"));
        cacheService.deleteAllPreviousCandleDataById(ids);
    }

    @GetMapping("/openedPosition")
    public List<OpenedPosition> getOpenedPositions() {
//        log.info("getOpenedPositions() call from {}.", headers.map().get("bot-id"));
        return cacheService.findAllOpenedPositions();
    }

    @GetMapping("/openedPosition/{id}")
    public OpenedPosition getOpenedPosition(@PathVariable String id) {
//        log.info("getOpenedPosition({}) call from {}.", id, headers.map().get("bot-id"));
        return cacheService.findOpenedPosition(id).orElseThrow();
    }

    @PostMapping("/openedPosition")
    public List<OpenedPosition> saveOpenedPositionsCache(@RequestBody List<OpenedPosition> openedPositions) {
//        log.info("saveOpenedPositionsCache({}) call from {}.", openedPositions, headers.map().get("bot-id"));
        return cacheService.saveAllOpenedPositions(openedPositions);
    }

//    @DeleteMapping("/openedPosition")
//    public void deleteAllOpenedPositions() {
////        log.info("deleteAllOpenedPositions() call from {}.", headers.map().get("bot-id"));
//        cacheService.deleteAllOpenedPositions();
//    }

    @PostMapping("/openedPosition/delete")
    public void deleteAllOpenedPositionsById(@RequestBody List<OpenedPosition> openedPositions) {
//        log.info("deleteAllOpenedPositionsById({}) call from {}.", ids, headers.map().get("bot-id"));
        cacheService.deleteAllOpenedPositions(openedPositions);
    }
}
