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

import com.github.mikephil.charting.charts.CandleStickChart;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.CombinedData;
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
import com.stockanalyzer.util.TechnicalIndicators;

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
    private boolean isDarkMode;

    private TextView symbolText, nameText, priceText, changeText;
    private TextView openText, highText, lowText, prevCloseText;
    private TextView marketCapText, peText, epsText, week52Text;
    private TextView circulatingMarketCapText, turnoverRateText;
    private TextView descriptionText;
    private CandleStickChart priceChart;
    private CombinedChart macdChart;
    private LineChart rsiChart;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView newsRecycler;
    private NewsAdapter newsAdapter;
    private View peContainer, epsContainer;
    private TextView peLabel, epsLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 应用暗色主题
        SharedPreferences prefs = getSharedPreferences("stock_analyzer_prefs", MODE_PRIVATE);
        isDarkMode = prefs.getBoolean("dark_mode", false);
        if (isDarkMode) {
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
        setupIndicatorCharts();
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
        peContainer = findViewById(R.id.detail_pe_container);
        epsContainer = findViewById(R.id.detail_eps_container);
        peLabel = findViewById(R.id.detail_pe_label);
        epsLabel = findViewById(R.id.detail_eps_label);
        descriptionText = findViewById(R.id.detail_description);
        priceChart = findViewById(R.id.price_chart);
        macdChart = findViewById(R.id.macd_chart);
        rsiChart = findViewById(R.id.rsi_chart);
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
        priceChart.setMaxVisibleValueCount(0);
        priceChart.setDrawBorders(false);

        int textColor = isDarkMode ? Color.WHITE : Color.GRAY;
        int gridColor = isDarkMode ? Color.argb(80, 255, 255, 255) : Color.LTGRAY;

        XAxis xAxis = priceChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(textColor);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelCount(5, false);

        YAxis leftAxis = priceChart.getAxisLeft();
        leftAxis.setTextColor(textColor);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(gridColor);

        priceChart.getAxisRight().setEnabled(false);
        priceChart.getLegend().setEnabled(false);
    }

    private void setupIndicatorCharts() {
        int textColor = isDarkMode ? Color.WHITE : Color.GRAY;
        int gridColor = isDarkMode ? Color.argb(80, 255, 255, 255) : Color.LTGRAY;

        // MACD 图表
        macdChart.getDescription().setEnabled(false);
        macdChart.setTouchEnabled(false);
        macdChart.setDrawGridBackground(false);
        macdChart.setBackgroundColor(Color.TRANSPARENT);
        macdChart.setScaleEnabled(false);
        macdChart.getLegend().setTextColor(textColor);

        XAxis macdX = macdChart.getXAxis();
        macdX.setPosition(XAxis.XAxisPosition.TOP);
        macdX.setTextColor(textColor);
        macdX.setDrawGridLines(false);
        macdX.setLabelCount(3, false);
        macdX.setDrawLabels(false);

        YAxis macdLeft = macdChart.getAxisLeft();
        macdLeft.setTextColor(textColor);
        macdLeft.setDrawGridLines(true);
        macdLeft.setGridColor(gridColor);
        macdLeft.setLabelCount(3, false);

        macdChart.getAxisRight().setEnabled(false);

        // RSI 图表
        rsiChart.getDescription().setEnabled(false);
        rsiChart.setTouchEnabled(false);
        rsiChart.setDrawGridBackground(false);
        rsiChart.setBackgroundColor(Color.TRANSPARENT);
        rsiChart.setScaleEnabled(false);
        rsiChart.getLegend().setTextColor(textColor);

        XAxis rsiX = rsiChart.getXAxis();
        rsiX.setPosition(XAxis.XAxisPosition.TOP);
        rsiX.setTextColor(textColor);
        rsiX.setDrawGridLines(false);
        rsiX.setLabelCount(3, false);
        rsiX.setDrawLabels(false);

        YAxis rsiLeft = rsiChart.getAxisLeft();
        rsiLeft.setTextColor(textColor);
        rsiLeft.setDrawGridLines(true);
        rsiLeft.setGridColor(gridColor);
        rsiLeft.setAxisMinimum(0);
        rsiLeft.setAxisMaximum(100);
        rsiLeft.setLabelCount(4, false);

        rsiChart.getAxisRight().setEnabled(false);
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

            // 判断是否为ETF/商品（代码5开头=上海ETF，1开头=深圳ETF/LOF）
            String sym = detail.getSymbol() != null ? detail.getSymbol() : "";
            boolean isEtf = sym.matches("^[51]\\d{5}$");

            if (isEtf) {
                // ETF模式
                if (detail.getEarningsYield() > 0) {
                    // 有PE（股票型ETF）：指数盈利 + PE分位数
                    if (peLabel != null) peLabel.setText("指数盈利");
                    peText.setText(String.format(Locale.US, "%.2f%%", detail.getEarningsYield()));
                    if (peContainer != null) peContainer.setVisibility(View.VISIBLE);

                    if (epsLabel != null) epsLabel.setText("PE分位数");
                    double pct = detail.getPePercentile();
                    String suffix = pct >= 70 ? " ⬆" : pct <= 30 ? " ⬇" : "";
                    epsText.setText(String.format(Locale.US, "%.1f%%%s", pct, suffix));
                    epsText.setTextColor(pct >= 70 ? getColor(R.color.stock_down)
                            : pct <= 30 ? getColor(R.color.stock_up)
                            : getColor(isDarkMode ? R.color.dark_text_primary : R.color.text_primary));
                    if (epsContainer != null) epsContainer.setVisibility(View.VISIBLE);
                } else {
                    // 无PE（商品ETF）：年化波动率 + 近1年涨幅
                    if (detail.getAnnualVolatility() > 0) {
                        if (peLabel != null) peLabel.setText("年化波动");
                        peText.setText(String.format(Locale.US, "%.2f%%", detail.getAnnualVolatility()));
                        if (peContainer != null) peContainer.setVisibility(View.VISIBLE);
                    } else {
                        if (peContainer != null) peContainer.setVisibility(View.GONE);
                    }

                    if (epsLabel != null) epsLabel.setText("近1年涨幅");
                    double yr = detail.getYearlyReturn();
                    if (yr != 0) {
                        epsText.setText(String.format(Locale.US, "%+.2f%%", yr));
                        epsText.setTextColor(yr >= 0 ? getColor(R.color.stock_up) : getColor(R.color.stock_down));
                        if (epsContainer != null) epsContainer.setVisibility(View.VISIBLE);
                    } else {
                        if (epsContainer != null) epsContainer.setVisibility(View.GONE);
                    }
                }
            } else {
                // 股票模式：显示市盈率 + 每股收益
                if (detail.getPeRatio() > 0) {
                    if (peLabel != null) peLabel.setText("市盈率");
                    peText.setText(String.format(Locale.US, "%.2f", detail.getPeRatio()));
                    if (peContainer != null) peContainer.setVisibility(View.VISIBLE);
                } else {
                    if (peContainer != null) peContainer.setVisibility(View.GONE);
                }

                if (detail.getEps() > 0) {
                    if (epsLabel != null) epsLabel.setText("每股收益");
                    epsText.setText(String.format(Locale.US, "%.2f", detail.getEps()));
                    if (epsContainer != null) epsContainer.setVisibility(View.VISIBLE);
                } else {
                    if (epsContainer != null) epsContainer.setVisibility(View.GONE);
                }
            }

            // 换手率
            if (detail.getTurnoverRate() != null && !detail.getTurnoverRate().isEmpty()) {
                turnoverRateText.setText(detail.getTurnoverRate());
            } else { turnoverRateText.setText("N/A"); }

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
        if (metrics.peRatio != null && metrics.peRatio > 0) {
            peText.setText(String.format(Locale.US, "%.2f", metrics.peRatio));
            if (peLabel != null) peLabel.setText("市盈率");
            if (peContainer != null) peContainer.setVisibility(View.VISIBLE);
        }
        if (metrics.epsTtm != null) {
            epsText.setText(String.format(Locale.US, "%.2f", metrics.epsTtm));
            if (epsLabel != null) epsLabel.setText("每股收益");
            if (epsContainer != null) epsContainer.setVisibility(View.VISIBLE);
        }
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

        // K线柱: 每个 CandleEntry 包含 时间x, 最高, 最低, 开盘, 收盘
        List<CandleEntry> candleEntries = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            StockDetail.CandleData candle = data.get(i);
            candleEntries.add(new CandleEntry(
                    i,
                    (float) candle.getHigh(),     // 上影线最高
                    (float) candle.getLow(),       // 下影线最低
                    (float) candle.getOpen(),      // 开盘价
                    (float) candle.getClose()      // 收盘价
            ));
        }

        CandleDataSet candleSet = new CandleDataSet(candleEntries, "K线");
        // 阳线(close>open)→红色, 阴线(close<open)→绿色（中国习惯）
        candleSet.setIncreasingColor(getColor(R.color.stock_up));     // 阳线: 红
        candleSet.setDecreasingColor(getColor(R.color.stock_down));   // 阴线: 绿
        int dimColor = isDarkMode ? Color.argb(180, 255, 255, 255) : Color.GRAY;
        candleSet.setNeutralColor(dimColor);
        candleSet.setShadowColor(dimColor);         // 影线
        candleSet.setShadowWidth(0.8f);
        candleSet.setBarSpace(0.3f);                  // 柱间距
        candleSet.setDrawValues(false);
        candleSet.setHighlightLineWidth(0.5f);

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

        CandleData candleData = new CandleData(candleSet);
        priceChart.setData(candleData);
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

        // 更新 MACD 和 RSI 子图
        updateIndicatorCharts(data);
    }

    // ========== MACD / RSI 指标图 ==========

    private void updateIndicatorCharts(List<StockDetail.CandleData> data) {
        if (data == null || data.size() < 30) {
            macdChart.setNoDataText("数据不足，无法计算");
            macdChart.invalidate();
            rsiChart.setNoDataText("数据不足，无法计算");
            rsiChart.invalidate();
            return;
        }
        renderMACD(data);
        renderRSI(data);
    }

    private void renderMACD(List<StockDetail.CandleData> data) {
        TechnicalIndicators.MACDResult result = TechnicalIndicators.calculateMACD(data);
        int n = data.size();

        // DIF 线
        List<Entry> difEntries = new ArrayList<>();
        // DEA 线
        List<Entry> deaEntries = new ArrayList<>();
        // MACD 柱
        List<BarEntry> barEntries = new ArrayList<>();

        // 找到 MACD 的绝对值最大值用于 Y 轴缩放
        float maxAbsMacd = 0;
        for (int i = 0; i < n; i++) {
            float macdVal = result.macd.get(i).floatValue();
            if (Math.abs(macdVal) > maxAbsMacd) maxAbsMacd = Math.abs(macdVal);
        }
        if (maxAbsMacd < 0.01f) maxAbsMacd = 1f;

        for (int i = 0; i < n; i++) {
            difEntries.add(new Entry(i, result.dif.get(i).floatValue()));
            deaEntries.add(new Entry(i, result.dea.get(i).floatValue()));
            float macdVal = result.macd.get(i).floatValue();
            barEntries.add(new BarEntry(i, macdVal));
        }

        LineDataSet difSet = new LineDataSet(difEntries, "DIF");
        difSet.setColor(Color.rgb(33, 150, 243));    // 蓝色
        difSet.setLineWidth(1.2f);
        difSet.setDrawCircles(false);
        difSet.setDrawValues(false);
        difSet.setMode(LineDataSet.Mode.LINEAR);

        LineDataSet deaSet = new LineDataSet(deaEntries, "DEA");
        deaSet.setColor(Color.rgb(255, 152, 0));      // 橙色
        deaSet.setLineWidth(1.2f);
        deaSet.setDrawCircles(false);
        deaSet.setDrawValues(false);
        deaSet.setMode(LineDataSet.Mode.LINEAR);

        // MACD 柱状图：用匿名类动态着色（正值→红色/涨，负值→绿色/跌）
        BarDataSet barSet = new BarDataSet(barEntries, "MACD") {
            @Override
            public int getColor(int index) {
                BarEntry entry = getEntryForIndex(index);
                if (entry != null && entry.getY() >= 0) {
                    return getColor(R.color.stock_up);     // 正→红色（多头）
                }
                return getColor(R.color.stock_down);        // 负→绿色（空头）
            }
        };
        barSet.setDrawValues(false);
        barSet.setValueTextColor(Color.TRANSPARENT);

        // 组合数据
        CombinedData combined = new CombinedData();
        combined.setData(new LineData(difSet, deaSet));
        combined.setData(new BarData(barSet));

        // BarData 宽度调整（默认 0.85f 可能太宽）
        combined.getBarData().setBarWidth(0.6f);

        XAxis xAxis = macdChart.getXAxis();
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = Math.round(value);
                if (idx >= 0 && idx < n) {
                    return FormatUtils.formatDate(data.get(idx).getTimestamp());
                }
                return "";
            }
        });

        YAxis leftAxis = macdChart.getAxisLeft();
        leftAxis.setAxisMinimum(-maxAbsMacd * 1.3f);
        leftAxis.setAxisMaximum(maxAbsMacd * 1.3f);

        macdChart.setData(combined);
        macdChart.notifyDataSetChanged();
        macdChart.fitScreen();
        macdChart.invalidate();
    }

    private void renderRSI(List<StockDetail.CandleData> data) {
        List<Double> rsiValues = TechnicalIndicators.calculateRSI(data);
        int n = rsiValues.size();

        List<Entry> rsiEntries = new ArrayList<>();
        List<Entry> overbought = new ArrayList<>();
        List<Entry> oversold = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            float v = rsiValues.get(i).floatValue();
            rsiEntries.add(new Entry(i, v));
            overbought.add(new Entry(i, 70f));
            oversold.add(new Entry(i, 30f));
        }

        // RSI 主线条
        LineDataSet rsiSet = new LineDataSet(rsiEntries, "RSI");
        rsiSet.setColor(Color.rgb(156, 39, 176));     // 紫色
        rsiSet.setLineWidth(1.5f);
        rsiSet.setDrawCircles(false);
        rsiSet.setDrawValues(false);
        rsiSet.setMode(LineDataSet.Mode.LINEAR);

        // 超买线 70
        LineDataSet obSet = new LineDataSet(overbought, "超买");
        obSet.setColor(Color.RED);
        obSet.setLineWidth(0.8f);
        obSet.setDrawCircles(false);
        obSet.setDrawValues(false);
        obSet.enableDashedLine(8f, 4f, 0f);

        // 超卖线 30
        LineDataSet osSet = new LineDataSet(oversold, "超卖");
        osSet.setColor(Color.rgb(76, 175, 80));        // 绿色
        osSet.setLineWidth(0.8f);
        osSet.setDrawCircles(false);
        osSet.setDrawValues(false);
        osSet.enableDashedLine(8f, 4f, 0f);

        // 中线 50
        List<Entry> midEntries = new ArrayList<>();
        for (int i = 0; i < n; i++) midEntries.add(new Entry(i, 50f));
        LineDataSet midSet = new LineDataSet(midEntries, "中线");
        midSet.setColor(Color.GRAY);
        midSet.setLineWidth(0.5f);
        midSet.setDrawCircles(false);
        midSet.setDrawValues(false);
        midSet.enableDashedLine(4f, 4f, 0f);

        LineData lineData = new LineData(rsiSet, obSet, osSet, midSet);

        // Y 轴始终 0-100
        rsiChart.getAxisLeft().setAxisMinimum(0);
        rsiChart.getAxisLeft().setAxisMaximum(100);

        rsiChart.setData(lineData);
        rsiChart.notifyDataSetChanged();
        rsiChart.fitScreen();
        rsiChart.invalidate();
    }
    public static void start(Context context, String symbol, String stockName) {
        Intent intent = new Intent(context, DetailActivity.class);
        intent.putExtra(Constants.EXTRA_SYMBOL, symbol);
        intent.putExtra(Constants.EXTRA_STOCK_NAME, stockName);
        context.startActivity(intent);
    }
}
