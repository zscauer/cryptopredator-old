package ru.tyumentsev.cryptopredator.statekeeper.cache;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.tyumentsev.cryptopredator.statekeeper.domain.PreviousCandleData;

@Repository
public interface PreviousCandleDataRepository extends CrudRepository<PreviousCandleData, String> {}

