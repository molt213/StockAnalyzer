package com.stockanalyzer.data.repository;

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

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

import android.os.Handler;
import android.os.Looper;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 东方财富 A 股数据爬虫
 * 使用东方财富公开数据接口，替代同花顺数据源
 *
 * API 说明：
 * - 实时行情: push2.eastmoney.com/api/qt/stock/get
 * - K 线数据: push2.eastmoney.com/api/qt/stock/kline/get
 * - 搜索: searchadapter.eastmoney.com/api/suggest/get
 * - 公司信息: emweb.securities.eastmoney.com/PC_HSF10/CompanySurvey
 * - 新闻: push2.eastmoney.com/api/qt/stock/news/get (复用 StockNewsScraper)
 */
public class EastMoneyScraper {

    private static final String TAG = "EastMoneyScraper";
    private static EastMoneyScraper instance;

    private final OkHttpClient client;
    private final ExecutorService executor;
    private final Handler mainHandler;

    // 上次请求时间（用于添加延迟）
    private long lastRequestTime = 0;

    // 东方财富 API 基础地址
    private static final String EASTMONEY_QUOTE_URL =
            "https://push2.eastmoney.com/api/qt/stock/get";
    // 注意：K线使用 push2his 子域名（历史数据接口），而非 push2（实时行情接口）
    private static final String EASTMONEY_KLINE_URL =
            "https://push2his.eastmoney.com/api/qt/stock/kline/get";
    private static final String EASTMONEY_SEARCH_URL =
            "https://searchadapter.eastmoney.com/api/suggest/get";
    private static final String EASTMONEY_SEARCH_URL2 =
            "https://searchapi.eastmoney.com/api/suggest/get";
    private static final String EASTMONEY_COMPANY_URL =
            "https://emweb.securities.eastmoney.com/PC_HSF10/CompanySurvey/CompanySurveyAjax";
    private static final String EASTMONEY_FINANCE_SUMMARY_URL =
            "https://emweb.securities.eastmoney.com/PC_HSF10/FinanceSummary/FinanceSummaryAjax";

    // 实时行情字段
    // f43=最新价, f44=最高, f45=最低, f46=今开, f47=昨收
    // f48=涨跌额, f50=涨跌幅, f52=成交量(手), f57=代码, f58=名称
    // f116=总市值, f117=流通市值, f162=市盈率(动), f100=换手率
    private static final String QUOTE_FIELDS =
            "f43,f44,f45,f46,f47,f48,f50,f52,f57,f58,f116,f117,f162,f100,f170,f171";

    private EastMoneyScraper() {
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());

        // 内存 Cookie 存储，模拟浏览器会话
        CookieJar cookieJar = new CookieJar() {
            private final java.util.HashMap<String, List<Cookie>> cookieStore = new java.util.HashMap<>();
            @Override
            public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                cookieStore.put(url.host(), cookies);
            }
            @Override
            public List<Cookie> loadForRequest(HttpUrl url) {
                List<Cookie> cookies = cookieStore.get(url.host());
                return cookies != null ? cookies : new java.util.ArrayList<>();
            }
        };

        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .cookieJar(cookieJar)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder builder = original.newBuilder()
                            .header("Accept", "*/*");
                    if (original.header("User-Agent") == null) {
                        builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36");
                    }
                    if (original.header("Referer") == null) {
                        builder.header("Referer", "https://quote.eastmoney.com/");
                    }
                    return chain.proceed(builder.build());
                })
                .build();
    }

    public static synchronized EastMoneyScraper getInstance() {
        if (instance == null) {
            instance = new EastMoneyScraper();
        }
        return instance;
    }

    // ========== 工具方法 ==========

    /**
     * 获取东方财富 secid 格式
     * 上海: 1.600519, 深圳: 0.000001
     */
    public static String toSecId(String symbol) {
        String c = symbol.trim().toUpperCase()
                .replace("SH", "").replace("SZ", "").replace("BJ", "");
        int market = c.startsWith("6") ? 1 : 0;
        return market + "." + c;
    }

    /**
     * 提取纯数字代码
     */
    public static String toCodeOnly(String symbol) {
        return symbol.trim().toUpperCase()
                .replace("SH", "").replace("SZ", "").replace("BJ", "");
    }

    /**
     * 判断是否为 A 股代码
     */
    public static boolean isAShareCode(String symbol) {
        String s = symbol.trim().toUpperCase();
        if (s.startsWith("SH") || s.startsWith("SZ") || s.startsWith("BJ")) return true;
        return s.matches("^[6003]\\d{5}$");
    }

    /**
     * 获取东方财富公司信息用的代码格式
     * 上海: SH600519, 深圳: SZ000001
     */
    public static String toCompanyCode(String symbol) {
        String c = toCodeOnly(symbol);
        return c.startsWith("6") ? "SH" + c : "SZ" + c;
    }

    // ========== 实时行情 ==========

    /**
     * 获取实时行情
     * 使用东方财富 push2 接口
     */
    public void getQuote(String symbol, RepositoryCallback<Stock> callback) {
        executor.execute(() -> {
            try {
                Stock stock = fetchQuote(symbol);
                if (stock != null && stock.getCurrentPrice() > 0) {
                    mainHandler.post(() -> callback.onSuccess(stock));
                } else {
                    postError(callback, "无法获取行情: 数据为空");
                }
            } catch (Exception e) {
                Log.e(TAG, "行情获取失败", e);
                postError(callback, "行情获取失败: " + e.getMessage());
            }
        });
    }

    /**
     * 从东方财富获取实时行情
     * 返回格式: {"data":{"f43":1999.0,"f44":2010.0,"f45":1988.0,...}}
     */
    private Stock fetchQuote(String symbol) throws Exception {
        String secId = toSecId(symbol);
        DataCache cache = DataCache.getInstance();

        // 1. 检查缓存
        String cachedBody = cache.getQuote(symbol);
        String body;
        if (cachedBody != null) {
            body = cachedBody;
        } else {
            // 2. 缓存未命中，发起请求
            String url = EASTMONEY_QUOTE_URL + "?secid=" + secId + "&fltt=1&fields=" + QUOTE_FIELDS + "&_=" + System.currentTimeMillis();
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Referer", "https://quote.eastmoney.com/" + toCompanyCode(symbol) + ".html")
                    .build();
            Response response = client.newCall(request).execute();
            body = response.body() != null ? response.body().string() : "";
            response.close();
            if (!body.isEmpty() && !body.equals("{}")) {
                cache.putQuote(symbol, body);
            }
        }

        if (body.isEmpty() || body.equals("{}")) return null;

        JSONObject json = new JSONObject(body);
        JSONObject data = json.optJSONObject("data");
        if (data == null) return null;

        // 验证数据有效性
        if (!data.has("f43") || data.optDouble("f43", 0) == 0) return null;

        Log.d(TAG, "行情原始数据: f43=" + data.optDouble("f43", 0)
                + " f44=" + data.optDouble("f44", 0)
                + " f45=" + data.optDouble("f45", 0)
                + " f46=" + data.optDouble("f46", 0)
                + " f47=" + data.optDouble("f47", 0)
                + " f57=" + data.optString("f57", "")
                + " f58=" + data.optString("f58", ""));

        String code = data.optString("f57", symbol);
        String name = data.optString("f58", "");
        // 东财行情API价格字段单位为分，需除以100转为元
        double currentPrice = data.optDouble("f43", 0) / 100.0;
        double high = data.optDouble("f44", 0) / 100.0;
        double low = data.optDouble("f45", 0) / 100.0;
        double open = data.optDouble("f46", 0) / 100.0;
        // 从日K线获取准确的昨收（preKPrice字段准确可靠）
        double previousClose = fetchPreKPrice(secId);
        // 本地计算涨跌额和涨跌幅，API返回的f48/f50可能有误
        double change = previousClose > 0 ? (currentPrice - previousClose) : 0;
        double changePercent = previousClose > 0 ? (change / previousClose) * 100 : 0;
        long volume = data.optLong("f52", 0) * 100; // 东方财富返回的是手数，转成股数
        double marketCap = data.optDouble("f116", 0);  // 市值单位为元，不用除
        double circulMarketCap = data.optDouble("f117", 0);
        double pe = data.optDouble("f162", 0);

        Stock stock = new Stock(code, name);
        stock.setCurrentPrice(currentPrice);
        stock.setChange(change);
        stock.setChangePercent(changePercent);
        stock.setOpen(open);
        stock.setHigh(high);
        stock.setLow(low);
        stock.setPreviousClose(previousClose);
        stock.setVolume(volume);
        stock.setLastUpdated(System.currentTimeMillis() / 1000);

        return stock;
    }

    // ========== K 线数据 ==========

    /**
     * 获取历史 K 线数据
     * klt: 1=1分, 5=5分, 15=15分, 30=30分, 60=60分, 101=日, 102=周, 103=月
     * fqt: 1=前复权, 2=后复权, 0=不复权
     * lmt: 返回条数
     * fields1/fields2: 指定返回字段，不加可能导致 data.klines 为空
     */
    public void getHistoricalData(String symbol, String resolution, int days,
                                   RepositoryCallback<List<StockDetail.CandleData>> callback) {
        executor.execute(() -> {
            try {
                String secId = toSecId(symbol);
                int klt = mapKlt(resolution);
                int lmt = mapCount(resolution, days);
                String cacheKey = symbol + "_" + resolution;
                DataCache cache = DataCache.getInstance();

                // 1. 检查缓存
                String body = cache.getKLine(symbol, resolution);
                if (body == null) {
                    // 2. 缓存未命中，发起请求
                    String url = EASTMONEY_KLINE_URL
                            + "?secid=" + secId
                            + "&ut=fa5fd1943c7b386f172d6893dbfd32bb"
                            + "&fields1=f1,f2,f3,f4,f5,f6"
                            + "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"
                            + "&klt=" + klt
                            + "&fqt=1"
                            + "&end=20500101"
                            + "&lmt=" + lmt;

                    Request request = new Request.Builder()
                            .url(url)
                            .addHeader("Referer", "https://quote.eastmoney.com/" + toCompanyCode(symbol) + ".html")
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .build();
                    Response response = client.newCall(request).execute();
                    body = response.body() != null ? response.body().string() : "";
                    response.close();
                    if (!body.isEmpty() && !body.equals("{}")) {
                        cache.putKLine(symbol, resolution, body);
                    }
                }

                List<StockDetail.CandleData> dataList = parseKLine(body);

                mainHandler.post(() -> callback.onSuccess(dataList));

            } catch (Exception e) {
                Log.e(TAG, "K线获取失败", e);
                mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            }
        });
    }

    /**
     * 解析东方财富 K 线数据
     * 返回格式: {"data":{"klines":["date,open,close,high,low,volume,amount",...]}}
     * 字段顺序: 0日期,1开盘,2收盘,3最高,4最低,5成交量,6成交额
     */
    private List<StockDetail.CandleData> parseKLine(String body) {
        List<StockDetail.CandleData> dataList = new ArrayList<>();
        if (body == null || body.isEmpty()) return dataList;

        try {
            JSONObject json = new JSONObject(body);
            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                Log.w(TAG, "K线响应无data节点: " + body);
                return dataList;
            }

            JSONArray klines = data.optJSONArray("klines");
            if (klines == null) {
                Log.w(TAG, "K线响应无klines数组: " + data.toString());
                return dataList;
            }

            SimpleDateFormat sdfDaily = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

            for (int i = 0; i < klines.length(); i++) {
                try {
                    String klineStr = klines.optString(i, "");
                    if (klineStr.isEmpty()) continue;

                    // 格式: "2024-01-15,1960.00,1970.00,1950.00,1940.00,5000000,9500000000"
                    String[] parts = klineStr.split(",");
                    if (parts.length < 6) {
                        Log.w(TAG, "K线字段不足: " + klineStr);
                        continue;
                    }

                    String dateStr = parts[0].trim();
                    double open = parseDouble(parts[1]);
                    double close = parseDouble(parts[2]);
                    double high = parseDouble(parts[3]);
                    double low = parseDouble(parts[4]);
                    long volume = parseLong(parts[5]);

                    // 解析日期 — 兼容 yyyy-MM-dd 和 yyyyMMdd 两种格式
                    long timestamp = 0;
                    try {
                        Date date = sdfDaily.parse(dateStr);
                        if (date != null) timestamp = date.getTime() / 1000;
                    } catch (Exception e) {
                        // 尝试 yyyyMMdd 格式
                        try {
                            SimpleDateFormat sdfCompact = new SimpleDateFormat("yyyyMMdd", Locale.US);
                            Date date = sdfCompact.parse(dateStr);
                            if (date != null) timestamp = date.getTime() / 1000;
                        } catch (Exception e2) {
                            Log.w(TAG, "K线日期解析失败: " + dateStr, e);
                            continue;
                        }
                    }

                    dataList.add(new StockDetail.CandleData(
                            timestamp, open, high, low, close, volume));
                } catch (Exception e) {
                    Log.w(TAG, "解析K线行失败", e);
                }
            }

            Log.d(TAG, "K线解析完成: " + dataList.size() + "条");

        } catch (Exception e) {
            Log.w(TAG, "K线JSON解析失败", e);
        }

        return dataList;
    }

    /**
     * 将 resolution 映射到东方财富 klt 参数
     */
    private int mapKlt(String resolution) {
        switch (resolution.toUpperCase()) {
            case "1":  return 1;    // 1分钟
            case "5":  return 5;    // 5分钟
            case "15": return 15;   // 15分钟
            case "30": return 30;   // 30分钟
            case "60": return 60;   // 60分钟
            case "D":  return 101;  // 日线
            case "W":  return 102;  // 周线
            case "M":  return 103;  // 月线
            case "Y":  return 104;  // 年线
            default:   return 101;  // 默认日线
        }
    }

    /**
     * 根据周期和天数映射 K 线条数
     * 保证最小返回条数以提供有意义的图表
     */
    private int mapCount(String resolution, int days) {
        switch (resolution.toUpperCase()) {
            case "D":  return Math.min(Math.max(days, 15), 365);    // 日线: days=60 → 60根
            case "W":  return Math.min(Math.max(days / 7, 4), 104); // 周线: days=180 → 26根
            case "M":  return Math.min(Math.max(days / 30, 3), 60); // 月线: days=360 → 12根
            case "60": return Math.min(Math.max(days * 4, 20), 240); // 60分: days=5 → 20根
            case "5":  return Math.min(Math.max(days * 78, 50), 2880); // 5分: days=1 → 78根
            default:   return Math.min(Math.max(days, 15), 120);
        }
    }

    // ========== 公司信息 ==========

    /**
     * 获取公司基本信息
     * 使用东方财富 PC_HSF10 接口 + 行情接口获取财务数据
     */
    public void getCompanyInfo(String symbol, RepositoryCallback<StockDetail> callback) {
        executor.execute(() -> {
            try {
                StockDetail detail = new StockDetail();
                detail.setSymbol(symbol);

                // 从行情接口获取所有数据（一次请求，快速）
                String secId = toSecId(symbol);
                String quoteUrl = EASTMONEY_QUOTE_URL + "?secid=" + secId
                        + "&fltt=1"
                        + "&fields=f43,f44,f45,f46,f47,f48,f50,f52,f57,f58,f116,f117,f162,f167,f168,f169"
                        + "&_=" + System.currentTimeMillis();

                Request quoteReq = new Request.Builder()
                        .url(quoteUrl)
                        .addHeader("Referer", "https://quote.eastmoney.com/" + toCompanyCode(symbol) + ".html")
                        .build();
                Response quoteResp = client.newCall(quoteReq).execute();
                String quoteBody = quoteResp.body() != null ? quoteResp.body().string() : "";
                quoteResp.close();

                if (!quoteBody.isEmpty() && !quoteBody.equals("{}")) {
                    try {
                        JSONObject json = new JSONObject(quoteBody);
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            detail.setName(data.optString("f58", symbol));
                            // 总市值 f116（元），转中文格式
                            double mv = data.optDouble("f116", 0);
                            if (mv > 0) detail.setMarketCap(formatMarketCapCn(mv));
                            // 动态市盈率 f162
                            double pe = data.optDouble("f162", 0);
                            if (pe > 0) detail.setPeRatio(pe);
                            // 计算每股收益 EPS = 股价 / 市盈率
                            double price = data.optDouble("f43", 0) / 100.0;
                            if (pe > 0 && price > 0) {
                                detail.setEps(price / pe);
                            }
                            // 流通市值 f117
                            double cmv = data.optDouble("f117", 0);
                            if (cmv > 0) detail.setCirculatingMarketCap(formatMarketCapCn(cmv));
                            // 换手率 = 成交股数 / 流通股数 * 100%
                            // f52 = 成交量(手), f43 = 股价, f117 = 流通市值(元)
                            if (price > 0 && cmv > 0) {
                                double vol = data.optDouble("f52", 0) * 100; // 手→股
                                if (vol > 0) {
                                    double sharesOutstanding = cmv / price;   // 流通股数
                                    double tr = (vol / sharesOutstanding) * 100.0;
                                    detail.setTurnoverRate(String.format(Locale.US, "%.2f%%", tr));
                                }
                            }
                            // 52周最高 f167, 52周最低 f168
                            detail.setWeek52High(data.optDouble("f167", 0));
                            detail.setWeek52Low(data.optDouble("f168", 0));
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "行情数据解析失败", e);
                    }
                }

                mainHandler.post(() -> callback.onSuccess(detail));

            } catch (Exception e) {
                Log.e(TAG, "公司信息获取失败", e);
                mainHandler.post(() -> callback.onSuccess(new StockDetail()));
            }
        });
    }

    // ========== 搜索 ==========

    /**
     * 搜索股票
     * 使用东方财富 suggest 搜索接口
     * 返回: {"result":[{"Code":"600519","Name":"贵州茅台","Type":1},...]}
     */
    public void search(String query, RepositoryCallback<List<Stock>> callback) {
        executor.execute(() -> {
            try {
                String clean = toCodeOnly(query);
                List<Stock> stocks = new ArrayList<>();

                // 仅支持代码搜索
                if (clean.matches("^[6003]\\d{5}$")) {
                    // 先从本地查询名称
                    stocks = StockRepository.searchAllStocks(query);

                    // 本地没有则尝试 API
                    if (stocks.isEmpty()) {
                        String encoded = java.net.URLEncoder.encode(query, "UTF-8");
                        String url = "https://searchadapter.eastmoney.com/api/suggest/get?input=" + encoded + "&count=10&type=1";
                        try {
                            Request request = new Request.Builder()
                                    .url(url)
                                    .addHeader("Referer", "https://quote.eastmoney.com/")
                                    .build();
                            Response response = client.newCall(request).execute();
                            String body = response.body() != null ? response.body().string() : "";
                            response.close();
                            if (!body.isEmpty()) {
                                if (body.startsWith("jQuery(")) {
                                    int s = body.indexOf("("), e = body.lastIndexOf(")");
                                    if (s >= 0 && e > s) body = body.substring(s + 1, e);
                                }
                                JSONObject json = new JSONObject(body);
                                JSONArray result = json.optJSONArray("result");
                                if (result != null) {
                                    for (int i = 0; i < result.length(); i++) {
                                        JSONObject item = result.getJSONObject(i);
                                        String code = item.optString("Code", "");
                                        String name = item.optString("Name", "");
                                        if (!code.isEmpty() && !name.isEmpty()) {
                                            String sym = code.startsWith("6") ? "SH" + code : "SZ" + code;
                                            stocks.add(new Stock(sym, name));
                                            StockRepository.getInstance().saveSearchedStock(sym, name);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "搜索API失败", e);
                        }
                    }

                    // 实在找不到名称，从行情API获取股票名称
                    if (stocks.isEmpty()) {
                        try {
                            String secId = toSecId(clean);
                            String qUrl = EASTMONEY_QUOTE_URL + "?secid=" + secId + "&fields=f57,f58&_=" + System.currentTimeMillis();
                            Request qReq = new Request.Builder().url(qUrl)
                                    .addHeader("Referer", "https://quote.eastmoney.com/")
                                    .build();
                            Response qResp = client.newCall(qReq).execute();
                            String qBody = qResp.body() != null ? qResp.body().string() : "";
                            qResp.close();
                            if (!qBody.isEmpty()) {
                                JSONObject qJson = new JSONObject(qBody);
                                JSONObject qData = qJson.optJSONObject("data");
                                if (qData != null) {
                                    String realName = qData.optString("f58", "");
                                    if (!realName.isEmpty()) {
                                        String sym = clean.startsWith("6") ? "SH" + clean : "SZ" + clean;
                                        stocks.add(new Stock(sym, realName));
                                        StockRepository.getInstance().saveSearchedStock(sym, realName);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "搜索获取名称失败", e);
                        }
                    }
                    if (stocks.isEmpty()) {
                        String sym = clean.startsWith("6") ? "SH" + clean : "SZ" + clean;
                        stocks.add(new Stock(sym, clean));
                    }
                }

                final List<Stock> finalResult = stocks;
                mainHandler.post(() -> callback.onSuccess(finalResult));

            } catch (Exception e) {
                Log.e(TAG, "搜索失败", e);
                mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            }
        });
    }


    /**
     * API 连通性诊断（返回各接口状态信息）
     */
    public void checkApiStatus(RepositoryCallback<String> callback) {
        executor.execute(() -> {
            StringBuilder sb = new StringBuilder();
            String ts = String.valueOf(System.currentTimeMillis());
            String[][] endpoints = {
                {"行情API(fltt=1)",
                 EASTMONEY_QUOTE_URL + "?secid=1.600519&fltt=1&fields=f43,f58&_=" + ts},
                {"行情API(无fltt)",
                 EASTMONEY_QUOTE_URL + "?secid=1.600519&fields=f43,f58&_=" + ts},
                {"K线API",
                 EASTMONEY_KLINE_URL + "?secid=1.600519&ut=fa5fd1943c7b386f172d6893dbfd32bb"
                 + "&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"
                 + "&klt=101&fqt=1&end=20500101&lmt=2"},
            };
            for (String[] ep : endpoints) {
                try {
                    long t = System.currentTimeMillis();
                    Request r = new Request.Builder().url(ep[1])
                            .header("User-Agent", "Mozilla/5.0")
                            .build();
                    Response resp = client.newCall(r).execute();
                    long ms = System.currentTimeMillis() - t;
                    int code = resp.code();
                    String body = resp.body() != null ? resp.body().string() : "";
                    resp.close();
                    sb.append(ep[0]).append(": HTTP ").append(code)
                            .append(" (").append(ms).append("ms)")
                            .append(body.isEmpty() ? " 空响应" : "")
                            .append("\n");
                } catch (Exception e) {
                    sb.append(ep[0]).append(": 失败 - ").append(e.getMessage()).append("\n");
                }
            }
            mainHandler.post(() -> callback.onSuccess(sb.toString()));
        });
    }

    // ========== 新闻 ==========

    /**
     * 获取个股新闻（先试个股接口，失败降级到市场要闻）
     */
    public void getNews(String symbol, RepositoryCallback<List<StockDetail.NewsItem>> callback) {
        executor.execute(() -> {
            try {
                List<StockDetail.NewsItem> news = fetchStockNewsFromHis(symbol);
                if (news == null || news.isEmpty()) {
                    news = fetchNewsSinaRoll(symbol);
                    if (news != null) {
                        for (StockDetail.NewsItem item : news) {
                            if (item.getSource() == null || item.getSource().isEmpty()) {
                                item.setSource("市场要闻");
                            }
                        }
                    }
                }
                final List<StockDetail.NewsItem> finalNews = news != null ? news : new ArrayList<>();
                mainHandler.post(() -> callback.onSuccess(finalNews));
            } catch (Exception e) {
                Log.e(TAG, "新闻获取失败", e);
                mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            }
        });
    }

    /**
     * 获取大盘指数（用于仪表盘市场概览）
     */
    public void getMarketIndices(RepositoryCallback<List<Stock>> callback) {
        executor.execute(() -> {
            try {
                DataCache cache = DataCache.getInstance();
                // 检查缓存
                String cached = cache.getIndices();
                if (cached != null) {
                    List<Stock> indices = parseIndicesFromJson(cached);
                    if (!indices.isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(indices));
                        return;
                    }
                }

                // 上证指数(1.000001), 深证成指(0.399001), 创业板指(0.399006), 科创50(1.000688)
                String[] indexSecIds = {"1.000001", "0.399001", "0.399006", "1.000688"};
                String[] indexNames = {"上证指数", "深证成指", "创业板指", "科创50"};
                List<Stock> indices = new ArrayList<>();

                for (int i = 0; i < indexSecIds.length; i++) {
                    try {
                        String url = EASTMONEY_QUOTE_URL + "?secid=" + indexSecIds[i]
                                + "&fields=f43,f48,f50,f57,f58&_=" + System.currentTimeMillis();
                        Request request = new Request.Builder()
                                .url(url)
                                .addHeader("Referer", "https://quote.eastmoney.com/")
                                .build();
                        Response response = client.newCall(request).execute();
                        String body = response.body() != null ? response.body().string() : "";
                        response.close();

                        if (!body.isEmpty() && !body.equals("{}")) {
                            JSONObject json = new JSONObject(body);
                            JSONObject data = json.optJSONObject("data");
                            if (data != null && data.has("f43")) {
                                String code = data.optString("f57", indexSecIds[i]);
                                double rawF43 = data.optDouble("f43", 0);
                                Log.d(TAG, indexNames[i] + " 原始f43=" + rawF43);
                                // 指数f43也是分单位（409048→4090.48点）
                                double cur = rawF43 / 100.0;
                                // 从日K线获取准确的昨收（和个股一样用前复权）
                                double prevClose = fetchPreKPrice(indexSecIds[i], 1);
                                Log.d(TAG, indexNames[i] + " preKPrice=" + prevClose);
                                if (prevClose <= 0) prevClose = cur;
                                double chg = cur - prevClose;
                                double chgPct = prevClose > 0 ? (chg / prevClose) * 100 : 0;
                                Stock index = new Stock(code, indexNames[i]);
                                index.setCurrentPrice(cur);
                                index.setChange(chg);
                                index.setChangePercent(chgPct);
                                indices.add(index);
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "指数获取失败: " + indexNames[i], e);
                    }
                }
                // 缓存结果
                DataCache.getInstance().putIndices(indicesToJson(indices));
                mainHandler.post(() -> callback.onSuccess(indices));
            } catch (Exception e) {
                Log.e(TAG, "指数获取全部失败", e);
                mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            }
        });
    }

    /**
     * 获取市场要闻（用于仪表盘市场概览）
     */
    public void getMarketNews(RepositoryCallback<List<StockDetail.NewsItem>> callback) {
        executor.execute(() -> {
            try {
                List<StockDetail.NewsItem> news = fetchNewsSinaRoll("000001");
                mainHandler.post(() -> callback.onSuccess(news != null ? news : new ArrayList<>()));
            } catch (Exception e) {
                Log.e(TAG, "市场要闻获取失败", e);
                mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            }
        });
    }

    /** 个股新闻：尝试 push2his，失败返回 null（由上层降级到市场要闻） */
    private List<StockDetail.NewsItem> fetchStockNewsFromHis(String symbol) {
        String secId = toSecId(symbol);
        String url = "https://push2his.eastmoney.com/api/qt/stock/news/get?secid=" + secId + "&count=15";
        try {
            Request request = new Request.Builder().url(url)
                    .addHeader("Referer", "https://quote.eastmoney.com/" + toCompanyCode(symbol) + ".html")
                    .build();
            Response response = client.newCall(request).execute();
            String body = response.body() != null ? response.body().string() : "";
            response.close();
            if (body.isEmpty() || body.equals("{}")) return null;
            JSONObject json = new JSONObject(body);
            JSONObject data = json.optJSONObject("data");
            if (data == null) return null;
            JSONArray list = data.optJSONArray("list");
            if (list == null || list.length() == 0) return null;
            List<StockDetail.NewsItem> newsList = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                try {
                    JSONObject item = list.getJSONObject(i);
                    StockDetail.NewsItem news = new StockDetail.NewsItem();
                    news.setHeadline(item.optString("title", ""));
                    news.setSummary(item.optString("content", ""));
                    news.setSource(item.optString("source", "东方财富"));
                    String artUrl = item.optString("url", "");
                    if (artUrl.startsWith("//")) artUrl = "https:" + artUrl;
                    news.setUrl(artUrl);
                    long artTime = item.optLong("art_430", 0);
                    if (artTime > 0) news.setDatetime(artTime / 1000);
                    if (!news.getHeadline().isEmpty()) newsList.add(news);
                } catch (Exception e) { /* skip */ }
            }
            if (!newsList.isEmpty()) return newsList;
        } catch (Exception e) { /* push2his无数据 */ }
        return null;
    }

    /** 市场要闻：新浪滚动新闻 API（用于仪表盘市场概览，非个股相关） */
    private List<StockDetail.NewsItem> fetchNewsSinaRoll(String code) {
        try {
            String url = "https://feed.mix.sina.com.cn/api/roll/get?pageid=153&lid="
                    + (code.startsWith("6") ? "2516" : "2517") + "&knum=20";
            Request request = new Request.Builder().url(url)
                    .addHeader("Referer", "https://finance.sina.com.cn/")
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .build();
            Response response = client.newCall(request).execute();
            String body = response.body() != null ? response.body().string() : "";
            response.close();
            if (body.isEmpty() || body.equals("{}")) return null;
            JSONObject json = new JSONObject(body);
            JSONObject resultObj = json.optJSONObject("result");
            if (resultObj == null) return null;
            JSONArray items = resultObj.optJSONArray("data");
            if (items == null || items.length() == 0) return null;
            List<StockDetail.NewsItem> newsList = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                try {
                    JSONObject item = items.getJSONObject(i);
                    StockDetail.NewsItem news = new StockDetail.NewsItem();
                    news.setHeadline(item.optString("title", ""));
                    news.setSummary(item.optString("intro", ""));
                    String src = item.optString("media_name", "");
                    news.setSource(src.isEmpty() ? "新浪财经" : src);
                    String artUrl = item.optString("url", "");
                    if (artUrl.startsWith("//")) artUrl = "https:" + artUrl;
                    news.setUrl(artUrl);
                    if (!news.getHeadline().isEmpty()) newsList.add(news);
                } catch (Exception e) { /* skip */ }
            }
            return newsList.isEmpty() ? null : newsList;
        } catch (Exception e) {
            Log.w(TAG, "市场要闻请求失败", e);
            return null;
        }
    }

    // ========== 昨收 ==========

    /** 从日K线获取准确的昨收价（preKPrice 字段准确可靠，f47 经常有误） */
    private double fetchPreKPrice(String secId) {
        return fetchPreKPrice(secId, 1);
    }

    /** 从日K线获取昨收价，fqt=1前复权（个股用），fqt=0不复权（指数用） */
    private double fetchPreKPrice(String secId, int fqt) {
        try {
            String url = "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=" + secId
                    + "&ut=fa5fd1943c7b386f172d6893dbfd32bb"
                    + "&fields1=f1,f2,f3,f4,f5,f6"
                    + "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"
                    + "&klt=101&fqt=" + fqt + "&end=20500101&lmt=2";
            Log.d(TAG, "fetchPreKPrice URL: " + url);
            Request request = new Request.Builder().url(url)
                    .addHeader("Referer", "https://quote.eastmoney.com/")
                    .build();
            Response response = client.newCall(request).execute();
            String body = response.body() != null ? response.body().string() : "";
            response.close();
            Log.d(TAG, "fetchPreKPrice body: " + (body.length() > 200 ? body.substring(0, 200) : body));
            if (!body.isEmpty()) {
                JSONObject json = new JSONObject(body);
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    double preKPrice = data.optDouble("preKPrice", 0);
                    Log.d(TAG, "fetchPreKPrice preKPrice=" + preKPrice);
                    if (preKPrice > 0) return preKPrice;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "fetchPreKPrice failed", e);
        }
        return 0;
    }

    // ========== 缓存序列化 ==========

    private String indicesToJson(List<Stock> indices) {
        try {
            JSONArray arr = new JSONArray();
            for (Stock s : indices) {
                JSONObject item = new JSONObject();
                item.put("code", s.getSymbol());
                item.put("name", s.getName());
                item.put("price", s.getCurrentPrice());
                item.put("change", s.getChange());
                item.put("changePercent", s.getChangePercent());
                arr.put(item);
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Stock> parseIndicesFromJson(String json) {
        List<Stock> indices = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                Stock s = new Stock(item.optString("code", ""), item.optString("name", ""));
                s.setCurrentPrice(item.optDouble("price", 0));
                s.setChange(item.optDouble("change", 0));
                s.setChangePercent(item.optDouble("changePercent", 0));
                indices.add(s);
            }
        } catch (Exception e) { /* ignore */ }
        return indices;
    }

    // ========== 工具方法 ==========

    private <T> void postError(RepositoryCallback<T> callback, String msg) {
        mainHandler.post(() -> callback.onError(new Exception(msg)));
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    /**
     * 格式化市值（元 → 中文单位）
     */
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
}
