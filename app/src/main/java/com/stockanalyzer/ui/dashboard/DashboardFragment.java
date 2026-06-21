package com.stockanalyzer.ui.dashboard;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.stockanalyzer.R;
import com.stockanalyzer.StockAnalyzerApp;
import com.stockanalyzer.adapter.NewsAdapter;
import com.stockanalyzer.adapter.StockAdapter;
import com.stockanalyzer.data.local.StockEntity;
import com.stockanalyzer.data.model.StockDetail;
import com.stockanalyzer.data.repository.StockScraper;
import com.stockanalyzer.data.model.Stock;
import com.stockanalyzer.data.repository.StockRepository;
import com.stockanalyzer.ui.detail.DetailActivity;
import com.stockanalyzer.util.Constants;
import com.stockanalyzer.util.FormatUtils;
import com.stockanalyzer.util.NetworkUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 仪表盘Fragment - 显示自选股列表
 */
public class DashboardFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView watchlistRecycler, marketNewsRecycler;
    private View emptyView;
    private TextView lastUpdateText, watchlistCount;
    private TextView[] indexValues, indexChanges;
    private StockAdapter adapter;
    private NewsAdapter marketNewsAdapter;
    private StockRepository repository;
    private Handler autoRefreshHandler;
    private boolean isAutoRefreshRunning = false;

    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            loadWatchlistData();
            autoRefreshHandler.postDelayed(this, Constants.AUTO_REFRESH_INTERVAL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = StockRepository.getInstance();
        autoRefreshHandler = new Handler(Looper.getMainLooper());

        initViews(view);
        setupRecyclerView();
        loadWatchlistData();
        loadMarketIndices();
        loadMarketNews();
    }

    private void initViews(View view) {
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        watchlistRecycler = view.findViewById(R.id.watchlist_recycler);
        marketNewsRecycler = view.findViewById(R.id.market_news_recycler);
        emptyView = view.findViewById(R.id.empty_view);
        lastUpdateText = view.findViewById(R.id.last_update_text);
        watchlistCount = view.findViewById(R.id.watchlist_count);

        // 大盘指数 TextViews
        indexValues = new TextView[]{
            view.findViewById(R.id.index_sh_value),
            view.findViewById(R.id.index_sz_value),
            view.findViewById(R.id.index_cy_value),
            view.findViewById(R.id.index_kc_value)
        };
        indexChanges = new TextView[]{
            view.findViewById(R.id.index_sh_change),
            view.findViewById(R.id.index_sz_change),
            view.findViewById(R.id.index_cy_change),
            view.findViewById(R.id.index_kc_change)
        };

        swipeRefresh.setOnRefreshListener(() -> {
            loadWatchlistData();
            loadMarketIndices();
            loadMarketNews();
        });
        swipeRefresh.setColorSchemeResources(R.color.primary, R.color.ai_accent, R.color.secondary);
    }

    private void setupRecyclerView() {
        adapter = new StockAdapter();
        adapter.setOnStockClickListener(stock -> {
            DetailActivity.start(requireContext(), stock.getSymbol(), stock.getName());
        });
        watchlistRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        watchlistRecycler.setAdapter(adapter);

        // 市场要闻
        marketNewsAdapter = new NewsAdapter();
        marketNewsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        marketNewsRecycler.setAdapter(marketNewsAdapter);
    }

    private void loadWatchlistData() {
        if (!NetworkUtils.isNetworkAvailable()) {
            // 从数据库加载离线数据
            loadFromDatabase();
            swipeRefresh.setRefreshing(false);
            return;
        }

        // 获取自选股列表（仅显示A股）
        List<StockEntity> entities = repository.getWatchlist();
        List<StockEntity> aShareEntities = new ArrayList<>();
        for (StockEntity e : entities) {
            if (StockScraper.isAShareCode(e.getSymbol())) {
                aShareEntities.add(e);
            }
        }
        if (aShareEntities.isEmpty()) {
            updateEmptyView(true);
            swipeRefresh.setRefreshing(false);
            return;
        }
        entities = aShareEntities;

        updateEmptyView(false);
        watchlistCount.setText(String.format(Locale.getDefault(),
                "%d 只股票", entities.size()));

        // 为每个自选股获取实时报价
        final List<Stock> stocks = new ArrayList<>();
        final int[] completed = {0};
        final int total = entities.size();

        for (StockEntity entity : entities) {
            repository.getQuote(entity.getSymbol(), new StockRepository.RepositoryCallback<Stock>() {
                @Override
                public void onSuccess(Stock stock) {
                    stock.setName(entity.getName());
                    synchronized (stocks) {
                        stocks.add(stock);
                    }
                    // 更新数据库中的价格
                    repository.updateWatchlistPrice(stock.getSymbol(), stock.getCurrentPrice());
                    checkComplete();
                }

                @Override
                public void onError(Exception e) {
                    // 如果获取失败，使用数据库中的价格
                    Stock fallback = new Stock(entity.getSymbol(), entity.getName());
                    if (entity.getLastPrice() > 0) {
                        fallback.setCurrentPrice(entity.getLastPrice());
                    }
                    synchronized (stocks) {
                        stocks.add(fallback);
                    }
                    checkComplete();
                }

                private void checkComplete() {
                    synchronized (completed) {
                        completed[0]++;
                        if (completed[0] >= total) {
                            updateUI(stocks);
                        }
                    }
                }
            });
        }
    }

    private void loadMarketIndices() {
        if (!NetworkUtils.isNetworkAvailable()) return;
        repository.getMarketIndices(new StockRepository.RepositoryCallback<List<Stock>>() {
            @Override
            public void onSuccess(List<Stock> indices) {
                if (getActivity() == null || indices == null) return;
                getActivity().runOnUiThread(() -> {
                    for (int i = 0; i < Math.min(indices.size(), 4); i++) {
                        Stock idx = indices.get(i);
                        if (i < indexValues.length && indexValues[i] != null) {
                            indexValues[i].setText(String.format(Locale.US, "%.2f", idx.getCurrentPrice()));
                        }
                        if (i < indexChanges.length && indexChanges[i] != null) {
                            String changeStr = String.format(Locale.US, "%+.2f%%", idx.getChangePercent());
                            indexChanges[i].setText(changeStr);
                            int color = idx.isPositiveChange()
                                    ? requireContext().getColor(R.color.stock_up)
                                    : requireContext().getColor(R.color.stock_down);
                            indexValues[i].setTextColor(color);
                            indexChanges[i].setTextColor(color);
                        }
                    }
                });
            }
            @Override
            public void onError(Exception e) { }
        });
    }

    private void loadMarketNews() {
        if (!NetworkUtils.isNetworkAvailable()) return;
        repository.getMarketNews(new StockRepository.RepositoryCallback<List<StockDetail.NewsItem>>() {
            @Override
            public void onSuccess(List<StockDetail.NewsItem> data) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> marketNewsAdapter.submitList(data));
                }
            }
            @Override
            public void onError(Exception e) { }
        });
    }

    private void updateUI(List<Stock> stocks) {
        requireActivity().runOnUiThread(() -> {
            adapter.submitList(stocks);
            updateLastUpdateTime();
            swipeRefresh.setRefreshing(false);
        });
    }

    private void loadFromDatabase() {
        List<StockEntity> entities = repository.getWatchlist();
        if (entities.isEmpty()) {
            updateEmptyView(true);
            return;
        }

        updateEmptyView(false);
        List<Stock> stocks = new ArrayList<>();
        for (StockEntity entity : entities) {
            Stock stock = new Stock(entity.getSymbol(), entity.getName());
            if (entity.getLastPrice() > 0) {
                stock.setCurrentPrice(entity.getLastPrice());
            }
            stocks.add(stock);
        }
        adapter.submitList(stocks);
        watchlistCount.setText(String.format(Locale.getDefault(),
                "%d 只股票 (离线)", entities.size()));
    }

    private void updateEmptyView(boolean isEmpty) {
        emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        watchlistRecycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void updateLastUpdateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        lastUpdateText.setText(String.format(Locale.getDefault(),
                "最后更新: %s", sdf.format(new Date())));
    }

    @Override
    public void onResume() {
        super.onResume();
        loadWatchlistData();
        // 启动自动刷新
        if (StockAnalyzerApp.getInstance().getPreferences()
                .getBoolean(Constants.PREF_AUTO_REFRESH, false)) {
            startAutoRefresh();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopAutoRefresh();
    }

    private void startAutoRefresh() {
        if (!isAutoRefreshRunning) {
            isAutoRefreshRunning = true;
            autoRefreshHandler.postDelayed(autoRefreshRunnable,
                    Constants.AUTO_REFRESH_INTERVAL_MS);
        }
    }

    private void stopAutoRefresh() {
        isAutoRefreshRunning = false;
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }
}
