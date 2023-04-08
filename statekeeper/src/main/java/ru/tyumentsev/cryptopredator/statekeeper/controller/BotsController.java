package ru.tyumentsev.cryptopredator.statekeeper.controller;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.tyumentsev.cryptopredator.commons.domain.StrategyLimit;
import ru.tyumentsev.cryptopredator.statekeeper.cache.BotState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/bots")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("unused")
public class BotsController {

    BotState botState;

    @GetMapping("/activeStrategies")
    public Map<Integer, String> getActiveStrategies() {
        return botState.getActiveBots();
    }

    @PostMapping("/activeStrategies")
    public void addActiveStrategy(@RequestBody Map<String, String> parameters) {
        botState.getActiveBots().put(Integer.valueOf(parameters.get("strategyId")), parameters.get("botAddress"));
        log.info("Strategy with id '{}' was added to user data stream monitoring with address {}.", parameters.get("strategyId"), parameters.get("botAddress"));
    }

    @DeleteMapping("/activeStrategies")
    public void deleteActiveStrategy(@RequestParam Integer strategyId) {
        botState.getActiveBots().remove(strategyId);
        log.info("Strategy with id '{}' was deleted from user data stream monitoring.", strategyId);
    }

    @PostMapping("/strategyLimits/{strategyId}")
    public void setInitialStrategyLimits(@PathVariable Integer strategyId, @RequestParam Integer ordersQtyLimit, @RequestParam Integer baseOrderVolume) {
        var currentLimits = Optional.ofNullable(botState.getStrategyLimits().get(strategyId)).orElseGet(HashMap::new);
        currentLimits.put(StrategyLimit.ORDERS_QTY, ordersQtyLimit);
        currentLimits.put(StrategyLimit.ORDER_VOLUME, baseOrderVolume);
        botState.getStrategyLimits().put(strategyId, currentLimits);
        log.info("Limits for strategy with id '{}' set to {}.", strategyId, currentLimits);
    }

    @GetMapping("/strategyLimits")
    public Map<Integer, Map<StrategyLimit, Integer>> getAllAvailableStrategyLimits() {
//        return Optional.ofNullable(botState.getStrategyLimits().get(strategyId)).orElseGet(HashMap::new);
        return botState.getStrategyLimits();
//        return botState.getStrategyLimits().entrySet().stream().collect(Collectors.groupingBy(Map.Entry::getKey));
    }

    @GetMapping("/strategyLimits/{strategyId}")
    public Map<StrategyLimit, Integer> getAvailableStrategyLimits(@PathVariable Integer strategyId) {
        return Optional.ofNullable(botState.getStrategyLimits().get(strategyId)).orElseGet(HashMap::new);
    }

    @GetMapping("/strategyLimits/{strategyId}/{limitName}")
    public Integer getAvailableStrategyLimits(@PathVariable Integer strategyId, @PathVariable StrategyLimit limitName) {
        return Optional.ofNullable(botState.getStrategyLimits().get(strategyId)).orElseGet(HashMap::new).get(limitName);
    }
}
