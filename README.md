# Capitol Trades Agent

An AI trading agent that monitors [Capitol Trades](https://www.capitoltrades.com/) for US politician stock disclosures and mirrors compelling trades via your Alpaca account.

Claude (claude-opus-4-8) reads the latest filings every 5 minutes, evaluates which trades are worth following, and places market orders within your configured risk limits.

## Setup

### 1. Install Python dependencies

```bash
pip install -r requirements.txt
```

### 2. Configure environment variables

```bash
cp .env.example .env
# Edit .env and fill in your API keys
```

You need:
- **Anthropic API key** — from [console.anthropic.com](https://console.anthropic.com)
- **Alpaca API key + secret** — from [alpaca.markets](https://alpaca.markets) (use paper keys to start)

### 3. Run in paper trading mode (recommended first)

Make sure `.env` has `ALPACA_BASE_URL=https://paper-api.alpaca.markets`, then:

```bash
python main.py
```

### 4. Switch to live trading

When you're confident in the agent's behaviour:
1. Create live API keys in your Alpaca dashboard
2. Update `.env`:
   ```
   ALPACA_API_KEY=your_live_key
   ALPACA_SECRET_KEY=your_live_secret
   ALPACA_BASE_URL=https://api.alpaca.markets
   ```

## Risk controls

| Setting | Default | Description |
|---|---|---|
| `MAX_POSITION_USD` | $500 | Max dollars per trade |
| `MAX_TOTAL_POSITIONS` | 10 | Max simultaneous open positions |
| `POLL_INTERVAL_SECONDS` | 300 | How often to check Capitol Trades |

All limits are enforced in both Claude's system prompt and the order-placement code.

## Architecture

```
main.py          — scheduler loop
scraper.py       — fetches & parses capitoltrades.com
agent.py         — Claude decides what to trade
alpaca_client.py — places orders via Alpaca API
config.py        — loads settings from .env
```

## Disclaimer

This software is for educational purposes. Politician trade data has a filing lag (up to 45 days). Past signal quality does not guarantee future returns. Use at your own risk.
