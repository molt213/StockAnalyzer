package com.stockanalyzer.data.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * AI分析历史数据访问对象
 */
@Dao
public interface AIAnalysisDao {

    @Query("SELECT * FROM ai_analysis_history ORDER BY created_at DESC")
    List<AIAnalysisEntity> getAllAnalysisHistory();

    @Query("SELECT * FROM ai_analysis_history WHERE stock_symbol = :symbol ORDER BY created_at DESC")
    List<AIAnalysisEntity> getAnalysisBySymbol(String symbol);

    @Query("SELECT * FROM ai_analysis_history WHERE stock_symbol = :symbol AND analysis_type = :type ORDER BY created_at DESC LIMIT 1")
    AIAnalysisEntity getLatestAnalysis(String symbol, String type);

    @Query("SELECT * FROM ai_analysis_history WHERE id = :id LIMIT 1")
    AIAnalysisEntity getAnalysisById(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertAnalysis(AIAnalysisEntity analysis);

    @Delete
    void deleteAnalysis(AIAnalysisEntity analysis);

    @Query("DELETE FROM ai_analysis_history WHERE id = :id")
    void deleteAnalysisById(long id);

    @Query("DELETE FROM ai_analysis_history WHERE stock_symbol = :symbol")
    void deleteAnalysisBySymbol(String symbol);

    @Query("DELETE FROM ai_analysis_history")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM ai_analysis_history")
    int getAnalysisCount();
}
