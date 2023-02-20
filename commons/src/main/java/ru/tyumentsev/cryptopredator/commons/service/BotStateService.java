package ru.tyumentsev.cryptopredator.commons.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.tyumentsev.cryptopredator.commons.domain.StrategyLimit;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class BotStateService {

    final BotStateServiceClient botStateServiceClient;

    public void addActiveStrategy(Map<String, String> parameters) {
        try {
            botStateServiceClient.addActiveStrategy(parameters).execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteActiveStrategy(String strategyName) {
        try {
            botStateServiceClient.deleteActiveStrategy(strategyName).execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Integer getAvailableOrdersCount(Integer strategyId) {
        try {
            return botStateServiceClient.getAvailableStrategyLimit(strategyId, StrategyLimit.ORDERS_QTY).execute().body();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setAvailableOrdersLimit(Integer strategyId, Integer ordersQtyLimit, Integer baseOrderVolume) {
        try {
            botStateServiceClient.setAvailableOrdersLimit(strategyId, ordersQtyLimit, baseOrderVolume).execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
