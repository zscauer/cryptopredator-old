package ru.tyumentsev.cryptopredator.commons.domain;

import java.time.ZonedDateTime;

public record MonitoredPosition(
        String symbol,
        float price,
        ZonedDateTime beginMonitoringTime
) {}
