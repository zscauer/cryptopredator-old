package ru.tyumentsev.binancespotbot.cache;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.tyumentsev.binancespotbot.domain.SellRecord;

@Repository
public interface SellRecordRepository extends CrudRepository<SellRecord, String> {}
