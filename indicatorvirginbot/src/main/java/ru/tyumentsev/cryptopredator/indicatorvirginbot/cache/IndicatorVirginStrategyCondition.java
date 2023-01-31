package ru.tyumentsev.cryptopredator.indicatorvirginbot.cache;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.tyumentsev.cryptopredator.commons.cache.StrategyCondition;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class IndicatorVirginStrategyCondition extends StrategyCondition {

}
