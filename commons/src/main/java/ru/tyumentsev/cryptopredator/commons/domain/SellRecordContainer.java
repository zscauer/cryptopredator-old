package ru.tyumentsev.cryptopredator.commons.domain;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record SellRecordContainer(
        String id,
        SellRecord sellRecord
) {}
