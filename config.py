import os
from dotenv import load_dotenv

load_dotenv()

ANTHROPIC_API_KEY = os.environ["ANTHROPIC_API_KEY"]
ALPACA_API_KEY = os.environ["ALPACA_API_KEY"]
ALPACA_SECRET_KEY = os.environ["ALPACA_SECRET_KEY"]
ALPACA_BASE_URL = os.getenv("ALPACA_BASE_URL", "https://paper-api.alpaca.markets")

MAX_POSITION_USD = float(os.getenv("MAX_POSITION_USD", "500"))
MAX_TOTAL_POSITIONS = int(os.getenv("MAX_TOTAL_POSITIONS", "10"))
POLL_INTERVAL_SECONDS = int(os.getenv("POLL_INTERVAL_SECONDS", "300"))

CAPITOL_TRADES_URL = "https://www.capitoltrades.com/trades"
