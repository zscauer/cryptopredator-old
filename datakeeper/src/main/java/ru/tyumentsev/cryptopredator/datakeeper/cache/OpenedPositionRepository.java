package ru.tyumentsev.cryptopredator.datakeeper.cache;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.tyumentsev.cryptopredator.datakeeper.domain.OpenedPosition;

@Repository
public interface OpenedPositionRepository extends CrudRepository<OpenedPosition, String> {}
