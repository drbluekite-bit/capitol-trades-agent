import json
import logging
import anthropic
import config
import alpaca_client as alpaca

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """You are a disciplined algorithmic trading agent that follows US politician stock disclosures.

Your job:
1. Review the latest politician trades from Capitol Trades.
2. Decide which trades (if any) are worth mirroring based on:
   - Politician seniority and committee relevance to the stock's sector.
   - Trade size (prefer $15K+ as a stronger signal).
   - Whether multiple politicians are buying/selling the same ticker.
   - Recency (prefer trades filed within the last 2 days).
3. Output a JSON array of actions. Each action must have:
   {
     "ticker": "AAPL",
     "action": "buy" | "sell" | "hold",
     "notional_usd": 250,          // dollar amount; max per-trade limit enforced externally
     "reasoning": "brief rationale"
   }
4. Output ONLY the JSON array — no prose, no markdown fences.
5. If no trades are compelling, return an empty array: []

Risk rules you must follow:
- Never allocate more than $500 per trade (the system will also cap this).
- Skip any ticker you cannot identify as a US-listed equity.
- Do not act on "exchange" or "gift" type transactions.
"""


def decide(trades_text: str, open_positions: dict) -> list[dict]:
    """Ask Claude to evaluate the trades and return a list of actions."""
    client = anthropic.Anthropic(api_key=config.ANTHROPIC_API_KEY)

    positions_text = (
        "Currently open positions: " + json.dumps(open_positions)
        if open_positions
        else "No open positions."
    )

    message = client.messages.create(
        model="claude-opus-4-8",
        max_tokens=1024,
        system=SYSTEM_PROMPT,
        messages=[
            {
                "role": "user",
                "content": f"{trades_text}\n\n{positions_text}",
            }
        ],
    )

    raw = message.content[0].text.strip()
    try:
        actions = json.loads(raw)
        if not isinstance(actions, list):
            raise ValueError("Expected a JSON array")
        return actions
    except (json.JSONDecodeError, ValueError) as e:
        logger.error("Claude returned invalid JSON: %s\nRaw: %s", e, raw)
        return []


def execute_actions(actions: list[dict]) -> None:
    for action in actions:
        ticker = action.get("ticker", "").upper()
        side = action.get("action", "hold").lower()
        notional = float(action.get("notional_usd", 0))
        reasoning = action.get("reasoning", "")

        if side == "hold" or not ticker or notional <= 0:
            logger.info("HOLD %s — %s", ticker, reasoning)
            continue

        logger.info("ACTION %s %s $%.0f — %s", side.upper(), ticker, notional, reasoning)
        alpaca.place_order(ticker, side, notional)
