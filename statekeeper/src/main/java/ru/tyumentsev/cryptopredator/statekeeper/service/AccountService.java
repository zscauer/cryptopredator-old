package ru.tyumentsev.cryptopredator.statekeeper.service;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.ExecutionType;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.event.UserDataUpdateEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.tyumentsev.cryptopredator.commons.domain.StrategyLimit;
import ru.tyumentsev.cryptopredator.commons.service.AccountManager;
import ru.tyumentsev.cryptopredator.commons.service.TradingService;
import ru.tyumentsev.cryptopredator.statekeeper.cache.BotState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static ru.tyumentsev.cryptopredator.commons.domain.StrategyLimit.*;

@Service
@FieldDefaults(level = AccessLevel.PROTECTED)
@Slf4j
@SuppressWarnings("unused")
public class AccountService extends AccountManager {

    @Autowired
    @Setter
    BotState botState;
    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;
    final Map<StrategyLimit, Integer> emptyStrategyLimits = new HashMap<>();
    @Getter
    final Map<TradingService, Boolean> pingPongs = new ConcurrentHashMap<>();

    public AccountService(BinanceApiRestClient restClient, BinanceApiWebSocketClient webSocketClient) {
        super(restClient, webSocketClient);
    }

    public void ping() {
        pingPongs.put(this, true);
    }

    private boolean pong() {
        return pingPongs.remove(this, true);
    }

    @Scheduled(fixedDelayString = "${monitoring.initializeUserDataUpdateStream.fixedDelay}", initialDelayString = "${monitoring.initializeUserDataUpdateStream.initialDelay}")
    public void monitoring_initializeAliveUserDataUpdateStream() {
        if (!testLaunch) {
            log.info("initializeAliveUserDataUpdateStream()");
            // User data stream are closing by binance after 24 hours after opening.
            initializeUserDataUpdateStream(new BinanceApiCallback<>() {
                @Override
                public void onResponse(final UserDataUpdateEvent callback) {
                    if (pong()) {
                        log.info("Pong from initializeUserDataUpdateStream():\nisAlive:{}/state:{}.\n{}", Thread.currentThread().isAlive(), Thread.currentThread().getState(), callback);
                    }

                    if (callback.getEventType().equals(UserDataUpdateEvent.UserDataUpdateEventType.ORDER_TRADE_UPDATE)) {
                        OrderTradeUpdateEvent event = callback.getOrderTradeUpdateEvent();
                        log.info("Get {} {} ORDER_TRADE_UPDATE of {}", event.getExecutionType(), event.getSide(), event.getSymbol());
                        switch (event.getExecutionType()) {
                            case NEW -> {
                                updateLimits(event);
                            }
                            case TRADE -> {
                                if (Float.valueOf(event.getAccumulatedQuantity()).equals(Float.valueOf(event.getOriginalQuantity()))) {
                                    if (!botState.getActiveBots().isEmpty()) {
                                        updateLimits(event);
                                        Optional.ofNullable(botState.getActiveBots().get(event.getStrategyId())).ifPresentOrElse(endpointURL -> {
                                            notifyBot(endpointURL, event);
                                        }, () -> {
                                            log.info("Bot with strategy id '{}' from trade event not found, notify all bots.", event.getStrategyId());
                                            botState.getActiveBots().values().forEach(endpointURL -> notifyBot(endpointURL, event));
                                        });
                                    } else {
                                        log.warn("No user data update event listeners found, but {} trade event recieved:\n{}", event.getExecutionType(), event);
                                    }
                                    refreshAccountBalances();
                                }
                            }
                        }
                    }
//                    if (callback.getEventType() == UserDataUpdateEvent.UserDataUpdateEventType.ORDER_TRADE_UPDATE
//                    && callback.getOrderTradeUpdateEvent().getExecutionType() == ExecutionType.NEW) {
//                        log.info("'NEW' order trade update recieved: {}", callback.getOrderTradeUpdateEvent());
//                    }
//                    if (callback.getEventType() == UserDataUpdateEvent.UserDataUpdateEventType.ORDER_TRADE_UPDATE
//                            && callback.getOrderTradeUpdateEvent().getExecutionType() == ExecutionType.TRADE) {
//
//                    }
                }

                @Override
                public void onFailure(Throwable cause) {
                    BinanceApiCallback.super.onFailure(cause);
                    log.error("Binance API callback failure: {}", cause.getMessage());
                }
            });
        } else {
            log.warn("Application launched in test mode. User update events listening disabled.");
        }
    }

    private void updateLimits(final OrderTradeUpdateEvent event) {
        var strategyLimits = botState.getStrategyLimits().getOrDefault(event.getStrategyId(), emptyStrategyLimits);
        if (!strategyLimits.isEmpty()) {
            strategyLimits.put(ORDERS_QTY, calculateNewOrdersQtyLimitValue(event, strategyLimits));
        }
    }

    private int calculateNewOrdersQtyLimitValue(final OrderTradeUpdateEvent event, final Map<StrategyLimit, Integer> strategyLimits) {
        int currentOrdersQtyLimitValue = strategyLimits.get(ORDERS_QTY);
        switch (event.getSide()) {
            case SELL -> {
                if (event.getExecutionType() == ExecutionType.TRADE) {
                    int ordersQtyInTrade = ((Double) Math.ceil(Double.parseDouble(event.getCumulativeQuoteQty()))).intValue() / strategyLimits.get(ORDER_VOLUME);// qty of base order in current trade.
                    return currentOrdersQtyLimitValue + ordersQtyInTrade;
                }
            }
            case BUY -> {
                switch (event.getExecutionType()) {
                    case NEW -> {
                        return currentOrdersQtyLimitValue - 1;
                    }
                    case CANCELED, EXPIRED -> {
                        log.info("'BUY' order of {} from strategy '{}' is expired. ({}).", event.getSymbol(), event.getStrategyId(), event);
                        return currentOrdersQtyLimitValue + 1;
                    }
                }
            }
        }
        return currentOrdersQtyLimitValue;
    }


    @Scheduled(fixedDelayString = "${monitoring.keepAliveUserDataUpdateStream.fixedDelay}", initialDelayString = "${monitoring.keepAliveUserDataUpdateStream.initialDelay}")
    public void monitoring_keepAliveUserDataUpdateStream() {
        if (!testLaunch) {
            keepAliveUserDataUpdateStream();
        }
    }

    private void notifyBot(final String listenerEndpoint, OrderTradeUpdateEvent event) {
        RestTemplate restTemplate = new RestTemplate();
        log.info("Sending to {} event {}.", listenerEndpoint, event);
        restTemplate.exchange(listenerEndpoint, HttpMethod.POST, new HttpEntity<>(event), Void.class);
    }

}
