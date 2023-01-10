package ru.tyumentsev.binancespotbot.cache;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.tyumentsev.binancespotbot.domain.OpenedPosition;

@Repository
public interface OpenedPositionRepository extends CrudRepository<OpenedPosition, String> {}
