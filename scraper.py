import requests
from bs4 import BeautifulSoup
from dataclasses import dataclass
from typing import List
import logging

logger = logging.getLogger(__name__)

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/124.0 Safari/537.36"
    )
}


@dataclass
class PoliticianTrade:
    politician: str
    party: str
    ticker: str
    asset_name: str
    trade_type: str   # "buy" or "sell"
    size_range: str   # e.g. "$1K–$15K"
    filed_date: str
    trade_date: str


def fetch_recent_trades(url: str) -> List[PoliticianTrade]:
    """Fetch and parse the most recent trades from Capitol Trades."""
    try:
        resp = requests.get(url, headers=HEADERS, timeout=15)
        resp.raise_for_status()
    except requests.RequestException as e:
        logger.error("Failed to fetch Capitol Trades: %s", e)
        return []

    soup = BeautifulSoup(resp.text, "lxml")
    trades: List[PoliticianTrade] = []

    # Capitol Trades renders a <table> with class "q-table" for trade rows.
    rows = soup.select("table tbody tr")
    for row in rows:
        cols = [td.get_text(strip=True) for td in row.find_all("td")]
        if len(cols) < 8:
            continue
        try:
            trade = PoliticianTrade(
                politician=cols[0],
                party=cols[1],
                ticker=cols[2],
                asset_name=cols[3],
                trade_type=cols[4].lower(),
                size_range=cols[5],
                filed_date=cols[6],
                trade_date=cols[7],
            )
            if trade.ticker:
                trades.append(trade)
        except (IndexError, ValueError) as e:
            logger.debug("Skipping malformed row: %s", e)

    logger.info("Fetched %d trades from Capitol Trades", len(trades))
    return trades


def trades_to_text(trades: List[PoliticianTrade]) -> str:
    """Format trade list as plain text for the Claude prompt."""
    if not trades:
        return "No recent trades found."
    lines = ["Recent politician trades from Capitol Trades:\n"]
    for t in trades:
        lines.append(
            f"- {t.politician} ({t.party}) {t.trade_type.upper()} {t.ticker} "
            f"({t.asset_name}), size {t.size_range}, "
            f"traded {t.trade_date}, filed {t.filed_date}"
        )
    return "\n".join(lines)
