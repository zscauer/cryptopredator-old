package ru.tyumentsev.cryptopredator.statekeeper.cache;

import lombok.Getter;
import org.springframework.stereotype.Service;
import ru.tyumentsev.cryptopredator.commons.domain.StrategyLimit;

import java.util.HashMap;
import java.util.Map;

@Service
public class BotState {

    /**
     * key - strategy name, value - bot endpoint.
     */
    @Getter
    final Map<String, String> activeBots = new HashMap<>();

    /**
     * key - strategy id, value - map (limit type / limit value).
     */
    @Getter
    final Map<Integer, Map<StrategyLimit, Integer>> strategyLimits = new HashMap<>();

}
