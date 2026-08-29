package com.tradingai.app;

import java.util.ArrayList;
import java.util.List;

/** Real technical-indicator math over OHLCV candle history. No external deps. */
public class Indicators {

    public static double ema(List<Double> closes, int period) {
        if (closes.isEmpty()) return 0;
        int start = Math.max(0, closes.size() - period * 3);
        double k = 2.0 / (period + 1);
        double e = closes.get(start);
        for (int i = start + 1; i < closes.size(); i++) e = closes.get(i) * k + e * (1 - k);
        return e;
    }

    public static double rsi(List<Double> closes, int period) {
        if (closes.size() < period + 1) return 50;
        double gain = 0, loss = 0;
        for (int i = closes.size() - period; i < closes.size(); i++) {
            double d = closes.get(i) - closes.get(i - 1);
            if (d >= 0) gain += d; else loss -= d;
        }
        if (loss == 0) return 100;
        double rs = gain / loss;
        return 100 - (100 / (1 + rs));
    }

    /** Returns {macdLine, signalLine, histogram} using standard 12/26/9 periods. */
    public static double[] macd(List<Double> closes) {
        if (closes.size() < 35) return new double[]{0, 0, 0};
        ArrayList<Double> macdSeries = new ArrayList<>();
        for (int i = 26; i <= closes.size(); i++) {
            List<Double> sub = closes.subList(0, i);
            macdSeries.add(ema(sub, 12) - ema(sub, 26));
        }
        double macdLine = macdSeries.get(macdSeries.size() - 1);
        double signalLine = ema(macdSeries, 9);
        return new double[]{macdLine, signalLine, macdLine - signalLine};
    }

    /** Average True Range over the last `period` candles. hlc[i] = {high, low, close}. */
    public static double atr(List<double[]> hlc, int period) {
        if (hlc.size() < period + 1) return 0;
        double sum = 0;
        for (int i = hlc.size() - period; i < hlc.size(); i++) {
            double h = hlc.get(i)[0], l = hlc.get(i)[1], prevClose = hlc.get(i - 1)[2];
            double tr = Math.max(h - l, Math.max(Math.abs(h - prevClose), Math.abs(l - prevClose)));
            sum += tr;
        }
        return sum / period;
    }

    /** Returns {upperBand, middleBand, lowerBand} using a 20-period SMA and 2 std-dev. */
    public static double[] bollinger(List<Double> closes, int period) {
        if (closes.size() < period) return new double[]{0, 0, 0};
        int start = closes.size() - period;
        double sum = 0;
        for (int i = start; i < closes.size(); i++) sum += closes.get(i);
        double mean = sum / period;
        double variance = 0;
        for (int i = start; i < closes.size(); i++) variance += Math.pow(closes.get(i) - mean, 2);
        double sd = Math.sqrt(variance / period);
        return new double[]{mean + 2 * sd, mean, mean - 2 * sd};
    }
}
