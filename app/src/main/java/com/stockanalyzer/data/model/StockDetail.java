package com.stockanalyzer.data.model;

import java.util.List;

/**
 * 股票详细信息模型（含历史K线数据）
 */
public class StockDetail {

    private String symbol;
    private String name;
    private String description;
    private String country;
    private String industry;
    private String sector;
    private String marketCap;
    private String circulatingMarketCap;  // 流通市值
    private String turnoverRate;          // 换手率
    private double peRatio;
    private double eps;
    private double earningsYield;      // 指数盈利 = 1/PE * 100%
    private double pePercentile;       // 历史PE分位数（当前PE在历史中的位置 %）
    private double amplitude;          // 振幅 (high-low)/prevClose * 100%
    private double turnoverAmount;     // 成交额（元）
    private double beta;
    private double week52High;
    private double week52Low;
    private long outstandingShares;

    // 历史K线数据
    private List<CandleData> historicalData;

    // 新闻列表
    private List<NewsItem> newsItems;

    // Getter / Setter
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public String getMarketCap() { return marketCap; }
    public void setMarketCap(String marketCap) { this.marketCap = marketCap; }

    public String getCirculatingMarketCap() { return circulatingMarketCap; }
    public void setCirculatingMarketCap(String v) { this.circulatingMarketCap = v; }

    public String getTurnoverRate() { return turnoverRate; }
    public void setTurnoverRate(String v) { this.turnoverRate = v; }

    public double getPeRatio() { return peRatio; }
    public void setPeRatio(double peRatio) { this.peRatio = peRatio; }

    public double getEps() { return eps; }
    public void setEps(double eps) { this.eps = eps; }

    public double getEarningsYield() { return earningsYield; }
    public void setEarningsYield(double earningsYield) { this.earningsYield = earningsYield; }

    public double getPePercentile() { return pePercentile; }
    public void setPePercentile(double pePercentile) { this.pePercentile = pePercentile; }

    public double getAmplitude() { return amplitude; }
    public void setAmplitude(double v) { this.amplitude = v; }

    public double getTurnoverAmount() { return turnoverAmount; }
    public void setTurnoverAmount(double v) { this.turnoverAmount = v; }

    public double getBeta() { return beta; }
    public void setBeta(double beta) { this.beta = beta; }

    public double getWeek52High() { return week52High; }
    public void setWeek52High(double week52High) { this.week52High = week52High; }

    public double getWeek52Low() { return week52Low; }
    public void setWeek52Low(double week52Low) { this.week52Low = week52Low; }

    public long getOutstandingShares() { return outstandingShares; }
    public void setOutstandingShares(long outstandingShares) { this.outstandingShares = outstandingShares; }

    public List<CandleData> getHistoricalData() { return historicalData; }
    public void setHistoricalData(List<CandleData> historicalData) { this.historicalData = historicalData; }

    public List<NewsItem> getNewsItems() { return newsItems; }
    public void setNewsItems(List<NewsItem> newsItems) { this.newsItems = newsItems; }

    /**
     * K线数据
     */
    public static class CandleData {
        private long timestamp;   // 时间戳 (秒)
        private double open;      // 开盘价
        private double high;      // 最高价
        private double low;       // 最低价
        private double close;     // 收盘价
        private long volume;      // 成交量

        public CandleData() {}

        public CandleData(long timestamp, double open, double high, double low, double close, long volume) {
            this.timestamp = timestamp;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public double getOpen() { return open; }
        public void setOpen(double open) { this.open = open; }

        public double getHigh() { return high; }
        public void setHigh(double high) { this.high = high; }

        public double getLow() { return low; }
        public void setLow(double low) { this.low = low; }

        public double getClose() { return close; }
        public void setClose(double close) { this.close = close; }

        public long getVolume() { return volume; }
        public void setVolume(long volume) { this.volume = volume; }
    }

    /**
     * 新闻条目
     */
    public static class NewsItem {
        private String headline;
        private String summary;
        private String url;
        private String source;
        private long datetime;
        private String imageUrl;
        private List<String> relatedSymbols;

        public String getHeadline() { return headline; }
        public void setHeadline(String headline) { this.headline = headline; }

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public long getDatetime() { return datetime; }
        public void setDatetime(long datetime) { this.datetime = datetime; }

        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

        public List<String> getRelatedSymbols() { return relatedSymbols; }
        public void setRelatedSymbols(List<String> relatedSymbols) { this.relatedSymbols = relatedSymbols; }
    }
}
