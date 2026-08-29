package com.tradingai.app;

import android.os.Handler;
import android.os.Looper;
import okhttp3.*;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;

/**
 * Streams live ticker + kline (candle) updates from Binance's public
 * combined WebSocket endpoint. Auto-reconnects on drop/failure.
 * No API key required — public market data only.
 */
public class LiveFeed {

    public interface Listener {
        void onTicker(double lastPrice, double changePercent);
        void onKline(double open, double high, double low, double close, double volume, boolean candleClosed);
        void onStatus(String status); // "Live", "Connecting…", "Reconnecting…", "Offline"
    }

    private final OkHttpClient client;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private WebSocket socket;
    private String currentStreams = "";
    private boolean active = false;

    public LiveFeed(Listener listener) {
        this.listener = listener;
        this.client = new OkHttpClient.Builder()
                .pingInterval(15, TimeUnit.SECONDS)
                .build();
    }

    /** Switch the live feed to a new symbol/timeframe. Safe to call repeatedly. */
    public void subscribe(String symbol, String interval) {
        String streams = symbol.toLowerCase() + "@ticker/" + symbol.toLowerCase() + "@kline_" + interval;
        if (streams.equals(currentStreams) && socket != null) return;
        currentStreams = streams;
        active = true;
        open(streams);
    }

    private void open(String streams) {
        if (socket != null) socket.cancel();
        main.post(() -> listener.onStatus("Connecting…"));
        String url = "wss://stream.binance.com:9443/stream?streams=" + streams;
        Request request = new Request.Builder().url(url).build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) {
                main.post(() -> listener.onStatus("Live"));
            }
            @Override public void onMessage(WebSocket ws, String text) {
                handleMessage(text);
            }
            @Override public void onFailure(WebSocket ws, Throwable t, Response response) {
                main.post(() -> listener.onStatus("Reconnecting…"));
                scheduleRetry(streams);
            }
            @Override public void onClosed(WebSocket ws, int code, String reason) {
                if (active) scheduleRetry(streams);
            }
        });
    }

    private void scheduleRetry(String streams) {
        main.postDelayed(() -> {
            if (active && streams.equals(currentStreams)) open(streams);
        }, 3000);
    }

    private void handleMessage(String text) {
        try {
            JSONObject root = new JSONObject(text);
            String stream = root.optString("stream", "");
            JSONObject data = root.optJSONObject("data");
            if (data == null) return;
            if (stream.contains("@ticker")) {
                double last = data.optDouble("c", 0);
                double pct = data.optDouble("P", 0);
                main.post(() -> listener.onTicker(last, pct));
            } else if (stream.contains("@kline")) {
                JSONObject k = data.optJSONObject("k");
                if (k == null) return;
                double o = k.optDouble("o"), h = k.optDouble("h"), l = k.optDouble("l"),
                        c = k.optDouble("c"), v = k.optDouble("v");
                boolean closed = k.optBoolean("x", false);
                main.post(() -> listener.onKline(o, h, l, c, v, closed));
            }
        } catch (Exception ignored) { /* malformed frame, skip */ }
    }

    public void stop() {
        active = false;
        currentStreams = "";
        if (socket != null) socket.cancel();
    }
}
