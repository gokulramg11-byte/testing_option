"""
Configuration settings and environment variable parser for Algorithmic Options Scalping Bot.
Supports Multi-Session Trading:
- Morning Session: NSE (NIFTY)
- Evening Sessions: MCX (CRUDEOIL, NATURALGAS, GOLDM)
Enforces Paper Trading (DRY_RUN = True) by default.
"""

import os
import json
import logging
from dataclasses import dataclass, field
from typing import Optional, Tuple, List, Dict, Any
from datetime import time
import pytz
from dotenv import load_dotenv

# Load local .env file if available (for local testing)
load_dotenv()

IST = pytz.timezone("Asia/Kolkata")

# Multi-Session Profile Specifications
SESSION_PROFILES: Dict[str, Dict[str, Any]] = {
    "NSE_NIFTY": {
        "session_id": "NSE_NIFTY",
        "segment": "NSE",
        "underlying_type": "INDEX",
        "symbol": "NIFTY",
        "display_name": "NIFTY 50",
        "spot_instrument_key": "NSE_INDEX|Nifty 50",
        "timeframe": "3minute",
        "timeframe_minutes": 3,
        "active_windows": [(time(9, 30), time(12, 0)), (time(13, 30), time(15, 10))],
        "auto_square_off_time": time(15, 10),
        "lot_size": 75,
        "strike_step": 50,
        "hard_sl_pct": 0.10,
        "order_type": "MARKET",
        "tick_size": 0.05,
        "eia_blackout_day": None
    },
    "MCX_CRUDEOIL": {
        "session_id": "MCX_CRUDEOIL",
        "segment": "MCX",
        "underlying_type": "FUTURES",
        "symbol": "CRUDEOIL",
        "display_name": "CRUDE OIL",
        "spot_instrument_key": "",  # Resolved dynamically from MCX Master
        "timeframe": "5minute",
        "timeframe_minutes": 5,
        "active_windows": [(time(17, 0), time(22, 55))],
        "auto_square_off_time": time(22, 55),
        "lot_size": 100,
        "strike_step": 50,
        "hard_sl_pct": 0.14,
        "order_type": "LIMIT",
        "tick_size": 1.0,
        "eia_blackout_day": 2  # Wednesday (EIA Crude Oil Inventories)
    },
    "MCX_NATURALGAS": {
        "session_id": "MCX_NATURALGAS",
        "segment": "MCX",
        "underlying_type": "FUTURES",
        "symbol": "NATURALGAS",
        "display_name": "NATURAL GAS",
        "spot_instrument_key": "",  # Resolved dynamically
        "timeframe": "5minute",
        "timeframe_minutes": 5,
        "active_windows": [(time(17, 0), time(22, 55))],
        "auto_square_off_time": time(22, 55),
        "lot_size": 1250,
        "strike_step": 5,
        "hard_sl_pct": 0.15,
        "order_type": "LIMIT",
        "tick_size": 0.10,
        "eia_blackout_day": 3  # Thursday (EIA Natural Gas Storage)
    },
    "MCX_GOLDM": {
        "session_id": "MCX_GOLDM",
        "segment": "MCX",
        "underlying_type": "FUTURES",
        "symbol": "GOLDM",
        "display_name": "GOLD MINI",
        "spot_instrument_key": "",  # Resolved dynamically
        "timeframe": "5minute",
        "timeframe_minutes": 5,
        "active_windows": [(time(17, 0), time(22, 55))],
        "auto_square_off_time": time(22, 55),
        "lot_size": 10,
        "strike_step": 100,
        "hard_sl_pct": 0.12,
        "order_type": "LIMIT",
        "tick_size": 1.0,
        "eia_blackout_day": None
    }
}

@dataclass
class TradingConfig:
    # Session Information
    session_id: str = "NSE_NIFTY"
    segment: str = "NSE"
    underlying_type: str = "INDEX"
    symbol: str = "NIFTY"
    display_name: str = "NIFTY 50"

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
    strike_step: int = 50
    timeframe: str = "3minute"
    timeframe_minutes: int = 3
    order_type: str = "MARKET"
    tick_size: float = 0.05

    # Risk Management & Limits (INR)
    max_risk_per_trade: float = 1500.0
    daily_max_loss: float = 4000.0
    max_consecutive_sl: int = 3

    # Scalping Strategy Parameters
    hard_sl_pct: float = 0.10
    time_sl_candles: int = 3
    target_1_points: float = 10.0
    target_2_points: float = 15.0
    stoch_rsi_oversold: float = 30.0
    stoch_rsi_overbought: float = 70.0
    volume_multiplier: float = 1.5
    supertrend_period: int = 7
    supertrend_multiplier: float = 2.0

    # Active IST Time Windows & Shutdown
    active_windows: List[Tuple[time, time]] = field(default_factory=lambda: [(time(9, 30), time(12, 0)), (time(13, 30), time(15, 10))])
    morning_window: Tuple[time, time] = (time(9, 30), time(12, 0))
    afternoon_window: Tuple[time, time] = (time(13, 30), time(15, 10))
    auto_square_off_time: time = time(15, 10)
    eia_blackout_day: Optional[int] = None


def load_config() -> TradingConfig:
    """Loads and validates configuration from environment variables with session profiling."""
    session_id = os.getenv("SESSION_ID", "NSE_NIFTY").strip().upper()
    if session_id not in SESSION_PROFILES:
        # Fallback to index_name env if provided
        idx = os.getenv("INDEX_NAME", "NIFTY").strip().upper()
        if idx in ("CRUDEOIL", "MCX_CRUDEOIL"):
            session_id = "MCX_CRUDEOIL"
        elif idx in ("NATURALGAS", "MCX_NATURALGAS"):
            session_id = "MCX_NATURALGAS"
        elif idx in ("GOLDM", "MCX_GOLDM"):
            session_id = "MCX_GOLDM"
        else:
            session_id = "NSE_NIFTY"

    profile = SESSION_PROFILES[session_id]

    # DRY_RUN defaults to True for safety
    dry_run_env = os.getenv("DRY_RUN", "true").strip().lower()
    dry_run = dry_run_env in ("true", "1", "yes", "t")

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
        session_id=session_id,
        segment=profile["segment"],
        underlying_type=profile["underlying_type"],
        symbol=profile["symbol"],
        display_name=profile["display_name"],
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
        index_name=profile["symbol"],
        spot_instrument_key=profile["spot_instrument_key"],
        lot_size=int(os.getenv("LOT_SIZE", str(profile["lot_size"]))),
        strike_step=int(profile["strike_step"]),
        timeframe=os.getenv("TIMEFRAME", profile["timeframe"]).strip(),
        timeframe_minutes=profile["timeframe_minutes"],
        order_type=profile["order_type"],
        tick_size=float(profile["tick_size"]),
        max_risk_per_trade=float(os.getenv("MAX_RISK_PER_TRADE", "1500.0")),
        daily_max_loss=float(os.getenv("DAILY_MAX_LOSS", "4000.0")),
        max_consecutive_sl=int(os.getenv("MAX_CONSECUTIVE_SL", "3")),
        hard_sl_pct=float(profile["hard_sl_pct"]),
        active_windows=profile["active_windows"],
        morning_window=profile["active_windows"][0] if profile["active_windows"] else (time(9, 30), time(12, 0)),
        afternoon_window=profile["active_windows"][1] if len(profile["active_windows"]) > 1 else profile["active_windows"][0],
        auto_square_off_time=profile["auto_square_off_time"],
        eia_blackout_day=profile["eia_blackout_day"]
    )
    return config

config = load_config()
