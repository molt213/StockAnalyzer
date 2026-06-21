package com.stockanalyzer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.stockanalyzer.R;
import com.stockanalyzer.data.local.AIAnalysisEntity;
import com.stockanalyzer.util.FormatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AI分析历史记录适配器
 */
public class AnalysisHistoryAdapter extends RecyclerView.Adapter<AnalysisHistoryAdapter.HistoryViewHolder> {

    private final List<AIAnalysisEntity> history = new ArrayList<>();
    private OnHistoryClickListener listener;

    public interface OnHistoryClickListener {
        void onHistoryClick(AIAnalysisEntity entity);
    }

    public void setOnHistoryClickListener(OnHistoryClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<AIAnalysisEntity> items) {
        history.clear();
        if (items != null) {
            history.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_analysis_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        holder.bind(history.get(position));
    }

    @Override
    public int getItemCount() {
        return history.size();
    }

    class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView symbolText, typeText, summaryText, dateText;

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            symbolText = itemView.findViewById(R.id.history_symbol);
            typeText = itemView.findViewById(R.id.history_type);
            summaryText = itemView.findViewById(R.id.history_summary);
            dateText = itemView.findViewById(R.id.history_date);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onHistoryClick(history.get(pos));
                }
            });
        }

        void bind(AIAnalysisEntity entity) {
            symbolText.setText(entity.getStockSymbol());
            typeText.setText(formatType(entity.getAnalysisType()));
            summaryText.setText(entity.getSummaryText() != null
                    ? entity.getSummaryText() : "暂无摘要");
            dateText.setText(FormatUtils.formatRelativeTime(entity.getCreatedAt()));
        }

        private String formatType(String type) {
            if (type == null) return "分析";
            switch (type.toUpperCase()) {
                case "TECHNICAL": return "技术分析";
                case "FUNDAMENTAL": return "基本面分析";
                case "COMPREHENSIVE": return "全面分析";
                case "SENTIMENT": return "情绪分析";
                default: return type;
            }
        }
    }
}
