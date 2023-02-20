package ru.tyumentsev.cryptopredator.statekeeper.service;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.ExecutionType;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.event.UserDataUpdateEvent;
import lombok.AccessLevel;
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
import ru.tyumentsev.cryptopredator.statekeeper.cache.BotState;

import java.util.HashMap;
import java.util.Optional;

import static ru.tyumentsev.cryptopredator.commons.domain.StrategyLimit.*;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
@SuppressWarnings("unused")
public class AccountService extends AccountManager {

    @Autowired
    @Setter
    BotState botState;
    @Value("${applicationconfig.testLaunch}")
    boolean testLaunch;

    public AccountService(BinanceApiRestClient restClient, BinanceApiWebSocketClient webSocketClient) {
        super(restClient, webSocketClient);
    }

    @Scheduled(fixedDelayString = "${monitoring.initializeUserDataUpdateStream.fixedDelay}", initialDelayString = "${monitoring.initializeUserDataUpdateStream.initialDelay}")
    public void monitoring_initializeAliveUserDataUpdateStream() {
        if (!testLaunch) {
            log.info("initializeAliveUserDataUpdateStream()");
            // User data stream are closing by binance after 24 hours of opening.
            initializeUserDataUpdateStream(new BinanceApiCallback<>() {
                @Override
                public void onResponse(UserDataUpdateEvent callback) {
                    if (callback.getEventType() == UserDataUpdateEvent.UserDataUpdateEventType.ORDER_TRADE_UPDATE
                            && callback.getOrderTradeUpdateEvent().getExecutionType() == ExecutionType.TRADE) {
                        OrderTradeUpdateEvent event = callback.getOrderTradeUpdateEvent();
                        if (Float.parseFloat(event.getAccumulatedQuantity()) == Float.parseFloat(event.getOriginalQuantity())) {
                            if (!botState.getActiveBots().isEmpty()) {
                                updateLimits(event);
                                botState.getActiveBots().values().forEach(endpointURL -> notifyBot(endpointURL, event));
                            } else {
                                log.warn("No user data update event listeners found, but new trade event recieved:\n{}", event);
                            }
                            refreshAccountBalances();
                        }
                    }
                }

                private void updateLimits(OrderTradeUpdateEvent event) {
                    var strategyLimits = Optional.ofNullable(botState.getStrategyLimits().get(event.getStrategyId())).orElseGet(HashMap::new);
                    if (!strategyLimits.isEmpty()) {
                        int ordersQtyInTrade = ((Double) Math.ceil(Double.parseDouble(event.getCumulativeQuoteQty()))).intValue() / strategyLimits.get(ORDER_VOLUME);// qty of base orders in current trade.
                        switch (event.getSide()) {
                            case SELL -> strategyLimits.put(ORDERS_QTY, strategyLimits.get(ORDERS_QTY) + ordersQtyInTrade);
                            case BUY -> strategyLimits.put(ORDERS_QTY, strategyLimits.get(ORDERS_QTY) - ordersQtyInTrade);
                        }
                    }
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

    @Scheduled(fixedDelayString = "${monitoring.keepAliveUserDataUpdateStream.fixedDelay}", initialDelayString = "${monitoring.keepAliveUserDataUpdateStream.initialDelay}")
    public void monitoring_keepAliveUserDataUpdateStream() {
        if (!testLaunch) {
            keepAliveUserDataUpdateStream();
        }
    }

    private void notifyBot(final String listenerEndpoint, OrderTradeUpdateEvent event) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.exchange(listenerEndpoint, HttpMethod.POST, new HttpEntity<>(event), Void.class);
    }

}
