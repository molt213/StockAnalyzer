package com.stockanalyzer.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.stockanalyzer.StockAnalyzerApp;
import com.stockanalyzer.data.local.AIAnalysisDao;
import com.stockanalyzer.data.local.AIAnalysisEntity;
import com.stockanalyzer.data.local.AppDatabase;
import com.stockanalyzer.data.model.AIAnalysis;
import com.stockanalyzer.data.remote.AiApiService;
import com.stockanalyzer.data.remote.RetrofitClient;
import com.stockanalyzer.data.remote.dto.AIRequest;

import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * AI 分析仓库
 * 管理AI分析的API调用和历史记录
 * 使用 DeepSeek API (OpenAI 兼容格式)
 */
public class AIRepository {

    private static final String TAG = "AIRepository";

    private final AiApiService apiService;
    private final AIAnalysisDao analysisDao;
    private final Gson gson;
    private static AIRepository instance;

    private AIRepository() {
        this.apiService = RetrofitClient.getAiApiService();
        this.analysisDao = StockAnalyzerApp.getInstance().getDatabase().aiAnalysisDao();
        this.gson = new Gson();
    }

    public static synchronized AIRepository getInstance() {
        if (instance == null) {
            instance = new AIRepository();
        }
        return instance;
    }

    /**
     * 执行技术分析
     */
    public void analyzeTechnical(String symbol, String stockName,
                                  String priceData, String marketData,
                                  final RepositoryCallback<AIAnalysis> callback) {
        String systemPrompt = "你是一位专业的股票技术分析师。请基于提供的价格数据和市场信息，" +
                "对股票进行全面的技术面分析。请用中文回复。\n\n" +
                "请严格按以下结构分析：\n" +
                "1. 趋势判断（明确说明：上升/下降/横盘震荡，并说明判断依据）\n" +
                "2. 关键支撑位和阻力位（给出具体的价格点位）\n" +
                "3. 技术指标分析（MACD、RSI、均线系统、KDJ等指标的综合解读）\n" +
                "4. 成交量分析（量价关系是否配合）\n" +
                "5. 市场情绪（根据技术形态判断市场情绪）\n" +
                "6. 风险评估（当前技术面的主要风险点）\n" +
                "7. 综合评级和评分（0-100分）\n" +
                "8. 操作建议（基于技术面的具体建议）\n\n" +
                "请以纯文本回复（不要加粗/斜体/Markdown），用清晰的段落和标题，每个部分用标题标注。";

        String userMessage = fmt(
                "请对股票 %s (%s) 进行技术分析。\n\n" +
                "=== 价格数据 ===\n%s\n\n=== 市场数据 ===\n%s\n\n" +
                "请给出详细的技术面分析和投资建议。",
                symbol, stockName, priceData, marketData);

        sendAnalysisRequest(symbol, stockName, AIAnalysis.AnalysisType.TECHNICAL,
                systemPrompt, userMessage, callback);
    }

    /**
     * 执行基本面分析
     */
    public void analyzeFundamental(String symbol, String stockName,
                                    String financialData, String companyInfo,
                                    final RepositoryCallback<AIAnalysis> callback) {
        String systemPrompt = "你是一位专业的股票基本面分析师。请基于提供的财务数据和公司信息，" +
                "对股票进行全面的基本面分析。请用中文回复。\n\n" +
                "请严格按以下结构分析：\n" +
                "1. 财务健康度分析（营收、利润、资产负债状况）\n" +
                "2. 估值分析（PE、PB等估值指标是否合理）\n" +
                "3. 增长潜力评估（成长性分析）\n" +
                "4. 行业竞争地位（公司在行业中的位置）\n" +
                "5. 竞争优势（护城河分析）\n" +
                "6. 主要风险（基本面面临的主要风险）\n" +
                "7. 市场情绪（市场对公司的整体看法）\n" +
                "8. 综合投资建议（基于基本面的操作建议）\n\n" +
                "请以纯文本回复（不要加粗/斜体/Markdown），用清晰的段落和标题，包含具体的财务数据分析和结论。";

        String userMessage = fmt(
                "请对股票 %s (%s) 进行基本面分析。\n\n" +
                "=== 财务数据 ===\n%s\n\n=== 公司信息 ===\n%s\n\n" +
                "请给出详细的基本面分析和投资建议。",
                symbol, stockName, financialData, companyInfo);

        sendAnalysisRequest(symbol, stockName, AIAnalysis.AnalysisType.FUNDAMENTAL,
                systemPrompt, userMessage, callback);
    }

    /**
     * 执行综合分析（技术面 + 基本面）
     */
    public void analyzeComprehensive(String symbol, String stockName,
                                      String priceData, String marketData,
                                      String financialData, String companyInfo,
                                      String newsData,
                                      final RepositoryCallback<AIAnalysis> callback) {
        String systemPrompt = "你是一位顶级的股票投资分析师，擅长技术分析和基本面分析。\n" +
                "请基于提供的全面数据，对股票进行综合的投资分析。请用中文回复。\n\n" +
                "请严格按以下维度进行全面的分析：\n" +
                "1. 趋势判断（明确说明当前趋势：上升/下降/横盘震荡，以及关键支撑位和阻力位）\n" +
                "2. 技术面分析（MACD、RSI、均线系统等技术指标分析）\n" +
                "3. 基本面分析（财务健康度、估值水平、增长潜力）\n" +
                "4. 市场情绪（根据量价关系和新闻判断市场情绪：乐观/中性/悲观）\n" +
                "5. 行业前景和竞争格局\n" +
                "6. 风险等级（低风险/中等风险/高风险/极高风险）\n" +
                "7. 主要优势（列出3-5个具体优势）\n" +
                "8. 主要风险（列出3-5个具体风险）\n" +
                "9. 综合评级和评分（0-100分，并给出评级：强烈买入/买入/持有/卖出/强烈卖出）\n" +
                "10. 具体的投资建议（明确的操作建议和理由）\n" +
                "11. 信心指数（0-100，说明您对这个分析的把握程度）\n\n" +
                "请确保分析深入、客观，数据支撑结论，并给出明确的投资建议。\n" +
                "请以纯文本回复，不要使用任何加粗、斜体或 Markdown 格式。";

        String userMessage = fmt(
                "请对股票 %s (%s) 进行全面综合分析。\n\n" +
                "=== 价格数据 ===\n%s\n\n=== 市场数据 ===\n%s\n\n" +
                "=== 财务数据 ===\n%s\n\n=== 公司信息 ===\n%s\n\n" +
                "=== 最新新闻 ===\n%s\n\n" +
                "请给出全面、深入的投资分析报告。",
                symbol, stockName, priceData, marketData,
                financialData, companyInfo, newsData);

        sendAnalysisRequest(symbol, stockName, AIAnalysis.AnalysisType.COMPREHENSIVE,
                systemPrompt, userMessage, callback);
    }

    /**
     * 向AI提问关于股票的问题
     */
    public void askQuestion(String symbol, String stockName,
                             String question, String contextData,
                             final RepositoryCallback<String> callback) {
        String systemPrompt = "你是一位专业的股票投资顾问。请根据用户的问题和提供的股票数据，" +
                "给出专业、准确的回答。请用中文回复。";

        String userMessage = fmt(
                "股票: %s (%s)\n\n" +
                "=== 相关数据 ===\n%s\n\n" +
                "用户问题: %s\n\n" +
                "请专业地回答用户的问题。",
                symbol, stockName, contextData, question);

        // DeepSeek 格式：system 和 user 都是 messages
        List<AIRequest.ChatMessage> messages = new ArrayList<>();
        messages.add(new AIRequest.ChatMessage("system", systemPrompt));
        messages.add(new AIRequest.ChatMessage("user", userMessage));

        sendDeepSeekRequest(messages, new RepositoryCallback<String>() {
            @Override
            public void onSuccess(String text) {
                callback.onSuccess(text);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    /** AI分析缓存有效时间（6小时） */
    private static final long ANALYSIS_CACHE_TTL_MS = 6 * 60 * 60 * 1000;

    /**
     * 通用的AI分析请求（带本地缓存，避免重复调用浪费资源）
     */
    private void sendAnalysisRequest(String symbol, String stockName,
                                      AIAnalysis.AnalysisType analysisType,
                                      String systemPrompt, String userMessage,
                                      final RepositoryCallback<AIAnalysis> callback) {
        // 1. 检查本地缓存
        try {
            AIAnalysisEntity cached = analysisDao.getLatestAnalysis(
                    symbol.toUpperCase(), analysisType.name());
            if (cached != null) {
                long age = System.currentTimeMillis() - cached.getCreatedAt();
                if (age < ANALYSIS_CACHE_TTL_MS) {
                    // 缓存有效，直接返回
                    AIAnalysis analysis = entityToAnalysis(cached);
                    if (analysis != null) {
                        Log.d(TAG, "命中AI分析缓存: " + symbol + " " + analysisType
                                + " (" + (age / 1000 / 60) + "分钟前)");
                        callback.onSuccess(analysis);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "查询AI缓存失败", e);
        }

        // 2. 缓存未命中，调用 API
        List<AIRequest.ChatMessage> messages = new ArrayList<>();
        messages.add(new AIRequest.ChatMessage("system", systemPrompt));
        messages.add(new AIRequest.ChatMessage("user", userMessage));

        sendDeepSeekRequest(messages, new RepositoryCallback<String>() {
            @Override
            public void onSuccess(String text) {
                AIAnalysis analysis = new AIAnalysis();
                analysis.setStockSymbol(symbol);
                analysis.setStockName(stockName);
                analysis.setTimestamp(System.currentTimeMillis());
                analysis.setType(analysisType);
                analysis.setRawResponse(text);

                // 解析AI响应中的结构化数据
                parseAnalysisResponse(analysis, text);

                // 保存到历史记录
                saveAnalysisToHistory(analysis);

                callback.onSuccess(analysis);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * 发送 DeepSeek Chat Completion 请求
     */
    private void sendDeepSeekRequest(List<AIRequest.ChatMessage> messages,
                                      final RepositoryCallback<String> callback) {
        String apiKey = StockAnalyzerApp.getInstance().getAiApiKey();
        String model = StockAnalyzerApp.getInstance().getAiModel();

        AIRequest.DeepSeekRequest request = new AIRequest.DeepSeekRequest(model, messages);

        apiService.sendChatCompletion("Bearer " + apiKey, "application/json", request)
                .enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call,
                                    @NonNull Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        AIRequest.DeepSeekResponse body = gson.fromJson(json, AIRequest.DeepSeekResponse.class);

                        // 检查 API 错误
                        if (body.error != null) {
                            callback.onError(new Exception("API错误: " + body.error.message));
                            return;
                        }

                        // 从 choices 中提取文本
                        if (body.choices != null && !body.choices.isEmpty()
                                && body.choices.get(0).message != null) {
                            String text = body.choices.get(0).message.content;
                            callback.onSuccess(text != null ? text : "");
                        } else {
                            callback.onError(new Exception("AI响应格式异常"));
                        }
                    } else {
                        String errMsg = "AI请求失败: " + response.code();
                        try {
                            if (response.errorBody() != null) {
                                String errBody = response.errorBody().string();
                                errMsg += " - " + errBody;
                            }
                        } catch (Exception ignored) {}
                        callback.onError(new Exception(errMsg));
                    }
                } catch (Exception e) {
                    callback.onError(new Exception("解析AI响应失败: " + e.getMessage()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call,
                                   @NonNull Throwable t) {
                callback.onError(new Exception("网络请求失败: " + t.getMessage()));
            }
        });
    }

    /**
     * 解析AI响应中的结构化数据
     * 从自然语言响应中提取关键信息
     */
    private void parseAnalysisResponse(AIAnalysis analysis, String text) {
        if (text == null || text.isEmpty()) return;

        AIAnalysis.Summary summary = new AIAnalysis.Summary();
        summary.setConclusion(extractSection(text, "综合评级", "投资建议", "结论"));

        // 提取评级
        String rating = extractValue(text, "评级", "评", "评分");
        summary.setAdvice(extractSection(text, "投资建议", "风险", "信心指数"));

        // 提取信心指数
        int confidence = extractScore(text, "信心指数", "信心");
        summary.setConfidenceScore(confidence > 0 ? confidence : 70);

        // 提取风险等级
        String risk = extractValue(text, "风险等级", "风险评估", "风险");
        analysis.setRiskLevel(risk);

        // 提取优势列表
        List<String> strengths = extractList(text, "优势", "优点", "亮点");
        summary.setStrengths(strengths.isEmpty() ? List.of("数据不足，无法提取") : strengths);

        // 提取风险列表
        List<String> risks = extractList(text, "风险", "劣势", "注意事项");
        summary.setRisks(risks.isEmpty() ? List.of("数据不足，无法提取") : risks);

        analysis.setSummary(summary);

        // 技术分析
        AIAnalysis.TechnicalAnalysis ta = new AIAnalysis.TechnicalAnalysis();
        ta.setTrend(extractValue(text, "趋势", "走势", "趋势判断"));
        ta.setSupport(extractValue(text, "支撑位", "支撑", "关键支撑"));
        ta.setResistance(extractValue(text, "阻力位", "阻力", "关键阻力"));
        ta.setRiskLevel(risk);
        ta.setRating(rating);
        ta.setRatingScore(extractScore(text, "评分", "综合评分", "打分"));
        ta.setDetail(extractSection(text, "技术分析", "基本面分析", "综合分析"));

        List<String> indicators = extractList(text, "MACD", "RSI", "均线", "KDJ", "布林带");
        if (!indicators.isEmpty()) {
            ta.setIndicators(indicators);
        }
        analysis.setTechnicalAnalysis(ta);

        // 基本面分析
        AIAnalysis.FundamentalAnalysis fa = new AIAnalysis.FundamentalAnalysis();
        fa.setDetail(extractSection(text, "基本面分析", "市场情绪", "综合分析"));
        analysis.setFundamentalAnalysis(fa);

        // 情绪分析
        AIAnalysis.SentimentAnalysis sa = new AIAnalysis.SentimentAnalysis();
        sa.setDetail(extractSection(text, "市场情绪", "新闻影响", "风险"));
        analysis.setSentimentAnalysis(sa);

        // 从评级中提取评分
        if (ta.getRatingScore() == 0) {
            int score = inferScoreFromRating(rating);
            ta.setRatingScore(score);
        }
    }

    // ============ 文本解析方法 ============

    private String extractSection(String text, String... headers) {
        for (String header : headers) {
            int idx = text.indexOf(header);
            if (idx >= 0) {
                int end = text.indexOf("\n\n", idx + header.length());
                if (end > idx) {
                    return text.substring(idx, end).trim();
                }
                return text.substring(idx, Math.min(idx + 200, text.length())).trim();
            }
        }
        return "";
    }

    private String extractValue(String text, String... keys) {
        for (String key : keys) {
            int idx = text.indexOf(key);
            if (idx >= 0) {
                int colonIdx = text.indexOf(":", idx);
                int colonIdxCn = text.indexOf("：", idx);
                int colon = colonIdx >= 0 ? colonIdx : colonIdxCn;
                if (colon >= 0 && colon - idx < 20) {
                    int end = text.indexOf("\n", colon + 1);
                    if (end > colon) {
                        String value = text.substring(colon + 1, end).trim();
                        if (value.length() < 100 && !value.isEmpty()) {
                            return value;
                        }
                    }
                }
            }
        }
        return "";
    }

    private int extractScore(String text, String... keys) {
        for (String key : keys) {
            int idx = text.indexOf(key);
            while (idx >= 0) {
                int colonIdx = text.indexOf(":", idx);
                if (colonIdx < 0) colonIdx = text.indexOf("：", idx);
                if (colonIdx >= 0 && colonIdx - idx < 15) {
                    String after = text.substring(colonIdx + 1, Math.min(colonIdx + 20, text.length()));
                    String numStr = after.replaceAll("[^0-9/分百].*$", "").trim();
                    if (numStr.contains("/")) {
                        String[] parts = numStr.split("/");
                        try {
                            return Integer.parseInt(parts[0].trim());
                        } catch (NumberFormatException ignored) {}
                    } else if (numStr.contains("分")) {
                        try {
                            return Integer.parseInt(numStr.replace("分", "").trim());
                        } catch (NumberFormatException ignored) {}
                    } else {
                        try {
                            return Integer.parseInt(numStr.trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }
                idx = text.indexOf(key, idx + 1);
            }
        }
        return 0;
    }

    private List<String> extractList(String text, String... keys) {
        List<String> items = new ArrayList<>();
        for (String key : keys) {
            int idx = text.indexOf(key);
            if (idx >= 0) {
                int end = text.indexOf("\n\n", idx);
                if (end < 0) end = Math.min(idx + 500, text.length());

                String section = text.substring(idx, end);
                String[] lines = section.split("\n");
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.matches("^[\\d\\-\\*•·]\\s*.+") && trimmed.length() > 3) {
                        String item = trimmed.replaceAll("^[\\d\\-\\*•·]\\s*", "").trim();
                        if (item.length() > 3 && !items.contains(item)) {
                            items.add(item);
                        }
                    }
                }
            }
        }
        return items;
    }

    private int inferScoreFromRating(String rating) {
        if (rating == null) return 50;
        String r = rating.toLowerCase();
        if (r.contains("强烈买入") || r.contains("强力买入")) return 90;
        if (r.contains("买入") || r.contains("增持") || r.contains("买")) return 75;
        if (r.contains("持有") || r.contains("中性") || r.contains("观望")) return 50;
        if (r.contains("减持") || r.contains("卖出") || r.contains("卖")) return 25;
        if (r.contains("强烈卖出")) return 10;
        return 50;
    }

    /**
     * 保存AI分析到历史记录
     */
    private void saveAnalysisToHistory(AIAnalysis analysis) {
        try {
            AIAnalysisEntity entity = new AIAnalysisEntity(
                    analysis.getStockSymbol(),
                    analysis.getStockName(),
                    analysis.getType().name(),
                    analysis.getTimestamp()
            );
            entity.setRawResponse(analysis.getRawResponse());

            if (analysis.getSummary() != null) {
                entity.setSummaryText(analysis.getSummary().getConclusion());
                entity.setAdvice(analysis.getSummary().getAdvice());
                entity.setConfidenceScore(analysis.getSummary().getConfidenceScore());
            }
            if (analysis.getTechnicalAnalysis() != null) {
                entity.setRating(analysis.getTechnicalAnalysis().getRating());
                entity.setRatingScore(analysis.getTechnicalAnalysis().getRatingScore());
                entity.setRiskLevel(analysis.getTechnicalAnalysis().getRiskLevel());
            }

            analysisDao.insertAnalysis(entity);
        } catch (Exception e) {
            Log.e(TAG, "保存分析历史失败", e);
        }
    }

    public List<AIAnalysisEntity> getAnalysisHistory() {
        return analysisDao.getAllAnalysisHistory();
    }

    public List<AIAnalysisEntity> getAnalysisBySymbol(String symbol) {
        return analysisDao.getAnalysisBySymbol(symbol);
    }

    public void deleteAnalysis(long id) {
        analysisDao.deleteAnalysisById(id);
    }

    /**
     * 将数据库实体转换为 AIAnalysis 对象
     */
    private AIAnalysis entityToAnalysis(AIAnalysisEntity entity) {
        try {
            AIAnalysis analysis = new AIAnalysis();
            analysis.setStockSymbol(entity.getStockSymbol());
            analysis.setStockName(entity.getStockName());
            analysis.setTimestamp(entity.getCreatedAt());
            analysis.setType(AIAnalysis.AnalysisType.valueOf(entity.getAnalysisType()));
            analysis.setRawResponse(entity.getRawResponse());

            AIAnalysis.Summary summary = new AIAnalysis.Summary();
            summary.setConclusion(entity.getSummaryText());
            summary.setAdvice(entity.getAdvice());
            summary.setConfidenceScore(entity.getConfidenceScore());
            analysis.setSummary(summary);

            AIAnalysis.TechnicalAnalysis ta = new AIAnalysis.TechnicalAnalysis();
            ta.setRating(entity.getRating());
            ta.setRatingScore(entity.getRatingScore());
            ta.setRiskLevel(entity.getRiskLevel());
            analysis.setTechnicalAnalysis(ta);

            analysis.setRiskLevel(entity.getRiskLevel());
            return analysis;
        } catch (Exception e) {
            Log.w(TAG, "实体转换失败", e);
            return null;
        }
    }

    /**
     * 将历史记录实体完整转换为 AIAnalysis 对象（含原始响应文本解析）
     * 供 UI 层点击历史记录时重新展示分析结果
     */
    public AIAnalysis getAnalysisFromEntity(AIAnalysisEntity entity) {
        AIAnalysis analysis = entityToAnalysis(entity);
        if (analysis != null && entity.getRawResponse() != null) {
            parseAnalysisResponse(analysis, entity.getRawResponse());
        }
        return analysis;
    }

    public void clearAllHistory() {
        analysisDao.deleteAll();
    }

    public interface RepositoryCallback<T> {
        void onSuccess(T data);
        void onError(Exception e);
    }

    private static String fmt(String format, Object... args) {
        return String.format(java.util.Locale.US, format, args);
    }
}
