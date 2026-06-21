package com.stockanalyzer.data.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 自选股数据访问对象
 */
@Dao
public interface StockDao {

    @Query("SELECT * FROM watchlist_stocks ORDER BY added_time DESC")
    List<StockEntity> getAllWatchlistStocks();

    @Query("SELECT * FROM watchlist_stocks WHERE symbol = :symbol LIMIT 1")
    StockEntity getStockBySymbol(String symbol);

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist_stocks WHERE symbol = :symbol)")
    boolean isInWatchlist(String symbol);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertStock(StockEntity stock);

    @Update
    void updateStock(StockEntity stock);

    @Delete
    void deleteStock(StockEntity stock);

    @Query("DELETE FROM watchlist_stocks WHERE symbol = :symbol")
    void deleteStockBySymbol(String symbol);

    @Query("DELETE FROM watchlist_stocks")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM watchlist_stocks")
    int getWatchlistCount();
}
