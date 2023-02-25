package ru.tyumentsev.cryptopredator.commons.service;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import ru.tyumentsev.cryptopredator.commons.domain.StrategyLimit;

import java.util.Map;

public interface BotStateServiceClient {

    @POST("/api/bots/activeStrategies")
    public Call<Void> addActiveStrategy(@Body Map<String, String> parameters);

    @DELETE("/api/bots/activeStrategies")
    public Call<Void> deleteActiveStrategy(@Query("strategyId") String strategyId);

    @POST("/api/bots/strategyLimits/{strategyId}")
    public Call<Void> setAvailableOrdersLimit(@Path("strategyId") Integer strategyId,
                                              @Query("ordersQtyLimit") Integer ordersQtyLimit,
                                              @Query("baseOrderVolume") Integer baseOrderVolume);

    @GET("/api/bots/strategyLimits/{strategyId}")
    public Call<Map<StrategyLimit, Integer>> getAvailableStrategyLimits(@Path("strategyId") Integer strategyId);

    @GET("/api/bots/strategyLimits/{strategyId}/{limitName}")
    public Call<Integer> getAvailableStrategyLimit(@Path("strategyId") Integer strategyId, @Path("limitName") StrategyLimit limitName);
}
