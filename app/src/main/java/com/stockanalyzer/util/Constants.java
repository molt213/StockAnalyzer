package com.stockanalyzer.util;

/**
 * 应用常量配置
 */
public final class Constants {

    private Constants() {}

    // AI API配置 (DeepSeek)
    public static final String DEEPSEEK_MODEL_CHAT = "deepseek-v4-flash";
    public static final String DEEPSEEK_MODEL_REASONER = "deepseek-reasoner";
    public static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1/";

    // 股票数据配置
    public static final int DEFAULT_HISTORICAL_DAYS = 90;
    public static final int NEWS_DAYS = 7;

    // 图表配置
    public static final int CHART_ANIMATION_DURATION = 800;
    public static final int MAX_CHART_DATA_POINTS = 200;

    // 刷新间隔 (毫秒)
    public static final long REFRESH_INTERVAL_MS = 30000;
    public static final long AUTO_REFRESH_INTERVAL_MS = 60000;

    // 缓存配置
    public static final long CACHE_DURATION_MS = 5 * 60 * 1000; // 5分钟

    // 分析类型
    public static final String ANALYSIS_TECHNICAL = "technical";
    public static final String ANALYSIS_FUNDAMENTAL = "fundamental";
    public static final String ANALYSIS_COMPREHENSIVE = "comprehensive";

    // Intent Extras
    public static final String EXTRA_SYMBOL = "extra_symbol";
    public static final String EXTRA_STOCK_NAME = "extra_stock_name";
    public static final String EXTRA_ANALYSIS_TYPE = "extra_analysis_type";
    public static final String EXTRA_ANALYSIS_ID = "extra_analysis_id";

    // API配置键 (SharedPreferences)
    public static final String PREF_STOCK_API_KEY = "stock_api_key";
    public static final String PREF_STOCK_BASE_URL = "stock_base_url";
    public static final String PREF_AI_API_KEY = "ai_api_key";
    public static final String PREF_AI_BASE_URL = "ai_base_url";
    public static final String PREF_AI_MODEL = "ai_model";
    public static final String PREF_DARK_MODE = "dark_mode";
    public static final String PREF_AUTO_REFRESH = "auto_refresh";
    public static final String PREF_XUEQIU_COOKIE = "xueqiu_cookie";
}
