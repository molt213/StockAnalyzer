package com.stockanalyzer.ui.settings;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.stockanalyzer.util.DataConfig;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.stockanalyzer.BuildConfig;
import com.stockanalyzer.R;
import com.stockanalyzer.StockAnalyzerApp;
import com.stockanalyzer.data.repository.AIRepository;
import com.stockanalyzer.data.repository.StockRepository;
import com.stockanalyzer.data.remote.RetrofitClient;
import com.stockanalyzer.util.Constants;

/**
 * 设置 Fragment
 * 管理API配置、显示设置和数据
 */
public class SettingsFragment extends Fragment {

    private TextInputEditText inputAiKey, inputAiUrl, inputAiModel;
    private SwitchMaterial switchDarkMode;
    private MaterialButton btnClearHistory;
    private RadioGroup dataSourceGroup;

    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = StockAnalyzerApp.getInstance().getPreferences();

        initViews(view);
        loadSavedSettings();
        setupListeners();
    }

    private void initViews(View view) {
        inputAiKey = view.findViewById(R.id.input_ai_key);
        inputAiUrl = view.findViewById(R.id.input_ai_url);
        inputAiModel = view.findViewById(R.id.input_ai_model);
        switchDarkMode = view.findViewById(R.id.switch_dark_mode);
        btnClearHistory = view.findViewById(R.id.btn_clear_history);
        dataSourceGroup = view.findViewById(R.id.data_source_group);

        // 显示版本号
        TextView versionText = view.findViewById(R.id.version_text);
        if (versionText != null) {
            versionText.setText("版本: " + BuildConfig.VERSION_NAME);
        }

        // 隐藏toolbar (使用fragment的标题)
        com.google.android.material.appbar.MaterialToolbar toolbar = view.findViewById(R.id.settings_toolbar);
        if (toolbar != null) {
            toolbar.setVisibility(View.GONE);
        }
    }

    private void loadSavedSettings() {
        inputAiKey.setText(prefs.getString(Constants.PREF_AI_API_KEY, ""));
        inputAiUrl.setText(prefs.getString(Constants.PREF_AI_BASE_URL, BuildConfig.AI_API_BASE_URL));
        inputAiModel.setText(prefs.getString(Constants.PREF_AI_MODEL, BuildConfig.AI_MODEL));
        switchDarkMode.setChecked(prefs.getBoolean(Constants.PREF_DARK_MODE, false));

        // 加载数据源选择
        String source = DataConfig.getCurrentSource();
        if (DataConfig.SOURCE_EASTMONEY.equals(source)) {
            dataSourceGroup.check(R.id.source_eastmoney);
        } else if (DataConfig.SOURCE_TENCENT.equals(source)) {
            dataSourceGroup.check(R.id.source_tencent);
        } else {
            dataSourceGroup.check(R.id.source_sina);
        }

    }

    private void setupListeners() {
        // 文本变化自动保存
        View.OnFocusChangeListener autoSaveListener = (v, hasFocus) -> {
            if (!hasFocus) {
                saveAllSettings();
            }
        };

        inputAiKey.setOnFocusChangeListener(autoSaveListener);
        inputAiUrl.setOnFocusChangeListener(autoSaveListener);
        inputAiModel.setOnFocusChangeListener(autoSaveListener);

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(Constants.PREF_DARK_MODE, isChecked).apply();
            // 立即重启 Activity 应用新主题
            requireActivity().recreate();
        });

        dataSourceGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.source_tencent) {
                DataConfig.setSource(DataConfig.SOURCE_TENCENT);
                Toast.makeText(requireContext(), "已切换到腾讯数据源（不易被封）", Toast.LENGTH_SHORT).show();
            } else if (checkedId == R.id.source_eastmoney) {
                DataConfig.setSource(DataConfig.SOURCE_EASTMONEY);
                Toast.makeText(requireContext(), "已切换到东方财富数据源", Toast.LENGTH_SHORT).show();
            } else {
                DataConfig.setSource(DataConfig.SOURCE_SINA);
                Toast.makeText(requireContext(), "已切换到新浪数据源（基础行情）", Toast.LENGTH_SHORT).show();
            }
        });

        btnClearHistory.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("确认清除")
                    .setMessage("确定要清除所有AI分析历史记录吗？")
                    .setPositiveButton("确认", (dialog, which) -> {
                        AIRepository.getInstance().clearAllHistory();
                        Toast.makeText(requireContext(), "已清除分析历史", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        // 清除搜索历史
        Button btnClearSearch = requireView().findViewById(R.id.btn_clear_search);
        btnClearSearch.setOnClickListener(v -> {
            requireContext().getSharedPreferences("search_history", 0)
                    .edit().clear().apply();
            Toast.makeText(requireContext(), "搜索历史已清除", Toast.LENGTH_SHORT).show();
        });

        Button btnDiagnose = requireView().findViewById(R.id.btn_api_diagnose);
        btnDiagnose.setOnClickListener(v -> {
            btnDiagnose.setEnabled(false);
            btnDiagnose.setText("诊断中...");
            StockRepository.getInstance().checkApiStatus(new StockRepository.RepositoryCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    btnDiagnose.setEnabled(true);
                    btnDiagnose.setText("API 连通性诊断");
                    TextView tv = new TextView(requireContext());
                    tv.setText(result);
                    tv.setTextSize(13f);
                    tv.setPadding(40, 20, 40, 10);
                    tv.setTextIsSelectable(true);
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("诊断结果（可长按复制）")
                            .setView(tv)
                            .setPositiveButton("复制并关闭", (d, w) -> {
                                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                                        requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("diagnose", result));
                                Toast.makeText(requireContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("关闭", null)
                            .show();
                }
                @Override
                public void onError(Exception e) {
                    btnDiagnose.setEnabled(true);
                    btnDiagnose.setText("API 连通性诊断");
                    Toast.makeText(requireContext(), "诊断失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void saveAllSettings() {
        String aiKey = inputAiKey.getText() != null ? inputAiKey.getText().toString().trim() : "";
        String aiUrl = inputAiUrl.getText() != null ? inputAiUrl.getText().toString().trim() : BuildConfig.AI_API_BASE_URL;
        String aiModel = inputAiModel.getText() != null ? inputAiModel.getText().toString().trim() : BuildConfig.AI_MODEL;
        prefs.edit()
                .putString(Constants.PREF_AI_API_KEY, aiKey)
                .putString(Constants.PREF_AI_BASE_URL, aiUrl)
                .putString(Constants.PREF_AI_MODEL, aiModel)
                .apply();

        // 重新初始化网络客户端
        RetrofitClient.reinitialize();

        Toast.makeText(requireContext(), R.string.saved, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPause() {
        super.onPause();
        saveAllSettings();
    }
}
