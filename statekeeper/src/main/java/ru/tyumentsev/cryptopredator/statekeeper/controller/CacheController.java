package ru.tyumentsev.cryptopredator.statekeeper.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.tyumentsev.cryptopredator.commons.domain.BTCTrend;
import ru.tyumentsev.cryptopredator.statekeeper.domain.OpenedPositionData;
import ru.tyumentsev.cryptopredator.statekeeper.domain.PreviousCandleData;
import ru.tyumentsev.cryptopredator.statekeeper.domain.SellRecordData;
import ru.tyumentsev.cryptopredator.statekeeper.service.AccountService;
import ru.tyumentsev.cryptopredator.statekeeper.service.CacheService;

import java.util.List;

@RestController
@RequestMapping("/api/cache/v1")
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unused")
public class CacheController {

    CacheService cacheService;
    AccountService accountService;

    @GetMapping("/btcTrend")
    public BTCTrend getBTCTrend() {
        return cacheService.getBTCTrend();
    }

    @GetMapping("/sellRecord")
    public List<SellRecordData> getSellRecords() {
//        log.info("getSellRecords() call from {}.", headers.map().get("bot-id"));
        return cacheService.findAllSellRecords();
    }

    @GetMapping("/sellRecord/{id}")
    public SellRecordData getSellRecord(@PathVariable String id) {
//        log.info("getSellRecord({}) call from {}.", id, headers.map().get("bot-id"));
        return cacheService.findSellRecord(id).orElseThrow();
    }

    @PostMapping("/sellRecord")
    public List<SellRecordData> saveSellRecordsCache(@RequestBody List<SellRecordData> sellRecords) {
        log.debug("Request to save sell records: {}", sellRecords);
        List<SellRecordData> savedRecords = cacheService.saveAllSellRecords(sellRecords);
        log.info("Saved {} sell records of {} strategy.",
                savedRecords.size(),
                savedRecords.stream().findFirst().map(sellRecordData -> sellRecordData.sellRecord().strategy()).orElse("NOT DEFINED")
        );
        return savedRecords;
    }

    @PostMapping("/sellRecord/delete")
    public void deleteAllSellRecordsById(@RequestBody List<String> ids) {
//        log.info("deleteAllSellRecordsById({}) call from {}.", ids, headers.map().get("bot-id"));
        cacheService.deleteAllSellRecordsById(ids);
    }

    @GetMapping("/previousCandleContainer")
    public List<PreviousCandleData> getPreviousCandlesData() {
//        log.info("getPreviousCandlesData() call from {}.", headers.map().get("bot-id"));
        return cacheService.findAllPreviousCandleData();
    }

    @GetMapping("/previousCandleContainer/{id}")
    public PreviousCandleData getPreviousCandleData(@PathVariable String id) {
//        log.info("getPreviousCandleData({}) call from {}.", id, headers.map().get("bot-id"));
        return cacheService.findPreviousCandleData(id).orElseThrow();
    }

    @PostMapping("/previousCandleContainer")
    public List<PreviousCandleData> savePreviousCandleDataCache(@RequestBody List<PreviousCandleData> previousCandleData) {
        log.debug("Request to save previous cande data: {}", previousCandleData);
        List<PreviousCandleData> savedCandles = cacheService.saveAllPreviousCandleData(previousCandleData);
        log.info("Saved {} candles.", savedCandles.size());
        return savedCandles;
    }

    @PostMapping("/previousCandleContainer/delete")
    public void deleteAllPreviousCandlesDataById(@RequestBody List<String> ids) {
//        log.info("deleteAllPreviousCandlesDataById({}) call from {}.", ids, headers.map().get("bot-id"));
        cacheService.deleteAllPreviousCandleDataById(ids);
    }

    @GetMapping("/openedPosition")
    public List<OpenedPositionData> getOpenedPositions() {
//        log.info("getOpenedPositions() call from {}.", headers.map().get("bot-id"));
        return cacheService.findAllOpenedPositions();
    }

    @GetMapping("/openedPosition/{id}")
    public OpenedPositionData getOpenedPosition(@PathVariable String id) {
//        log.info("getOpenedPosition({}) call from {}.", id, headers.map().get("bot-id"));
        return cacheService.findOpenedPositionsById(id).orElse(null);
    }

    @PostMapping("/openedPosition")
    public List<OpenedPositionData> saveOpenedPositionsCache(@RequestBody List<OpenedPositionData> openedPositions) {
//        log.info("saveOpenedPositionsCache({}) call from {}.", openedPositions, headers.map().get("bot-id"));
        log.debug("Request to save openend positions: {}", openedPositions);
        List<OpenedPositionData> openedPositionList = cacheService.saveAllOpenedPositions(openedPositions);
        log.info("Saved {} opened positions of {} strategy.",
                openedPositionList.size(),
                openedPositionList.stream().findFirst().map(openedPositionData -> openedPositionData.openedPosition().strategy()).orElse("NOT DEFINED")
        );
        return openedPositionList;
    }

    @PostMapping("/openedPosition/delete")
    public void deleteAllOpenedPositionsById(@RequestBody List<String> openedPositionsIds) {
//        log.info("deleteAllOpenedPositionsById({}) call from {}.", ids, headers.map().get("bot-id"));
        cacheService.deleteAllOpenedPositionsById(openedPositionsIds);
    }
}
