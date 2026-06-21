package com.stockanalyzer.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Finnhub API 响应模型
 */
public class StockResponse {

    // ========== 股票行情报价 ==========
    public static class QuoteResponse {
        @SerializedName("c")
        public double currentPrice;    // 当前价格

        @SerializedName("d")
        public double change;          // 变动

        @SerializedName("dp")
        public double changePercent;   // 变动百分比

        @SerializedName("h")
        public double high;            // 当日最高

        @SerializedName("l")
        public double low;             // 当日最低

        @SerializedName("o")
        public double open;            // 开盘价

        @SerializedName("pc")
        public double previousClose;   // 昨收

        @SerializedName("t")
        public long timestamp;         // 时间戳
    }

    // ========== 股票搜索 ==========
    public static class SearchResponse {
        @SerializedName("count")
        public int count;

        @SerializedName("result")
        public List<SearchResult> result;
    }

    public static class SearchResult {
        @SerializedName("description")
        public String description;

        @SerializedName("displaySymbol")
        public String displaySymbol;

        @SerializedName("symbol")
        public String symbol;

        @SerializedName("type")
        public String type;

        @SerializedName("currency")
        public String currency;

        @SerializedName("figi")
        public String figi;

        @SerializedName("mic")
        public String mic;
    }

    // ========== 公司基本信息 ==========
    public static class CompanyProfile {
        @SerializedName("country")
        public String country;

        @SerializedName("currency")
        public String currency;

        @SerializedName("exchange")
        public String exchange;

        @SerializedName("finnhubIndustry")
        public String industry;

        @SerializedName("ipo")
        public String ipo;

        @SerializedName("logo")
        public String logo;

        @SerializedName("marketCapitalization")
        public double marketCapitalization;

        @SerializedName("name")
        public String name;

        @SerializedName("phone")
        public String phone;

        @SerializedName("shareOutstanding")
        public double shareOutstanding;

        @SerializedName("ticker")
        public String ticker;

        @SerializedName("weburl")
        public String weburl;

        @SerializedName("sector")
        public String sector;

        @SerializedName("description")
        public String description;
    }

    // ========== 历史K线数据 ==========
    public static class CandleResponse {
        @SerializedName("c")
        public List<Double> close;      // 收盘价列表

        @SerializedName("h")
        public List<Double> high;       // 最高价列表

        @SerializedName("l")
        public List<Double> low;        // 最低价列表

        @SerializedName("o")
        public List<Double> open;       // 开盘价列表

        @SerializedName("s")
        public String status;           // "ok" 或 "no_data"

        @SerializedName("t")
        public List<Long> timestamp;    // 时间戳列表

        @SerializedName("v")
        public List<Long> volume;       // 成交量列表
    }

    // ========== 公司新闻 ==========
    public static class NewsResponse {
        @SerializedName("category")
        public String category;

        @SerializedName("datetime")
        public long datetime;

        @SerializedName("headline")
        public String headline;

        @SerializedName("id")
        public long id;

        @SerializedName("image")
        public String image;

        @SerializedName("related")
        public String related;

        @SerializedName("source")
        public String source;

        @SerializedName("summary")
        public String summary;

        @SerializedName("url")
        public String url;
    }

    // ========== 财务指标 ==========
    public static class MetricsResponse {
        @SerializedName("metric")
        public MetricsData metric;
    }

    public static class MetricsData {
        @SerializedName("beta")
        public Double beta;

        @SerializedName("epsIncrementalTTM")
        public Double epsTtm;

        @SerializedName("dividendYieldIndicated")
        public Double dividendYield;

        @SerializedName("peBasicExclExtraTTM")
        public Double peRatio;

        @SerializedName("52WeekHigh")
        public Double week52High;

        @SerializedName("52WeekLow")
        public Double week52Low;

        @SerializedName("nsSharesOutstanding")
        public Long sharesOutstanding;
    }
}
