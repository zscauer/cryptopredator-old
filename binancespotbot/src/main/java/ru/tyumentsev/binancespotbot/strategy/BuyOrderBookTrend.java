package ru.tyumentsev.binancespotbot.strategy;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.event.DepthEvent;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.binancespotbot.cache.MarketData;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BuyOrderBookTrend {

    BinanceApiWebSocketClient binanceApiWebSocketClient;
    MarketData marketData;

    public void testMethod1() {
        List<String> cheapPairs = marketData.getCheapPairsExcludeOpenedPositions("USDT").subList(0, 9);
        Map<String, List<DepthEvent>> books = new HashMap<>();

        for (String pair : cheapPairs) {
            Closeable depth = binanceApiWebSocketClient.onDepthEvent(pair.toLowerCase(),
                    response -> {
                        List<DepthEvent> eventsList = books.getOrDefault(pair, new ArrayList<>());
                        eventsList.add(response);
                        books.put(pair, eventsList);
                    });
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                try {
                    depth.close();
                    log.info("Stream closed.");
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

        log.info("Get next elements from streams:\n{}", books);

        // Closeable depth = binanceApiWebSocketClient.onDepthEvent("yfiiusdt", response
        // -> System.out.println(response));
        // try {
        // Thread.sleep(3000L);
        // } catch (InterruptedException e) {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // } finally {
        // try {
        // depth.close();
        // log.info("Stream closed.");
        // } catch (IOException e) {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
        // }

    }

}
