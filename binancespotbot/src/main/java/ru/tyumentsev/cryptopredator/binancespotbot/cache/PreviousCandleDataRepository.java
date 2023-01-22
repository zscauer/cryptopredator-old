package ru.tyumentsev.cryptopredator.binancespotbot.cache;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.tyumentsev.cryptopredator.binancespotbot.domain.PreviousCandleData;

@Repository
public interface PreviousCandleDataRepository extends CrudRepository<PreviousCandleData, String> {}
