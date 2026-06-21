package com.stockanalyzer.data.remote;

import com.stockanalyzer.data.remote.dto.StockResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * 股票数据 API 接口 (Finnhub)
 */
public interface StockApiService {

    /**
     * 搜索股票
     */
    @GET("search")
    Call<StockResponse.SearchResponse> search(@Query("q") String query);

    /**
     * 获取实时报价
     */
    @GET("quote")
    Call<StockResponse.QuoteResponse> getQuote(@Query("symbol") String symbol);

    /**
     * 获取公司基本信息
     */
    @GET("stock/profile2")
    Call<StockResponse.CompanyProfile> getCompanyProfile(@Query("symbol") String symbol);

    /**
     * 获取历史K线数据
     * @param resolution 1, 5, 15, 30, 60, D, W, M
     */
    @GET("stock/candle")
    Call<StockResponse.CandleResponse> getCandles(
            @Query("symbol") String symbol,
            @Query("resolution") String resolution,
            @Query("from") long from,
            @Query("to") long to
    );

    /**
     * 获取公司新闻
     */
    @GET("company-news")
    Call<List<StockResponse.NewsResponse>> getCompanyNews(
            @Query("symbol") String symbol,
            @Query("from") String fromDate,
            @Query("to") String toDate
    );

    /**
     * 获取公司基本指标
     */
    @GET("stock/metric")
    Call<StockResponse.MetricsResponse> getMetrics(@Query("symbol") String symbol);
}
