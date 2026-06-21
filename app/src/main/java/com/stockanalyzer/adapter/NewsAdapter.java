package com.stockanalyzer.adapter;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.stockanalyzer.R;
import com.stockanalyzer.data.model.StockDetail;
import com.stockanalyzer.util.FormatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 新闻列表适配器
 */
public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.NewsViewHolder> {

    private final List<StockDetail.NewsItem> newsList = new ArrayList<>();

    public void submitList(List<StockDetail.NewsItem> items) {
        newsList.clear();
        if (items != null) {
            newsList.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NewsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_news, parent, false);
        return new NewsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NewsViewHolder holder, int position) {
        holder.bind(newsList.get(position));
    }

    @Override
    public int getItemCount() {
        return newsList.size();
    }

    class NewsViewHolder extends RecyclerView.ViewHolder {
        TextView headlineText, summaryText, sourceText;

        NewsViewHolder(@NonNull View itemView) {
            super(itemView);
            headlineText = itemView.findViewById(R.id.news_headline);
            summaryText = itemView.findViewById(R.id.news_summary);
            sourceText = itemView.findViewById(R.id.news_source);
        }

        void bind(StockDetail.NewsItem item) {
            headlineText.setText(item.getHeadline());
            summaryText.setText(item.getSummary());
            sourceText.setText(item.getSource());

            itemView.setOnClickListener(v -> {
                if (item.getUrl() != null && !item.getUrl().isEmpty()) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.getUrl()));
                    itemView.getContext().startActivity(intent);
                }
            });
        }
    }
}
