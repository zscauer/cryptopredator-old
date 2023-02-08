package ru.tyumentsev.cryptopredator.statekeeper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPosition;

@RedisHash("OpenedPosition")
public record OpenedPositionData(
        @Id
        String id,
        OpenedPosition openedPosition
) {}