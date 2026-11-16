"""
Technical Indicators Engine:
Implements intraday VWAP, 9 EMA, Stochastic RSI (14,14,3,3), 15 Volume SMA, and SuperTrend (7,2).
Includes pure-pandas implementations as zero-dependency fallbacks.
"""

import pandas as pd
import numpy as np

def calculate_vwap(df: pd.DataFrame) -> pd.Series:
    """Calculates Intraday Volume Weighted Average Price (VWAP)."""
    if df.empty or "volume" not in df.columns or "close" not in df.columns:
        return pd.Series(dtype=float)
    
    typical_price = (df["high"] + df["low"] + df["close"]) / 3.0
    vol = df["volume"].replace(0, 1) # avoid division by zero
    cum_vol_price = (typical_price * vol).cumsum()
    cum_vol = vol.cumsum()
    vwap = cum_vol_price / cum_vol
    return vwap

def calculate_ema(series: pd.Series, period: int = 9) -> pd.Series:
    """Calculates Exponential Moving Average (EMA)."""
    return series.ewm(span=period, adjust=False).mean()

def calculate_sma(series: pd.Series, period: int = 15) -> pd.Series:
    """Calculates Simple Moving Average (SMA)."""
    return series.rolling(window=period, min_periods=1).mean()

def calculate_stoch_rsi(df: pd.DataFrame, length: int = 14, rsi_length: int = 14, k: int = 3, d: int = 3):
    """
    Calculates Stochastic RSI (14, 14, 3, 3).
    Returns (stoch_k, stoch_d).
    """
    close = df["close"]
    # 1. RSI calculation
    delta = close.diff()
    gain = delta.clip(lower=0)
    loss = -1 * delta.clip(upper=0)
    
    avg_gain = gain.ewm(alpha=1/rsi_length, min_periods=rsi_length, adjust=False).mean()
    avg_loss = loss.ewm(alpha=1/rsi_length, min_periods=rsi_length, adjust=False).mean()
    
    rs = avg_gain / avg_loss.replace(0, 1e-9)
    rsi = 100.0 - (100.0 / (1.0 + rs))

    # 2. Stochastic of RSI
    rsi_low = rsi.rolling(window=length, min_periods=1).min()
    rsi_high = rsi.rolling(window=length, min_periods=1).max()
    rsi_range = (rsi_high - rsi_low).replace(0, 1e-9)
    
    stoch = 100.0 * (rsi - rsi_low) / rsi_range
    stoch_k = stoch.rolling(window=k, min_periods=1).mean()
    stoch_d = stoch_k.rolling(window=d, min_periods=1).mean()
    
    return stoch_k, stoch_d

def calculate_supertrend(df: pd.DataFrame, period: int = 7, multiplier: float = 2.0):
    """
    Calculates SuperTrend indicator (7, 2).
    Returns (supertrend_series, direction_series) where direction: 1 = Bullish, -1 = Bearish.
    """
    high = df["high"]
    low = df["low"]
    close = df["close"]
    
    # ATR calculation
    tr1 = high - low
    tr2 = (high - close.shift(1)).abs()
    tr3 = (low - close.shift(1)).abs()
    tr = pd.concat([tr1, tr2, tr3], axis=1).max(axis=1)
    atr = tr.rolling(window=period, min_periods=1).mean()

    hl2 = (high + low) / 2.0
    upperband = hl2 + (multiplier * atr)
    lowerband = hl2 - (multiplier * atr)

    supertrend = [0.0] * len(df)
    direction = [1] * len(df)

    for i in range(1, len(df)):
        curr_close = close.iloc[i]
        prev_close = close.iloc[i - 1]
        curr_upper = upperband.iloc[i]
        curr_lower = lowerband.iloc[i]
        prev_supertrend = supertrend[i - 1]

        if curr_close > prev_supertrend:
            direction[i] = 1
        elif curr_close < prev_supertrend:
            direction[i] = -1
        else:
            direction[i] = direction[i - 1]

        if direction[i] == 1:
            supertrend[i] = max(curr_lower, prev_supertrend) if direction[i - 1] == 1 else curr_lower
        else:
            supertrend[i] = min(curr_upper, prev_supertrend) if direction[i - 1] == -1 else curr_upper

    return pd.Series(supertrend, index=df.index), pd.Series(direction, index=df.index)

def apply_all_indicators(df: pd.DataFrame) -> pd.DataFrame:
    """Enriches candles DataFrame with all required quantitative scalping indicators."""
    if len(df) < 15:
        return df

    data = df.copy()
    data["vwap"] = calculate_vwap(data)
    data["ema_9"] = calculate_ema(data["close"], period=9)
    data["vol_sma_15"] = calculate_sma(data["volume"], period=15)
    
    stoch_k, stoch_d = calculate_stoch_rsi(data, length=14, rsi_length=14, k=3, d=3)
    data["stoch_k"] = stoch_k
    data["stoch_d"] = stoch_d

    supertrend, st_dir = calculate_supertrend(data, period=7, multiplier=2.0)
    data["supertrend"] = supertrend
    data["supertrend_dir"] = st_dir

    return data
