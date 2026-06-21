package com.stockanalyzer.data.remote;

import android.util.Log;

import com.stockanalyzer.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Retrofit 网络客户端管理器
 * 管理美股API、A股API 和 AI API 的HTTP客户端
 */
public class RetrofitClient {

    private static final String TAG = "RetrofitClient";

    // 美股 Finnhub
    private static StockApiService stockApiService;
    // AI
    private static AiApiService aiApiService;

    private static String stockApiKey;
    private static String aiApiKey;
    private static String aiModel;
    private static String aiBaseUrl;
    private static String stockBaseUrl;

    private static OkHttpClient commonClient;

    /**
     * 初始化所有网络客户端
     */
    public static void init(String stockBaseUrl, String stockKey,
                             String aiBaseUrl, String aiKey, String model) {
        stockApiKey = stockKey;
        aiApiKey = aiKey;
        aiModel = model;
        RetrofitClient.stockBaseUrl = stockBaseUrl;
        RetrofitClient.aiBaseUrl = aiBaseUrl;

        // 创建通用 OkHttpClient（带日志）
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor(message ->
                Log.d(TAG, message));
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        commonClient = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        // 初始化各API客户端
        initStockApi();
        initAiApi();
    }

    /**
     * 初始化美股API客户端 (Finnhub)
     */
    private static void initStockApi() {
        OkHttpClient stockClient = commonClient.newBuilder()
                .addInterceptor(chain -> {
                    okhttp3.Request original = chain.request();
                    okhttp3.HttpUrl url = original.url().newBuilder()
                            .addQueryParameter("token", stockApiKey)
                            .build();
                    okhttp3.Request request = original.newBuilder()
                            .url(url)
                            .build();
                    return chain.proceed(request);
                })
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(stockBaseUrl)
                .client(stockClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        stockApiService = retrofit.create(StockApiService.class);
    }

    /**
     * 初始化AI API客户端
     */
    private static void initAiApi() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(aiBaseUrl)
                .client(commonClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        aiApiService = retrofit.create(AiApiService.class);
    }

    public static StockApiService getStockApiService() {
        if (stockApiService == null) {
            initStockApi();
        }
        return stockApiService;
    }

    public static AiApiService getAiApiService() {
        if (aiApiService == null) {
            initAiApi();
        }
        return aiApiService;
    }

    public static String getAiModel() { return aiModel; }
    public static String getAiApiKey() { return aiApiKey; }
    public static String getAiBaseUrl() { return aiBaseUrl; }
    public static String getStockApiKey() { return stockApiKey; }

    /**
     * 重新初始化（当用户更改API设置时调用）
     */
    public static void reinitialize() {
        stockApiService = null;
        aiApiService = null;
        init(stockBaseUrl, stockApiKey, aiBaseUrl, aiApiKey, aiModel);
    }
}
