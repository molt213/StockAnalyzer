package com.stockanalyzer.ui.ai;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.stockanalyzer.R;
import com.stockanalyzer.StockAnalyzerApp;
import com.stockanalyzer.adapter.AnalysisHistoryAdapter;
import com.stockanalyzer.ai.AIAnalysisService;
import com.stockanalyzer.data.local.AIAnalysisEntity;
import com.stockanalyzer.data.model.AIAnalysis;
import com.stockanalyzer.data.repository.AIRepository;
import com.stockanalyzer.util.NetworkUtils;

import java.util.List;

/**
 * AI 分析 Fragment
 * 提供股票的技术分析、基本面分析和综合分析功能
 */
public class AIAnalysisFragment extends Fragment {

    private EditText symbolInput;
    private MaterialButton btnTechnical, btnFundamental, btnComprehensive;
    private MaterialButton btnAsk;
    private EditText questionInput;

    private View welcomeLayout, loadingLayout;
    private TextView loadingText, progressText;
    private MaterialCardView resultCard, questionCard;
    private TextView ratingText, confidenceText, trendText, riskText, sentimentText;
    private TextView conclusionText, strengthsText, risksText, adviceText;
    private TextView analysisDetailText;
    private TextView answerText;
    private ProgressBar analysisProgress;
    private RecyclerView historyRecycler;

    private AIAnalysisService analysisService;
    private AIRepository aiRepository;
    private AnalysisHistoryAdapter historyAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai_analysis, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        analysisService = AIAnalysisService.getInstance();
        aiRepository = AIRepository.getInstance();

        initViews(view);
        setupButtons();
        setupHistoryList();
        loadHistory();
    }

    private void initViews(View view) {
        symbolInput = view.findViewById(R.id.symbol_input);
        btnTechnical = view.findViewById(R.id.btn_technical);
        btnFundamental = view.findViewById(R.id.btn_fundamental);
        btnComprehensive = view.findViewById(R.id.btn_comprehensive);
        btnAsk = view.findViewById(R.id.btn_ask);
        questionInput = view.findViewById(R.id.question_input);

        welcomeLayout = view.findViewById(R.id.welcome_layout);
        loadingLayout = view.findViewById(R.id.loading_layout);
        loadingText = view.findViewById(R.id.loading_text);
        analysisProgress = view.findViewById(R.id.analysis_progress);
        resultCard = view.findViewById(R.id.result_card);
        questionCard = view.findViewById(R.id.question_card);

        ratingText = view.findViewById(R.id.rating_text);
        confidenceText = view.findViewById(R.id.confidence_text);
        trendText = view.findViewById(R.id.trend_text);
        riskText = view.findViewById(R.id.risk_text);
        sentimentText = view.findViewById(R.id.sentiment_text);
        conclusionText = view.findViewById(R.id.conclusion_text);
        strengthsText = view.findViewById(R.id.strengths_text);
        risksText = view.findViewById(R.id.risks_text);
        adviceText = view.findViewById(R.id.advice_text);
        analysisDetailText = view.findViewById(R.id.analysis_detail_text);
        answerText = view.findViewById(R.id.answer_text);

        historyRecycler = view.findViewById(R.id.history_recycler);
    }

    private void setupButtons() {
        View.OnClickListener analysisListener = v -> {
            String symbol = symbolInput.getText().toString().trim().toUpperCase();
            if (symbol.isEmpty()) {
                symbolInput.setError("请输入股票代码");
                return;
            }
            if (!StockAnalyzerApp.getInstance().isAiConfigured()) {
                Toast.makeText(requireContext(), R.string.ai_not_configured, Toast.LENGTH_LONG).show();
                return;
            }
            if (!NetworkUtils.checkNetworkWithToast()) return;

            int id = v.getId();
            if (id == R.id.btn_technical) {
                performAnalysis(symbol, "技术分析", AIAnalysis.AnalysisType.TECHNICAL);
            } else if (id == R.id.btn_fundamental) {
                performAnalysis(symbol, "基本面分析", AIAnalysis.AnalysisType.FUNDAMENTAL);
            } else if (id == R.id.btn_comprehensive) {
                performAnalysis(symbol, "全面综合分析", AIAnalysis.AnalysisType.COMPREHENSIVE);
            }
        };

        btnTechnical.setOnClickListener(analysisListener);
        btnFundamental.setOnClickListener(analysisListener);
        btnComprehensive.setOnClickListener(analysisListener);

        // 问答按钮
        btnAsk.setOnClickListener(v -> {
            String symbol = symbolInput.getText().toString().trim().toUpperCase();
            String question = questionInput.getText().toString().trim();

            if (symbol.isEmpty()) {
                symbolInput.setError("请输入股票代码");
                return;
            }
            if (question.isEmpty()) {
                questionInput.setError("请输入问题");
                return;
            }
            if (!StockAnalyzerApp.getInstance().isAiConfigured()) {
                Toast.makeText(requireContext(), R.string.ai_not_configured, Toast.LENGTH_LONG).show();
                return;
            }
            if (!NetworkUtils.checkNetworkWithToast()) return;

            askQuestion(symbol, question);
        });
    }

    private void performAnalysis(String symbol, String typeName, AIAnalysis.AnalysisType analysisType) {
        showLoading(typeName + "中...");

        AIRepository.RepositoryCallback<AIAnalysis> callback = new AIRepository.RepositoryCallback<AIAnalysis>() {
            @Override
            public void onSuccess(AIAnalysis analysis) {
                requireActivity().runOnUiThread(() -> {
                    showResult(analysis);
                    loadHistory();
                });
            }

            @Override
            public void onError(Exception e) {
                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(requireContext(),
                            "分析失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        };

        switch (analysisType) {
            case TECHNICAL:
                analysisService.performTechnicalAnalysis(symbol, symbol, callback);
                break;
            case FUNDAMENTAL:
                analysisService.performFundamentalAnalysis(symbol, symbol, callback);
                break;
            case COMPREHENSIVE:
                analysisService.performComprehensiveAnalysis(symbol, symbol, callback);
                break;
            default:
                analysisService.performComprehensiveAnalysis(symbol, symbol, callback);
                break;
        }
    }

    private void askQuestion(String symbol, String question) {
        showLoading("思考中...");
        answerText.setVisibility(View.GONE);

        analysisService.askQuestion(symbol, symbol, question,
                new AIRepository.RepositoryCallback<String>() {
            @Override
            public void onSuccess(String answer) {
                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    answerText.setText(answer);
                    answerText.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onError(Exception e) {
                requireActivity().runOnUiThread(() -> {
                    hideLoading();
                    answerText.setText("获取回答失败: " + e.getMessage());
                    answerText.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void showLoading(String text) {
        welcomeLayout.setVisibility(View.GONE);
        resultCard.setVisibility(View.GONE);
        loadingLayout.setVisibility(View.VISIBLE);
        loadingText.setText(text);
        analysisProgress.setIndeterminate(true);
    }

    private void hideLoading() {
        loadingLayout.setVisibility(View.GONE);
    }

    private void showResult(AIAnalysis analysis) {
        hideLoading();
        resultCard.setVisibility(View.VISIBLE);

        // 评级和信心指数
        if (analysis.getTechnicalAnalysis() != null) {
            String rating = analysis.getTechnicalAnalysis().getRating();
            ratingText.setText(!TextUtils.isEmpty(rating) ? rating : "N/A");

            int score = analysis.getTechnicalAnalysis().getRatingScore();
            confidenceText.setText(score > 0 ? score + "/100" : "N/A");
            confidenceText.setTextColor(score >= 70 ? requireContext().getColor(R.color.stock_up) :
                    score >= 40 ? requireContext().getColor(R.color.warning) : requireContext().getColor(R.color.stock_down));
        }

        if (analysis.getSummary() != null) {
            // 趋势
            String trend = analysis.getTechnicalAnalysis() != null
                    ? analysis.getTechnicalAnalysis().getTrend() : "";
            trendText.setText(!TextUtils.isEmpty(trend) ? trend : "N/A");

            // 风险
            riskText.setText(!TextUtils.isEmpty(analysis.getRiskLevel())
                    ? analysis.getRiskLevel() : "N/A");

            // 市场情绪（从原始响应中提取）
            String raw = analysis.getRawResponse();
            String sentiment = extractSentiment(raw);
            sentimentText.setText(!TextUtils.isEmpty(sentiment) ? sentiment : "N/A");
            if (sentiment.contains("乐观")) sentimentText.setTextColor(requireContext().getColor(R.color.stock_up));
            else if (sentiment.contains("悲观")) sentimentText.setTextColor(requireContext().getColor(R.color.stock_down));
            else sentimentText.setTextColor(requireContext().getColor(R.color.warning));

            // 结论
            String conclusion = analysis.getSummary().getConclusion();
            conclusionText.setText(!TextUtils.isEmpty(conclusion) ? conclusion : "N/A");

            // 优势
            List<String> strengths = analysis.getSummary().getStrengths();
            strengthsText.setText(formatList(strengths));

            // 风险
            List<String> risks = analysis.getSummary().getRisks();
            risksText.setText(formatList(risks));

            // 建议
            String advice = analysis.getSummary().getAdvice();
            adviceText.setText(!TextUtils.isEmpty(advice) ? advice : "请结合自身风险承受能力决策");

            // 完整分析详情
            if (!TextUtils.isEmpty(raw)) {
                analysisDetailText.setText(raw);
                analysisDetailText.setVisibility(View.VISIBLE);
            }
        }

        // 显示问答卡片
        questionCard.setVisibility(View.VISIBLE);
    }

    /** 从 AI 原始响应中提取市场情绪 */
    private String extractSentiment(String text) {
        if (TextUtils.isEmpty(text)) return "";
        // 尝试匹配 "市场情绪" 后面的内容
        String[] keys = {"市场情绪", "情绪", "市场看法"};
        for (String key : keys) {
            int idx = text.indexOf(key);
            if (idx >= 0) {
                int colon = text.indexOf(":", idx);
                if (colon < 0) colon = text.indexOf("：", idx);
                if (colon >= 0 && colon - idx < 10) {
                    int end = text.indexOf("\n", colon + 1);
                    if (end < 0) end = Math.min(colon + 30, text.length());
                    String value = text.substring(colon + 1, end).trim();
                    if (!value.isEmpty() && value.length() < 20) return value;
                }
            }
        }
        // 全文搜索情绪关键词
        if (text.contains("乐观")) return "乐观";
        if (text.contains("悲观")) return "悲观";
        if (text.contains("中性")) return "中性";
        return "";
    }

    private String formatList(List<String> items) {
        if (items == null || items.isEmpty()) return "暂无数据";
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            sb.append("• ").append(item).append("\n");
        }
        return sb.toString().trim();
    }

    private void setupHistoryList() {
        historyAdapter = new AnalysisHistoryAdapter();
        historyAdapter.setOnHistoryClickListener(entity -> {
            // 点击历史记录，填充股票代码并展示分析结果
            symbolInput.setText(entity.getStockSymbol());

            AIAnalysis analysis = aiRepository.getAnalysisFromEntity(entity);
            if (analysis != null) {
                showResult(analysis);
            } else {
                Toast.makeText(requireContext(),
                        "无法读取分析记录", Toast.LENGTH_SHORT).show();
            }
        });
        historyRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        historyRecycler.setAdapter(historyAdapter);
    }

    private void loadHistory() {
        List<AIAnalysisEntity> history = aiRepository.getAnalysisHistory();
        historyAdapter.submitList(history);
    }
}
