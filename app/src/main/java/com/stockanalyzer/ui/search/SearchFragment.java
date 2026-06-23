package com.stockanalyzer.ui.search;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.stockanalyzer.R;
import com.stockanalyzer.adapter.StockAdapter;
import com.stockanalyzer.data.model.Stock;
import com.stockanalyzer.data.repository.StockRepository;
import com.stockanalyzer.ui.detail.DetailActivity;
import com.stockanalyzer.util.NetworkUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索Fragment - 搜索股票
 */
public class SearchFragment extends Fragment {

    private EditText searchInput;
    private ImageButton clearButton;
    private RecyclerView searchResults;
    private View historyLayout, searchLoading, noHistory;
    private RecyclerView historyList;

    private StockAdapter resultAdapter;
    private StockRepository repository;

    // 搜索历史（持久化到本地）
    private final List<Stock> searchHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 20;
    private SharedPreferences historyPrefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = StockRepository.getInstance();
        historyPrefs = requireContext().getSharedPreferences("search_history", 0);

        initViews(view);
        setupSearchInput();
        setupResultAdapter();
        loadSearchHistory();
        updateHistoryView();
    }

    private void initViews(View view) {
        searchInput = view.findViewById(R.id.search_input);
        clearButton = view.findViewById(R.id.clear_search);
        searchResults = view.findViewById(R.id.search_results);
        historyLayout = view.findViewById(R.id.history_layout);
        searchLoading = view.findViewById(R.id.search_loading);
        noHistory = view.findViewById(R.id.no_history);
        historyList = view.findViewById(R.id.history_list);
    }

    private void setupSearchInput() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (s.length() == 0) {
                    showHistoryView();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = searchInput.getText().toString().trim();
                if (query.isEmpty()) {
                    return true;
                }
                // 支持：A股代码(600519)、ETF代码(518880)、商品代码(159934)、名称搜索(黄金、石油、茅台)
                performSearch(query);
                return true;
            }
            return false;
        });

        clearButton.setOnClickListener(v -> {
            searchInput.setText("");
            searchResults.setVisibility(View.GONE);
        });
    }

    private void setupResultAdapter() {
        resultAdapter = new StockAdapter();
        resultAdapter.setShowPrice(true);
        resultAdapter.setOnStockClickListener(stock -> {
            addToHistory(stock);
            DetailActivity.start(requireContext(), stock.getSymbol(), stock.getName());
        });

        searchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        searchResults.setAdapter(resultAdapter);
    }

    private void performSearch(String query) {
        Log.d("SearchFragment", "performSearch: " + query);
        if (!NetworkUtils.checkNetworkWithToast()) {
            Log.d("SearchFragment", "网络不可用");
            return;
        }

        searchLoading.setVisibility(View.VISIBLE);
        searchResults.setVisibility(View.GONE);
        historyLayout.setVisibility(View.GONE);

        final String searchQuery = query.toUpperCase().trim();

        repository.searchStocks(searchQuery, new StockRepository.RepositoryCallback<List<Stock>>() {
            @Override
            public void onSuccess(List<Stock> data) {
                Log.d("SearchFragment", "搜索结果: " + (data != null ? data.size() : "null") + "条");
                requireActivity().runOnUiThread(() -> {
                    searchLoading.setVisibility(View.GONE);
                    if (data.isEmpty()) {
                        Toast.makeText(requireContext(),
                                R.string.search_no_results, Toast.LENGTH_SHORT).show();
                        showHistoryView();
                    } else {
                        resultAdapter.submitList(data);
                        searchResults.setVisibility(View.VISIBLE);
                        // 将第一个搜索结果加入历史
                        if (!data.isEmpty()) addToHistory(data.get(0));
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                requireActivity().runOnUiThread(() -> {
                    searchLoading.setVisibility(View.GONE);
                    Toast.makeText(requireContext(),
                            "搜索失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    showHistoryView();
                });
            }
        });
    }

    private void loadSearchHistory() {
        String json = historyPrefs.getString("history", "[]");
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject item = arr.getJSONObject(i);
                String sym = item.optString("sym", "");
                String name = item.optString("name", "");
                if (!sym.isEmpty()) searchHistory.add(new Stock(sym, name));
            }
        } catch (Exception e) { /* ignore */ }
    }

    private void saveSearchHistory() {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (Stock s : searchHistory) {
                org.json.JSONObject item = new org.json.JSONObject();
                item.put("sym", s.getSymbol());
                item.put("name", s.getName());
                arr.put(item);
            }
            historyPrefs.edit().putString("history", arr.toString()).apply();
        } catch (Exception e) { /* ignore */ }
    }

    private void addToHistory(Stock stock) {
        // 去重
        for (int i = 0; i < searchHistory.size(); i++) {
            if (searchHistory.get(i).getSymbol().equals(stock.getSymbol())) {
                searchHistory.remove(i);
                break;
            }
        }
        searchHistory.add(0, stock);
        if (searchHistory.size() > MAX_HISTORY) {
            searchHistory.remove(searchHistory.size() - 1);
        }
        saveSearchHistory();
        updateHistoryView();
    }

    private void updateHistoryView() {
        if (searchHistory.isEmpty()) {
            noHistory.setVisibility(View.VISIBLE);
            historyList.setVisibility(View.GONE);
        } else {
            noHistory.setVisibility(View.GONE);
            historyList.setVisibility(View.VISIBLE);

            historyList.removeAllViews();
            historyList.setLayoutManager(new LinearLayoutManager(requireContext()));
            StockAdapter historyAdapter = new StockAdapter();
            historyAdapter.setShowPrice(false);
            historyAdapter.setOnStockClickListener(stock -> {
                searchInput.setText(stock.getSymbol());
                performSearch(stock.getSymbol());
            });

            List<Stock> historyStocks = new ArrayList<>(searchHistory);
            historyAdapter.submitList(historyStocks);
            historyList.setAdapter(historyAdapter);
        }
    }

    private void showHistoryView() {
        searchResults.setVisibility(View.GONE);
        searchLoading.setVisibility(View.GONE);
        historyLayout.setVisibility(View.VISIBLE);
        updateHistoryView();
    }
}
