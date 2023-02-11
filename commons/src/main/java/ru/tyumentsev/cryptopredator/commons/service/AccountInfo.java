package ru.tyumentsev.cryptopredator.commons.service;

import com.binance.api.client.domain.account.AssetBalance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class AccountInfo {

    final AccountServiceClient accountServiceClient;

    public List<AssetBalance> getAllAccountBalances() {
        try {
            return accountServiceClient.getAllAccountBalances().execute().body();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public AssetBalance getAssetBalance(String asset) {
        try {
            return accountServiceClient.getAssetBalance(asset).execute().body();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Float getFreeAssetBalance(String asset) {
        try {
            return accountServiceClient.getFreeAssetBalance(asset).execute().body();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
