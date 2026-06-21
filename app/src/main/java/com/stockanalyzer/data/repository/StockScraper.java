package com.stockanalyzer.data.repository;

import android.util.Log;

import com.stockanalyzer.data.model.Stock;
import com.stockanalyzer.data.model.StockDetail;
import com.stockanalyzer.data.repository.StockRepository.RepositoryCallback;
import com.stockanalyzer.data.repository.StockNewsScraper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Handler;
import android.os.Looper;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 新浪财经爬虫
 * 直接抓取网页数据，绕过 API 反爬限制
 */
public class StockScraper {

    private static final String TAG = "StockScraper";

    private final OkHttpClient client;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private static StockScraper instance;

    // 新浪财经接口
    private static final String SINA_QUOTE_URL = "https://hq.sinajs.cn/list=";
    private static final String SINA_KLINE_URL = "https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData";
    private static final String SINA_NEWS_URL = "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getNewsData";
    private static final String EASTMONEY_NEWS_URL = "https://push2.eastmoney.com/api/qt/stock/news/get";

    private StockScraper() {
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request request = original.newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36")
                            .header("Referer", "https://finance.sina.com.cn/")
                            .header("Accept", "*/*")
                            .build();
                    return chain.proceed(request);
                })
                .build();
    }

    public static synchronized StockScraper getInstance() {
        if (instance == null) {
            instance = new StockScraper();
        }
        return instance;
    }

    // ========== 工具 ==========

    /**
     * 转换代码为新浪格式：600519 → sh600519，000858 → sz000858
     */
    public static String toSinaSymbol(String code) {
        String c = code.trim().toUpperCase()
                .replace("SH", "").replace("SZ", "").replace("BJ", "");
        if (c.startsWith("6")) return "sh" + c;
        if (c.startsWith("0") || c.startsWith("3")) return "sz" + c;
        return "sh" + c;
    }

    public static boolean isAShareCode(String symbol) {
        String s = symbol.trim().toUpperCase();
        if (s.startsWith("SH") || s.startsWith("SZ") || s.startsWith("BJ")) return true;
        return s.matches("^[6003]\\d{5}$");
    }

    // ========== 实时行情（新浪） ==========

    /**
     * 获取实时行情（后台线程执行）
     * 新浪格式: var hq_str_sh600519="贵州茅台,1960.00,1950.00,1970.00,1940.00,...,2024-01-15,15:00:00";
     * 字段: 0名称,1今开,2昨收,3当前,4最高,5最低,6买一,7卖一,8成交量(手),9成交额,...
     */
    public void getQuote(String symbol, RepositoryCallback<Stock> callback) {
        executor.execute(() -> {
            try {
                String sinaSymbol = toSinaSymbol(symbol);
                Request request = new Request.Builder()
                        .url(SINA_QUOTE_URL + sinaSymbol)
                        .build();
                Response response = client.newCall(request).execute();
                String body = response.body() != null ? response.body().string() : "";
                response.close();

                if (body.isEmpty() || !body.contains("\"")) {
                    postError(callback, "获取行情失败：返回数据为空");
                    return;
                }

                // 解析 CSV 数据
                String dataStr = body.substring(body.indexOf("\"") + 1, body.lastIndexOf("\""));
                String[] p = dataStr.split(",");

                if (p.length < 10) {
                    postError(callback, "数据格式异常");
                    return;
                }

                Stock stock = new Stock(symbol, p[0]); // 名称
                stock.setCurrentPrice(parseDouble(p[3]));     // 当前价
                stock.setPreviousClose(parseDouble(p[2]));    // 昨收
                stock.setOpen(parseDouble(p[1]));             // 今开
                stock.setHigh(parseDouble(p[4]));             // 最高
                stock.setLow(parseDouble(p[5]));              // 最低
                stock.setChange(stock.getCurrentPrice() - stock.getPreviousClose());
                stock.setChangePercent(stock.getPreviousClose() > 0
                        ? (stock.getChange() / stock.getPreviousClose()) * 100 : 0);
                stock.setVolume(parseLong(p[8]));             // 成交量
                stock.setLastUpdated(System.currentTimeMillis() / 1000);

                mainHandler.post(() -> callback.onSuccess(stock));

            } catch (Exception e) {
                Log.e(TAG, "行情抓取失败", e);
                postError(callback, "行情抓取失败: " + e.getMessage());
            }
        });
    }

    /**
     * 在主线程回调错误
     */
    private <T> void postError(RepositoryCallback<T> callback, String msg) {
        mainHandler.post(() -> callback.onError(new Exception(msg)));
    }

    // ========== K 线数据（新浪） ==========

    /**
     * K线周期映射
     */
    private static int getScaleForResolution(String resolution) {
        switch (resolution.toUpperCase()) {
            case "5": return 5;      // 5分钟
            case "15": return 15;    // 15分钟
            case "30": return 30;    // 30分钟
            case "60": return 60;    // 60分钟
            case "D": return 240;    // 日线 (4小时)
            case "W": return 1200;   // 周线 (5天)
            case "M": return 4800;   // 月线 (20天)
            default: return 240;     // 默认日线
        }
    }

    /**
     * 根据周期获取合适的 K 线条数
     */
    private static int getCountForPeriod(String resolution) {
        switch (resolution.toUpperCase()) {
            case "W": return 52;   // 近52周
            case "M": return 24;   // 近24月
            default: return 60;    // 近60天
        }
    }

    /**
     * 获取K线数据（后台线程执行）
     * 支持日/周/月等周期
     */
    public void getHistoricalData(String symbol, String resolution, int days,
                                   RepositoryCallback<List<StockDetail.CandleData>> callback) {
        executor.execute(() -> {
            try {
                String sinaSymbol = toSinaSymbol(symbol);
                int count = getCountForPeriod(resolution);
                int scale = getScaleForResolution(resolution);
                List<StockDetail.CandleData> dataList = fetchKLine(sinaSymbol, count, scale);

                if (dataList != null && !dataList.isEmpty()) {
                    List<StockDetail.CandleData> finalData = dataList;
                    mainHandler.post(() -> callback.onSuccess(finalData));
                } else {
                    postError(callback, "K线数据为空");
                }
            } catch (Exception e) {
                Log.e(TAG, "K线抓取失败", e);
                postError(callback, "K线抓取失败: " + e.getMessage());
            }
        });
    }

    /**
     * 获取K线，尝试多个接口
     * @param sinaSymbol 新浪格式代码，如 sh600519
     * @param count 返回条数
     * @param scale 周期 240=日线 1200=周线 4800=月线
     */
    private List<StockDetail.CandleData> fetchKLine(String sinaSymbol, int count, int scale) {
        // 接口1: 标准新浪 K线 API（带scale参数支持周K/月K）
        String[] urls = {
            SINA_KLINE_URL + "?symbol=" + sinaSymbol + "&datalen=" + count + "&scale=" + scale,
            SINA_KLINE_URL + "?symbol=" + sinaSymbol + "&datalen=" + count,
            "https://quotes.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData?symbol=" + sinaSymbol + "&datalen=" + count + "&scale=" + scale,
        };

        for (String url : urls) {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Referer", "https://finance.sina.com.cn/")
                        .addHeader("User-Agent", "Mozilla/5.0")
                        .build();
                Response response = client.newCall(request).execute();
                String body = response.body() != null ? response.body().string() : "";
                response.close();

                if (body == null || body.isEmpty() || body.equals("null")) continue;

                JSONArray jsonArray = new JSONArray(body);
                List<StockDetail.CandleData> dataList = new ArrayList<>();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject item = jsonArray.getJSONObject(i);
                    try {
                        Date date = sdf.parse(item.getString("day"));
                        long timestamp = date != null ? date.getTime() / 1000 : 0;
                        double open = item.getDouble("open");
                        double high = item.getDouble("high");
                        double low = item.getDouble("low");
                        double close = item.getDouble("close");
                        long volume = item.optLong("volume", 0);
                        dataList.add(new StockDetail.CandleData(
                                timestamp, open, high, low, close, volume));
                    } catch (Exception e) {
                        Log.w(TAG, "解析K线行失败", e);
                    }
                }

                if (!dataList.isEmpty()) {
                    Log.d(TAG, "K线获取成功: " + url + " -> " + dataList.size() + "条");
                    return dataList;
                }
            } catch (Exception e) {
                Log.w(TAG, "K线接口失败，换下一个: " + url, e);
            }
        }
        return null;
    }

    /**
     * 获取公司信息（从行情CSV中提取可用的数据）
     */
    public void getCompanyInfo(String symbol, RepositoryCallback<StockDetail> callback) {
        executor.execute(() -> {
            try {
                String sinaSymbol = toSinaSymbol(symbol);
                Request request = new Request.Builder()
                        .url(SINA_QUOTE_URL + sinaSymbol)
                        .build();
                Response response = client.newCall(request).execute();
                String body = response.body() != null ? response.body().string() : "";
                response.close();

                StockDetail detail = new StockDetail();
                detail.setSymbol(symbol);

                if (body.contains("\"")) {
                    String dataStr = body.substring(body.indexOf("\"") + 1, body.lastIndexOf("\""));
                    String[] p = dataStr.split(",");
                    if (p.length > 0) detail.setName(p[0]);
                    // 新浪行情不含PE/市值等数据，留空表示N/A
                }

                StockDetail finalDetail = detail;
                mainHandler.post(() -> callback.onSuccess(finalDetail));

            } catch (Exception e) {
                Log.e(TAG, "公司信息获取失败", e);
                mainHandler.post(() -> callback.onSuccess(new StockDetail()));
            }
        });
    }

    // ========== 新闻 ==========

    /**
     * 获取 A 股新闻（通过 StockNewsScraper）
     */
    public void getNews(String symbol, RepositoryCallback<List<StockDetail.NewsItem>> callback) {
        executor.execute(() -> {
            List<StockDetail.NewsItem> news = StockNewsScraper.getInstance().getNews(symbol);
            mainHandler.post(() -> callback.onSuccess(news));
        });
    }

    // ========== 搜索（本地代码匹配） ==========

    /**
     * 搜索 A 股（本地匹配）
     */
    public static List<Stock> search(String input) {
        List<Stock> results = new ArrayList<>();
        String clean = input.trim().toUpperCase()
                .replace("SH", "").replace("SZ", "").replace("BJ", "");

        // 精确代码匹配
        if (clean.matches("^[6003]\\d{5}$")) {
            results.add(new Stock(clean, getStockName(clean)));
            return results;
        }

        // 名称模糊匹配
        for (String[] stock : POPULAR_STOCKS) {
            if (stock[0].contains(clean) || stock[1].contains(clean)) {
                results.add(new Stock(stock[0], stock[1]));
            }
        }
        return results;
    }

    /**
     * 获取股票名称（从本地库）
     */
    private static String getStockName(String code) {
        for (String[] stock : POPULAR_STOCKS) {
            if (stock[0].equals(code)) return stock[1];
        }
        return code + " (A股)";
    }

    // ========== 工具方法 ==========

    private double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    // ========== 热门股票库 ==========

    public static final String[][] POPULAR_STOCKS = {
        {"600519", "贵州茅台"}, {"000858", "五粮液"}, {"300750", "宁德时代"},
        {"601318", "中国平安"}, {"600036", "招商银行"}, {"000333", "美的集团"},
        {"600276", "恒瑞医药"}, {"002415", "海康威视"}, {"002594", "比亚迪"},
        {"300059", "东方财富"}, {"000002", "万科A"}, {"600900", "长江电力"},
        {"688981", "中芯国际"}, {"601398", "工商银行"}, {"600941", "中国移动"},
        {"601857", "中国石油"}, {"600028", "中国石化"}, {"601088", "中国神华"},
        {"600690", "海尔智家"}, {"000063", "中兴通讯"}, {"002352", "顺丰控股"},
        {"601166", "兴业银行"}, {"600030", "中信证券"}, {"600585", "海螺水泥"},
        {"601899", "紫金矿业"}, {"600438", "通威股份"}, {"002475", "立讯精密"},
        {"300124", "汇川技术"}, {"000725", "京东方A"}, {"002230", "科大讯飞"},
        {"688111", "金山办公"}, {"300274", "阳光电源"}, {"000568", "泸州老窖"},
        {"600809", "山西汾酒"}, {"002304", "洋河股份"}, {"300015", "爱尔眼科"},
        {"300760", "迈瑞医疗"}, {"000651", "格力电器"}, {"600887", "伊利股份"},
        {"002714", "牧原股份"}, {"600104", "上汽集团"}, {"601328", "交通银行"},
        {"600016", "民生银行"}, {"002142", "宁波银行"}, {"601688", "华泰证券"},
        {"600000", "浦发银行"}, {"600837", "海通证券"}, {"300122", "智飞生物"},
        {"688012", "中微公司"}, {"002371", "北方华创"}, {"603259", "药明康德"},
        {"300896", "爱美客"}, {"688981", "中芯国际"}, {"601012", "隆基绿能"},
        {"600809", "山西汾酒"}, {"002466", "天齐锂业"}, {"300274", "阳光电源"},
    };
}
