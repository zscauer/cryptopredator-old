package ru.tyumentsev.cryptopredator.binancespotbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.tyumentsev.cryptopredator.binancespotbot.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.binancespotbot.domain.PreviousCandleData;
import ru.tyumentsev.cryptopredator.binancespotbot.domain.SellRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;


@Service
@Slf4j
public class DataService {

    RestTemplate restTemplate;
    @Value("${databaseconfig.dataKeeperURL}")
    String dataKeeperURL;
    String cacheEndpoint = "/api/cache/v1";
    final String botId = "binancespotbot";

    public List<SellRecord> saveAllSellRecords(Collection<SellRecord> sellRecords) {
//        RestTemplate restTemplate = new RestTemplate();
        ParameterizedTypeReference<List<SellRecord>> typeRef = new ParameterizedTypeReference<List<SellRecord>>(){};
        HttpHeaders headers = new HttpHeaders();
        headers.set("bot-id", botId);
        HttpEntity<List<SellRecord>> request = new HttpEntity<>(new ArrayList<>(sellRecords), headers);
        var response = restTemplate.exchange(dataKeeperURL + cacheEndpoint + "/sellRecord", HttpMethod.POST, request, typeRef);
        log.debug("Next sell records saved:\n{}", response);

        return response.getBody();
    }

    public List<SellRecord> findAllSellRecords() {
//        RestTemplate restTemplate = new RestTemplate();
        ParameterizedTypeReference<List<SellRecord>> typeRef = new ParameterizedTypeReference<List<SellRecord>>(){};
        HttpHeaders headers = new HttpHeaders();
        headers.set("bot-id", botId);
        HttpEntity<List<SellRecord>> request = new HttpEntity<>(new ArrayList<>(), headers);
        var response = restTemplate.exchange(dataKeeperURL + cacheEndpoint + "/sellRecord", HttpMethod.GET, request, typeRef);
        log.debug("Get next sell records:\n{}", response);

        return response.getBody();
    }

    public void deleteAllSellRecords() {
//        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("bot-id", botId);
        restTemplate.delete(dataKeeperURL + cacheEndpoint + "/sellRecord", headers);
    }

    public List<PreviousCandleData> saveAllPreviousCandleData(Collection<PreviousCandleData> previousCandleData) {
//        RestTemplate restTemplate = new RestTemplate();
        ParameterizedTypeReference<List<PreviousCandleData>> typeRef = new ParameterizedTypeReference<List<PreviousCandleData>>(){};
        HttpHeaders headers = new HttpHeaders();
        headers.set("bot-id", botId);
        HttpEntity<List<PreviousCandleData>> request = new HttpEntity<>(new ArrayList<>(previousCandleData), headers);
        var response = restTemplate.exchange(dataKeeperURL + cacheEndpoint + "/previousCandleData", HttpMethod.POST, request, typeRef);
        log.debug("Next previous candle data saved:\n{}", response.getBody());

        return response.getBody();
    }

    public List<PreviousCandleData> findAllPreviousCandleData() {
//        RestTemplate restTemplate = new RestTemplate();
        ParameterizedTypeReference<List<PreviousCandleData>> typeRef = new ParameterizedTypeReference<List<PreviousCandleData>>(){};
        HttpHeaders headers = new HttpHeaders();
        headers.set("bot-id", botId);
        HttpEntity<List<PreviousCandleData>> request = new HttpEntity<>(new ArrayList<>(), headers);
        var response = restTemplate.exchange(dataKeeperURL + cacheEndpoint + "/previousCandleData", HttpMethod.GET, request, typeRef);
        log.debug("Get next previous candle data:\n{}", response.getBody());

        return response.getBody();
    }

    public void deleteAllPreviousCandleData(Collection<PreviousCandleData> previousCandleData) {
//        RestTemplate restTemplate = new RestTemplate();

        ParameterizedTypeReference<List<PreviousCandleData>> typeRef = new ParameterizedTypeReference<List<PreviousCandleData>>(){};
        HttpHeaders headers = new HttpHeaders();
        headers.set("bot-id", botId);
        HttpEntity<List<String>> request = new HttpEntity<>((previousCandleData.stream().map(PreviousCandleData::id)).collect(Collectors.toList()), headers);
        var response = restTemplate.exchange(dataKeeperURL + cacheEndpoint + "/previousCandleData/delete", HttpMethod.POST, request, typeRef);
        log.debug("Deleted next previous candle data:\n{}", response.getBody());
    }

    public List<OpenedPosition> saveAllOpenedPositions(Collection<OpenedPosition> openedPositions) {
//        RestTemplate restTemplate = new RestTemplate();

        ParameterizedTypeReference<List<OpenedPosition>> typeRef = new ParameterizedTypeReference<List<OpenedPosition>>(){};
        HttpHeaders headers = new HttpHeaders();
        headers.set("bot-id", botId);
        HttpEntity<List<OpenedPosition>> request = new HttpEntity<>(new ArrayList<>(openedPositions), headers);
        var response = restTemplate.exchange(dataKeeperURL + cacheEndpoint + "/openedPosition", HttpMethod.POST, request, typeRef);
        log.debug("Next opened positions saved:\n{}", response.getBody());

        return response.getBody();
    }

    public List<OpenedPosition> findAllOpenedPositions() {
//        RestTemplate restTemplate = new RestTemplate();

        ParameterizedTypeReference<List<OpenedPosition>> typeRef = new ParameterizedTypeReference<List<OpenedPosition>>(){};
        HttpHeaders headers = new HttpHeaders();
        headers.set("bot-id", botId);
        HttpEntity<List<OpenedPosition>> request = new HttpEntity<>(new ArrayList<>(), headers);
        var response = restTemplate.exchange(dataKeeperURL + cacheEndpoint + "/openedPosition", HttpMethod.GET, request, typeRef);
        log.debug("Get next opened positions:\n{}", response.getBody());

        return response.getBody();
    }

    public void deleteAllOpenedPositions() {
//        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("bot-id", botId);
        restTemplate.delete(dataKeeperURL + cacheEndpoint + "/openedPosition", headers);
    }
}
