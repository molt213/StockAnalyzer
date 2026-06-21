package com.stockanalyzer.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 自选股/关注列表 Room 实体
 */
@Entity(tableName = "watchlist_stocks",
        indices = {@Index(value = "symbol", unique = true)})
public class StockEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    @ColumnInfo(name = "symbol")
    private String symbol;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "display_symbol")
    private String displaySymbol;

    @ColumnInfo(name = "type")
    private String type;

    @ColumnInfo(name = "currency")
    private String currency;

    @ColumnInfo(name = "added_time")
    private long addedTime;

    @ColumnInfo(name = "last_price")
    private double lastPrice;

    @ColumnInfo(name = "last_price_update")
    private long lastPriceUpdate;

    public StockEntity(@NonNull String symbol, String name, long addedTime) {
        this.symbol = symbol;
        this.name = name;
        this.addedTime = addedTime;
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    @NonNull
    public String getSymbol() { return symbol; }
    public void setSymbol(@NonNull String symbol) { this.symbol = symbol; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplaySymbol() { return displaySymbol; }
    public void setDisplaySymbol(String displaySymbol) { this.displaySymbol = displaySymbol; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public long getAddedTime() { return addedTime; }
    public void setAddedTime(long addedTime) { this.addedTime = addedTime; }

    public double getLastPrice() { return lastPrice; }
    public void setLastPrice(double lastPrice) { this.lastPrice = lastPrice; }

    public long getLastPriceUpdate() { return lastPriceUpdate; }
    public void setLastPriceUpdate(long lastPriceUpdate) { this.lastPriceUpdate = lastPriceUpdate; }
}
