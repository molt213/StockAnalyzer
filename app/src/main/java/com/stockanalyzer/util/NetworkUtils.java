package com.stockanalyzer.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

import com.stockanalyzer.StockAnalyzerApp;

/**
 * 网络工具类
 */
public class NetworkUtils {

    /**
     * 检查网络是否可用
     */
    public static boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager)
                StockAnalyzerApp.getInstance().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
        return false;
    }

    /**
     * 检查网络并提示
     */
    public static boolean checkNetworkWithToast() {
        if (!isNetworkAvailable()) {
            Toast.makeText(StockAnalyzerApp.getInstance(),
                    "网络不可用，请检查网络连接", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * 是否是WiFi连接
     */
    public static boolean isWifiConnected() {
        ConnectivityManager cm = (ConnectivityManager)
                StockAnalyzerApp.getInstance().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            return wifi != null && wifi.isConnected();
        }
        return false;
    }
}
