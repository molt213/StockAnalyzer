package com.stockanalyzer.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room 数据库
 */
@Database(entities = {StockEntity.class, AIAnalysisEntity.class},
          version = 1,
          exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    public abstract StockDao stockDao();
    public abstract AIAnalysisDao aiAnalysisDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "stock_analyzer_db"
                    )
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .addCallback(new RoomDatabase.Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
                            // 预置一些热门美股到自选股示例
                            preloadWatchlist(db);
                        }
                    })
                    .build();
                }
            }
        }
        return instance;
    }

    /**
     * 首次创建时预置热门A股到自选股
     */
    private static void preloadWatchlist(SupportSQLiteDatabase db) {
        long now = System.currentTimeMillis();
        String[][] presetStocks = {
            {"600519", "贵州茅台"}, {"000858", "五粮液"}, {"300750", "宁德时代"},
            {"601318", "中国平安"}, {"000333", "美的集团"}, {"002594", "比亚迪"},
        };

        for (String[] stock : presetStocks) {
            db.execSQL(
                "INSERT OR IGNORE INTO watchlist_stocks " +
                "(symbol, name, display_symbol, type, added_time, last_price, last_price_update) " +
                "VALUES (?, ?, ?, 'A股', ?, 0, 0)",
                new Object[]{stock[0], stock[1], stock[0], now}
            );
        }
    }
}
