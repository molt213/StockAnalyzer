package com.stockanalyzer.ui.detail;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.stockanalyzer.R;
import com.stockanalyzer.adapter.NewsAdapter;
import com.stockanalyzer.data.model.Stock;
import com.stockanalyzer.data.model.StockDetail;
import com.stockanalyzer.data.remote.dto.StockResponse;
import com.stockanalyzer.util.Constants;
import com.stockanalyzer.util.FormatUtils;

import java.util.HashMap;
import java.util.Map;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 股票详情Activity
 * 显示股票的详细信息、图表和新闻
 */
public class DetailActivity extends AppCompatActivity {

    private DetailViewModel viewModel;

    private TextView symbolText, nameText, priceText, changeText;
    private TextView openText, highText, lowText, prevCloseText;
    private TextView marketCapText, peText, epsText, week52Text;
    private TextView circulatingMarketCapText, turnoverRateText;
    private TextView descriptionText;
    private LineChart priceChart;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView newsRecycler;
    private NewsAdapter newsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 应用暗色主题
        SharedPreferences prefs = getSharedPreferences("stock_analyzer_prefs", MODE_PRIVATE);
        if (prefs.getBoolean("dark_mode", false)) {
            setTheme(R.style.Theme_StockAnalyzer_Dark);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        String symbol = getIntent().getStringExtra(Constants.EXTRA_SYMBOL);
        String stockName = getIntent().getStringExtra(Constants.EXTRA_STOCK_NAME);

        if (symbol == null) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(DetailViewModel.class);

        initViews();
        setupChart();
        setupNewsList();
        setupToolbar(symbol, stockName);
        observeData();

        viewModel.loadStockData(symbol);
        viewModel.loadChartData(symbol, "D", 60);
    }

    private void initViews() {
        symbolText = findViewById(R.id.detail_symbol);
        nameText = findViewById(R.id.detail_name);
        priceText = findViewById(R.id.detail_price);
        changeText = findViewById(R.id.detail_change);
        openText = findViewById(R.id.detail_open);
        highText = findViewById(R.id.detail_high);
        lowText = findViewById(R.id.detail_low);
        prevCloseText = findViewById(R.id.detail_prev_close);
        marketCapText = findViewById(R.id.detail_market_cap);
        circulatingMarketCapText = findViewById(R.id.detail_circulating_market_cap);
        peText = findViewById(R.id.detail_pe);
        turnoverRateText = findViewById(R.id.detail_turnover_rate);
        epsText = findViewById(R.id.detail_eps);
        week52Text = findViewById(R.id.detail_52w);
        descriptionText = findViewById(R.id.detail_description);
        priceChart = findViewById(R.id.price_chart);
        newsRecycler = findViewById(R.id.news_recycler);
        swipeRefresh = findViewById(R.id.detail_swipe_refresh);

        swipeRefresh.setOnRefreshListener(() -> {
            try {
                String symbol = getIntent().getStringExtra(Constants.EXTRA_SYMBOL);
                if (symbol != null) {
                    viewModel.loadStockData(symbol);
                }
            } catch (Exception e) {
                Log.e("DetailActivity", "刷新失败", e);
                swipeRefresh.setRefreshing(false);
            }
        });

        // 自选股按钮（统一的观察者，不移到 observeData）
        MaterialButton btnWatchlist = findViewById(R.id.btn_watchlist);
        btnWatchlist.setOnClickListener(v -> viewModel.toggleWatchlist());

        // 图表周期切换
        setupChipListeners();
    }

    private void setupChipListeners() {
        Log.d("ChartDebug", "setupChipListeners called");
        String symbol = getIntent().getStringExtra(Constants.EXTRA_SYMBOL);
        Log.d("ChartDebug", "symbol=" + symbol);
        if (symbol == null) return;

        ChipGroup chipGroup = findViewById(R.id.chip_group);
        Log.d("ChartDebug", "chipGroup=" + (chipGroup != null ? "found" : "NULL"));
        if (chipGroup == null) return;

        // chip ID → resolution 映射
        Map<Integer, String> resMap = new HashMap<>();
        resMap.put(R.id.chip_1d, "5");    // 5分钟K
        resMap.put(R.id.chip_1w, "60");   // 60分钟K
        resMap.put(R.id.chip_1m, "M");    // 月K
        resMap.put(R.id.chip_3m, "D");    // 日K
        resMap.put(R.id.chip_1y, "W");    // 周K

        // 方式1: ChipGroup 监听（备用）
        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String resolution = resMap.get(checkedId);
            if (resolution == null) resolution = "D";
            Chip selectedChip = findViewById(checkedId);
            String periodText = selectedChip != null ? selectedChip.getText().toString() : resolution;
            Log.d("ChartDebug", "ChipGroup切换: id=" + checkedId + " res=" + resolution + " text=" + periodText);
            loadChartForChip(symbol, resolution, periodText, 60);
        });

        // 硬编码设置点击监听，不同K线类型使用不同的天数参数
        int[] chipIds = {R.id.chip_1d, R.id.chip_1w, R.id.chip_3m, R.id.chip_1y, R.id.chip_1m};
        String[] chipRes = {"5", "60", "D", "W", "M"};
        String[] chipLabels = {"5分K", "60分K", "日K", "周K", "月K"};
        int[] chipDays = {1, 5, 60, 180, 360};  // 5分K看1天, 60分K看5天, 日K60天, 周K180天, 月K360天
        for (int i = 0; i < chipIds.length; i++) {
            Chip chip = findViewById(chipIds[i]);
            if (chip != null) {
                final String res = chipRes[i];
                final String label = chipLabels[i];
                final int days = chipDays[i];
                chip.setOnClickListener(v -> {
                    Log.d("ChartDebug", "芯片点击: " + label + " res=" + res + " days=" + days);
                    loadChartForChip(symbol, res, label, days);
                });
            } else {
                Log.d("ChartDebug", "芯片NULL: id=" + chipIds[i] + " label=" + chipLabels[i]);
            }
        }
    }

    private void loadChartForChip(String symbol, String resolution, String label, int days) {
        Log.d("ChartDebug", "加载图表: res=" + resolution + " label=" + label + " days=" + days);
        priceChart.clear();
        priceChart.setNoDataText("加载 " + label + "...");
        priceChart.invalidate();
        viewModel.loadChartData(symbol, resolution, days);
    }

    private void setupChart() {
        priceChart.getDescription().setEnabled(false);
        priceChart.setTouchEnabled(true);
        priceChart.setDragEnabled(true);
        priceChart.setScaleEnabled(true);
        priceChart.setPinchZoom(true);
        priceChart.setDrawGridBackground(false);
        priceChart.setBackgroundColor(Color.TRANSPARENT);

        XAxis xAxis = priceChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.GRAY);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelCount(5, false);

        YAxis leftAxis = priceChart.getAxisLeft();
        leftAxis.setTextColor(Color.GRAY);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.LTGRAY);

        priceChart.getAxisRight().setEnabled(false);
        priceChart.getLegend().setEnabled(false);
    }

    private void setupNewsList() {
        newsAdapter = new NewsAdapter();
        newsRecycler.setLayoutManager(new LinearLayoutManager(this));
        newsRecycler.setAdapter(newsAdapter);
    }

    private void setupToolbar(String symbol, String stockName) {
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.detail_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(symbol);
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        // 添加自选按钮
        toolbar.getMenu().clear();
        toolbar.inflateMenu(R.menu.detail_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_watchlist) {
                viewModel.toggleWatchlist();
                return true;
            }
            return false;
        });
    }

    private void observeData() {
        viewModel.quoteData.observe(this, this::updateQuoteUI);
        viewModel.companyData.observe(this, this::updateCompanyUI);
        viewModel.chartData.observe(this, this::updateChartUI);
        viewModel.newsData.observe(this, newsAdapter::submitList);
        viewModel.metricsData.observe(this, this::updateMetricsUI);
        viewModel.isInWatchlist.observe(this, inList -> {
            MaterialButton btn = findViewById(R.id.btn_watchlist);
            if (btn != null) {
                if (inList != null && inList) {
                    btn.setText("已添加自选股 ★");
                    btn.setIconResource(R.drawable.ic_star_filled);
                } else {
                    btn.setText("添加至自选股");
                    btn.setIconResource(R.drawable.ic_star_outline);
                }
            }
        });
        viewModel.isLoading.observe(this, loading -> swipeRefresh.setRefreshing(loading != null && loading));
        viewModel.errorMessage.observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateQuoteUI(Stock stock) {
        priceText.setText(FormatUtils.formatPrice(stock.getCurrentPrice()));

        // 显示昨收和涨跌幅: "昨收 ¥1,360.50  +0.77%"
        String changeStr = "昨收 " + FormatUtils.formatPrice(stock.getPreviousClose())
                + "  " + FormatUtils.formatChangePercent(stock.getChangePercent());
        changeText.setText(changeStr);

        boolean isUp = stock.isPositiveChange();
        int color = isUp ? getColor(R.color.stock_up) : getColor(R.color.stock_down);
        priceText.setTextColor(color);
        changeText.setTextColor(color);

        openText.setText(FormatUtils.formatPrice(stock.getOpen()));
        highText.setText(FormatUtils.formatPrice(stock.getHigh()));
        lowText.setText(FormatUtils.formatPrice(stock.getLow()));
        prevCloseText.setText(FormatUtils.formatVolume(stock.getVolume()));
    }

    private void updateCompanyUI(StockDetail detail) {
        if (detail != null) {
            // 名称在上，代码在下
            nameText.setText(detail.getName());
            symbolText.setText(detail.getSymbol());
            // 标题显示股票名称
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(detail.getName() != null ? detail.getName() : detail.getSymbol());
            }
            descriptionText.setText(detail.getDescription());

            // 总市值
            if (detail.getMarketCap() != null && !detail.getMarketCap().isEmpty()) {
                marketCapText.setText(detail.getMarketCap());
            } else { marketCapText.setText("N/A"); }

            // 流通市值
            if (detail.getCirculatingMarketCap() != null && !detail.getCirculatingMarketCap().isEmpty()) {
                circulatingMarketCapText.setText(detail.getCirculatingMarketCap());
            } else { circulatingMarketCapText.setText("N/A"); }

            // 市盈率
            if (detail.getPeRatio() > 0) {
                peText.setText(String.format(Locale.US, "%.2f", detail.getPeRatio()));
            } else { peText.setText("N/A"); }

            // 换手率
            if (detail.getTurnoverRate() != null && !detail.getTurnoverRate().isEmpty()) {
                turnoverRateText.setText(detail.getTurnoverRate());
            } else { turnoverRateText.setText("N/A"); }

            // 每股收益 EPS
            if (detail.getEps() > 0) {
                epsText.setText(String.format(Locale.US, "%.2f", detail.getEps()));
            } else { epsText.setText("N/A"); }

            // 52周范围
            if (detail.getWeek52High() > 0 || detail.getWeek52Low() > 0) {
                week52Text.setText(String.format(Locale.US, "%.2f - %.2f",
                        detail.getWeek52Low(), detail.getWeek52High()));
            } else { week52Text.setText("N/A"); }
        }
    }

    private void updateMetricsUI(StockResponse.MetricsData metrics) {
        // Finnhub 美股数据走这里；A 股数据直接从 StockDetail 获取（见 updateCompanyUI）
        // 只有当 metrics 有具体值时覆盖，避免覆盖 A 股来自 StockDetail 的数据
        if (metrics == null) return;
        if (metrics.peRatio != null) peText.setText(String.format(Locale.US, "%.2f", metrics.peRatio));
        if (metrics.epsTtm != null) epsText.setText(String.format(Locale.US, "%.2f", metrics.epsTtm));
        if (metrics.week52Low != null && metrics.week52High != null)
            week52Text.setText(String.format(Locale.US, "%.2f - %.2f", metrics.week52Low, metrics.week52High));
    }

    // 保存图表原始数据，供 formatter 和 marker 使用
    private List<StockDetail.CandleData> chartDataCache;

    private void updateChartUI(List<StockDetail.CandleData> data) {
        Log.d("ChartDebug", "updateChartUI called, data size=" + (data != null ? data.size() : "null"));
        priceChart.clear();
        if (data == null || data.isEmpty()) {
            priceChart.setNoDataText("暂无图表数据");
            priceChart.invalidate();
            return;
        }

        chartDataCache = data;

        List<Entry> entries = new ArrayList<>();
        boolean isUp = true;

        for (int i = 0; i < data.size(); i++) {
            StockDetail.CandleData candle = data.get(i);
            entries.add(new Entry(i, (float) candle.getClose()));
            if (i == data.size() - 1) {
                isUp = candle.getClose() >= (data.size() > 1
                        ? data.get(data.size() - 2).getClose() : candle.getClose());
            }
        }

        LineDataSet dataSet = new LineDataSet(entries, "价格");
        int lineColor = isUp ? getColor(R.color.chart_up) : getColor(R.color.chart_down);
        int fillColor = isUp ? getColor(R.color.stock_up_bg) : getColor(R.color.stock_down_bg);

        dataSet.setColor(lineColor);
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(fillColor);
        dataSet.setFillAlpha(40);

        // X 轴显示日期
        XAxis xAxis = priceChart.getXAxis();
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = Math.round(value);
                if (idx >= 0 && idx < chartDataCache.size()) {
                    return FormatUtils.formatDate(chartDataCache.get(idx).getTimestamp());
                }
                return "";
            }
        });

        // Y 轴显示 ¥ 价格
        priceChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return "¥" + String.format(Locale.US, "%.2f", value);
            }
        });

        LineData lineData = new LineData(dataSet);
        priceChart.setData(lineData);
        priceChart.notifyDataSetChanged();
        priceChart.fitScreen();
        priceChart.getXAxis().resetAxisMinimum();
        priceChart.getXAxis().resetAxisMaximum();
        priceChart.getAxisLeft().resetAxisMinimum();
        priceChart.getAxisLeft().resetAxisMaximum();

        // 点击图表显示日期和价格
        priceChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int idx = (int) e.getX();
                if (idx >= 0 && idx < chartDataCache.size()) {
                    String date = FormatUtils.formatDate(chartDataCache.get(idx).getTimestamp());
                    String priceStr = String.format(Locale.US, "¥%.2f", e.getY());
                    Toast.makeText(DetailActivity.this,
                            date + "  " + priceStr, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNothingSelected() { }
        });

        priceChart.invalidate();
    }

    /**
     * 启动详情页
     */
    public static void start(Context context, String symbol, String stockName) {
        Intent intent = new Intent(context, DetailActivity.class);
        intent.putExtra(Constants.EXTRA_SYMBOL, symbol);
        intent.putExtra(Constants.EXTRA_STOCK_NAME, stockName);
        context.startActivity(intent);
    }
}
