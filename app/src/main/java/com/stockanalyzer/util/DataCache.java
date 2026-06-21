package com.stockanalyzer.util;

import android.content.SharedPreferences;

import com.stockanalyzer.StockAnalyzerApp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 轻量本地数据缓存，避免重复抓取相同数据触发风控
 *
 * 缓存策略：
 * - 行情报价：30秒
 * - K线数据：5分钟
 * - 公司信息：30分钟
 * - 新闻：5分钟
 * - 大盘指数：30秒
 * - 搜索：1小时
 */
public class DataCache {

    // 各类型数据 TTL（毫秒）
    public static final long TTL_QUOTE = 30 * 1000;          // 30秒
    public static final long TTL_KLINE = 5 * 60 * 1000;     // 5分钟
    public static final long TTL_COMPANY = 30 * 60 * 1000;  // 30分钟
    public static final long TTL_NEWS = 5 * 60 * 1000;      // 5分钟
    public static final long TTL_INDEX = 30 * 1000;          // 30秒
    public static final long TTL_SEARCH = 60 * 60 * 1000;   // 1小时

    private static DataCache instance;
    private final SharedPreferences prefs;

    // 内存缓存（最快）
    private final Map<String, CacheEntry> memCache = new HashMap<>();

    private DataCache() {
        this.prefs = StockAnalyzerApp.getInstance()
                .getSharedPreferences("data_cache", 0);
    }

    public static synchronized DataCache getInstance() {
        if (instance == null) {
            instance = new DataCache();
        }
        return instance;
    }

    /** 写入缓存 */
    public void put(String key, String data) {
        if (data == null || data.isEmpty()) return;
        long now = System.currentTimeMillis();
        // 内存缓存
        memCache.put(key, new CacheEntry(data, now));
        // 持久化到 SharedPreferences
        prefs.edit().putString("c_" + key, data)
                .putLong("t_" + key, now).apply();
    }

    /** 读取缓存（未过期则返回，过期返回 null） */
    public String get(String key, long ttlMs) {
        long now = System.currentTimeMillis();

        // 先查内存
        CacheEntry mem = memCache.get(key);
        if (mem != null && (now - mem.time) < ttlMs) {
            return mem.data;
        }

        // 再查持久化
        String data = prefs.getString("c_" + key, null);
        long time = prefs.getLong("t_" + key, 0);
        if (data != null && (now - time) < ttlMs) {
            // 写回内存
            memCache.put(key, new CacheEntry(data, time));
            return data;
        }

        return null;
    }

    /** 清除所有缓存 */
    public void clearAll() {
        memCache.clear();
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("c_") || key.startsWith("t_")) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    /** 清除指定前缀缓存 */
    public void clearByPrefix(String prefix) {
        memCache.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("c_" + prefix) || key.startsWith("t_" + prefix)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    // ========== 快捷方法 ==========

    /** 缓存行情 */
    public String getQuote(String symbol) {
        return get("quote_" + symbol, TTL_QUOTE);
    }
    public void putQuote(String symbol, String data) {
        put("quote_" + symbol, data);
    }

    /** 缓存 K 线 */
    public String getKLine(String symbol, String resolution) {
        return get("kline_" + symbol + "_" + resolution, TTL_KLINE);
    }
    public void putKLine(String symbol, String resolution, String data) {
        put("kline_" + symbol + "_" + resolution, data);
    }

    /** 缓存公司信息 */
    public String getCompany(String symbol) {
        return get("company_" + symbol, TTL_COMPANY);
    }
    public void putCompany(String symbol, String data) {
        put("company_" + symbol, data);
    }

    /** 缓存新闻 */
    public String getNews(String symbol) {
        return get("news_" + symbol, TTL_NEWS);
    }
    public void putNews(String symbol, String data) {
        put("news_" + symbol, data);
    }

    /** 缓存大盘指数 */
    public String getIndices() {
        return get("indices_all", TTL_INDEX);
    }
    public void putIndices(String data) {
        put("indices_all", data);
    }

    /** 缓存搜索 */
    public String getSearch(String query) {
        return get("search_" + query, TTL_SEARCH);
    }
    public void putSearch(String query, String data) {
        put("search_" + query, data);
    }

    // ========== 内部类 ==========

    private static class CacheEntry {
        final String data;
        final long time;
        CacheEntry(String data, long time) {
            this.data = data;
            this.time = time;
        }
    }
}
