package com.stockanalyzer.util;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 格式化工具类
 */
public class FormatUtils {

    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("+0.00%;-0.00%");
    private static final DecimalFormat VOLUME_FORMAT = new DecimalFormat("#,##0");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("MM-dd HH:mm", Locale.US);

    /**
     * 格式化价格（带$符号）
     */
    public static String formatPrice(double price) {
        return "¥" + PRICE_FORMAT.format(price);
    }

    /**
     * 格式化涨跌额
     */
    public static String formatChange(double change) {
        return (change >= 0 ? "+" : "") + PRICE_FORMAT.format(change);
    }

    /**
     * 格式化涨跌幅
     */
    public static String formatChangePercent(double changePercent) {
        return String.format(Locale.US, "%+.2f%%", changePercent);
    }

    /**
     * 格式化成交量
     */
    public static String formatVolume(long volume) {
        if (volume >= 1_000_000_000) {
            return String.format(Locale.US, "%.2fB", volume / 1_000_000_000.0);
        } else if (volume >= 1_000_000) {
            return String.format(Locale.US, "%.2fM", volume / 1_000_000.0);
        } else if (volume >= 1_000) {
            return String.format(Locale.US, "%.2fK", volume / 1_000.0);
        }
        return String.valueOf(volume);
    }

    /**
     * 格式化市值
     */
    public static String formatMarketCap(double marketCap) {
        if (marketCap >= 1_000_000_000_000d) {
            return String.format(Locale.US, "$%.2fT", marketCap / 1_000_000_000_000d);
        } else if (marketCap >= 1_000_000_000) {
            return String.format(Locale.US, "$%.2fB", marketCap / 1_000_000_000);
        } else if (marketCap >= 1_000_000) {
            return String.format(Locale.US, "$%.2fM", marketCap / 1_000_000);
        }
        return "¥" + PRICE_FORMAT.format(marketCap);
    }

    /**
     * 格式化日期
     */
    public static String formatDate(long timestamp) {
        return DATE_FORMAT.format(new Date(timestamp * 1000));
    }

    /**
     * 格式化时间
     */
    public static String formatTime(long timestamp) {
        return TIME_FORMAT.format(new Date(timestamp * 1000));
    }

    /**
     * 格式化日期时间 (MM-dd HH:mm)
     */
    public static String formatDateTime(long timestamp) {
        return DATE_TIME_FORMAT.format(new Date(timestamp * 1000));
    }

    /**
     * 格式化时间戳为相对时间
     */
    public static String formatRelativeTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        if (diff < 60_000) {
            return "刚刚";
        } else if (diff < 3600_000) {
            return (diff / 60_000) + "分钟前";
        } else if (diff < 86400_000) {
            return (diff / 3600_000) + "小时前";
        } else if (diff < 604800_000) {
            return (diff / 86400_000) + "天前";
        } else {
            return formatDate(timestamp / 1000);
        }
    }

    /**
     * 截取数字到指定小数位
     */
    public static double roundTo(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
