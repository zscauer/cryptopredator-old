package ru.tyumentsev.cryptopredator.statekeeper.cache;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.tyumentsev.cryptopredator.statekeeper.domain.OpenedPositionData;

@Repository
public interface OpenedPositionRepository extends CrudRepository<OpenedPositionData, String> {}
