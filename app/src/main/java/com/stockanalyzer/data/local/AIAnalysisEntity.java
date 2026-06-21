package com.stockanalyzer.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * AI分析历史记录 Room 实体
 */
@Entity(tableName = "ai_analysis_history",
        indices = {@Index(value = "stock_symbol")})
public class AIAnalysisEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    @ColumnInfo(name = "stock_symbol")
    private String stockSymbol;

    @ColumnInfo(name = "stock_name")
    private String stockName;

    @ColumnInfo(name = "analysis_type")
    private String analysisType;   // TECHNICAL / FUNDAMENTAL / COMPREHENSIVE

    @ColumnInfo(name = "raw_response")
    private String rawResponse;    // AI原始响应

    @ColumnInfo(name = "summary_text")
    private String summaryText;    // 总结

    @ColumnInfo(name = "rating")
    private String rating;         // 评级

    @ColumnInfo(name = "rating_score")
    private int ratingScore;       // 评分

    @ColumnInfo(name = "risk_level")
    private String riskLevel;      // 风险等级

    @ColumnInfo(name = "advice")
    private String advice;         // 建议

    @ColumnInfo(name = "confidence_score")
    private int confidenceScore;   // 信心指数

    @ColumnInfo(name = "created_at")
    private long createdAt;        // 创建时间

    @ColumnInfo(name = "input_data_snapshot")
    private String inputDataSnapshot; // 分析时的数据快照 (JSON)

    public AIAnalysisEntity(@NonNull String stockSymbol, String stockName,
                             String analysisType, long createdAt) {
        this.stockSymbol = stockSymbol;
        this.stockName = stockName;
        this.analysisType = analysisType;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    @NonNull
    public String getStockSymbol() { return stockSymbol; }
    public void setStockSymbol(@NonNull String stockSymbol) { this.stockSymbol = stockSymbol; }

    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }

    public String getAnalysisType() { return analysisType; }
    public void setAnalysisType(String analysisType) { this.analysisType = analysisType; }

    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }

    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }

    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }

    public int getRatingScore() { return ratingScore; }
    public void setRatingScore(int ratingScore) { this.ratingScore = ratingScore; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getAdvice() { return advice; }
    public void setAdvice(String advice) { this.advice = advice; }

    public int getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(int confidenceScore) { this.confidenceScore = confidenceScore; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getInputDataSnapshot() { return inputDataSnapshot; }
    public void setInputDataSnapshot(String inputDataSnapshot) { this.inputDataSnapshot = inputDataSnapshot; }
}
