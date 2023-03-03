package ru.tyumentsev.cryptopredator.macsaw.controller;

import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import io.micronaut.core.convert.format.Format;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.BarSeries;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;
import ru.tyumentsev.cryptopredator.commons.domain.PlacedOrder;
import ru.tyumentsev.cryptopredator.commons.domain.SellRecord;
import ru.tyumentsev.cryptopredator.commons.service.MarketInfo;
import ru.tyumentsev.cryptopredator.macsaw.cache.MacSawStrategyCondition;
import ru.tyumentsev.cryptopredator.macsaw.strategy.MacSaw;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller("/state")
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
@Slf4j
public class StateController {

    MacSawStrategyCondition macSawStrategyCondition;
    MarketInfo marketInfo;
    MacSaw macSaw;

    @Inject
    public StateController(MarketInfo marketInfo, MacSaw macSaw, MacSawStrategyCondition macSawStrategyCondition) {
        this.marketInfo = marketInfo;
        this.macSaw = macSaw;
        this.macSawStrategyCondition = macSawStrategyCondition;
    }

    @Get(uri = "/ping", produces = MediaType.APPLICATION_JSON)
    public SellRecord testController() {
        return new SellRecord("sss", LocalDateTime.now(), "mac");
    }

    @Get("/placedOrders")
    public Map<String, PlacedOrder> getPlacedOrders() {
        return marketInfo.getPlacedOrders();
    }

    @Get("/openedPositions/long")
    public List<String> getOpenedLongPositions() {
        return macSawStrategyCondition.getLongPositions().values().stream()
                .map(OpenedPosition::toString)
//                .sorted(Comparator.comparing(OpenedPosition::updateStamp))
                .collect(Collectors.toList());
    }

    @Get("/openedPositions/short")
    public List<String> getOpenedShortPositions() {
        return macSawStrategyCondition.getShortPositions().values().stream()
                .map(OpenedPosition::toString)
//                .sorted(Comparator.comparing(OpenedPosition::updateStamp))
                .collect(Collectors.toList());
    }

    @Delete("/openedPositions/long/{pair}")
    public void deletePairFromOpenedLongPositionsCache(@PathVariable String pair) {
        macSawStrategyCondition.getLongPositions().remove(pair.toUpperCase());
    }

    @Get("/sellJournal")
    public List<SellRecord> getSellJournal() {
        return macSawStrategyCondition.getSellJournal().values().stream()
                .sorted(Comparator.comparing(SellRecord::sellTime).reversed())
                .collect(Collectors.toList());
    }

    @Get("/candleStickEventsStreams")
    public Map<String, List<String>> getCandleStickEventsStreams() {
        Map<String, List<String>> response = new HashMap<>();
        response.put("Opened", macSaw.getOpenedPositionsCandleStickEventsStreams().keySet().stream()
                .sorted()
                .collect(Collectors.toList()));
        response.put("Market", macSaw.getMarketCandleStickEventsStreams().keySet().stream()
                .sorted()
                .collect(Collectors.toList()));
        return response;
    }

    @Get("/barSeries/market")
    public List<BarSeries> getAllMarketBarSeries() {
        return new ArrayList<>(macSaw.getMarketBarSeriesMap().values());
    }

    @Get("/barSeries/opened")
    public List<BarSeries> getAllOpenedPositionsBarSeries() {
        return new ArrayList<>(macSaw.getOpenedPositionsBarSeriesMap().values());
    }

    @Get("/barSeries/market/{symbol}")
    public BarSeries getMarketBarSeries(@PathVariable String symbol) {
        return macSaw.getMarketBarSeriesMap().get(symbol);
    }

    @Get("/barSeries/opened/{symbol}")
    public BarSeries getOpenedPositionBarSeries(@PathVariable String symbol) {
        return macSaw.getOpenedPositionsBarSeriesMap().get(symbol);
    }
    @Post("/userDataUpdateEvent")
    public void handleUserDataUpdateEvent(@Body OrderTradeUpdateEvent event) {
        log.debug("Get order trade update event: {}", event);
        switch (event.getSide()) {
            case BUY -> {
                macSaw.handleBuying(event);
            }
            case SELL -> {
                macSaw.handleSelling(event);
            }
        }
    }

}
