package com.stockanalyzer.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.stockanalyzer.R;
import com.stockanalyzer.data.model.Stock;
import com.stockanalyzer.util.FormatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 股票列表适配器
 * 用于仪表盘自选股和搜索结果
 */
public class StockAdapter extends RecyclerView.Adapter<StockAdapter.StockViewHolder> {

    private final List<Stock> stocks = new ArrayList<>();
    private OnStockClickListener listener;
    private boolean showPrice = true;

    public interface OnStockClickListener {
        void onStockClick(Stock stock);
    }

    public void setOnStockClickListener(OnStockClickListener listener) {
        this.listener = listener;
    }

    public void setShowPrice(boolean showPrice) {
        this.showPrice = showPrice;
    }

    @NonNull
    @Override
    public StockViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stock, parent, false);
        return new StockViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StockViewHolder holder, int position) {
        Stock stock = stocks.get(position);
        holder.bind(stock, showPrice);
    }

    @Override
    public int getItemCount() {
        return stocks.size();
    }

    public void submitList(List<Stock> newStocks) {
        stocks.clear();
        if (newStocks != null) {
            stocks.addAll(newStocks);
        }
        notifyDataSetChanged();
    }

    public List<Stock> getCurrentList() {
        return new ArrayList<>(stocks);
    }

    class StockViewHolder extends RecyclerView.ViewHolder {
        TextView symbolText, nameText, priceText, changeText;

        StockViewHolder(@NonNull View itemView) {
            super(itemView);
            symbolText = itemView.findViewById(R.id.stock_symbol);
            nameText = itemView.findViewById(R.id.stock_name);
            priceText = itemView.findViewById(R.id.stock_price);
            changeText = itemView.findViewById(R.id.stock_change);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onStockClick(stocks.get(pos));
                }
            });
        }

        /** 提取纯净股票代码（去掉SH/SZ/sh/sz前缀） */
        private String cleanCode(String raw) {
            return raw.trim().toUpperCase()
                    .replace("SH", "").replace("SZ", "").replace("BJ", "")
                    .replace("SH.", "").replace("SZ.", "");
        }

        void bind(Stock stock, boolean showPrice) {
            // 调试日志
            Log.d("StockAdapter", "bind: name=" + stock.getName()
                    + " symbol=" + stock.getSymbol()
                    + " displaySymbol=" + stock.getDisplaySymbol());
            // 上方：股票名称（有名称显示名称，没有就显示代码）
            String name = stock.getName();
            if (name == null || name.isEmpty()) name = stock.getDisplaySymbol();
            if (name == null || name.isEmpty()) name = cleanCode(stock.getSymbol());
            nameText.setText(name);
            // 下方：纯净数字代码
            String code = cleanCode(stock.getSymbol());
            Log.d("StockAdapter", "显示: name=" + name + " code=" + code);
            symbolText.setText(code);

            if (showPrice && stock.getCurrentPrice() > 0) {
                priceText.setText(FormatUtils.formatPrice(stock.getCurrentPrice()));
                priceText.setVisibility(View.VISIBLE);

                // 显示昨收和涨跌幅
                String changeStr = "昨收 " + FormatUtils.formatPrice(stock.getPreviousClose())
                        + "  " + FormatUtils.formatChangePercent(stock.getChangePercent());
                changeText.setText(changeStr);
                changeText.setTextColor(stock.isPositiveChange()
                        ? itemView.getContext().getColor(R.color.stock_up)
                        : itemView.getContext().getColor(R.color.stock_down));
                changeText.setVisibility(View.VISIBLE);
            } else {
                priceText.setVisibility(View.GONE);
                changeText.setVisibility(View.GONE);
            }
        }
    }
}
