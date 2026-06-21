package com.stockanalyzer.ui.detail;

import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.stockanalyzer.StockAnalyzerApp;
import com.stockanalyzer.data.model.Stock;
import com.stockanalyzer.data.model.StockDetail;
import com.stockanalyzer.data.remote.dto.StockResponse;
import com.stockanalyzer.data.repository.StockRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 股票详情 ViewModel
 */
public class DetailViewModel extends ViewModel {

    private static final String TAG = "DetailViewModel";

    private final StockRepository repository = StockRepository.getInstance();

    public final MutableLiveData<Stock> quoteData = new MutableLiveData<>();
    public final MutableLiveData<StockDetail> companyData = new MutableLiveData<>();
    public final MutableLiveData<List<StockDetail.CandleData>> chartData = new MutableLiveData<>();
    public final MutableLiveData<List<StockDetail.NewsItem>> newsData = new MutableLiveData<>();
    public final MutableLiveData<StockResponse.MetricsData> metricsData = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isInWatchlist = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    public final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private String currentSymbol;
    private boolean quoteLoaded = false;
    private boolean companyLoaded = false;
    private boolean chartLoaded = false;

    public void loadStockData(String symbol) {
        this.currentSymbol = symbol;
        quoteLoaded = false;
        companyLoaded = false;
        isLoading.setValue(true);

        // 检查自选股状态
        isInWatchlist.setValue(repository.isInWatchlist(symbol));

        // 并发加载各项数据
        loadQuote(symbol);
        loadCompanyProfile(symbol);
        loadMetrics(symbol);
        loadNews(symbol);
    }

    public void loadChartData(String symbol, String resolution, int days) {
        repository.getHistoricalData(symbol, resolution, days,
                new StockRepository.RepositoryCallback<List<StockDetail.CandleData>>() {
            @Override
            public void onSuccess(List<StockDetail.CandleData> data) {
                chartData.postValue(data);
                chartLoaded = true;
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "加载图表数据失败", e);
                chartData.postValue(new ArrayList<>());
                chartLoaded = true;
            }
        });
    }

    private void loadQuote(String symbol) {
        repository.getQuote(symbol, new StockRepository.RepositoryCallback<Stock>() {
            @Override
            public void onSuccess(Stock stock) {
                Log.d(TAG, "行情加载成功: " + stock.getCurrentPrice());
                quoteData.setValue(stock);
                quoteLoaded = true;
                finishLoading();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "加载报价失败", e);
                errorMessage.setValue("获取报价失败: " + e.getMessage());
                quoteLoaded = true;
                finishLoading();
            }
        });
    }

    private void loadCompanyProfile(String symbol) {
        repository.getCompanyProfile(symbol, new StockRepository.RepositoryCallback<StockDetail>() {
            @Override
            public void onSuccess(StockDetail detail) {
                companyData.setValue(detail);
                companyLoaded = true;
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "加载公司信息失败", e);
                companyLoaded = true;
            }
        });
    }

    private void loadMetrics(String symbol) {
        repository.getMetrics(symbol, new StockRepository.RepositoryCallback<StockResponse.MetricsData>() {
            @Override
            public void onSuccess(StockResponse.MetricsData data) {
                metricsData.setValue(data);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "加载指标失败", e);
            }
        });
    }

    private void loadNews(String symbol) {
        repository.getCompanyNews(symbol, 7,
                new StockRepository.RepositoryCallback<List<StockDetail.NewsItem>>() {
            @Override
            public void onSuccess(List<StockDetail.NewsItem> items) {
                newsData.setValue(items);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "加载新闻失败", e);
                newsData.setValue(new ArrayList<>());
            }
        });
    }

    private void finishLoading() {
        if (quoteLoaded) {
            isLoading.setValue(false);
        }
    }

    public void toggleWatchlist() {
        if (currentSymbol == null) return;

        Boolean inList = isInWatchlist.getValue();
        if (inList != null && inList) {
            repository.removeFromWatchlist(currentSymbol);
            isInWatchlist.setValue(false);
            errorMessage.setValue("已从自选股移除");
        } else {
            StockDetail detail = companyData.getValue();
            String name = detail != null ? detail.getName() : currentSymbol;
            boolean added = repository.addToWatchlist(currentSymbol, name);
            if (added) {
                isInWatchlist.setValue(true);
                errorMessage.setValue("已添加到自选股");
            } else {
                errorMessage.setValue("自选股已达上限(10只)，请先移除再添加");
            }
        }
    }

    public String getCurrentSymbol() {
        return currentSymbol;
    }

    public void setCurrentSymbol(String symbol) {
        this.currentSymbol = symbol;
    }
}
