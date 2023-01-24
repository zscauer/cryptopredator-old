package ru.tyumentsev.cryptopredator.binancespotbot.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import ru.tyumentsev.cryptopredator.binancespotbot.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.binancespotbot.domain.PreviousCandleData;
import ru.tyumentsev.cryptopredator.binancespotbot.domain.SellRecord;

public class DataService {

    String dataKeeperURL = "http://localhost:86/api/cache/v1";

    public void saveAllSellRecords(Iterable<SellRecord> sellRecords) {
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<Iterable<SellRecord>> requeset = new HttpEntity<>(sellRecords);
        restTemplate.exchange(dataKeeperURL, HttpMethod.POST, requeset, Iterable.class);
        sellRecordRepository.saveAll(sellRecords);
    }

    public void saveAllOpenedPositions(Iterable<OpenedPosition> openedPositions) {
        RestTemplate restTemplate = new RestTemplate();

        openedPositionRepository.saveAll(openedPositions);
    }

    public void saveAllPreviousCandleData(Iterable<PreviousCandleData> previousCandleData) {
        RestTemplate restTemplate = new RestTemplate();

        previousCandleDataRepository.saveAll(previousCandleData);
    }
}
