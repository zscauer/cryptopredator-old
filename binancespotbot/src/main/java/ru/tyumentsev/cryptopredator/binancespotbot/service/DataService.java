package ru.tyumentsev.cryptopredator.binancespotbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.tyumentsev.cryptopredator.binancespotbot.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.binancespotbot.domain.PreviousCandleData;
import ru.tyumentsev.cryptopredator.binancespotbot.domain.SellRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


@Service
@Slf4j
public class DataService {

    String dataKeeperURL = "http://localhost:86/api/cache/v1";

    public void saveAllSellRecords(Collection<SellRecord> sellRecords) {
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<List<SellRecord>> requeset = new HttpEntity<>(new ArrayList<>(sellRecords));
        var response = restTemplate.postForEntity(dataKeeperURL, requeset, ArrayList.class);
        log.info("Next sell records saved:\n{}", response.getBody());
    }

    public void saveAllOpenedPositions(Collection<OpenedPosition> openedPositions) {
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<List<OpenedPosition>> requeset = new HttpEntity<>(new ArrayList<>(openedPositions));
        var response = restTemplate.postForEntity(dataKeeperURL, requeset, ArrayList.class);
        log.info("Next opened positions saved:\n{}", response.getBody());
    }

    public void saveAllPreviousCandleData(Collection<PreviousCandleData> previousCandleData) {
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<List<PreviousCandleData>> requeset = new HttpEntity<>(new ArrayList<>(previousCandleData));
        var response = restTemplate.postForEntity(dataKeeperURL, requeset, ArrayList.class);
//        log.info("Next previous candle data saved:\n{}", response.getBody());
    }

    public List<SellRecord> findAllSellRecords() {
        RestTemplate restTemplate = new RestTemplate();
        var records = restTemplate.getForObject(dataKeeperURL, ArrayList.class);
        log.info("Get next sell records:\n{}", records);

        return records;
    }

    public List<OpenedPosition> findAllOpenedPositions() {
        RestTemplate restTemplate = new RestTemplate();
        var positions = restTemplate.getForObject(dataKeeperURL, ArrayList.class);
        log.info("Get next sell records:\n{}", positions);

        return positions;
    }

    public List<PreviousCandleData> findAllPreviousCandleData() {
        RestTemplate restTemplate = new RestTemplate();
        var candleData = restTemplate.getForObject(dataKeeperURL, ArrayList.class);
        log.info("Get next sell records:\n{}", candleData);

        return candleData;
    }

    public void deleteAllSellRecords() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.delete(dataKeeperURL);
    }

    public void deleteAllOpenedPositions() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.delete(dataKeeperURL);
    }

    public void deleteAllPreviousCandleData(Collection<PreviousCandleData> openedPositions) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.delete(dataKeeperURL, Collections.singletonMap("openedPositions", openedPositions));
    }

}
