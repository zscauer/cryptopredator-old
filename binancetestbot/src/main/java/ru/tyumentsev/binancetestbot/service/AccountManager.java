package ru.tyumentsev.binancetestbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiRestClient;

@Service
public class AccountManager {
    
    @Autowired
    BinanceApiRestClient restClient;

    public Double getFreeAssetBalance(String asset) {
        return Double.parseDouble(restClient.getAccount().getAssetBalance(asset).getFree());
    }

    public void name() {
        restClient.getAccount().getBalances();
    }


}
