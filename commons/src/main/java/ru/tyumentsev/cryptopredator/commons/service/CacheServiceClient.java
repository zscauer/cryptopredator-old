package ru.tyumentsev.cryptopredator.commons.service;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import ru.tyumentsev.cryptopredator.commons.domain.OpenedPositionContainer;
import ru.tyumentsev.cryptopredator.commons.domain.PreviousCandleContainer;
import ru.tyumentsev.cryptopredator.commons.domain.SellRecordContainer;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface CacheServiceClient {

    @GET("/api/cache/v1/sellRecord")
    public Call<List<SellRecordContainer>> findAllSellRecords();

    @POST("/api/cache/v1/sellRecord")
    public Call<List<SellRecordContainer>> saveAllSellRecords(@Body Collection<SellRecordContainer> sellRecords);

    @POST("/api/cache/v1/sellRecord/delete")
    public Call<Void> deleteAllSellRecordsById(@Body Collection<String> ids);

    @GET("/api/cache/v1/previousCandleContainer")
    public Call<List<PreviousCandleContainer>> findAllPreviousCandleContainers();

    @POST("/api/cache/v1/previousCandleContainer")
    public Call<List<PreviousCandleContainer>> saveAllPreviousCandleContainers(@Body Collection<PreviousCandleContainer> previousCandleData);

    @POST("/api/cache/v1/previousCandleContainer/delete")
    public Call<Void> deleteAllPreviousCandleContainersById(@Body Collection<String> ids);

    @GET("/api/cache/v1/openedPosition")
    public Call<List<OpenedPositionContainer>> findAllOpenedPositions();

    @POST("/api/cache/v1/openedPosition")
    public Call<List<OpenedPositionContainer>> saveAllOpenedPositions(@Body Collection<OpenedPositionContainer> openedPositions);

    @POST("/api/cache/v1/openedPosition/delete")
    public Call<Void> deleteAllOpenedPositionsById(@Body Collection<String> openedPositionsIds);

    // User data streams
    @POST("/api/account/activeStrategies")
    public Call<Void> addActiveStrategy(@Body Map<String, String> parameters);

    @DELETE("/api/account/activeStrategies")
    public Call<Void> deleteActiveStrategy(@Query("strategyName") String strategyName);

}
