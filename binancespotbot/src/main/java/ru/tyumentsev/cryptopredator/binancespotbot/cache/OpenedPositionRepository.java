package ru.tyumentsev.cryptopredator.binancespotbot.cache;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.tyumentsev.cryptopredator.binancespotbot.domain.OpenedPosition;

@Repository
public interface OpenedPositionRepository extends CrudRepository<OpenedPosition, String> {}
