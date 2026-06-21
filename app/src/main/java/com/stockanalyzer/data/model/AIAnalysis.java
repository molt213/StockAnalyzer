package com.stockanalyzer.data.model;

import java.util.List;

/**
 * AI分析结果模型
 */
public class AIAnalysis {

    private String stockSymbol;
    private String stockName;
    private long timestamp;
    private AnalysisType type;

    // 技术面分析
    private TechnicalAnalysis technicalAnalysis;

    // 基本面分析
    private FundamentalAnalysis fundamentalAnalysis;

    // 综合分析
    private Summary summary;

    // 情绪分析
    private SentimentAnalysis sentimentAnalysis;

    // 原始AI响应文本
    private String rawResponse;

    // 风险等级（快捷访问）
    private String riskLevel;

    public enum AnalysisType {
        TECHNICAL,      // 技术分析
        FUNDAMENTAL,    // 基本面分析
        COMPREHENSIVE,  // 综合分析
        SENTIMENT,      // 情绪分析
        QUESTION        // 问答
    }

    public String getStockSymbol() { return stockSymbol; }
    public void setStockSymbol(String stockSymbol) { this.stockSymbol = stockSymbol; }

    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public AnalysisType getType() { return type; }
    public void setType(AnalysisType type) { this.type = type; }

    public TechnicalAnalysis getTechnicalAnalysis() { return technicalAnalysis; }
    public void setTechnicalAnalysis(TechnicalAnalysis technicalAnalysis) { this.technicalAnalysis = technicalAnalysis; }

    public FundamentalAnalysis getFundamentalAnalysis() { return fundamentalAnalysis; }
    public void setFundamentalAnalysis(FundamentalAnalysis fundamentalAnalysis) { this.fundamentalAnalysis = fundamentalAnalysis; }

    public Summary getSummary() { return summary; }
    public void setSummary(Summary summary) { this.summary = summary; }

    public SentimentAnalysis getSentimentAnalysis() { return sentimentAnalysis; }
    public void setSentimentAnalysis(SentimentAnalysis sentimentAnalysis) { this.sentimentAnalysis = sentimentAnalysis; }

    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    /**
     * 技术分析
     */
    public static class TechnicalAnalysis {
        private String trend;           // 趋势判断
        private String support;         // 支撑位
        private String resistance;      // 阻力位
        private List<String> indicators; // 指标分析 (MACD, RSI, 均线等)
        private String rating;          // 评级 (买入/持有/卖出)
        private int ratingScore;        // 评分 (0-100)
        private String detail;          // 详细分析文本
        private String riskLevel;       // 风险等级

        public String getTrend() { return trend; }
        public void setTrend(String trend) { this.trend = trend; }

        public String getSupport() { return support; }
        public void setSupport(String support) { this.support = support; }

        public String getResistance() { return resistance; }
        public void setResistance(String resistance) { this.resistance = resistance; }

        public List<String> getIndicators() { return indicators; }
        public void setIndicators(List<String> indicators) { this.indicators = indicators; }

        public String getRating() { return rating; }
        public void setRating(String rating) { this.rating = rating; }

        public int getRatingScore() { return ratingScore; }
        public void setRatingScore(int ratingScore) { this.ratingScore = ratingScore; }

        public String getDetail() { return detail; }
        public void setDetail(String detail) { this.detail = detail; }

        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    }

    /**
     * 基本面分析
     */
    public static class FundamentalAnalysis {
        private String revenueAnalysis;     // 营收分析
        private String profitAnalysis;      // 利润分析
        private String valuationAnalysis;   // 估值分析
        private String growthAnalysis;      // 增长分析
        private String competitivePosition; // 竞争地位
        private String detail;

        public String getRevenueAnalysis() { return revenueAnalysis; }
        public void setRevenueAnalysis(String revenueAnalysis) { this.revenueAnalysis = revenueAnalysis; }

        public String getProfitAnalysis() { return profitAnalysis; }
        public void setProfitAnalysis(String profitAnalysis) { this.profitAnalysis = profitAnalysis; }

        public String getValuationAnalysis() { return valuationAnalysis; }
        public void setValuationAnalysis(String valuationAnalysis) { this.valuationAnalysis = valuationAnalysis; }

        public String getGrowthAnalysis() { return growthAnalysis; }
        public void setGrowthAnalysis(String growthAnalysis) { this.growthAnalysis = growthAnalysis; }

        public String getCompetitivePosition() { return competitivePosition; }
        public void setCompetitivePosition(String competitivePosition) { this.competitivePosition = competitivePosition; }

        public String getDetail() { return detail; }
        public void setDetail(String detail) { this.detail = detail; }
    }

    /**
     * 综合总结
     */
    public static class Summary {
        private String conclusion;       // 总结结论
        private List<String> strengths;  // 优势
        private List<String> risks;      // 风险
        private String advice;           // 建议
        private int confidenceScore;     // 信心指数 (0-100)

        public String getConclusion() { return conclusion; }
        public void setConclusion(String conclusion) { this.conclusion = conclusion; }

        public List<String> getStrengths() { return strengths; }
        public void setStrengths(List<String> strengths) { this.strengths = strengths; }

        public List<String> getRisks() { return risks; }
        public void setRisks(List<String> risks) { this.risks = risks; }

        public String getAdvice() { return advice; }
        public void setAdvice(String advice) { this.advice = advice; }

        public int getConfidenceScore() { return confidenceScore; }
        public void setConfidenceScore(int confidenceScore) { this.confidenceScore = confidenceScore; }
    }

    /**
     * 情绪分析
     */
    public static class SentimentAnalysis {
        private String overall;          // 整体情绪 (正面/中性/负面)
        private double positiveScore;    // 正面评分
        private double negativeScore;    // 负面评分
        private double neutralScore;     // 中性评分
        private List<String> keyTopics;  // 关键话题
        private String detail;

        public String getOverall() { return overall; }
        public void setOverall(String overall) { this.overall = overall; }

        public double getPositiveScore() { return positiveScore; }
        public void setPositiveScore(double positiveScore) { this.positiveScore = positiveScore; }

        public double getNegativeScore() { return negativeScore; }
        public void setNegativeScore(double negativeScore) { this.negativeScore = negativeScore; }

        public double getNeutralScore() { return neutralScore; }
        public void setNeutralScore(double neutralScore) { this.neutralScore = neutralScore; }

        public List<String> getKeyTopics() { return keyTopics; }
        public void setKeyTopics(List<String> keyTopics) { this.keyTopics = keyTopics; }

        public String getDetail() { return detail; }
        public void setDetail(String detail) { this.detail = detail; }
    }
}
