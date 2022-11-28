package ru.tyumentsev.binancespotbot.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GeneralMonitoring {

    AccountManager accountManager;

    /**
     * Search for not executed orders to cancel them.
     */
    @Scheduled(fixedDelayString = "${strategy.monitoring.cancelExpiredOrders.fixedDelay}", initialDelayString = "${strategy.monitoring.cancelExpiredOrders.initialDelay}")
    public void generalMonitoring_cancelExpiredOrders() {
        
    }
    
}
