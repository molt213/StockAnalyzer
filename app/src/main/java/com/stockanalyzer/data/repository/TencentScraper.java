package com.stockanalyzer.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.stockanalyzer.data.model.Stock;
import com.stockanalyzer.data.model.StockDetail;
import com.stockanalyzer.data.repository.StockRepository.RepositoryCallback;
import com.stockanalyzer.util.DataCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 腾讯财经 A 股数据爬虫
 * 使用 qt.gtimg.cn 实时行情接口（CSV格式，非常稳定不易被封）
 * 备用：新浪财经 K 线接口
 */
public class TencentScraper {

    private static final String TAG = "TencentScraper";
    private static TencentScraper instance;

    private final OkHttpClient client;
    private final ExecutorService executor;
    private final Handler mainHandler;

    // 腾讯行情 API（CSV格式）
    private static final String TENCENT_QUOTE_URL = "https://qt.gtimg.cn/q=";
    // 新浪 K 线 API（腾讯 K 线已废弃，用新浪替代）
    private static final String SINA_KLINE_URL =
            "https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData";

    private TencentScraper() {
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder builder = original.newBuilder()
                            .header("Accept", "*/*");
                    if (original.header("User-Agent") == null) {
                        builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36");
                    }
                    if (original.header("Referer") == null) {
                        builder.header("Referer", "https://gu.qq.com/");
                    }
                    return chain.proceed(builder.build());
                })
                .build();
    }

    public static synchronized TencentScraper getInstance() {
        if (instance == null) {
            instance = new TencentScraper();
        }
        return instance;
    }

    // ========== 工具 ==========

    /** 转为腾讯格式：600519 → sh600519 */
    private String toTencentSymbol(String symbol) {
        String c = symbol.trim().toUpperCase()
                .replace("SH", "").replace("SZ", "").replace("BJ", "");
        if (c.startsWith("6") || c.startsWith("5")) return "sh" + c;  // 上海A股 + 上海ETF(518880等)
        if (c.startsWith("8") || c.startsWith("4")) return "bj" + c;  // 北交所
        return "sz" + c;  // 深圳A股 + 深圳ETF(159xxx)
    }

    /** 提取纯数字代码 */
    private String toCodeOnly(String symbol) {
        return symbol.trim().toUpperCase()
                .replace("SH", "").replace("SZ", "").replace("BJ", "");
    }

    /** 转为新浪格式（K线用） */
    private String toSinaSymbol(String symbol) {
        String c = toCodeOnly(symbol);
        if (c.startsWith("6") || c.startsWith("5")) return "sh" + c;  // 上海A股 + 上海ETF
        if (c.startsWith("8") || c.startsWith("4")) return "bj" + c;  // 北交所
        return "sz" + c;  // 深圳A股 + 深圳ETF(159xxx)
    }

    /** 判断是否为 A 股/ETF */
    public static boolean isAShareCode(String symbol) {
        String s = symbol.trim().toUpperCase();
        if (s.startsWith("SH") || s.startsWith("SZ") || s.startsWith("BJ")) return true;
        return s.matches("^[0134568]\\d{5}$");
    }

    // ========== 实时行情 ==========

    /**
     * 获取实时行情
     * 腾讯格式: v_sh600519="1~贵州茅台~600519~1715.00~1720.00~1710.00~..."
     * 字段索引（0-based）:
     *  1=名称, 2=代码, 3=当前价, 4=昨收, 5=开盘, 6=成交量(手)
     *  7=外盘, 8=内盘, 9~18=买一~买五(价+量), 19~28=卖一~卖五(价+量)
     *  33=今日最高价, 34=今日最低价
     */
    public void getQuote(String symbol, RepositoryCallback<Stock> callback) {
        executor.execute(() -> {
            try {
                String ts = toTencentSymbol(symbol);
                DataCache cache = DataCache.getInstance();

                // 1. 检查缓存
                String cachedBody = cache.getQuote(symbol);
                String body;
                if (cachedBody != null) {
                    body = cachedBody;
                } else {
                    // 2. 缓存在未命中，发起请求
                    String url = TENCENT_QUOTE_URL + ts;
                    Request request = new Request.Builder()
                            .url(url)
                            .addHeader("Referer", "https://gu.qq.com/")
                            .build();
                    Response response = client.newCall(request).execute();
                    body = response.body() != null ? response.body().string() : "";
                    response.close();
                    if (body != null && !body.isEmpty()) {
                        cache.putQuote(symbol, body);
                    }
                }

                if (body == null || !body.contains("~")) {
                    postError(callback, "获取行情失败");
                    return;
                }

                // 解析 CSV: v_sh600519="1~贵州茅台~600519~1715.00~..."
                String dataStr = body.substring(body.indexOf("\"") + 1, body.lastIndexOf("\""));
                String[] p = dataStr.split("~");
                if (p.length < 35) { postError(callback, "数据格式异常"); return; }

                String code = p[2];
                String name = p[1];
                double currentPrice = parseDouble(p[3]);
                double previousClose = parseDouble(p[4]);
                double open = parseDouble(p[5]);
                double high = parseDouble(p[33]);
                double low = parseDouble(p[34]);
                double change = currentPrice - previousClose;
                double changePercent = previousClose > 0 ? (change / previousClose) * 100 : 0;
                long volume = parseLong(p[6]) * 100; // 手转股

                Stock stock = new Stock(symbol, name);
                stock.setCurrentPrice(currentPrice);
                stock.setChange(change);
                stock.setChangePercent(changePercent);
                stock.setOpen(open);
                stock.setHigh(high);
                stock.setLow(low);
                stock.setPreviousClose(previousClose);
                stock.setVolume(volume);
                stock.setLastUpdated(System.currentTimeMillis() / 1000);

                mainHandler.post(() -> callback.onSuccess(stock));

            } catch (Exception e) {
                Log.e(TAG, "行情失败", e);
                postError(callback, "行情失败: " + e.getMessage());
            }
        });
    }

    // ========== K 线数据（新浪财经备用，含多 URL 容错） ==========

    /**
     * 获取 K 线数据（使用新浪财经接口）
     * 尝试多个 URL，支持月K格式 yyyy-MM
     */
    public void getHistoricalData(String symbol, String resolution, int days,
                                   RepositoryCallback<List<StockDetail.CandleData>> callback) {
        executor.execute(() -> {
            try {
                String sinaSymbol = toSinaSymbol(symbol);
                int count = getCount(resolution, days);
                int scale = getScale(resolution);

                // 尝试多个 URL
                String[] urls = {
                    SINA_KLINE_URL + "?symbol=" + sinaSymbol + "&datalen=" + count + "&scale=" + scale,
                    SINA_KLINE_URL + "?symbol=" + sinaSymbol + "&datalen=" + count,
                    "https://quotes.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData"
                        + "?symbol=" + sinaSymbol + "&datalen=" + count + "&scale=" + scale,
                };

                final List<StockDetail.CandleData>[] resultRef = new List[]{null};
                for (String url : urls) {
                    try {
                        Request request = new Request.Builder()
                                .url(url)
                                .addHeader("Referer", "https://finance.sina.com.cn/")
                                .build();
                        Response response = client.newCall(request).execute();
                        String body = response.body() != null ? response.body().string() : "";
                        response.close();

                        if (body == null || body.isEmpty() || body.equals("null")) continue;

                        JSONArray arr = new JSONArray(body);
                        List<StockDetail.CandleData> parsed = parseKLineJson(arr);
                        if (parsed != null && !parsed.isEmpty()) {
                            Log.d(TAG, "K线数据获取成功: " + url + " → " + parsed.size() + "条");
                            resultRef[0] = parsed;
                            break;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "K线接口失败，换下一个: " + url, e);
                    }
                }

                final List<StockDetail.CandleData> finalData =
                    resultRef[0] != null ? resultRef[0] : new ArrayList<>();
                mainHandler.post(() -> callback.onSuccess(finalData));

            } catch (Exception e) {
                Log.e(TAG, "K线失败", e);
                mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            }
        });
    }

    /**
     * 解析新浪 K 线 JSON 数组
     * 兼容 yyyy-MM-dd、yyyyMMdd、yyyy-MM（月K）三种日期格式
     */
    private List<StockDetail.CandleData> parseKLineJson(JSONArray arr) {
        List<StockDetail.CandleData> dataList = new ArrayList<>();
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMdd", Locale.US);
        SimpleDateFormat sdf3 = new SimpleDateFormat("yyyy-MM", Locale.US);

        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject item = arr.getJSONObject(i);
                String day = item.getString("day");

                long timestamp = 0;
                try {
                    Date date = sdf1.parse(day);
                    if (date != null) timestamp = date.getTime() / 1000;
                } catch (Exception e) {
                    try {
                        Date date = sdf2.parse(day);
                        if (date != null) timestamp = date.getTime() / 1000;
                    } catch (Exception e2) {
                        try {
                            Date date = sdf3.parse(day);
                            if (date != null) timestamp = date.getTime() / 1000;
                        } catch (Exception e3) {
                            Log.w(TAG, "K线日期解析失败: " + day);
                            continue;
                        }
                    }
                }

                dataList.add(new StockDetail.CandleData(
                        timestamp,
                        item.getDouble("open"),
                        item.getDouble("high"),
                        item.getDouble("low"),
                        item.getDouble("close"),
                        item.optLong("volume", 0)
                ));
            } catch (Exception e) {
                Log.w(TAG, "解析K线行失败", e);
            }
        }
        return dataList;
    }

    private int getScale(String resolution) {
        switch (resolution.toUpperCase()) {
            case "5": return 5;
            case "15": return 15;
            case "30": return 30;
            case "60": return 60;
            case "D": return 240;
            case "W": return 1200;
            case "M": return 4800;
            default: return 240;
        }
    }

    private int getCount(String resolution, int days) {
        switch (resolution.toUpperCase()) {
            case "5": return Math.min(days * 48, 720);
            case "60": return Math.min(days * 4, 120);
            case "D": return Math.min(Math.max(days, 15), 365);
            case "W": return Math.min(Math.max(days / 7, 10), 52);
            case "M": return Math.min(Math.max(days / 30, 6), 24);
            default: return Math.min(days, 60);
        }
    }

    // ========== 公司信息 ==========

    public void getCompanyInfo(String symbol, RepositoryCallback<StockDetail> callback) {
        executor.execute(() -> {
            try {
                StockDetail detail = new StockDetail();
                detail.setSymbol(symbol);

                String ts = toTencentSymbol(symbol);
                String url = TENCENT_QUOTE_URL + ts;

                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Referer", "https://gu.qq.com/")
                        .build();
                Response response = client.newCall(request).execute();
                String body = response.body() != null ? response.body().string() : "";
                response.close();

                if (body.contains("\"")) {
                    String dataStr = body.substring(body.indexOf("\"") + 1, body.lastIndexOf("\""));
                    String[] p = dataStr.split("~");
                    StringBuilder sb = new StringBuilder("腾讯行情CSV字段数=" + p.length + " p[30-55]:");
                    for (int j = 30; j < Math.min(p.length, 56); j++) sb.append(" ").append(j).append("=").append(p[j]);
                    Log.d(TAG, sb.toString());

                    if (p.length > 1) {
                        detail.setName(p[1]);

                        // 腾讯行情CSV字段索引（从日志分析和确认）
                        // p[38]=换手率%, p[39]=市盈率, p[44]=流通市值(亿), p[45]=总市值(亿)
                        // p[47]=52周最高, p[48]=52周最低
                        if (p.length > 38) {
                            double tr = parseDouble(p[38]);
                            if (tr > 0) detail.setTurnoverRate(String.format(Locale.US, "%.2f%%", tr));
                        }
                        if (p.length > 39) {
                            double pe = parseDouble(p[39]);
                            if (pe > 0 && pe < 10000) {
                                detail.setPeRatio(pe);
                                // 计算每股收益 EPS = 股价 / 市盈率
                                double curPrice = parseDouble(p[3]);
                                if (curPrice > 0) {
                                    detail.setEps(curPrice / pe);
                                    // 指数盈利 = 1/PE * 100%
                                    detail.setEarningsYield(100.0 / pe);
                                }
                            }
                        }
                        if (p.length > 44) {
                            double cmv = parseDouble(p[44]) * 1_0000_0000; // 亿转元
                            if (cmv > 0) detail.setCirculatingMarketCap(formatMarketCapCn(cmv));
                        }
                        if (p.length > 45) {
                            double mv = parseDouble(p[45]) * 1_0000_0000; // 亿转元
                            if (mv > 0) detail.setMarketCap(formatMarketCapCn(mv));
                        }
                        if (p.length > 47) {
                            detail.setWeek52High(parseDouble(p[47]));
                        }
                        if (p.length > 48) {
                            detail.setWeek52Low(parseDouble(p[48]));
                        }

                        // 振幅 = (最高 - 最低) / 昨收 * 100%
                        if (p.length > 34) {
                            double high = parseDouble(p[33]);
                            double low = parseDouble(p[34]);
                            double prevClose = parseDouble(p[4]);
                            if (high > 0 && prevClose > 0) {
                                detail.setAmplitude((high - low) / prevClose * 100.0);
                            }
                        }
                        // 成交额 = 股价 × 成交量(股)
                        double curPrice = p.length > 3 ? parseDouble(p[3]) : 0;
                        double vol = p.length > 6 ? parseDouble(p[6]) * 100 : 0; // 手→股
                        if (curPrice > 0 && vol > 0) {
                            detail.setTurnoverAmount(curPrice * vol);
                        }
                    }
                }

                mainHandler.post(() -> callback.onSuccess(detail));

            } catch (Exception e) {
                Log.e(TAG, "公司信息失败", e);
                mainHandler.post(() -> callback.onSuccess(new StockDetail()));
            }
        });
    }

    private String formatMarketCapCn(double value) {
        if (value >= 1_0000_0000_0000d)
            return String.format(Locale.US, "%.2f万亿", value / 1_0000_0000_0000d);
        else if (value >= 1_0000_0000)
            return String.format(Locale.US, "%.2f亿", value / 1_0000_0000);
        else if (value >= 1_0000)
            return String.format(Locale.US, "%.2f万", value / 1_0000);
        else
            return String.format(Locale.US, "%.2f元", value);
    }

    // ========== 搜索 ==========

    public void search(String query, RepositoryCallback<List<Stock>> callback) {
        executor.execute(() -> {
            List<Stock> stocks = StockRepository.searchAllStocks(query);
            // 本地没找到，尝试腾讯行情API获取名称
            if (stocks.isEmpty()) {
                String clean = toCodeOnly(query);
                if (StockScraper.isValidMarketCode(clean)) {
                    String name = fetchNameFromTencent(clean);
                    String sym = (clean.startsWith("6") || clean.startsWith("5")) ? "SH" + clean
                            : clean.startsWith("8") || clean.startsWith("4") ? "BJ" + clean
                            : "SZ" + clean;
                    if (name == null || name.isEmpty()) name = clean;
                    stocks.add(new Stock(sym, name));
                    if (!name.equals(clean)) {
                        StockRepository.getInstance().saveSearchedStock(sym, name);
                    }
                }
            }
            mainHandler.post(() -> callback.onSuccess(stocks));
        });
    }

    /** 从腾讯行情获取股票/ETF名称 */
    private String fetchNameFromTencent(String code) {
        try {
            String ts = (code.startsWith("6") || code.startsWith("5")) ? "sh" + code
                    : code.startsWith("8") || code.startsWith("4") ? "bj" + code
                    : "sz" + code;
            String url = TENCENT_QUOTE_URL + ts;
            Request request = new Request.Builder().url(url)
                    .addHeader("Referer", "https://gu.qq.com/")
                    .build();
            Response response = client.newCall(request).execute();
            String body = response.body() != null ? response.body().string() : "";
            response.close();
            if (body.contains("\"") && body.contains("~")) {
                String dataStr = body.substring(body.indexOf("\"") + 1, body.lastIndexOf("\""));
                String[] p = dataStr.split("~");
                if (p.length > 1 && !p[1].isEmpty()) return p[1];
            }
        } catch (Exception e) {
            Log.w(TAG, "获取名称失败", e);
        }
        return null;
    }

    // ========== 新闻 ==========

    public void getNews(String symbol, RepositoryCallback<List<StockDetail.NewsItem>> callback) {
        // 复用 EastMoneyScraper 的新闻
        EastMoneyScraper.getInstance().getNews(symbol, callback);
    }

    // ========== 市场数据 ==========

    public void getMarketIndices(RepositoryCallback<List<Stock>> callback) {
        executor.execute(() -> {
            try {
                // 腾讯行情接口获取指数
                String[] symbols = {"sh000001", "sz399001", "sz399006", "sh000688"};
                String[] names = {"上证指数", "深证成指", "创业板指", "科创50"};
                List<Stock> indices = new ArrayList<>();

                for (int i = 0; i < symbols.length; i++) {
                    try {
                        String url = TENCENT_QUOTE_URL + symbols[i];
                        Request request = new Request.Builder()
                                .url(url)
                                .addHeader("Referer", "https://gu.qq.com/")
                                .build();
                        Response response = client.newCall(request).execute();
                        String body = response.body() != null ? response.body().string() : "";
                        response.close();

                        if (body.contains("\"") && body.contains("~")) {
                            String dataStr = body.substring(body.indexOf("\"") + 1, body.lastIndexOf("\""));
                            String[] p = dataStr.split("~");
                            if (p.length > 32) {
                                double cur = parseDouble(p[3]);   // 当前点位
                                double prev = parseDouble(p[4]);  // 昨收
                                double chg = cur - prev;
                                double chgPct = prev > 0 ? (chg / prev) * 100 : 0;

                                Stock idx = new Stock(symbols[i], names[i]);
                                idx.setCurrentPrice(cur);
                                idx.setChange(chg);
                                idx.setChangePercent(chgPct);
                                indices.add(idx);
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "指数获取失败: " + names[i], e);
                    }
                }

                mainHandler.post(() -> callback.onSuccess(indices));
            } catch (Exception e) {
                Log.e(TAG, "指数获取全部失败", e);
                mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            }
        });
    }

    public void getMarketNews(RepositoryCallback<List<StockDetail.NewsItem>> callback) {
        EastMoneyScraper.getInstance().getMarketNews(callback);
    }

    /**
     * API 连通性诊断（腾讯数据源）
     */
    public void checkApiStatus(RepositoryCallback<String> callback) {
        executor.execute(() -> {
            StringBuilder sb = new StringBuilder();
            String[][] endpoints = {
                {"行情API", TENCENT_QUOTE_URL + "sh600519"},
                {"K线API(新浪)", SINA_KLINE_URL + "?symbol=sh600519&datalen=2&scale=240"},
            };
            for (String[] ep : endpoints) {
                try {
                    long t = System.currentTimeMillis();
                    Request r = new Request.Builder().url(ep[1])
                            .header("User-Agent", "Mozilla/5.0").build();
                    Response resp = client.newCall(r).execute();
                    long ms = System.currentTimeMillis() - t;
                    int code = resp.code();
                    String body = resp.body() != null ? resp.body().string() : "";
                    resp.close();
                    sb.append(ep[0]).append(": HTTP ").append(code)
                            .append(" (").append(ms).append("ms)")
                            .append(body.contains("~") ? " 有数据" : body.isEmpty() ? " 空响应" : "")
                            .append("\n");
                } catch (Exception e) {
                    sb.append(ep[0]).append(": 失败 - ").append(e.getMessage()).append("\n");
                }
            }
            mainHandler.post(() -> callback.onSuccess(sb.toString()));
        });
    }

    // ========== 工具 ==========

    private <T> void postError(RepositoryCallback<T> callback, String msg) {
        mainHandler.post(() -> callback.onError(new Exception(msg)));
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }
}
