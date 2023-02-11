package ru.tyumentsev.cryptopredator.commons.service;

import com.binance.api.client.domain.account.AssetBalance;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

import java.util.List;

public interface AccountServiceClient {

    @GET("/api/account/accountBalance")
    public Call<List<AssetBalance>> getAllAccountBalances();

    @GET("/api/account/accountBalance/{asset}")
    public Call<AssetBalance> getAssetBalance(@Path("asset") String asset);

    @GET("/api/account/accountBalance/{asset}/free")
    public Call<Float> getFreeAssetBalance(@Path("asset") String asset);

}
