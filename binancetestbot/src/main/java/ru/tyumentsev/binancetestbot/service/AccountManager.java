package ru.tyumentsev.binancetestbot.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.AssetBalance;

@Service
public class AccountManager {
    
    @Autowired
    BinanceApiRestClient restClient;

    public Double getFreeAssetBalance(String asset) {
        return Double.parseDouble(restClient.getAccount().getAssetBalance(asset).getFree());
    }

    public List<AssetBalance> getAccountBalances() {
        return restClient.getAccount().getBalances().stream().filter(balance -> Double.parseDouble(balance.getFree()) > 0).toList();
    }


}
