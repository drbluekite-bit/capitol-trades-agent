import logging
from alpaca.trading.client import TradingClient
from alpaca.trading.requests import MarketOrderRequest
from alpaca.trading.enums import OrderSide, TimeInForce
from alpaca.data.historical import StockHistoricalDataClient
from alpaca.data.requests import StockLatestQuoteRequest
import config

logger = logging.getLogger(__name__)

_trading = TradingClient(config.ALPACA_API_KEY, config.ALPACA_SECRET_KEY, paper=("paper" in config.ALPACA_BASE_URL))
_data = StockHistoricalDataClient(config.ALPACA_API_KEY, config.ALPACA_SECRET_KEY)


def get_latest_price(ticker: str) -> float | None:
    try:
        req = StockLatestQuoteRequest(symbol_or_symbols=ticker)
        quote = _data.get_stock_latest_quote(req)
        return float(quote[ticker].ask_price)
    except Exception as e:
        logger.error("Could not get price for %s: %s", ticker, e)
        return None


def open_positions() -> dict[str, float]:
    """Return {ticker: market_value} for all current open positions."""
    try:
        positions = _trading.get_all_positions()
        return {p.symbol: float(p.market_value) for p in positions}
    except Exception as e:
        logger.error("Could not fetch positions: %s", e)
        return {}


def place_order(ticker: str, side: str, notional_usd: float) -> bool:
    """Place a market order for a given dollar amount. Returns True on success."""
    positions = open_positions()

    if side == "buy":
        if len(positions) >= config.MAX_TOTAL_POSITIONS:
            logger.warning("Max positions reached (%d), skipping %s buy", config.MAX_TOTAL_POSITIONS, ticker)
            return False
        if notional_usd > config.MAX_POSITION_USD:
            notional_usd = config.MAX_POSITION_USD
            logger.info("Capped order for %s at $%.0f", ticker, notional_usd)

    try:
        req = MarketOrderRequest(
            symbol=ticker,
            notional=round(notional_usd, 2),
            side=OrderSide.BUY if side == "buy" else OrderSide.SELL,
            time_in_force=TimeInForce.DAY,
        )
        order = _trading.submit_order(req)
        logger.info("Order placed: %s %s $%.2f — id %s", side.upper(), ticker, notional_usd, order.id)
        return True
    except Exception as e:
        logger.error("Order failed for %s: %s", ticker, e)
        return False
