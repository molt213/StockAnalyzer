package com.stockanalyzer.data.model;

import androidx.annotation.NonNull;

/**
 * 股票基础信息模型
 */
public class Stock {

    private String symbol;       // 股票代码，如 AAPL
    private String name;         // 公司名称
    private String displaySymbol; // 显示代码
    private String description;  // 公司描述
    private String type;         // 类型 (Common Stock, ETF 等)
    private String currency;     // 货币

    // 实时行情
    private double currentPrice;
    private double change;
    private double changePercent;
    private double open;
    private double high;
    private double low;
    private double previousClose;
    private long volume;
    private long lastUpdated;

    public Stock() {
    }

    public Stock(String symbol, String name) {
        this.symbol = symbol;
        this.name = name;
    }

    // Getters and Setters
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplaySymbol() { return displaySymbol; }
    public void setDisplaySymbol(String displaySymbol) { this.displaySymbol = displaySymbol; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getChange() { return change; }
    public void setChange(double change) { this.change = change; }

    public double getChangePercent() { return changePercent; }
    public void setChangePercent(double changePercent) { this.changePercent = changePercent; }

    public double getOpen() { return open; }
    public void setOpen(double open) { this.open = open; }

    public double getHigh() { return high; }
    public void setHigh(double high) { this.high = high; }

    public double getLow() { return low; }
    public void setLow(double low) { this.low = low; }

    public double getPreviousClose() { return previousClose; }
    public void setPreviousClose(double previousClose) { this.previousClose = previousClose; }

    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }

    /**
     * 判断涨跌
     */
    public boolean isPositiveChange() {
        return change >= 0;
    }

    /**
     * 格式化涨跌幅
     */
    @NonNull
    public String getFormattedChangePercent() {
        return String.format("%.2f%%", changePercent);
    }

    @NonNull
    @Override
    public String toString() {
        return symbol + " - " + (name != null ? name : "");
    }
}
