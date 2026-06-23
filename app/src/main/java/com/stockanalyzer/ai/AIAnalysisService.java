package com.stockanalyzer.ai;

import android.util.Log;

import com.stockanalyzer.data.model.AIAnalysis;
import com.stockanalyzer.data.model.Stock;
import com.stockanalyzer.data.model.StockDetail;
import com.stockanalyzer.data.remote.dto.StockResponse;
import com.stockanalyzer.data.repository.AIRepository;
import com.stockanalyzer.data.repository.StockRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * AI 分析服务
 * 整合股票数据并构建AI分析请求
 */
public class AIAnalysisService {

    private static final String TAG = "AIAnalysisService";

    private final StockRepository stockRepository;
    private final AIRepository aiRepository;
    private static AIAnalysisService instance;

    private AIAnalysisService() {
        this.stockRepository = StockRepository.getInstance();
        this.aiRepository = AIRepository.getInstance();
    }

    public static synchronized AIAnalysisService getInstance() {
        if (instance == null) {
            instance = new AIAnalysisService();
        }
        return instance;
    }

    /**
     * 执行全面分析（整合所有数据后再进行AI分析）
     */
    public void performComprehensiveAnalysis(String symbol, String stockName,
                                              final AIRepository.RepositoryCallback<AIAnalysis> callback) {
        // 第一步：并行获取各种数据
        final StringBuilder priceData = new StringBuilder();
        final StringBuilder marketData = new StringBuilder();
        final StringBuilder financialData = new StringBuilder();
        final StringBuilder companyInfo = new StringBuilder();
        final StringBuilder newsData = new StringBuilder();

        final int[] completedRequests = {0};
        final int totalRequests = 5;

        StockRepository.RepositoryCallback<Void> checkCompletion = new StockRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                checkAndAnalyze();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "数据获取失败", e);
                checkAndAnalyze();
            }

            private void checkAndAnalyze() {
                synchronized (completedRequests) {
                    completedRequests[0]++;
                    if (completedRequests[0] >= totalRequests) {
                        // 所有数据获取完毕，进行AI分析
                        aiRepository.analyzeComprehensive(
                                symbol, stockName,
                                priceData.toString(), marketData.toString(),
                                financialData.toString(), companyInfo.toString(),
                                newsData.toString(),
                                callback
                        );
                    }
                }
            }
        };

        // 1. 获取实时报价
        stockRepository.getQuote(symbol, new StockRepository.RepositoryCallback<Stock>() {
            @Override
            public void onSuccess(Stock stock) {
                marketData.append(String.format(Locale.US,
                        "当前价格: ¥%.2f\n涨跌: $%.2f (%.2f%%)\n开盘: $%.2f\n最高: $%.2f\n最低: $%.2f\n昨收: $%.2f\n",
                        stock.getCurrentPrice(), stock.getChange(), stock.getChangePercent(),
                        stock.getOpen(), stock.getHigh(), stock.getLow(), stock.getPreviousClose()));
                checkCompletion.onSuccess(null);
            }

            @Override
            public void onError(Exception e) {
                marketData.append("无法获取实时报价\n");
                checkCompletion.onSuccess(null);
            }
        });

        // 2. 获取公司信息
        stockRepository.getCompanyProfile(symbol, new StockRepository.RepositoryCallback<StockDetail>() {
            @Override
            public void onSuccess(StockDetail detail) {
                String sym = detail.getSymbol() != null ? detail.getSymbol() : "";
                boolean isEtf = sym.matches("^[51]\\d{5}$");
                StringBuilder ciSb = new StringBuilder();
                ciSb.append(String.format(Locale.US,
                        "公司: %s\n行业: %s\n板块: %s\n国家: %s\n总市值: %s\n流通市值: %s\n52周高: %.2f\n52周低: %.2f\n",
                        detail.getName(), detail.getIndustry(), detail.getSector(),
                        detail.getCountry(),
                        detail.getMarketCap() != null ? detail.getMarketCap() : "N/A",
                        detail.getCirculatingMarketCap() != null ? detail.getCirculatingMarketCap() : "N/A",
                        detail.getWeek52High(), detail.getWeek52Low()));
                if (isEtf) {
                    if (detail.getEarningsYield() > 0) {
                        ciSb.append(String.format(Locale.US, "指数盈利: %.2f%%\n", detail.getEarningsYield()));
                    }
                    if (detail.getAmplitude() > 0) {
                        ciSb.append(String.format(Locale.US, "振幅: %.2f%%\n", detail.getAmplitude()));
                    }
                    if (detail.getTurnoverAmount() > 0) {
                        ciSb.append(String.format(Locale.US, "成交额: %s\n", formatAmount(detail.getTurnoverAmount())));
                    }
                } else {
                    ciSb.append(String.format(Locale.US, "市盈率: %.2f\n每股收益(EPS): %.2f\n",
                            detail.getPeRatio(), detail.getEps()));
                }
                companyInfo.append(ciSb.toString());
                checkCompletion.onSuccess(null);
            }

            @Override
            public void onError(Exception e) {
                companyInfo.append("无法获取公司信息\n");
                checkCompletion.onSuccess(null);
            }
        });

        // 3. 获取历史K线数据（60天）
        stockRepository.getHistoricalData(symbol, "D", 60,
                new StockRepository.RepositoryCallback<List<StockDetail.CandleData>>() {
            @Override
            public void onSuccess(List<StockDetail.CandleData> data) {
                StringBuilder sb = new StringBuilder();
                sb.append("最近60个交易日数据:\n");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                int count = Math.min(data.size(), 60);
                for (int i = data.size() - count; i < data.size(); i++) {
                    StockDetail.CandleData c = data.get(i);
                    String date = sdf.format(new Date(c.getTimestamp() * 1000));
                    sb.append(String.format(Locale.US,
                            "%s O:%.2f H:%.2f L:%.2f C:%.2f V:%d\n",
                            date, c.getOpen(), c.getHigh(), c.getLow(), c.getClose(), c.getVolume()));
                }
                priceData.append(sb.toString());
                checkCompletion.onSuccess(null);
            }

            @Override
            public void onError(Exception e) {
                priceData.append("无法获取历史数据\n");
                checkCompletion.onSuccess(null);
            }
        });

        // 4. 获取公司指标
        stockRepository.getMetrics(symbol, new StockRepository.RepositoryCallback<StockResponse.MetricsData>() {
            @Override
            public void onSuccess(StockResponse.MetricsData metrics) {
                financialData.append(String.format(Locale.US,
                        "市盈率(PE): %.2f\nBeta: %.2f\n股息率: %.2f%%\nEPS: %.2f\n52周最高: $%.2f\n52周最低: $%.2f\n流通股: %d\n",
                        metrics.peRatio != null ? metrics.peRatio : 0,
                        metrics.beta != null ? metrics.beta : 0,
                        metrics.dividendYield != null ? metrics.dividendYield * 100 : 0,
                        metrics.epsTtm != null ? metrics.epsTtm : 0,
                        metrics.week52High != null ? metrics.week52High : 0,
                        metrics.week52Low != null ? metrics.week52Low : 0,
                        metrics.sharesOutstanding != null ? metrics.sharesOutstanding : 0));
                checkCompletion.onSuccess(null);
            }

            @Override
            public void onError(Exception e) {
                financialData.append("无法获取财务指标\n");
                checkCompletion.onSuccess(null);
            }
        });

        // 5. 获取新闻
        stockRepository.getCompanyNews(symbol, 7,
                new StockRepository.RepositoryCallback<List<StockDetail.NewsItem>>() {
            @Override
            public void onSuccess(List<StockDetail.NewsItem> items) {
                SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.US);
                int count = Math.min(items.size(), 10);
                for (int i = 0; i < count; i++) {
                    StockDetail.NewsItem item = items.get(i);
                    String date = sdf.format(new Date(item.getDatetime() * 1000));
                    newsData.append(String.format(Locale.US,
                            "[%s] %s - %s\n摘要: %s\n\n",
                            date, item.getSource(), item.getHeadline(), item.getSummary()));
                }
                if (count == 0) {
                    newsData.append("暂无最新新闻\n");
                }
                checkCompletion.onSuccess(null);
            }

            @Override
            public void onError(Exception e) {
                newsData.append("无法获取新闻\n");
                checkCompletion.onSuccess(null);
            }
        });
    }

    /**
     * 执行技术分析（仅需要价格数据）
     */
    public void performTechnicalAnalysis(String symbol, String stockName,
                                          final AIRepository.RepositoryCallback<AIAnalysis> callback) {
        final StringBuilder priceData = new StringBuilder();
        final StringBuilder marketData = new StringBuilder();

        // 获取历史K线 + 实时报价
        stockRepository.getQuote(symbol, new StockRepository.RepositoryCallback<Stock>() {
            @Override
            public void onSuccess(Stock stock) {
                marketData.append(String.format(Locale.US,
                        "当前价格: ¥%.2f\n涨跌: $%.2f (%.2f%%)\n开盘: $%.2f\n最高: $%.2f\n最低: $%.2f\n昨收: $%.2f\n",
                        stock.getCurrentPrice(), stock.getChange(), stock.getChangePercent(),
                        stock.getOpen(), stock.getHigh(), stock.getLow(), stock.getPreviousClose()));

                // 获取历史数据
                stockRepository.getHistoricalData(symbol, "D", 90,
                        new StockRepository.RepositoryCallback<List<StockDetail.CandleData>>() {
                    @Override
                    public void onSuccess(List<StockDetail.CandleData> data) {
                        StringBuilder sb = new StringBuilder();
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                        int count = Math.min(data.size(), 90);
                        for (int i = data.size() - count; i < data.size(); i++) {
                            StockDetail.CandleData c = data.get(i);
                            String date = sdf.format(new Date(c.getTimestamp() * 1000));
                            sb.append(String.format(Locale.US,
                                    "%s O:%.2f H:%.2f L:%.2f C:%.2f V:%d\n",
                                    date, c.getOpen(), c.getHigh(), c.getLow(), c.getClose(), c.getVolume()));
                        }
                        priceData.append(sb.toString());

                        aiRepository.analyzeTechnical(symbol, stockName,
                                priceData.toString(), marketData.toString(), callback);
                    }

                    @Override
                    public void onError(Exception e) {
                        priceData.append("无法获取历史数据");
                        aiRepository.analyzeTechnical(symbol, stockName,
                                priceData.toString(), marketData.toString(), callback);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                marketData.append("无法获取实时报价");
                aiRepository.analyzeTechnical(symbol, stockName,
                        priceData.toString(), marketData.toString(), callback);
            }
        });
    }

    /**
     * 执行基本面分析
     */
    public void performFundamentalAnalysis(String symbol, String stockName,
                                            final AIRepository.RepositoryCallback<AIAnalysis> callback) {
        final StringBuilder financialData = new StringBuilder();
        final StringBuilder companyInfo = new StringBuilder();

        stockRepository.getCompanyProfile(symbol, new StockRepository.RepositoryCallback<StockDetail>() {
            @Override
            public void onSuccess(StockDetail detail) {
                String symF = detail.getSymbol() != null ? detail.getSymbol() : "";
                boolean isEtfF = symF.matches("^[51]\\d{5}$");
                StringBuilder ciF = new StringBuilder();
                ciF.append(String.format(Locale.US,
                        "公司: %s\n行业: %s\n板块: %s\n国家: %s\n市值: %s\n描述: %s\n",
                        detail.getName(), detail.getIndustry(), detail.getSector(),
                        detail.getCountry(), detail.getMarketCap(), detail.getDescription()));
                if (isEtfF) {
                    if (detail.getEarningsYield() > 0)
                        ciF.append(String.format(Locale.US, "指数盈利: %.2f%%\n", detail.getEarningsYield()));
                    if (detail.getAmplitude() > 0)
                        ciF.append(String.format(Locale.US, "振幅: %.2f%%\n", detail.getAmplitude()));
                    if (detail.getTurnoverAmount() > 0)
                        ciF.append(String.format(Locale.US, "成交额: %s\n", formatAmount(detail.getTurnoverAmount())));
                } else {
                    ciF.append(String.format(Locale.US, "市盈率: %.2f\n每股收益: %.2f\n",
                            detail.getPeRatio(), detail.getEps()));
                }
                companyInfo.append(ciF.toString());
            }

            @Override
            public void onError(Exception e) {
                companyInfo.append("无法获取公司信息\n");
            }
        });

        stockRepository.getMetrics(symbol, new StockRepository.RepositoryCallback<StockResponse.MetricsData>() {
            @Override
            public void onSuccess(StockResponse.MetricsData metrics) {
                financialData.append(String.format(Locale.US,
                        "市盈率(PE): %.2f\nBeta: %.2f\n股息率: %.2f%%\nEPS: %.2f\n52周最高: $%.2f\n52周最低: $%.2f\n流通股: %d\n",
                        metrics.peRatio != null ? metrics.peRatio : 0,
                        metrics.beta != null ? metrics.beta : 0,
                        metrics.dividendYield != null ? metrics.dividendYield * 100 : 0,
                        metrics.epsTtm != null ? metrics.epsTtm : 0,
                        metrics.week52High != null ? metrics.week52High : 0,
                        metrics.week52Low != null ? metrics.week52Low : 0,
                        metrics.sharesOutstanding != null ? metrics.sharesOutstanding : 0));
            }

            @Override
            public void onError(Exception e) {
                financialData.append("无法获取财务指标\n");
            }
        });

        // 短暂延迟确保数据获取完毕
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            aiRepository.analyzeFundamental(symbol, stockName,
                    financialData.toString(), companyInfo.toString(), callback);
        }, 2000);
    }

    /**
     * 向AI提问
     */
    public void askQuestion(String symbol, String stockName,
                             String question,
                             final AIRepository.RepositoryCallback<String> callback) {
        // 获取当前行情数据作为上下文
        final StringBuilder contextData = new StringBuilder();

        stockRepository.getQuote(symbol, new StockRepository.RepositoryCallback<Stock>() {
            @Override
            public void onSuccess(Stock stock) {
                contextData.append(String.format(Locale.US,
                        "当前价格: ¥%.2f\n涨跌: $%.2f (%.2f%%)\n开盘: $%.2f\n最高: $%.2f\n最低: $%.2f\n昨收: $%.2f\n",
                        stock.getCurrentPrice(), stock.getChange(), stock.getChangePercent(),
                        stock.getOpen(), stock.getHigh(), stock.getLow(), stock.getPreviousClose()));
                aiRepository.askQuestion(symbol, stockName, question,
                        contextData.toString(), callback);
            }

            @Override
            public void onError(Exception e) {
                aiRepository.askQuestion(symbol, stockName, question,
                        "无法获取实时数据", callback);
            }
        });
    }

    /** 格式化成交额（元 → 万/亿） */
    private static String formatAmount(double amount) {
        if (amount >= 100_000_000) return String.format(Locale.US, "%.2f亿", amount / 100_000_000);
        if (amount >= 10_000) return String.format(Locale.US, "%.2f万", amount / 10_000);
        return String.format(Locale.US, "%.0f元", amount);
    }
}
