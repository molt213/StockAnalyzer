package com.stockanalyzer.util;

import android.content.SharedPreferences;

import com.stockanalyzer.StockAnalyzerApp;

/**
 * 数据源配置管理
 * 支持新浪爬虫和东方财富爬虫两种 A 股数据源
 */
public class DataConfig {

    private static final String PREF_KEY = "data_source";

    // 数据源常量
    public static final String SOURCE_SINA = "sina";           // 新浪财经（基础行情）
    public static final String SOURCE_TONGHUASHUN = "ths";     // 同花顺（已弃用，保留兼容）
    public static final String SOURCE_EASTMONEY = "eastmoney"; // 东方财富（综合行情+财务指标+新闻）
    public static final String SOURCE_TENCENT = "tencent";     // 腾讯财经（基础行情，不易被封）

    /**
     * 获取当前数据源
     * 默认为东方财富；兼容旧版同花顺配置，自动迁移到东方财富
     */
    public static String getCurrentSource() {
        SharedPreferences prefs = StockAnalyzerApp.getInstance().getPreferences();
        String source = prefs.getString(PREF_KEY, SOURCE_EASTMONEY);
        // 兼容迁移：旧版同花顺用户自动切换到东方财富
        if (SOURCE_TONGHUASHUN.equals(source)) {
            setSource(SOURCE_EASTMONEY);
            return SOURCE_EASTMONEY;
        }
        return source;
    }

    /**
     * 设置数据源
     */
    public static void setSource(String source) {
        SharedPreferences prefs = StockAnalyzerApp.getInstance().getPreferences();
        prefs.edit().putString(PREF_KEY, source).apply();
    }
}
