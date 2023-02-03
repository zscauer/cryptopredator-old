package ru.tyumentsev.cryptopredator.datakeeper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import ru.tyumentsev.cryptopredator.commons.domain.SellRecord;

@RedisHash("SellRecord")
public record SellRecordData (
        @Id
        String id,
        SellRecord sellRecord
) {}
