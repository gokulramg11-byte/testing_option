"""
Configuration settings and environment variable parser for Algorithmic Options Scalping Bot.
Ensures zero hardcoded secrets and enforces Paper Trading (DRY_RUN = True) by default.
"""

import os
import json
import logging
from dataclasses import dataclass
from typing import Optional, Tuple
from datetime import time
import pytz
from dotenv import load_dotenv

# Load local .env file if available (for local testing)
load_dotenv()

IST = pytz.timezone("Asia/Kolkata")

@dataclass
class TradingConfig:
    # Mode & Safety Defaults
    dry_run: bool = True
    authorized_email: str = ""

    # Upstox Credentials
    upstox_api_key: str = ""
    upstox_api_secret: str = ""
    upstox_redirect_uri: str = "https://127.0.0.1:5000/"
    upstox_mobile: str = ""
    upstox_pin: str = ""
    upstox_totp_secret: str = ""
    upstox_access_token: Optional[str] = None

    # Firebase Credentials
    firebase_service_account: Optional[dict] = None

    # Instrument Settings
    index_name: str = "NIFTY"
    spot_instrument_key: str = "NSE_INDEX|Nifty 50"
    lot_size: int = 75
    timeframe: str = "3minute"

    # Risk Management & Limits (INR)
    max_risk_per_trade: float = 1500.0
    daily_max_loss: float = 4000.0
    max_consecutive_sl: int = 3

    # Scalping Strategy Parameters
    hard_sl_pct: float = 0.10             # 10% hard stop-loss on option premium
    time_sl_candles: int = 3              # 3 closed candles (9 mins) without profit
    target_1_points: float = 10.0         # +10 pts (Move SL to Breakeven)
    target_2_points: float = 15.0         # +15 pts (Book 50% partial & trail remainder)
    stoch_rsi_oversold: float = 30.0
    stoch_rsi_overbought: float = 70.0
    volume_multiplier: float = 1.5
    supertrend_period: int = 7
    supertrend_multiplier: float = 2.0

    # Active IST Time Windows
    morning_window: Tuple[time, time] = (time(9, 30), time(12, 0))
    afternoon_window: Tuple[time, time] = (time(13, 30), time(15, 10))
    auto_square_off_time: time = time(15, 10)  # Complete shutdown and square-off before 15:15 IST


def load_config() -> TradingConfig:
    """Loads and validates configuration from environment variables."""
    # DRY_RUN defaults to True for safety
    dry_run_env = os.getenv("DRY_RUN", "true").strip().lower()
    dry_run = dry_run_env in ("true", "1", "yes", "t")

    index_name = os.getenv("INDEX_NAME", "NIFTY").strip().upper()
    if index_name == "BANKNIFTY":
        spot_instrument_key = "NSE_INDEX|Nifty Bank"
        lot_size = int(os.getenv("LOT_SIZE", "30"))
    else:
        index_name = "NIFTY"
        spot_instrument_key = "NSE_INDEX|Nifty 50"
        lot_size = int(os.getenv("LOT_SIZE", "75"))

    # Firebase service account loading
    firebase_cred = None
    fb_env = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON", "").strip()
    if fb_env:
        try:
            if fb_env.startswith("{"):
                firebase_cred = json.loads(fb_env)
            elif os.path.isfile(fb_env):
                with open(fb_env, "r", encoding="utf-8") as f:
                    firebase_cred = json.load(f)
        except Exception as e:
            logging.error(f"Failed to parse FIREBASE_SERVICE_ACCOUNT_JSON: {e}")

    config = TradingConfig(
        dry_run=dry_run,
        authorized_email=os.getenv("AUTHORIZED_USER_EMAIL", "").strip().lower(),
        upstox_api_key=os.getenv("UPSTOX_API_KEY", "").strip(),
        upstox_api_secret=os.getenv("UPSTOX_API_SECRET", "").strip(),
        upstox_redirect_uri=os.getenv("UPSTOX_REDIRECT_URI", "https://127.0.0.1:5000/").strip(),
        upstox_mobile=os.getenv("UPSTOX_MOBILE", "").strip(),
        upstox_pin=os.getenv("UPSTOX_PIN", "").strip(),
        upstox_totp_secret=os.getenv("UPSTOX_TOTP_SECRET", "").strip(),
        upstox_access_token=os.getenv("UPSTOX_ACCESS_TOKEN", "").strip() or None,
        firebase_service_account=firebase_cred,
        index_name=index_name,
        spot_instrument_key=spot_instrument_key,
        lot_size=lot_size,
        timeframe=os.getenv("TIMEFRAME", "3minute").strip(),
        max_risk_per_trade=float(os.getenv("MAX_RISK_PER_TRADE", "1500.0")),
        daily_max_loss=float(os.getenv("DAILY_MAX_LOSS", "4000.0")),
        max_consecutive_sl=int(os.getenv("MAX_CONSECUTIVE_SL", "3")),
    )
    return config

config = load_config()
