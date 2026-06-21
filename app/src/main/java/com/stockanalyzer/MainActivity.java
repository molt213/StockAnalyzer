package com.stockanalyzer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

/**
 * 主Activity
 * 包含底部导航和侧边栏
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private NavController navController;
    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 应用暗色主题（必须在 super.onCreate 之前设置）
        SharedPreferences prefs = getSharedPreferences("stock_analyzer_prefs", MODE_PRIVATE);
        if (prefs.getBoolean("dark_mode", false)) {
            setTheme(R.style.Theme_StockAnalyzer_Dark);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 设置工具栏
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        // 初始化导航
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navView = findViewById(R.id.nav_view);

        // 通过 FragmentManager 获取 NavHostFragment 和 NavController
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment == null) {
            Log.e(TAG, "NavHostFragment 为空");
            return;
        }

        navController = navHostFragment.getNavController();

        if (navController == null) {
            Log.e(TAG, "NavController 为空");
            return;
        }

        // 配置 AppBar（标识哪些页面是顶层页面，显示汉堡菜单）
        AppBarConfiguration appBarConfig = new AppBarConfiguration.Builder(
                R.id.nav_dashboard, R.id.nav_search,
                R.id.nav_ai_analysis, R.id.nav_settings)
                .setOpenableLayout(drawerLayout)
                .build();

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfig);

        // 绑定底部导航栏
        NavigationUI.setupWithNavController(bottomNav, navController);

        // 绑定侧边栏
        NavigationUI.setupWithNavController(navView, navController);

        // 监听导航变化更新标题
        navController.addOnDestinationChangedListener((controller, dest, arguments) -> {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(dest.getLabel());
            }
        });

        Log.d(TAG, "MainActivity 启动完成");
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (navController != null && drawerLayout != null) {
            return NavigationUI.navigateUp(navController, drawerLayout)
                    || super.onSupportNavigateUp();
        }
        return super.onSupportNavigateUp();
    }
}
