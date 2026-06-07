import logging
import schedule
import time
import config
from scraper import fetch_recent_trades, trades_to_text
from agent import decide, execute_actions
import alpaca_client as alpaca

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s — %(message)s",
)
logger = logging.getLogger(__name__)


def run_cycle() -> None:
    logger.info("=== Starting trade cycle ===")

    trades = fetch_recent_trades(config.CAPITOL_TRADES_URL)
    trades_text = trades_to_text(trades)
    logger.info(trades_text[:500])

    open_positions = alpaca.open_positions()
    actions = decide(trades_text, open_positions)

    if not actions:
        logger.info("No actions this cycle.")
    else:
        execute_actions(actions)

    logger.info("=== Cycle complete ===")


if __name__ == "__main__":
    logger.info(
        "Capitol Trades Agent starting — polling every %ds, paper=%s, max $%s/trade",
        config.POLL_INTERVAL_SECONDS,
        "paper" in config.ALPACA_BASE_URL,
        config.MAX_POSITION_USD,
    )

    run_cycle()  # run immediately on start

    schedule.every(config.POLL_INTERVAL_SECONDS).seconds.do(run_cycle)
    while True:
        schedule.run_pending()
        time.sleep(10)
