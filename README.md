# Trading AI Pro v6 — advanced Binance-style mobile demo terminal

This release upgrades the app from polling-based demo data to a genuinely
live, indicator-driven paper-trading terminal with real order execution.

## What's new in v6
- **Leveraged demo positions (1x–20x)** — like Binance Futures, orders can be
  opened with leverage. Margin, liquidation price, and P&L all scale
  correctly, and liquidation price is shown on every leveraged position.
- **Real auto-execution engine** — this was the biggest gap before: SL/TP and
  pending LIMIT/STOP-LIMIT orders previously just sat there and never
  triggered. Now every live price tick checks open positions and pending
  orders and fills/closes them automatically — even while you're on another
  tab — with a toast notification when it happens.
- **Liquidation simulation** — leveraged positions can now be liquidated if
  price moves against the margin, just like a real exchange.
- **Chart overlays** — EMA20 (gold) and EMA50 (blue) lines plus Bollinger
  Band levels are now drawn directly on the candlestick chart, not just
  listed as numbers.
- **Equity sparkline** — the Home tab now shows a live mini equity curve of
  your paper account building up over the session.
- **Pending orders now persist** — open LIMIT/STOP-LIMIT orders survive an
  app restart, same as positions and trade history.

## What's new in v5
- **Live WebSocket data** — ticker price and the active candle now stream in
  real time over Binance's public combined WebSocket (`stream.binance.com`),
  with automatic reconnect and a "Live / Reconnecting…" status badge.
- **Real technical indicators** — EMA20/50, RSI14, MACD (12/26/9), ATR14, and
  20-period Bollinger Bands are now computed from actual candle history
  (`Indicators.java`), not placeholders.
- **Smarter AI signal** — the BUY/SELL/NO TRADE call now requires trend (EMA
  cross), momentum, RSI range, *and* MACD histogram direction to agree before
  suggesting a side, with confidence boosted when indicators confirm.
- **Persistent paper account** — cash, P&L, open positions, trade history,
  and favorites are saved to local storage (`Prefs.java`) and restored on
  next launch.
- **Close Position button** — positions in the Orders tab can now actually be
  closed (realizes P&L against the live price); previously this logic
  existed but was never wired to the UI.

## Included
- Binance public Spot USDT pair discovery (dynamic list)
- Live 24h ticker prices + live WebSocket ticks
- Searchable markets
- Candlestick chart from Binance public klines, live-updating current candle
- Timeframe selector: 1m, 5m, 15m, 1h, 4h, 1d
- Public order book (REST snapshot, refreshed per symbol/timeframe change)
- AI Terminal: signal / confidence / risk / EMA / RSI / MACD / ATR / Bollinger
- Demo BUY / SELL / LIMIT / STOP-LIMIT orders, SL/TP
- $10,000 virtual account with persistence across restarts
- Demo P&L, wins, losses, win rate, order history

## Important
The AI signal in this app is an educational heuristic built from public
indicator math, not a validated predictive model. It should not be
represented as guaranteed profitable, and no real orders are ever placed —
this is a paper-trading simulator only.

## Building the APK
This source ships with a GitHub Actions workflow
(`.github/workflows/build-apk.yml`) that builds a debug APK automatically:

1. Push this project to a GitHub repository.
2. Open the **Actions** tab → run **"Build Trading AI APK"** (or just push to
   `main`, which triggers it automatically).
3. Once the run finishes, download the `TradingAI-v4-debug` artifact — it
   contains `app-debug.apk`, ready to install.

Alternatively, open the project in Android Studio and use
**Build → Build Bundle(s) / APK(s) → Build APK(s)**.

## Next production stage
- Real indicator-based backtesting + walk-forward validation
- Server-side ML/LLM ensemble
- Secure authenticated account-history connector
- Forex/XAUUSD provider
- Push notifications and configurable signal rules

Never embed an exchange secret/API key in the APK. Use secure server-side
storage and least-privilege permissions for any authenticated integration.
