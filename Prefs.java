package com.tradingai.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Saves/restores the paper-trading account (cash, PnL, positions, trade history, favorites). */
public class Prefs {
    private static final String FILE = "tradingai_state";
    private static final String KEY = "demo_state";

    public static void save(Context ctx, MainActivity.Demo demo) {
        try {
            JSONObject o = new JSONObject();
            o.put("cash", demo.cash);
            o.put("pnl", demo.pnl);
            o.put("fees", demo.fees);
            o.put("wins", demo.wins);
            o.put("losses", demo.losses);

            JSONArray trades = new JSONArray();
            for (MainActivity.DTrade t : demo.trades) {
                JSONObject to = new JSONObject();
                to.put("symbol", t.symbol); to.put("side", t.side);
                to.put("entry", t.entry); to.put("exit", t.exit); to.put("pnl", t.pnl);
                trades.put(to);
            }
            o.put("trades", trades);

            JSONArray positions = new JSONArray();
            for (MainActivity.Position p : demo.positions) {
                JSONObject po = new JSONObject();
                po.put("symbol", p.symbol); po.put("side", p.side); po.put("qty", p.qty);
                po.put("entry", p.entry); po.put("sl", p.sl); po.put("tp", p.tp);
                po.put("leverage", p.leverage); po.put("margin", p.margin); po.put("liq", p.liq);
                positions.put(po);
            }
            o.put("positions", positions);

            JSONArray orders = new JSONArray();
            for (MainActivity.Order ord : demo.open) {
                JSONObject oo = new JSONObject();
                oo.put("symbol", ord.symbol); oo.put("side", ord.side); oo.put("type", ord.type);
                oo.put("amount", ord.amount); oo.put("price", ord.price);
                oo.put("sl", ord.sl); oo.put("tp", ord.tp); oo.put("leverage", ord.leverage);
                orders.put(oo);
            }
            o.put("orders", orders);

            JSONArray favs = new JSONArray();
            for (String f : demo.favorites) favs.put(f);
            o.put("favorites", favs);

            ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                    .edit().putString(KEY, o.toString()).apply();
        } catch (Exception ignored) { /* best-effort persistence */ }
    }

    public static void load(Context ctx, MainActivity.Demo demo) {
        String raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null);
        if (raw == null) return;
        try {
            JSONObject o = new JSONObject(raw);
            demo.cash = o.optDouble("cash", 10000);
            demo.pnl = o.optDouble("pnl", 0);
            demo.fees = o.optDouble("fees", 0);
            demo.wins = o.optInt("wins", 0);
            demo.losses = o.optInt("losses", 0);

            demo.trades.clear();
            JSONArray trades = o.optJSONArray("trades");
            if (trades != null) for (int i = 0; i < trades.length(); i++) {
                JSONObject t = trades.getJSONObject(i);
                demo.trades.add(new MainActivity.DTrade(
                        t.getString("symbol"), t.getString("side"),
                        t.getDouble("entry"), t.getDouble("exit"), t.getDouble("pnl")));
            }

            demo.positions.clear();
            JSONArray positions = o.optJSONArray("positions");
            if (positions != null) for (int i = 0; i < positions.length(); i++) {
                JSONObject p = positions.getJSONObject(i);
                MainActivity.Position pos = new MainActivity.Position(
                        p.getString("symbol"), p.getString("side"), p.getDouble("qty"),
                        p.getDouble("entry"), p.getDouble("sl"), p.getDouble("tp"));
                pos.leverage = p.optInt("leverage", 1);
                pos.margin = p.optDouble("margin", 0);
                pos.liq = p.optDouble("liq", 0);
                demo.positions.add(pos);
            }

            demo.open.clear();
            JSONArray orders = o.optJSONArray("orders");
            if (orders != null) for (int i = 0; i < orders.length(); i++) {
                JSONObject ord = orders.getJSONObject(i);
                MainActivity.Order od = new MainActivity.Order(
                        ord.getString("symbol"), ord.getString("side"), ord.getString("type"),
                        ord.getDouble("amount"), ord.getDouble("price"), ord.getDouble("sl"), ord.getDouble("tp"));
                od.leverage = ord.optInt("leverage", 1);
                demo.open.add(od);
            }

            demo.favorites.clear();
            JSONArray favs = o.optJSONArray("favorites");
            if (favs != null) for (int i = 0; i < favs.length(); i++) demo.favorites.add(favs.getString(i));
        } catch (Exception ignored) { /* corrupt/old state, start fresh */ }
    }
}
