package ru.tyumentsev.binancetradebot.binancespotbot.cache;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.tyumentsev.binancetradebot.binancespotbot.domain.SellRecord;

@Repository
public interface SellRecordRepository extends CrudRepository<SellRecord, String> {}
