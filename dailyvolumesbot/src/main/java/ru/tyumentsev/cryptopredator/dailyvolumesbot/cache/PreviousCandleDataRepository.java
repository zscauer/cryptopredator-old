package ru.tyumentsev.cryptopredator.dailyvolumesbot.cache;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.tyumentsev.cryptopredator.dailyvolumesbot.domain.PreviousCandleData;

@Repository
public interface PreviousCandleDataRepository extends CrudRepository<PreviousCandleData, String> {}
