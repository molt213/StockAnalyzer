package com.stockanalyzer.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;

import com.stockanalyzer.StockAnalyzerApp;
import com.stockanalyzer.data.local.AppDatabase;

import org.json.JSONArray;
import org.json.JSONObject;
import com.stockanalyzer.data.local.StockEntity;
import com.stockanalyzer.data.model.Stock;
import com.stockanalyzer.data.model.StockDetail;
import android.os.Handler;
import android.os.Looper;

import com.stockanalyzer.data.remote.RetrofitClient;
import com.stockanalyzer.data.remote.StockApiService;
import com.stockanalyzer.data.remote.dto.StockResponse;
import com.stockanalyzer.util.DataConfig;

import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 股票数据仓库
 * 协调远程API和本地数据库的数据获取
 */
public class StockRepository {

    private static final String TAG = "StockRepository";
    private static final String DEFAULT_RESOLUTION = "D";  // 日线

    private final StockApiService apiService;
    private final StockScraper sinaScraper;
    private final EastMoneyScraper eastMoneyScraper;
    private final TencentScraper tencentScraper;
    private final AppDatabase database;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private static StockRepository instance;

    private StockRepository() {
        this.apiService = RetrofitClient.getStockApiService();
        this.sinaScraper = StockScraper.getInstance();
        this.eastMoneyScraper = EastMoneyScraper.getInstance();
        this.tencentScraper = TencentScraper.getInstance();
        this.database = StockAnalyzerApp.getInstance().getDatabase();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    private String getSource() {
        return DataConfig.getCurrentSource();
    }
    private boolean isEastMoneySource() {
        return DataConfig.SOURCE_EASTMONEY.equals(getSource());
    }
    private boolean isTencentSource() {
        return DataConfig.SOURCE_TENCENT.equals(getSource());
    }

    public static synchronized StockRepository getInstance() {
        if (instance == null) {
            instance = new StockRepository();
        }
        return instance;
    }

    // ==================== 远程 API 调用 ====================

    /**
     * 判断是否是 A 股代码
     */
    public boolean isAShareSymbol(String symbol) {
        return StockScraper.isAShareCode(symbol);
    }

    // ========== 数据源路由 ==========

    private void routeQuote(String symbol, RepositoryCallback<Stock> callback) {
        if (isEastMoneySource()) { eastMoneyScraper.getQuote(symbol, callback); }
        else if (isTencentSource()) { tencentScraper.getQuote(symbol, callback); }
        else { sinaScraper.getQuote(symbol, callback); }
    }

    private void routeCompanyInfo(String symbol, RepositoryCallback<StockDetail> callback) {
        if (isEastMoneySource()) { eastMoneyScraper.getCompanyInfo(symbol, callback); }
        else if (isTencentSource()) { tencentScraper.getCompanyInfo(symbol, callback); }
        else { sinaScraper.getCompanyInfo(symbol, callback); }
    }

    private void routeHistoricalData(String symbol, String resolution, int days,
                                      RepositoryCallback<List<StockDetail.CandleData>> callback) {
        if (isEastMoneySource()) { eastMoneyScraper.getHistoricalData(symbol, resolution, days, callback); }
        else if (isTencentSource()) { tencentScraper.getHistoricalData(symbol, resolution, days, callback); }
        else { sinaScraper.getHistoricalData(symbol, resolution, days, callback); }
    }

    private void routeSearch(String query, RepositoryCallback<List<Stock>> callback) {
        if (isEastMoneySource()) { eastMoneyScraper.search(query, callback); }
        else if (isTencentSource()) { tencentScraper.search(query, callback); }
        else {
            List<Stock> results = StockScraper.search(query);
            callback.onSuccess(results);
        }
    }

    private void routeNews(String symbol, RepositoryCallback<List<StockDetail.NewsItem>> callback) {
        if (isEastMoneySource()) { eastMoneyScraper.getNews(symbol, callback); }
        else if (isTencentSource()) { tencentScraper.getNews(symbol, callback); }
        else { sinaScraper.getNews(symbol, callback); }
    }

    /**
     * 搜索股票（仅支持A股代码搜索）
     */
    public void searchStocks(String query, final RepositoryCallback<List<Stock>> callback) {
        searchAShares(query, callback);
    }

    /**
     * 搜索 A 股
     */
    public void searchAShares(String query, final RepositoryCallback<List<Stock>> callback) {
        routeSearch(query, callback);
    }

    /**
     * 搜索美股
     */
    public void searchUSStocks(String query, final RepositoryCallback<List<Stock>> callback) {
        apiService.search(query).enqueue(new Callback<StockResponse.SearchResponse>() {
            @Override
            public void onResponse(@NonNull Call<StockResponse.SearchResponse> call,
                                    @NonNull Response<StockResponse.SearchResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Stock> stocks = new ArrayList<>();
                    for (StockResponse.SearchResult result : response.body().result) {
                        Stock stock = new Stock(result.symbol, result.description);
                        stock.setDisplaySymbol(result.displaySymbol);
                        stock.setType(result.type);
                        stock.setCurrency(result.currency);
                        stocks.add(stock);
                    }
                    callback.onSuccess(stocks);
                } else {
                    callback.onError(new Exception("搜索失败: " + response.code()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<StockResponse.SearchResponse> call,
                                   @NonNull Throwable t) {
                callback.onError(new Exception("网络请求失败: " + t.getMessage()));
            }
        });
    }

    /**
     * 获取实时报价（自动识别 A 股/美股）
     */
    public void getQuote(String symbol, final RepositoryCallback<Stock> callback) {
        if (isAShareSymbol(symbol)) {
            routeQuote(symbol, callback);
            return;
        }
        apiService.getQuote(symbol).enqueue(new Callback<StockResponse.QuoteResponse>() {
            @Override
            public void onResponse(@NonNull Call<StockResponse.QuoteResponse> call,
                                    @NonNull Response<StockResponse.QuoteResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    StockResponse.QuoteResponse quote = response.body();
                    Stock stock = new Stock(symbol, "");
                    stock.setCurrentPrice(quote.currentPrice);
                    stock.setChange(quote.change);
                    stock.setChangePercent(quote.changePercent);
                    stock.setOpen(quote.open);
                    stock.setHigh(quote.high);
                    stock.setLow(quote.low);
                    stock.setPreviousClose(quote.previousClose);
                    stock.setVolume(0);
                    stock.setLastUpdated(quote.timestamp);
                    callback.onSuccess(stock);
                } else {
                    callback.onError(new Exception("获取报价失败: " + response.code()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<StockResponse.QuoteResponse> call,
                                   @NonNull Throwable t) {
                callback.onError(new Exception("网络请求失败: " + t.getMessage()));
            }
        });
    }

    /**
     * 获取公司信息（自动识别 A 股/美股）
     */
    public void getCompanyProfile(String symbol, final RepositoryCallback<StockDetail> callback) {
        if (isAShareSymbol(symbol)) {
            routeCompanyInfo(symbol, callback);
            return;
        }
        apiService.getCompanyProfile(symbol).enqueue(new Callback<StockResponse.CompanyProfile>() {
            @Override
            public void onResponse(@NonNull Call<StockResponse.CompanyProfile> call,
                                    @NonNull Response<StockResponse.CompanyProfile> response) {
                if (response.isSuccessful() && response.body() != null) {
                    StockResponse.CompanyProfile profile = response.body();
                    StockDetail detail = new StockDetail();
                    detail.setSymbol(profile.ticker);
                    detail.setName(profile.name);
                    detail.setDescription(profile.description);
                    detail.setCountry(profile.country);
                    detail.setIndustry(profile.industry);
                    detail.setSector(profile.sector);

                    if (profile.marketCapitalization > 0) {
                        detail.setMarketCap(formatMarketCap(profile.marketCapitalization));
                    }
                    if (profile.shareOutstanding > 0) {
                        detail.setOutstandingShares((long) profile.shareOutstanding);
                    }

                    callback.onSuccess(detail);
                } else {
                    callback.onError(new Exception("获取公司信息失败: " + response.code()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<StockResponse.CompanyProfile> call,
                                   @NonNull Throwable t) {
                callback.onError(new Exception("网络请求失败: " + t.getMessage()));
            }
        });
    }

    /**
     * 获取历史K线数据（异步回调）
     */
    public void getHistoricalData(String symbol, String resolution, int days,
                                   final RepositoryCallback<List<StockDetail.CandleData>> callback) {
        if (isAShareSymbol(symbol)) {
            routeHistoricalData(symbol, resolution, days, callback);
            return;
        }
        long to = System.currentTimeMillis() / 1000;
        long from = to - (long) days * 24 * 60 * 60;

        apiService.getCandles(symbol, resolution, from, to)
                .enqueue(new Callback<StockResponse.CandleResponse>() {
            @Override
            public void onResponse(@NonNull Call<StockResponse.CandleResponse> call,
                                    @NonNull Response<StockResponse.CandleResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && "ok".equals(response.body().status)) {
                    StockResponse.CandleResponse candle = response.body();
                    List<StockDetail.CandleData> dataList = new ArrayList<>();

                    int size = candle.timestamp != null ? candle.timestamp.size() : 0;
                    for (int i = 0; i < size; i++) {
                        StockDetail.CandleData data = new StockDetail.CandleData(
                                candle.timestamp.get(i),
                                candle.open.get(i),
                                candle.high.get(i),
                                candle.low.get(i),
                                candle.close.get(i),
                                candle.volume.get(i)
                        );
                        dataList.add(data);
                    }
                    callback.onSuccess(dataList);
                } else {
                    callback.onError(new Exception("获取历史数据失败"));
                }
            }

            @Override
            public void onFailure(@NonNull Call<StockResponse.CandleResponse> call,
                                   @NonNull Throwable t) {
                callback.onError(new Exception("网络请求失败: " + t.getMessage()));
            }
        });
    }

    /**
     * 获取公司新闻（异步回调）
     */
    public void getCompanyNews(String symbol, int days,
                                final RepositoryCallback<List<StockDetail.NewsItem>> callback) {
        // A 股新闻使用爬虫
        if (isAShareSymbol(symbol)) {
            routeNews(symbol, callback);
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        String to = sdf.format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, -days);
        String from = sdf.format(cal.getTime());

        apiService.getCompanyNews(symbol, from, to)
                .enqueue(new Callback<List<StockResponse.NewsResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<StockResponse.NewsResponse>> call,
                                    @NonNull Response<List<StockResponse.NewsResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<StockDetail.NewsItem> newsList = new ArrayList<>();
                    for (StockResponse.NewsResponse news : response.body()) {
                        StockDetail.NewsItem item = new StockDetail.NewsItem();
                        item.setHeadline(news.headline);
                        item.setSummary(news.summary);
                        item.setUrl(news.url);
                        item.setSource(news.source);
                        item.setDatetime(news.datetime);
                        item.setImageUrl(news.image);
                        newsList.add(item);
                    }
                    callback.onSuccess(newsList);
                } else {
                    callback.onError(new Exception("获取新闻失败: " + response.code()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<StockResponse.NewsResponse>> call,
                                   @NonNull Throwable t) {
                callback.onError(new Exception("网络请求失败: " + t.getMessage()));
            }
        });
    }

    /**
     * 获取大盘指数（用于仪表盘市场概览）
     */
    public void getMarketIndices(final RepositoryCallback<List<Stock>> callback) {
        if (isEastMoneySource()) { eastMoneyScraper.getMarketIndices(callback); }
        else if (isTencentSource()) { tencentScraper.getMarketIndices(callback); }
        else { callback.onSuccess(new ArrayList<>()); }
    }

    /**
     * API 连通性诊断
     */
    public void checkApiStatus(final RepositoryCallback<String> callback) {
        if (isEastMoneySource()) { eastMoneyScraper.checkApiStatus(callback); }
        else if (isTencentSource()) { tencentScraper.checkApiStatus(callback); }
        else { callback.onSuccess("诊断仅支持东方财富/腾讯数据源"); }
    }

    /**
     * 获取市场要闻（用于仪表盘市场概览）
     */
    public void getMarketNews(final RepositoryCallback<List<StockDetail.NewsItem>> callback) {
        if (isEastMoneySource()) { eastMoneyScraper.getMarketNews(callback); }
        else if (isTencentSource()) { tencentScraper.getMarketNews(callback); }
        else { callback.onSuccess(new ArrayList<>()); }
    }

    /**
     * 获取公司基本指标
     */
    public void getMetrics(String symbol,
                            final RepositoryCallback<StockResponse.MetricsData> callback) {
        // A 股暂不支持市盈率等财务指标
        if (isAShareSymbol(symbol)) {
            callback.onError(new Exception("A股财务指标暂不支持"));
            return;
        }
        apiService.getMetrics(symbol).enqueue(new Callback<StockResponse.MetricsResponse>() {
            @Override
            public void onResponse(@NonNull Call<StockResponse.MetricsResponse> call,
                                    @NonNull Response<StockResponse.MetricsResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().metric != null) {
                    callback.onSuccess(response.body().metric);
                } else {
                    callback.onError(new Exception("获取指标失败: " + response.code()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<StockResponse.MetricsResponse> call,
                                   @NonNull Throwable t) {
                callback.onError(new Exception("网络请求失败: " + t.getMessage()));
            }
        });
    }

    // ==================== 搜索缓存 ====================

    /** 保存搜索到的股票到本地缓存（后续可按名称搜索） */
    public void saveSearchedStock(String code, String name) {
        executor.execute(() -> {
            try {
                String json = StockAnalyzerApp.getInstance().getPreferences()
                        .getString("searched_stocks", "[]");
                JSONArray arr = new JSONArray(json);
                // 去重
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    if (item.optString("code").equals(code)) return; // 已存在
                }
                JSONObject newItem = new JSONObject();
                newItem.put("code", code);
                newItem.put("name", name);
                arr.put(newItem);
                // 限制最多200条
                while (arr.length() > 200) arr.remove(0);
                StockAnalyzerApp.getInstance().getPreferences()
                        .edit().putString("searched_stocks", arr.toString()).apply();
            } catch (Exception e) {
                Log.w(TAG, "保存搜索缓存失败", e);
            }
        });
    }

    /** 搜索所有股票（含本地缓存 + 热门股） */
    public static List<Stock> searchAllStocks(String input) {
        List<Stock> results = new ArrayList<>();
        String clean = input.trim().toUpperCase()
                .replace("SH", "").replace("SZ", "").replace("BJ", "");

        // 1. 精确代码匹配（优先返回）
        for (String[] stock : StockScraper.POPULAR_STOCKS) {
            if (stock[0].equals(clean)) {
                results.add(new Stock(stock[0], stock[1]));
                return results;
            }
        }
        // 搜索缓存中代码匹配
        try {
            String json = StockAnalyzerApp.getInstance().getPreferences()
                    .getString("searched_stocks", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                if (item.optString("code").equals(clean)) {
                    results.add(new Stock(item.optString("code"), item.optString("name")));
                    return results;
                }
            }
        } catch (Exception e) { /* ignore */ }

        // 2. 名称模糊匹配（热门股 + 缓存）
        for (String[] stock : StockScraper.POPULAR_STOCKS) {
            if (stock[0].contains(clean) || stock[1].contains(input)) {
                results.add(new Stock(stock[0], stock[1]));
            }
        }
        try {
            String json = StockAnalyzerApp.getInstance().getPreferences()
                    .getString("searched_stocks", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                String code = item.optString("code", "");
                String name = item.optString("name", "");
                if (code.equals(clean) || name.contains(input)) {
                    // 去重
                    boolean exists = false;
                    for (Stock s : results) {
                        if (s.getSymbol().equals(code)) { exists = true; break; }
                    }
                    if (!exists) results.add(new Stock(code, name));
                }
            }
        } catch (Exception e) { /* ignore */ }

        return results;
    }

    // ==================== 本地数据库操作 ====================

    /**
     * 获取所有自选股
     */
    public List<StockEntity> getWatchlist() {
        return database.stockDao().getAllWatchlistStocks();
    }

    /**
     * 统一处理代码格式：去掉SH/SZ前缀，统一大写
     */
    private String normalizeSymbol(String symbol) {
        return symbol.trim().toUpperCase()
                .replace("SH", "").replace("SZ", "").replace("BJ", "");
    }

    /**
     * 检查是否已在自选股
     */
    public boolean isInWatchlist(String symbol) {
        return database.stockDao().isInWatchlist(normalizeSymbol(symbol));
    }

    /**
     * 添加自选股（上限10只）
     * @return true=添加成功, false=已达上限
     */
    public boolean addToWatchlist(String symbol, String name) {
        String clean = normalizeSymbol(symbol);
        // 检查上限
        List<StockEntity> current = database.stockDao().getAllWatchlistStocks();
        if (current.size() >= 10) return false;

        StockEntity entity = new StockEntity(
                clean,
                name != null ? name : clean,
                System.currentTimeMillis()
        );
        database.stockDao().insertStock(entity);
        return true;
    }

    /**
     * 从自选股中移除
     */
    public void removeFromWatchlist(String symbol) {
        database.stockDao().deleteStockBySymbol(normalizeSymbol(symbol));
    }

    /**
     * 更新自选股价格
     */
    public void updateWatchlistPrice(String symbol, double price) {
        StockEntity entity = database.stockDao().getStockBySymbol(symbol.toUpperCase());
        if (entity != null) {
            entity.setLastPrice(price);
            entity.setLastPriceUpdate(System.currentTimeMillis());
            database.stockDao().updateStock(entity);
        }
    }

    // ==================== 工具方法 ====================

    private String formatMarketCap(double value) {
        if (value >= 1_000_000_000_000d) {
            return String.format("%.2fT", value / 1_000_000_000_000d);
        } else if (value >= 1_000_000_000) {
            return String.format("%.2fB", value / 1_000_000_000);
        } else if (value >= 1_000_000) {
            return String.format("%.2fM", value / 1_000_000);
        }
        return String.format("%.2f", value);
    }

    /**
     * 仓库回调接口
     */
    public interface RepositoryCallback<T> {
        void onSuccess(T data);
        void onError(Exception e);
    }
}
