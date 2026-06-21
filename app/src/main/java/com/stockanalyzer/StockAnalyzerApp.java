package com.stockanalyzer;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import com.stockanalyzer.data.local.AppDatabase;
import com.stockanalyzer.data.remote.RetrofitClient;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 股票分析器 Application 类
 * 负责全局初始化
 */
public class StockAnalyzerApp extends Application {

    private static final String TAG = "StockAnalyzerApp";
    private static StockAnalyzerApp instance;
    private AppDatabase database;
    private SharedPreferences preferences;

    public static StockAnalyzerApp getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // 设置全局崩溃捕获
        setupCrashHandler();

        try {
            // 初始化 SharedPreferences
            preferences = getSharedPreferences("stock_analyzer_prefs", MODE_PRIVATE);

            // 初始化数据库
            database = AppDatabase.getInstance(this);

            // 初始化网络客户端
            initNetworkClients();

            Log.d(TAG, "Application 初始化完成");
        } catch (Exception e) {
            Log.e(TAG, "Application 初始化失败", e);
            saveCrashLog(e);
        }
    }

    /**
     * 设置全局未捕获异常处理器
     */
    private void setupCrashHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                Log.e(TAG, "全局未捕获异常", throwable);
                saveCrashLog(throwable);
            } catch (Exception ignored) {}

            // 交给默认处理器（会弹出"应用已停止"对话框）
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    /**
     * 保存崩溃日志到文件
     */
    private void saveCrashLog(Throwable throwable) {
        try {
            File crashDir = new File(getExternalFilesDir(null), "crash_logs");
            if (!crashDir.exists()) {
                crashDir.mkdirs();
            }

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());
            File crashFile = new File(crashDir, "crash_" + timeStamp + ".txt");

            PrintWriter pw = new PrintWriter(new FileWriter(crashFile));
            pw.println("=== Crash Log ===");
            pw.println("Time: " + new Date().toString());
            pw.println("App: StockAnalyzer");
            pw.println();
            throwable.printStackTrace(pw);
            pw.close();

            Log.d(TAG, "崩溃日志已保存到: " + crashFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "保存崩溃日志失败", e);
        }
    }

    private void initNetworkClients() {
        String stockApiKey = preferences.getString("stock_api_key", BuildConfig.STOCK_API_KEY);
        String aiApiKey = preferences.getString("ai_api_key", BuildConfig.AI_API_KEY);
        String aiBaseUrl = preferences.getString("ai_base_url", BuildConfig.AI_API_BASE_URL);
        String aiModel = preferences.getString("ai_model", BuildConfig.AI_MODEL);
        String stockBaseUrl = preferences.getString("stock_base_url", BuildConfig.STOCK_API_BASE_URL);

        RetrofitClient.init(stockBaseUrl, stockApiKey, aiBaseUrl, aiApiKey, aiModel);
    }

    public AppDatabase getDatabase() {
        return database;
    }

    public SharedPreferences getPreferences() {
        return preferences;
    }

    public String getAiApiKey() {
        String key = preferences != null ? preferences.getString("ai_api_key", BuildConfig.AI_API_KEY) : BuildConfig.AI_API_KEY;
        if (key.isEmpty()) {
            key = BuildConfig.AI_API_KEY;
        }
        return key;
    }

    public String getAiModel() {
        return preferences != null ? preferences.getString("ai_model", BuildConfig.AI_MODEL) : BuildConfig.AI_MODEL;
    }

    public String getAiBaseUrl() {
        return preferences != null ? preferences.getString("ai_base_url", BuildConfig.AI_API_BASE_URL) : BuildConfig.AI_API_BASE_URL;
    }

    public String getStockApiKey() {
        String key = preferences != null ? preferences.getString("stock_api_key", BuildConfig.STOCK_API_KEY) : BuildConfig.STOCK_API_KEY;
        if (key.isEmpty()) {
            key = BuildConfig.STOCK_API_KEY;
        }
        return key;
    }

    public boolean isAiConfigured() {
        return !getAiApiKey().isEmpty();
    }

    public boolean isStockApiConfigured() {
        return !getStockApiKey().isEmpty();
    }
}
