package ru.tyumentsev.binancetradebot.binancespotbot.cache;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.tyumentsev.binancetradebot.binancespotbot.domain.PreviousCandleData;

@Repository
public interface PreviousCandleDataRepository extends CrudRepository<PreviousCandleData, String> {}
