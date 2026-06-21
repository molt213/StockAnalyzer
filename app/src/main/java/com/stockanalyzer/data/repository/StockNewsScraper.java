package com.stockanalyzer.data.repository;

import android.util.Log;

import com.stockanalyzer.data.model.StockDetail;
import com.stockanalyzer.data.repository.StockRepository.RepositoryCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * A 股新闻爬虫
 */
public class StockNewsScraper {

    private static final String TAG = "StockNewsScraper";
    private static StockNewsScraper instance;

    private final OkHttpClient client;

    // 新浪新闻 API
    private static final String SINA_NEWS_URL =
            "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getNewsData";
    // 东方财富新闻 API
    private static final String EASTMONEY_NEWS_URL =
            "https://push2.eastmoney.com/api/qt/stock/news/get";

    private StockNewsScraper() {
        this.client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    String referer;
                    if (original.url().toString().contains("eastmoney")) {
                        referer = "https://quote.eastmoney.com/";
                    } else {
                        referer = "https://finance.sina.com.cn/";
                    }
                    Request request = original.newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                            .header("Referer", referer)
                            .build();
                    return chain.proceed(request);
                })
                .build();
    }

    public static synchronized StockNewsScraper getInstance() {
        if (instance == null) {
            instance = new StockNewsScraper();
        }
        return instance;
    }

    /**
     * 获取 A 股新闻
     * 优先使用新浪（更稳定），失败使用东方财富备用
     */
    public List<StockDetail.NewsItem> getNews(String symbol) {
        // 先试新浪（原接口较稳定）
        List<StockDetail.NewsItem> news = fetchFromSina(symbol);
        if (news != null && !news.isEmpty()) return news;
        // 备选：东方财富
        news = fetchFromEastMoney(symbol);
        return news != null ? news : new ArrayList<>();
    }

    /**
     * 新浪财经新闻 (JSONP格式)
     * 返回格式: [{"title":"...","date":"...","source":"...","url":"..."}]
     */
    private List<StockDetail.NewsItem> fetchFromSina(String symbol) {
        try {
            String sinaSymbol = toSinaSymbol(symbol);
            String url = SINA_NEWS_URL + "?symbol=" + sinaSymbol + "&count=20";

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Referer", "https://finance.sina.com.cn/")
                    .build();
            Response response = client.newCall(request).execute();
            String body = response.body() != null ? response.body().string() : "";
            response.close();

            if (body == null || body.isEmpty() || body.equals("null")) return null;

            // 新浪可能返回 JSONP 格式: jsonCallback([...])，需要提取数组
            if (body.startsWith("var") || body.contains("(")) {
                int start = body.indexOf("[");
                int end = body.lastIndexOf("]");
                if (start >= 0 && end > start) {
                    body = body.substring(start, end + 1);
                }
            }

            JSONArray jsonArray = new JSONArray(body);
            List<StockDetail.NewsItem> newsList = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            SimpleDateFormat outSdf = new SimpleDateFormat("MM-dd HH:mm", Locale.US);

            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject item = jsonArray.getJSONObject(i);
                    StockDetail.NewsItem news = new StockDetail.NewsItem();
                    news.setHeadline(item.optString("title", ""));
                    news.setSummary(item.optString("summary", ""));
                    news.setSource(item.optString("source", "新浪财经"));
                    news.setUrl(item.optString("url", ""));
                    if (news.getHeadline().isEmpty()) continue;
                    newsList.add(news);
                } catch (Exception e) {
                    Log.w(TAG, "解析新闻条目失败", e);
                }
            }
            return newsList;

        } catch (Exception e) {
            Log.w(TAG, "新浪新闻获取失败", e);
            return null;
        }
    }

    /**
     * 东方财富新闻 (备用)
     */
    private List<StockDetail.NewsItem> fetchFromEastMoney(String symbol) {
        try {
            String secId = getEastMoneySecId(symbol);
            String url = EASTMONEY_NEWS_URL + "?secid=" + secId + "&count=10";

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Referer", "https://quote.eastmoney.com/")
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build();
            Response response = client.newCall(request).execute();
            String body = response.body() != null ? response.body().string() : "";
            response.close();

            if (body == null || body.isEmpty()) return null;

            JSONObject json = new JSONObject(body);
            if (!json.has("data")) return null;

            JSONObject data = json.getJSONObject("data");
            if (!data.has("list")) return null;

            JSONArray list = data.getJSONArray("list");
            List<StockDetail.NewsItem> newsList = new ArrayList<>();

            for (int i = 0; i < list.length(); i++) {
                try {
                    JSONObject item = list.getJSONObject(i);
                    StockDetail.NewsItem news = new StockDetail.NewsItem();
                    news.setHeadline(item.optString("title", ""));
                    news.setSummary(item.optString("content", ""));
                    news.setSource(item.optString("source", "东方财富"));
                    news.setUrl(item.optString("url", ""));
                    if (news.getHeadline().isEmpty()) continue;
                    newsList.add(news);
                } catch (Exception e) {
                    Log.w(TAG, "解析新闻条目失败", e);
                }
            }
            return newsList;

        } catch (Exception e) {
            Log.w(TAG, "东方财富新闻获取失败", e);
            return null;
        }
    }

    private String toSinaSymbol(String code) {
        String c = code.trim().toUpperCase()
                .replace("SH", "").replace("SZ", "").replace("BJ", "");
        return c.startsWith("6") ? "sh" + c : "sz" + c;
    }

    private String getEastMoneySecId(String code) {
        String c = code.trim().toUpperCase()
                .replace("SH", "").replace("SZ", "").replace("BJ", "");
        int market = c.startsWith("6") ? 1 : 0;
        return market + "." + c;
    }
}
