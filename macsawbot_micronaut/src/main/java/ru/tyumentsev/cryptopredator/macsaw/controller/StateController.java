package ru.tyumentsev.cryptopredator.macsaw.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.convert.format.Format;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.cryptopredator.commons.domain.SellRecord;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.macsaw.cache.MacSawStrategyCondition;
import ru.tyumentsev.cryptopredator.macsaw.strategy.MacSaw;

import java.time.LocalDateTime;

@Controller("/state")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class StateController {

//    ObjectMapper objectMapper;
    MarketInfo marketInfo;
    MacSaw macSaw;
    MacSawStrategyCondition macSawStrategyCondition;

    @Inject
    public StateController(MarketInfo marketInfo, MacSaw macSaw, MacSawStrategyCondition macSawStrategyCondition) {
        this.marketInfo = marketInfo;
        this.macSaw = macSaw;
        this.macSawStrategyCondition = macSawStrategyCondition;
//        this.objectMapper = objectMapper;
    }

    @Get(uri = "/ping", produces = MediaType.APPLICATION_JSON)
    public @Format("yyyy-MM-dd") SellRecord testController() throws JsonProcessingException {
//        log.info(objectMapper.writeValueAsString(LocalDateTime.now()));
        var body = LocalDateTime.now();
//        var body = new SellRecord("BTC", LocalDateTime.now(), "macsaw");
        return new SellRecord("sss", LocalDateTime.now(), "mac");
    }

}
