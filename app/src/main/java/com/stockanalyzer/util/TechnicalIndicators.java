package com.stockanalyzer.util;

import com.stockanalyzer.data.model.StockDetail;

import java.util.ArrayList;
import java.util.List;

/**
 * 技术指标计算工具类
 * 提供 MACD、RSI 等常用技术指标的本地计算
 */
public class TechnicalIndicators {

    /**
     * MACD 计算结果
     * 三个列表长度与输入 K 线数据相同，前导无效值设为 0
     */
    public static class MACDResult {
        public final List<Double> dif;    // DIF 快线 = EMA(12) - EMA(26)
        public final List<Double> dea;    // DEA 慢线 = EMA(DIF, 9)
        public final List<Double> macd;   // MACD 柱 = 2 × (DIF - DEA)

        public MACDResult(List<Double> dif, List<Double> dea, List<Double> macd) {
            this.dif = dif;
            this.dea = dea;
            this.macd = macd;
        }
    }

    /**
     * 计算 MACD（经典参数 12, 26, 9）
     * @param data K 线数据列表（按时间升序）
     * @return MACDResult，每个列表长度等于 data.size()
     */
    public static MACDResult calculateMACD(List<StockDetail.CandleData> data) {
        return calculateMACD(data, 12, 26, 9);
    }

    /**
     * 计算 MACD（自定义参数）
     */
    public static MACDResult calculateMACD(List<StockDetail.CandleData> data,
                                            int shortPeriod, int longPeriod, int signalPeriod) {
        int n = data.size();
        List<Double> dif = new ArrayList<>(n);
        List<Double> dea = new ArrayList<>(n);
        List<Double> macd = new ArrayList<>(n);

        if (n < longPeriod + 1) {
            for (int i = 0; i < n; i++) {
                dif.add(0.0);
                dea.add(0.0);
                macd.add(0.0);
            }
            return new MACDResult(dif, dea, macd);
        }

        // 1. EMA(shortPeriod) 和 EMA(longPeriod)
        List<Double> emaShort = calculateEMA(data, shortPeriod);
        List<Double> emaLong = calculateEMA(data, longPeriod);

        // 2. DIF = EMA(12) - EMA(26)
        for (int i = 0; i < n; i++) {
            dif.add(emaShort.get(i) - emaLong.get(i));
        }

        // 3. DEA = EMA(DIF, signalPeriod)
        for (int i = 0; i < n; i++) {
            if (i < signalPeriod - 1) {
                dea.add(0.0);
                macd.add(0.0);
                continue;
            }
            if (i == signalPeriod - 1) {
                double sum = 0;
                for (int j = 0; j < signalPeriod; j++) {
                    sum += dif.get(j);
                }
                dea.add(sum / signalPeriod);
            } else {
                double k = 2.0 / (signalPeriod + 1);
                dea.add(dif.get(i) * k + dea.get(i - 1) * (1 - k));
            }
            macd.add(2.0 * (dif.get(i) - dea.get(i)));
        }

        return new MACDResult(dif, dea, macd);
    }

    /**
     * 计算 EMA（指数移动平均）
     */
    private static List<Double> calculateEMA(List<StockDetail.CandleData> data, int period) {
        int n = data.size();
        List<Double> ema = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            double close = data.get(i).getClose();
            if (i < period - 1) {
                double sum = 0;
                for (int j = 0; j <= i; j++) sum += data.get(j).getClose();
                ema.add(sum / (i + 1));
            } else if (i == period - 1) {
                double sum = 0;
                for (int j = 0; j < period; j++) sum += data.get(j).getClose();
                ema.add(sum / period);
            } else {
                double k = 2.0 / (period + 1);
                ema.add(close * k + ema.get(i - 1) * (1 - k));
            }
        }
        return ema;
    }

    /**
     * 计算 RSI（相对强弱指标，默认 14 日）
     * @param data K 线数据列表（按时间升序）
     * @return RSI 值列表，前导无效为 50（中性值），长度 = data.size()
     */
    public static List<Double> calculateRSI(List<StockDetail.CandleData> data) {
        return calculateRSI(data, 14);
    }

    /**
     * 计算 RSI（自定义周期）
     */
    public static List<Double> calculateRSI(List<StockDetail.CandleData> data, int period) {
        int n = data.size();
        List<Double> rsi = new ArrayList<>(n);

        if (n < period + 1) {
            for (int i = 0; i < n; i++) rsi.add(50.0);
            return rsi;
        }

        // 计算每日涨跌
        double[] gains = new double[n];
        double[] losses = new double[n];
        for (int i = 1; i < n; i++) {
            double change = data.get(i).getClose() - data.get(i - 1).getClose();
            gains[i] = Math.max(change, 0);
            losses[i] = Math.max(-change, 0);
        }

        double prevAvgGain = 0, prevAvgLoss = 0;

        for (int i = 0; i < n; i++) {
            if (i < period) {
                rsi.add(50.0);
                continue;
            }

            double avgGain, avgLoss;
            if (i == period) {
                double sumGain = 0, sumLoss = 0;
                for (int j = 1; j <= period; j++) {
                    sumGain += gains[j];
                    sumLoss += losses[j];
                }
                avgGain = sumGain / period;
                avgLoss = sumLoss / period;
            } else {
                avgGain = (prevAvgGain * (period - 1) + gains[i]) / period;
                avgLoss = (prevAvgLoss * (period - 1) + losses[i]) / period;
            }
            prevAvgGain = avgGain;
            prevAvgLoss = avgLoss;

            double rs = (avgLoss == 0) ? 100 : avgGain / avgLoss;
            rsi.add(100.0 - (100.0 / (1.0 + rs)));
        }

        return rsi;
    }
}
