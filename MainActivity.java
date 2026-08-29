package com.tradingai.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class MainActivity extends Activity implements LiveFeed.Listener {

    // ---- Palette ----
    static final int BG = Color.rgb(11, 14, 20), CARD = Color.rgb(19, 23, 31), CARD2 = Color.rgb(28, 33, 43),
            WHITE = Color.WHITE, MUTED = Color.rgb(145, 153, 169), GOLD = Color.rgb(240, 185, 11),
            GREEN = Color.rgb(14, 203, 129), RED = Color.rgb(246, 70, 93), BLUE = Color.rgb(64, 150, 255);

    LinearLayout body, bottom;
    TextView liveBadge, headerPrice, headerChange;
    Handler main = new Handler(Looper.getMainLooper());
    String tab = "Home", symbol = "BTCUSDT", interval = "15m";
    ArrayList<String> symbols = new ArrayList<>();
    HashMap<String, Double> px = new HashMap<>(), chg = new HashMap<>();
    ArrayList<Candle> candles = new ArrayList<>();
    ArrayList<Book> bids = new ArrayList<>(), asks = new ArrayList<>();
    Demo demo = new Demo();
    LiveFeed live;
    ArrayList<Double> equityHistory = new ArrayList<>();
    long lastEquitySnapshot = 0;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.load(this, demo);
        live = new LiveFeed(this);
        buildShell();
        loadExchange();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Prefs.save(this, demo);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        live.stop();
        Prefs.save(this, demo);
    }

    // ---------------- UI helpers ----------------
    TextView t(String s, float z, int c) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(z); v.setTextColor(c);
        v.setGravity(Gravity.CENTER_VERTICAL); v.setPadding(8, 4, 8, 4); return v;
    }
    GradientDrawable bg(int c, float r) { GradientDrawable g = new GradientDrawable(); g.setColor(c); g.setCornerRadius(r); return g; }
    LinearLayout box() {
        LinearLayout x = new LinearLayout(this); x.setOrientation(LinearLayout.VERTICAL);
        x.setPadding(12, 10, 12, 10); x.setBackground(bg(CARD, 18));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 0, 0, 9);
        x.setLayoutParams(p); return x;
    }
    Button btn(String s) {
        Button b = new Button(this); b.setText(s); b.setTextSize(11); b.setTextColor(WHITE);
        b.setAllCaps(false); b.setBackground(bg(CARD2, 14)); return b;
    }
    EditText input(String hint, String value) {
        EditText e = new EditText(this); e.setSingleLine(); e.setHint(hint); e.setHintTextColor(MUTED);
        e.setTextColor(WHITE); if (value != null) e.setText(value); e.setInputType(8194);
        e.setBackground(bg(CARD2, 14)); e.setPadding(12, 0, 12, 0); return e;
    }
    void add(LinearLayout x, String s, float z, int c) { x.addView(t(s, z, c)); }
    int leverageValue(Spinner sp) {
        try { return Integer.parseInt(sp.getSelectedItem().toString().replace("x", "")); } catch (Exception e) { return 1; }
    }

    void buildShell() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this); top.setPadding(8, 8, 8, 3); top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout title = new LinearLayout(this); title.setOrientation(LinearLayout.VERTICAL);
        add(title, "Trading AI Pro", 18, WHITE);
        LinearLayout sub = new LinearLayout(this);
        sub.addView(t("SPOT • PAPER TRADING • AI", 8, MUTED));
        liveBadge = t("● Connecting…", 8, MUTED); sub.addView(liveBadge);
        title.addView(sub);
        top.addView(title, new LinearLayout.LayoutParams(0, 56, 1));
        TextView coin = t(symbol, 12, WHITE); coin.setGravity(Gravity.CENTER); coin.setBackground(bg(CARD2, 18));
        coin.setOnClickListener(v -> { tab = "Markets"; render(); });
        top.addView(coin, new LinearLayout.LayoutParams(104, 44));
        root.addView(top);

        LinearLayout ticker = new LinearLayout(this); ticker.setPadding(10, 0, 10, 6); ticker.setBackgroundColor(Color.rgb(14, 18, 25));
        headerPrice = t("--", 16, WHITE); headerChange = t("--", 11, MUTED);
        ticker.addView(headerPrice, new LinearLayout.LayoutParams(0, 40, 1));
        ticker.addView(headerChange, new LinearLayout.LayoutParams(110, 40));
        root.addView(ticker);

        ScrollView sc = new ScrollView(this);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(10, 6, 10, 14);
        sc.addView(body); root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));

        bottom = new LinearLayout(this); bottom.setPadding(2, 4, 2, 7); bottom.setBackgroundColor(Color.rgb(16, 20, 28));
        String[] nav = {"Home", "Markets", "Chart", "Trade", "AI", "Orders"};
        for (String n : nav) {
            TextView bt = t(n, 9, MUTED); bt.setGravity(Gravity.CENTER);
            bt.setOnClickListener(v -> { tab = n; render(); });
            bottom.addView(bt, new LinearLayout.LayoutParams(0, 54, 1));
        }
        root.addView(bottom);
        setContentView(root);
        refreshHeader();
    }

    void refreshHeader() {
        double v = px.getOrDefault(symbol, 0.0), d = chg.getOrDefault(symbol, 0.0);
        headerPrice.setText(v > 0 ? "$" + fmt(v) : "--");
        headerChange.setText(String.format(Locale.US, "%+.2f%%", d));
        headerChange.setTextColor(d >= 0 ? GREEN : RED);
    }

    // ---------------- LiveFeed.Listener (WebSocket callbacks, always main-thread) ----------------
    @Override public void onTicker(double lastPrice, double changePercent) {
        px.put(symbol, lastPrice); chg.put(symbol, changePercent);
        refreshHeader();

        ArrayList<String> executed = demo.checkExecutions(px);
        if (!executed.isEmpty()) {
            for (String m : executed) Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
            Prefs.save(this, demo);
        }

        long now = System.currentTimeMillis();
        if (now - lastEquitySnapshot > 5000) {
            lastEquitySnapshot = now;
            equityHistory.add(demo.equity(px));
            while (equityHistory.size() > 60) equityHistory.remove(0);
        }

        if (tab.equals("Home") || tab.equals("Chart") || tab.equals("AI")) render();
    }

    @Override public void onKline(double o, double h, double l, double c, double v, boolean closed) {
        if (candles.isEmpty()) return;
        Candle last = candles.get(candles.size() - 1);
        last.o = o; last.h = h; last.l = l; last.c = c; last.v = v;
        if (closed) candles.add(new Candle(c, c, c, c, 0)); // seed next candle
        if (tab.equals("Chart")) render();
    }

    @Override public void onStatus(String status) {
        liveBadge.setText("● " + status);
        liveBadge.setTextColor(status.equals("Live") ? GREEN : status.equals("Offline") ? RED : GOLD);
    }

    // ---------------- Tabs ----------------
    void render() {
        body.removeAllViews();
        if (tab.equals("Home")) home();
        else if (tab.equals("Markets")) markets();
        else if (tab.equals("Chart")) chart();
        else if (tab.equals("Trade")) trade();
        else if (tab.equals("AI")) ai();
        else orders();
    }

    void home() {
        LinearLayout p = box();
        add(p, "PAPER ACCOUNT EQUITY", 9, MUTED);
        add(p, String.format(Locale.US, "$%,.2f", demo.equity(px)), 29, WHITE);
        add(p, String.format(Locale.US, "P&L %+.2f USDT  •  %d trades  •  Win rate %.1f%%", demo.pnl, demo.completed(), demo.winRate()), 11, demo.pnl >= 0 ? GREEN : RED);
        if (equityHistory.size() > 1) p.addView(new EquityView(this), new LinearLayout.LayoutParams(-1, 80));
        body.addView(p);

        LinearLayout q = box();
        add(q, "QUICK TRADE", 10, MUTED);
        LinearLayout r = new LinearLayout(this);
        Button buy = btn("BUY"), sell = btn("SELL"), aiBtn = btn("AI SIGNAL");
        r.addView(buy, new LinearLayout.LayoutParams(0, 56, 1));
        r.addView(sell, new LinearLayout.LayoutParams(0, 56, 1));
        r.addView(aiBtn, new LinearLayout.LayoutParams(0, 56, 1));
        q.addView(r); body.addView(q);
        buy.setOnClickListener(v -> { tab = "Trade"; render(); });
        sell.setOnClickListener(v -> { tab = "Trade"; render(); });
        aiBtn.setOnClickListener(v -> { tab = "AI"; render(); });

        LinearLayout w = box();
        add(w, "WATCHLIST", 10, MUTED);
        for (int i = 0; i < Math.min(12, symbols.size()); i++) marketLine(w, symbols.get(i));
        body.addView(w);
    }

    void marketLine(LinearLayout p, String s) {
        LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL);
        TextView a = t(s.replace("USDT", " / USDT"), 12, WHITE),
                pr = t("$" + fmt(px.getOrDefault(s, 0.0)), 11, WHITE),
                c = t(String.format(Locale.US, "%+.2f%%", chg.getOrDefault(s, 0.0)), 11, chg.getOrDefault(s, 0.0) >= 0 ? GREEN : RED);
        r.addView(a, new LinearLayout.LayoutParams(0, 46, 1));
        r.addView(pr, new LinearLayout.LayoutParams(100, 46));
        r.addView(c, new LinearLayout.LayoutParams(78, 46));
        r.setOnClickListener(v -> { switchSymbol(s); tab = "Chart"; render(); });
        p.addView(r);
    }

    void markets() {
        add(body, "Markets", 23, WHITE);
        add(body, "Spot • USDT • live public market data", 9, MUTED);
        EditText search = input("Search BTC / ETH / SOL...", null);
        body.addView(search, new LinearLayout.LayoutParams(-1, 52));
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); body.addView(list);
        Runnable fill = () -> {
            list.removeAllViews();
            String q = search.getText().toString().trim().toUpperCase(Locale.US);
            int n = 0;
            for (String s : symbols) {
                if (!q.isEmpty() && !s.contains(q)) continue;
                marketCard(list, s);
                if (++n >= 150) break;
            }
        };
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { fill.run(); }
            public void afterTextChanged(Editable e) {}
        });
        fill.run();
    }

    void marketCard(LinearLayout list, String s) {
        LinearLayout c = box();
        LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL);
        TextView star = t(demo.favorites.contains(s) ? "★" : "☆", 22, demo.favorites.contains(s) ? GOLD : MUTED),
                n = t(s.replace("USDT", ""), 14, WHITE), q = t("USDT", 9, MUTED),
                pr = t("$" + fmt(px.getOrDefault(s, 0.0)), 12, WHITE),
                ch = t(String.format(Locale.US, "%+.2f%%", chg.getOrDefault(s, 0.0)), 11, chg.getOrDefault(s, 0.0) >= 0 ? GREEN : RED);
        LinearLayout name = new LinearLayout(this); name.setOrientation(LinearLayout.VERTICAL);
        name.addView(n); name.addView(q);
        r.addView(star, new LinearLayout.LayoutParams(38, 55));
        r.addView(name, new LinearLayout.LayoutParams(0, 55, 1));
        r.addView(pr, new LinearLayout.LayoutParams(105, 55));
        r.addView(ch, new LinearLayout.LayoutParams(78, 55));
        star.setOnClickListener(v -> { if (demo.favorites.contains(s)) demo.favorites.remove(s); else demo.favorites.add(s); render(); });
        c.addView(r);
        c.setOnClickListener(v -> { switchSymbol(s); tab = "Chart"; render(); });
        list.addView(c);
    }

    void chart() {
        LinearLayout h = box();
        add(h, symbol.replace("USDT", "/USDT"), 18, WHITE);
        add(h, "$" + fmt(px.getOrDefault(symbol, 0.0)) + "   24h " + String.format(Locale.US, "%+.2f%%", chg.getOrDefault(symbol, 0.0)), 11, chg.getOrDefault(symbol, 0.0) >= 0 ? GREEN : RED);
        LinearLayout times = new LinearLayout(this);
        for (String x : new String[]{"1m", "5m", "15m", "1h", "4h", "1d"}) {
            TextView bt = t(x, 10, x.equals(interval) ? GOLD : MUTED); bt.setGravity(Gravity.CENTER);
            bt.setOnClickListener(v -> { interval = x; loadKlines(); live.subscribe(symbol, interval); });
            times.addView(bt, new LinearLayout.LayoutParams(0, 42, 1));
        }
        h.addView(times); body.addView(h);

        CandleView cv = new CandleView(this);
        body.addView(cv, new LinearLayout.LayoutParams(-1, 340));

        LinearLayout ind = box();
        add(ind, "INDICATORS  •  live", 10, MUTED);
        add(ind, indicatorSummary(), 11, WHITE);
        body.addView(ind);
        orderBook();
    }

    List<Double> closes() { ArrayList<Double> c = new ArrayList<>(); for (Candle x : candles) c.add(x.c); return c; }
    List<double[]> hlc() { ArrayList<double[]> l = new ArrayList<>(); for (Candle x : candles) l.add(new double[]{x.h, x.l, x.c}); return l; }

    String indicatorSummary() {
        if (candles.size() < 20) return "EMA20 • EMA50 • RSI14 • MACD • ATR14 • Bollinger\nLoading indicator data…";
        List<Double> closes = closes();
        double e20 = Indicators.ema(closes, 20), e50 = Indicators.ema(closes, 50), r = Indicators.rsi(closes, 14);
        double[] macd = Indicators.macd(closes);
        double atr = Indicators.atr(hlc(), 14);
        double[] boll = Indicators.bollinger(closes, 20);
        String trend = e20 > e50 ? "Bullish" : "Bearish";
        return String.format(Locale.US,
                "EMA20 %.6f  •  EMA50 %.6f\nRSI14 %.1f  •  Trend %s\nMACD %.6f  •  Signal %.6f  •  Hist %.6f\nATR14 %.6f  •  Bollinger %.6f / %.6f / %.6f",
                e20, e50, r, trend, macd[0], macd[1], macd[2], atr, boll[0], boll[1], boll[2]);
    }

    void orderBook() {
        LinearLayout b = box();
        add(b, "ORDER BOOK", 12, WHITE);
        add(b, "ASK / BID depth", 9, MUTED);
        for (int i = 0; i < Math.min(7, asks.size()); i++) add(b, String.format(Locale.US, "ASK  %-18s %.5f", fmt(asks.get(i).p), asks.get(i).q), 10, RED);
        add(b, "────────  " + fmt(px.getOrDefault(symbol, 0.0)) + "  ────────", 10, GOLD);
        for (int i = 0; i < Math.min(7, bids.size()); i++) add(b, String.format(Locale.US, "BID  %-18s %.5f", fmt(bids.get(i).p), bids.get(i).q), 10, GREEN);
        body.addView(b);
    }

    void trade() {
        add(body, "Demo Trading", 23, WHITE);
        add(body, "No real money • all orders are simulated locally", 9, MUTED);
        LinearLayout c = box();
        add(c, "PAIR", 9, MUTED);
        c.addView(t(symbol, 18, WHITE));
        add(c, String.format(Locale.US, "Cash %.2f USDT", demo.cash), 10, MUTED);
        Spinner type = new Spinner(this);
        String[] types = {"MARKET", "LIMIT", "STOP-LIMIT"};
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types));
        c.addView(type, new LinearLayout.LayoutParams(-1, 50));
        add(c, "LEVERAGE", 9, MUTED);
        Spinner leverage = new Spinner(this);
        String[] levs = {"1x", "2x", "3x", "5x", "10x", "20x"};
        leverage.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, levs));
        c.addView(leverage, new LinearLayout.LayoutParams(-1, 50));
        EditText amount = input("Amount in USDT (margin)", "100"); c.addView(amount, new LinearLayout.LayoutParams(-1, 52));
        EditText limit = input("Limit price (optional)", ""); c.addView(limit, new LinearLayout.LayoutParams(-1, 52));
        EditText sl = input("Stop loss % (optional)", "2"); c.addView(sl, new LinearLayout.LayoutParams(-1, 52));
        EditText tp = input("Take profit % (optional)", "4"); c.addView(tp, new LinearLayout.LayoutParams(-1, 52));
        add(c, "Orders auto-execute in the background — SL/TP/liquidation trigger live, even off this tab.", 9, MUTED);
        LinearLayout r = new LinearLayout(this);
        Button buy = btn("BUY"), sell = btn("SELL");
        r.addView(buy, new LinearLayout.LayoutParams(0, 60, 1));
        r.addView(sell, new LinearLayout.LayoutParams(0, 60, 1));
        c.addView(r); body.addView(c);
        buy.setOnClickListener(v -> { int lev = leverageValue(leverage); demo.place(this, symbol, "BUY", amount, limit, sl, tp, type.getSelectedItem().toString(), lev); Prefs.save(this, demo); render(); });
        sell.setOnClickListener(v -> { int lev = leverageValue(leverage); demo.place(this, symbol, "SELL", amount, limit, sl, tp, type.getSelectedItem().toString(), lev); Prefs.save(this, demo); render(); });
        LinearLayout o = box();
        add(o, "OPEN ORDERS", 11, MUTED);
        if (demo.open.isEmpty()) add(o, "No open orders", 11, MUTED);
        for (Order x : demo.open) add(o, x.describe(), 11, WHITE);
        body.addView(o);
    }

    void ai() {
        add(body, "AI Terminal", 23, WHITE);
        add(body, "Educational market analysis — not financial advice", 9, MUTED);
        Signal s = signal();
        LinearLayout c = box();
        add(c, symbol, 12, MUTED);
        add(c, s.side, 30, s.side.equals("BUY") ? GREEN : s.side.equals("SELL") ? RED : GOLD);
        add(c, String.format(Locale.US, "Confidence %d%%  •  Risk %s", s.conf, s.risk), 12, WHITE);
        add(c, String.format(Locale.US,
                "Price %.8f\nTrend %s\nRSI %.1f\nEMA20 %.8f\nEMA50 %.8f\nMACD hist %.6f\nATR14 %.6f\nBollinger band %s\nMomentum %s\nVolume %s",
                s.price, s.trend, s.rsi, s.e20, s.e50, s.macdHist, s.atr, s.bollPos, s.mom, s.vol), 11, MUTED);
        body.addView(c);
        LinearLayout why = box();
        add(why, "TRADE PLAN", 11, MUTED);
        add(why, s.side.equals("BUY") ? "Trend + momentum + MACD aligned bullish. Demo only.\nSuggested SL: 2%   TP: 4%"
                : s.side.equals("SELL") ? "Trend + momentum + MACD aligned bearish. Demo only.\nSuggested SL: 2%   TP: 4%"
                : "No strong edge. Stay flat until indicators align.", 12, WHITE);
        body.addView(why);
        Button b = btn("OPEN DEMO TRADE");
        b.setOnClickListener(v -> { tab = "Trade"; render(); });
        body.addView(b, new LinearLayout.LayoutParams(-1, 56));
    }

    Signal signal() {
        Signal s = new Signal();
        s.price = px.getOrDefault(symbol, 0.0);
        List<Double> closes = closes();
        boolean ready = candles.size() >= 35;
        s.e20 = candles.size() > 20 ? Indicators.ema(closes, 20) : s.price;
        s.e50 = candles.size() > 50 ? Indicators.ema(closes, 50) : s.price;
        s.rsi = candles.size() > 15 ? Indicators.rsi(closes, 14) : 50;
        s.atr = candles.size() > 14 ? Indicators.atr(hlc(), 14) : 0;
        double[] macd = ready ? Indicators.macd(closes) : new double[]{0, 0, 0};
        s.macdHist = macd[2];
        double[] boll = candles.size() > 20 ? Indicators.bollinger(closes, 20) : new double[]{0, 0, 0};
        s.bollPos = boll[1] == 0 ? "n/a" : s.price > boll[0] ? "Above upper" : s.price < boll[2] ? "Below lower" : "Inside bands";
        double c = chg.getOrDefault(symbol, 0.0);
        s.trend = s.e20 > s.e50 ? "Bullish" : s.e20 < s.e50 ? "Bearish" : "Sideways";
        boolean macdBull = s.macdHist > 0, macdBear = s.macdHist < 0;
        s.side = (s.trend.equals("Bullish") && s.rsi < 70 && c > 0 && (!ready || macdBull)) ? "BUY"
                : (s.trend.equals("Bearish") && s.rsi > 30 && c < 0 && (!ready || macdBear)) ? "SELL" : "NO TRADE";
        s.conf = (int) Math.max(50, Math.min(92, 55 + Math.abs(c) * 6 + (s.trend.equals("Sideways") ? 0 : 8) + (ready && ((macdBull && s.trend.equals("Bullish")) || (macdBear && s.trend.equals("Bearish"))) ? 6 : 0)));
        s.risk = Math.abs(c) > 2 ? "High" : Math.abs(c) < .5 ? "Low" : "Medium";
        s.mom = c > 0 ? "Positive ↑" : c < 0 ? "Negative ↓" : "Neutral";
        s.vol = Math.abs(c) > 1.5 ? "High" : "Normal";
        return s;
    }

    static class Signal {
        String side = "NO TRADE", trend = "Sideways", risk = "Medium", mom = "Neutral", vol = "Normal", bollPos = "n/a";
        int conf = 50; double price, e20, e50, rsi = 50, atr = 0, macdHist = 0;
    }

    void orders() {
        add(body, "Orders & Portfolio", 23, WHITE);
        LinearLayout c = box();
        add(c, "DEMO WALLET", 10, MUTED);
        add(c, String.format(Locale.US, "$%,.2f USDT", demo.cash), 26, WHITE);
        add(c, String.format(Locale.US, "P&L %+.2f  •  Fees %.2f  •  Trades %d", demo.pnl, demo.fees, demo.completed()), 11, demo.pnl >= 0 ? GREEN : RED);
        body.addView(c);
        LinearLayout p = box();
        add(p, "POSITIONS", 11, MUTED);
        if (demo.positions.isEmpty()) add(p, "No open positions", 11, MUTED);
        for (Position x : new ArrayList<>(demo.positions)) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
            row.addView(t(x.describe(), 11, WHITE));
            Button closeBtn = btn("CLOSE POSITION");
            closeBtn.setOnClickListener(v -> { demo.close(px, x); Prefs.save(this, demo); render(); });
            row.addView(closeBtn, new LinearLayout.LayoutParams(-1, 48));
            p.addView(row);
        }
        body.addView(p);
        for (DTrade d : demo.trades) {
            LinearLayout x = box();
            add(x, d.side + "  " + d.symbol, 13, WHITE);
            add(x, String.format(Locale.US, "Entry %.8f → Exit %.8f", d.entry, d.exit), 10, MUTED);
            add(x, String.format(Locale.US, "%+.2f USDT", d.pnl), 18, d.pnl >= 0 ? GREEN : RED);
            body.addView(x);
        }
        Button reset = btn("RESET PAPER ACCOUNT $10,000");
        reset.setOnClickListener(v -> { demo.reset(); Prefs.save(this, demo); render(); });
        body.addView(reset, new LinearLayout.LayoutParams(-1, 55));
    }

    // ---------------- Data loading (REST for discovery/snapshots, WS for live updates) ----------------
    void switchSymbol(String s) {
        symbol = s;
        candles.clear();
        loadSymbol();
        live.subscribe(symbol, interval);
    }

    void loadExchange() {
        new Thread(() -> {
            try {
                JSONObject root = getObj("https://api.binance.com/api/v3/exchangeInfo");
                JSONArray a = root.getJSONArray("symbols");
                ArrayList<String> ss = new ArrayList<>();
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    if ("TRADING".equals(o.optString("status")) && "USDT".equals(o.optString("quoteAsset"))) ss.add(o.getString("symbol"));
                }
                Collections.sort(ss);
                symbols = ss;
                loadTickers();
                main.post(this::render);
            } catch (Exception e) {
                main.post(() -> Toast.makeText(this, "Market data error", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    void loadTickers() {
        try {
            JSONArray a = getArr("https://api.binance.com/api/v3/ticker/24hr");
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String s = o.optString("symbol");
                if (symbols.contains(s)) { px.put(s, o.optDouble("lastPrice")); chg.put(s, o.optDouble("priceChangePercent")); }
            }
        } catch (Exception ignored) {}
        loadSymbol();
        live.subscribe(symbol, interval);
    }

    void loadSymbol() {
        new Thread(() -> {
            try { loadKlinesSync(); loadBookSync(); } catch (Exception ignored) {}
            main.post(this::render);
        }).start();
    }

    void loadKlines() {
        new Thread(() -> {
            try { loadKlinesSync(); } catch (Exception ignored) {}
            main.post(this::render);
        }).start();
    }

    void loadKlinesSync() throws Exception {
        JSONArray a = getArr("https://api.binance.com/api/v3/klines?symbol=" + symbol + "&interval=" + interval + "&limit=200");
        ArrayList<Candle> cc = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONArray x = a.getJSONArray(i);
            cc.add(new Candle(x.getDouble(1), x.getDouble(2), x.getDouble(3), x.getDouble(4), x.getDouble(5)));
        }
        candles = cc;
    }

    void loadBookSync() throws Exception {
        JSONObject o = getObj("https://api.binance.com/api/v3/depth?symbol=" + symbol + "&limit=15");
        bids.clear(); asks.clear();
        JSONArray bb = o.getJSONArray("bids"), aa = o.getJSONArray("asks");
        for (int i = 0; i < bb.length(); i++) { JSONArray x = bb.getJSONArray(i); bids.add(new Book(x.getDouble(0), x.getDouble(1))); }
        for (int i = 0; i < aa.length(); i++) { JSONArray x = aa.getJSONArray(i); asks.add(new Book(x.getDouble(0), x.getDouble(1))); }
    }

    JSONArray getArr(String u) throws Exception { return new JSONArray(http(u)); }
    JSONObject getObj(String u) throws Exception { return new JSONObject(http(u)); }
    String http(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(15000); c.setRequestProperty("User-Agent", "TradingAI-Pro/2.0");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder b = new StringBuilder(); String l;
        while ((l = r.readLine()) != null) b.append(l);
        r.close(); c.disconnect(); return b.toString();
    }

    String fmt(double v) {
        if (v >= 1000) return String.format(Locale.US, "%,.2f", v);
        if (v >= 1) return String.format(Locale.US, "%.4f", v);
        return String.format(Locale.US, "%.8f", v);
    }

    // ---------------- Model classes ----------------
    static class Candle { double o, h, l, c, v; Candle(double O, double H, double L, double C, double V) { o = O; h = H; l = L; c = C; v = V; } }
    static class Book { double p, q; Book(double P, double Q) { p = P; q = Q; } }

    class CandleView extends View {
        Paint paint = new Paint(1);
        CandleView(Context c) { super(c); paint.setStrokeWidth(2); }
        protected void onDraw(Canvas c) {
            c.drawColor(Color.rgb(13, 17, 24));
            if (candles.size() < 2) { paint.setColor(MUTED); paint.setTextSize(18); c.drawText("Loading chart…", 30, 60, paint); return; }
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (Candle x : candles) { lo = Math.min(lo, x.l); hi = Math.max(hi, x.h); }
            float w = getWidth(), h = getHeight(), gap = w / (float) candles.size(), range = (float) (hi - lo == 0 ? 1 : hi - lo);
            for (int i = 0; i < candles.size(); i++) {
                Candle x = candles.get(i);
                float xx = i * gap + gap / 2,
                        yh = (float) ((hi - x.h) / range * h * .82 + 10), yl = (float) ((hi - x.l) / range * h * .82 + 10),
                        yo = (float) ((hi - x.o) / range * h * .82 + 10), yc = (float) ((hi - x.c) / range * h * .82 + 10);
                paint.setColor(x.c >= x.o ? GREEN : RED);
                c.drawLine(xx, yh, xx, yl, paint);
                float top = Math.min(yo, yc), bot = Math.max(yo, yc);
                c.drawRect(xx - gap * .3f, top, xx + gap * .3f, Math.max(top + 2, bot), paint);
            }
            if (candles.size() >= 20) {
                overlayLine(c, ema20Series(20), hi, lo, range, w, h, gap, GOLD);
                overlayLine(c, ema20Series(50), hi, lo, range, w, h, gap, BLUE);
                double[] boll = Indicators.bollinger(closes(), 20);
                overlayHorizontal(c, boll[0], hi, lo, range, w, h, MUTED);
                overlayHorizontal(c, boll[2], hi, lo, range, w, h, MUTED);
            }
            paint.setColor(MUTED); paint.setTextSize(10);
            c.drawText("H " + fmt(hi), 8, 18, paint);
            c.drawText("L " + fmt(lo), 8, h - 8, paint);
            c.drawText("EMA20", w - 150, 18, paintColor(GOLD));
            c.drawText("EMA50", w - 90, 18, paintColor(BLUE));
        }
        Paint paintColor(int col) { Paint p = new Paint(1); p.setColor(col); p.setTextSize(10); return p; }
        // running EMA value at each candle index (walk-forward), so the overlay tracks price like a real chart
        ArrayList<Double> ema20Series(int period) {
            ArrayList<Double> out = new ArrayList<>();
            if (candles.size() < period) return out;
            double k = 2.0 / (period + 1), e = candles.get(0).c;
            for (int i = 0; i < candles.size(); i++) { e = candles.get(i).c * k + e * (1 - k); out.add(e); }
            return out;
        }
        void overlayLine(Canvas c, ArrayList<Double> series, double hi, double lo, float range, float w, float h, float gap, int color) {
            if (series.size() < 2) return;
            Paint lp = new Paint(1); lp.setColor(color); lp.setStrokeWidth(3); lp.setAlpha(200);
            float prevX = -1, prevY = -1;
            for (int i = 0; i < series.size(); i++) {
                float xx = i * gap + gap / 2, yy = (float) ((hi - series.get(i)) / range * h * .82 + 10);
                if (prevX >= 0) c.drawLine(prevX, prevY, xx, yy, lp);
                prevX = xx; prevY = yy;
            }
        }
        void overlayHorizontal(Canvas c, double value, double hi, double lo, float range, float w, float h, int color) {
            if (value == 0) return;
            Paint lp = new Paint(1); lp.setColor(color); lp.setStrokeWidth(1); lp.setAlpha(110);
            float yy = (float) ((hi - value) / range * h * .82 + 10);
            c.drawLine(0, yy, w, yy, lp);
        }
    }

    /** Simple sparkline of paper-account equity over the last few minutes. */
    class EquityView extends View {
        Paint paint = new Paint(1);
        EquityView(Context c) { super(c); paint.setStrokeWidth(3); }
        protected void onDraw(Canvas c) {
            if (equityHistory.size() < 2) return;
            double lo = Collections.min(equityHistory), hi = Collections.max(equityHistory);
            double range = (hi - lo == 0) ? 1 : hi - lo;
            float w = getWidth(), h = getHeight(), gap = w / (float) (equityHistory.size() - 1);
            paint.setColor(equityHistory.get(equityHistory.size() - 1) >= equityHistory.get(0) ? GREEN : RED);
            float prevX = 0, prevY = (float) ((hi - equityHistory.get(0)) / range * h * .85 + 5);
            for (int i = 1; i < equityHistory.size(); i++) {
                float xx = i * gap, yy = (float) ((hi - equityHistory.get(i)) / range * h * .85 + 5);
                c.drawLine(prevX, prevY, xx, yy, paint);
                prevX = xx; prevY = yy;
            }
        }
    }

    public static class DTrade {
        public String symbol, side; public double entry, exit, pnl;
        public DTrade(String s, String d, double e, double x, double q) { symbol = s; side = d; entry = e; exit = x; pnl = q; }
    }

    public static class Position {
        public String symbol, side; public double qty, entry, sl, tp;
        public int leverage = 1; public double margin = 0, liq = 0;
        public Position(String s, String d, double q, double e, double S, double T) { symbol = s; side = d; qty = q; entry = e; sl = S; tp = T; }
        String describe() {
            String lev = leverage > 1 ? "  " + leverage + "x" : "";
            String liqLine = leverage > 1 ? "\nLiquidation " + String.format(Locale.US, "%.6f", liq) : "";
            return side + lev + " " + symbol + "  Qty " + String.format(Locale.US, "%.6f", qty) + "  Entry " + String.format(Locale.US, "%.6f", entry)
                    + "\nSL " + String.format(Locale.US, "%.6f", sl) + "  TP " + String.format(Locale.US, "%.6f", tp) + liqLine;
        }
    }

    static class Order {
        String symbol, side, type; double amount, price, sl, tp; int leverage = 1;
        Order(String s, String d, String t, double a, double p, double S, double T) { symbol = s; side = d; type = t; amount = a; price = p; sl = S; tp = T; }
        String describe() { return side + " " + type + " " + symbol + (leverage > 1 ? "  " + leverage + "x" : "") + "  " + String.format(Locale.US, "%.2f USDT @ %s", amount, String.format(Locale.US, "%.6f", price)); }
    }

    public static class Demo {
        public double cash = 10000, pnl = 0, fees = 0; public int wins = 0, losses = 0;
        public ArrayList<DTrade> trades = new ArrayList<>();
        public ArrayList<Position> positions = new ArrayList<>();
        ArrayList<Order> open = new ArrayList<>();
        public HashSet<String> favorites = new HashSet<>();

        int completed() { return trades.size(); }
        double winRate() { return completed() == 0 ? 0 : wins * 100.0 / completed(); }
        double equity(HashMap<String, Double> p) {
            double e = cash;
            for (Position x : positions) {
                double now = p.getOrDefault(x.symbol, x.entry);
                e += (x.side.equals("BUY") ? (now - x.entry) : (x.entry - now)) * x.qty;
            }
            return e;
        }

        void place(Context ctx, String s, String side, EditText amt, EditText lim, EditText sl, EditText tp, String type, int leverage) {
            double a = num(amt, 100), now = 0;
            if (ctx instanceof MainActivity) now = ((MainActivity) ctx).px.getOrDefault(s, 0.0);
            double price = num(lim, now), slp = num(sl, 2), tpp = num(tp, 4);
            if (a <= 0 || now <= 0) { toast(ctx, "Invalid amount"); return; }
            if (a > cash) { toast(ctx, "Not enough demo USDT"); return; }
            if (type.equals("MARKET")) fill(ctx, s, side, a, now, slp, tpp, leverage);
            else {
                Order o = new Order(s, side, type, a, price, side.equals("BUY") ? price * (1 - slp / 100) : price * (1 + slp / 100), side.equals("BUY") ? price * (1 + tpp / 100) : price * (1 - tpp / 100));
                o.leverage = leverage;
                open.add(o);
                toast(ctx, type + " order placed" + (leverage > 1 ? " (" + leverage + "x)" : ""));
            }
        }

        void fill(Context ctx, String s, String side, double amount, double price, double slp, double tpp, int leverage) {
            double fee = amount * leverage * .001; cash -= amount + fee; fees += fee;
            double q = (amount * leverage) / price;
            double stop = side.equals("BUY") ? price * (1 - slp / 100) : price * (1 + slp / 100);
            double take = side.equals("BUY") ? price * (1 + tpp / 100) : price * (1 - tpp / 100);
            Position pos = new Position(s, side, q, price, stop, take);
            pos.leverage = leverage; pos.margin = amount;
            // liquidation ~ price move that wipes out margin (90% buffer for fees/slippage)
            double liqMove = price * (0.9 / leverage);
            pos.liq = side.equals("BUY") ? price - liqMove : price + liqMove;
            positions.add(pos);
            toast(ctx, "Demo " + side + " filled" + (leverage > 1 ? " (" + leverage + "x)" : ""));
        }

        void close(HashMap<String, Double> p, Position pos) {
            double now = p.getOrDefault(pos.symbol, pos.entry);
            double gross = (pos.side.equals("BUY") ? (now - pos.entry) : (pos.entry - now)) * pos.qty;
            double fee = (now * pos.qty) * .001;
            double net = gross - fee;
            cash += pos.margin + net;
            pnl += net; fees += fee;
            if (net >= 0) wins++; else losses++;
            trades.add(new DTrade(pos.symbol, pos.side, pos.entry, now, net));
            positions.remove(pos);
        }

        /** Called on every live price tick: auto-closes SL/TP/liquidation hits and fills pending orders. Returns status messages. */
        ArrayList<String> checkExecutions(HashMap<String, Double> p) {
            ArrayList<String> msgs = new ArrayList<>();
            for (Position pos : new ArrayList<>(positions)) {
                Double now = p.get(pos.symbol);
                if (now == null) continue;
                boolean buy = pos.side.equals("BUY");
                boolean hitTp = buy ? now >= pos.tp : now <= pos.tp;
                boolean hitSl = buy ? now <= pos.sl : now >= pos.sl;
                boolean liquidated = pos.leverage > 1 && (buy ? now <= pos.liq : now >= pos.liq);
                if (liquidated) {
                    // margin was already deducted from cash when the position was opened —
                    // liquidation just means it's gone for good, cash doesn't change further
                    pnl -= pos.margin;
                    losses++;
                    trades.add(new DTrade(pos.symbol, pos.side, pos.entry, now, -pos.margin));
                    positions.remove(pos);
                    msgs.add("LIQUIDATED " + pos.symbol + " (" + pos.leverage + "x)");
                } else if (hitTp) { close(p, pos); msgs.add("Take-profit hit: " + pos.symbol); }
                else if (hitSl) { close(p, pos); msgs.add("Stop-loss hit: " + pos.symbol); }
            }
            for (Order o : new ArrayList<>(open)) {
                Double now = p.get(o.symbol);
                if (now == null) continue;
                boolean triggered = o.type.equals("LIMIT")
                        ? (o.side.equals("BUY") ? now <= o.price : now >= o.price)
                        : (o.side.equals("BUY") ? now >= o.price : now <= o.price); // STOP-LIMIT triggers on breakout
                if (triggered && cash >= o.amount) {
                    double slp = Math.abs(o.price - o.sl) / o.price * 100, tpp = Math.abs(o.tp - o.price) / o.price * 100;
                    fill(null, o.symbol, o.side, o.amount, now, slp, tpp, o.leverage);
                    open.remove(o);
                    msgs.add(o.type + " filled: " + o.symbol);
                }
            }
            return msgs;
        }

        double num(EditText e, double d) { try { return Double.parseDouble(e.getText().toString().trim()); } catch (Exception x) { return d; } }
        void toast(Context ctx, String s) { if (ctx != null) Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show(); }
        public void reset() { cash = 10000; pnl = fees = 0; wins = losses = 0; trades.clear(); positions.clear(); open.clear(); }
    }
}
