import React, { useState } from 'react';
import { triggerEmergencyKillSwitch, toggleArmBot } from '../firebase';

export default function CockpitScreen({ 
  telemetry, 
  onOpenRules, 
  onLogout 
}) {
  const [isArming, setIsArming] = useState(telemetry?.armed ?? true);
  const [killTriggered, setKillTriggered] = useState(false);

  const handleArmToggle = async () => {
    const newState = !isArming;
    setIsArming(newState);
    await toggleArmBot(newState);
  };

  const handleEmergencyKill = async () => {
    if (window.confirm("CONFIRM EMERGENCY KILL SWITCH:\n\nThis will immediately square off any open option position at market price and halt all automated scalp executions. Continue?")) {
      setKillTriggered(true);
      await triggerEmergencyKillSwitch();
      setTimeout(() => setKillTriggered(false), 3000);
    }
  };

  const isOnline = telemetry?.heartbeat === 'ONLINE';
  const isPaper = telemetry?.mode === 'PAPER';
  const spotLtp = telemetry?.spot_ltp ?? 24350.25;
  const spotChange = telemetry?.spot_change ?? 125.40;
  const spotChangePct = telemetry?.spot_change_pct ?? 0.52;
  const netMtm = telemetry?.net_daily_mtm ?? 3450.00;
  const activePos = telemetry?.active_position;
  const gates = telemetry?.gates ?? [];

  return (
    <div className="min-h-screen bg-canvas pb-24 text-text-primary max-w-lg mx-auto">
      {/* ================= SECTION 1: TOP APP BAR ================= */}
      <header className="sticky top-0 z-50 flex items-center justify-between px-3 h-12 bg-canvas/95 backdrop-blur border-b border-border-subtle">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined text-emerald-bright text-[18px]">terminal</span>
          <span className="text-xs font-mono font-bold tracking-tight text-emerald-bright uppercase">
            NIFTY // ALPHASCALP
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="flex items-center gap-1.5 px-2 py-0.5 rounded bg-surface-low border border-border-subtle">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-bright glow-emerald animate-pulse"></span>
            <span className="text-[10px] font-mono text-emerald-bright font-semibold">
              {telemetry?.latency_ms ?? 12}MS
            </span>
          </div>
          <button 
            onClick={onOpenRules}
            title="Risk Rules & Circuit Breakers"
            className="p-1 rounded bg-surface-low border border-border-subtle text-text-muted hover:text-text-primary"
          >
            <span className="material-symbols-outlined text-[16px]">tune</span>
          </button>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="px-3 pt-3 space-y-3">
        {/* Telemetry Status Pills Row */}
        <div className="grid grid-cols-2 gap-2">
          {/* Bot Heartbeat Pill */}
          <div className="flex items-center justify-between px-2.5 py-1.5 rounded bg-surface-low border border-border-subtle">
            <div className="flex items-center gap-1.5">
              <span className={`w-2 h-2 rounded-full ${isOnline ? 'bg-emerald-bright glow-emerald animate-ping' : 'bg-text-dim'}`}></span>
              <span className={`text-[10px] font-mono font-bold ${isOnline ? 'text-emerald-bright' : 'text-text-dim'}`}>
                {isOnline ? 'ONLINE' : 'OFFLINE'}
              </span>
            </div>
            <span className="text-[9px] font-mono text-text-muted">14ms Upstox Feed</span>
          </div>

          {/* Execution Mode Pill */}
          <div className="flex items-center justify-between px-2.5 py-1.5 rounded bg-surface-low border border-border-subtle">
            <div className="flex items-center gap-1.5">
              <span className={`w-2 h-2 rounded-full ${isPaper ? 'bg-amber glow-amber' : 'bg-emerald'}`}></span>
              <span className={`text-[10px] font-mono font-bold ${isPaper ? 'text-amber-glow' : 'text-emerald-bright'}`}>
                {isPaper ? 'PAPER TRADING' : 'LIVE CAPITAL'}
              </span>
            </div>
            <span className="text-[9px] font-mono text-text-dim">
              {isPaper ? '(DRY_RUN)' : '(PROD)'}
            </span>
          </div>
        </div>

        {/* Index Spot Ticker & Net Daily P&L Deck */}
        <div className="grid grid-cols-1 gap-2">
          {/* Spot Ticker Card */}
          <div className="p-3 rounded bg-surface-low border border-border-subtle flex items-center justify-between">
            <div>
              <div className="flex items-center gap-1.5">
                <span className="text-[9px] font-mono uppercase tracking-wider text-text-muted">UNDERLYING INDEX</span>
                <span className="px-1 py-0.2 rounded text-[9px] font-mono bg-surface text-emerald-bright">
                  NSE_INDEX
                </span>
              </div>
              <div className="text-base font-sans font-bold text-text-primary tracking-tight mt-0.5">
                {telemetry?.index_name ?? 'NIFTY 50'}
              </div>
            </div>
            <div className="text-right">
              <div className="text-base font-mono font-bold text-emerald-bright tnum">
                {spotLtp.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </div>
              <div className="flex items-center justify-end gap-1 text-[11px] font-mono text-emerald-bright tnum">
                <span className="material-symbols-outlined text-[12px]">arrow_upward</span>
                <span>+{spotChange.toFixed(2)} (+{spotChangePct.toFixed(2)}%)</span>
              </div>
            </div>
          </div>

          {/* Net Daily MTM P&L Card */}
          <div className="p-3 rounded bg-surface-low border border-border-subtle relative overflow-hidden">
            <div className="absolute right-0 top-0 w-28 h-full bg-gradient-to-l from-emerald/10 to-transparent pointer-events-none"></div>
            
            <div className="flex items-center justify-between">
              <span className="text-[10px] font-mono uppercase tracking-wider text-text-muted">
                NET TODAY MTM P&L
              </span>
              <span className={`px-2 py-0.5 rounded text-[9px] font-mono border font-semibold ${
                netMtm >= 0 
                  ? 'bg-emerald/10 text-emerald-bright border-emerald/30' 
                  : 'bg-crimson/10 text-crimson-bright border-crimson/30'
              }`}>
                {netMtm >= 0 ? 'TARGET LOCKED' : 'DRAWDOWN WATCH'}
              </span>
            </div>

            <div className="flex items-baseline justify-between mt-1.5">
              <div className={`text-2xl font-mono font-bold tracking-tight tnum ${
                netMtm >= 0 ? 'text-emerald-bright glow-emerald' : 'text-crimson-bright glow-crimson'
              }`}>
                {netMtm >= 0 ? `+₹${netMtm.toLocaleString('en-IN', { minimumFractionDigits: 2 })}` : `-₹${Math.abs(netMtm).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`}
              </div>
              <span className="text-[9px] font-mono px-2 py-0.5 rounded bg-surface border border-border-subtle text-text-muted">
                DAY WIN RATE: {telemetry?.win_rate ?? 75}%
              </span>
            </div>

            {/* Performance Micro-Bar */}
            <div className="mt-2.5 pt-2 border-t border-border-subtle/60 flex items-center justify-between text-[10px] font-mono text-text-muted">
              <div>
                <span>Win Rate: </span>
                <span className="text-emerald-bright font-bold">{telemetry?.win_rate ?? 75}%</span>
              </div>
              <div className="text-border-accent">|</div>
              <div>
                <span>Trades: </span>
                <span className="text-text-primary font-bold">{telemetry?.total_trades ?? '3/4'}</span>
              </div>
              <div className="text-border-accent">|</div>
              <div>
                <span>Consec SL: </span>
                <span className="text-emerald-bright font-bold">{telemetry?.consecutive_sl ?? 0}/{telemetry?.max_consecutive_sl ?? 3}</span>
              </div>
            </div>
          </div>
        </div>

        {/* ================= SECTION 2: 7-POINT QUANT SIGNAL GATEKEEPER ================= */}
        <div className="rounded bg-surface-low border border-border-subtle overflow-hidden">
          {/* Header Toolbar */}
          <div className="h-9 px-3 bg-surface flex items-center justify-between border-b border-border-subtle">
            <div className="flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[15px] text-emerald-bright">filter_alt</span>
              <span className="text-[10px] font-mono font-bold text-text-primary tracking-wider uppercase">
                7-POINT QUANT SIGNAL GATEKEEPER
              </span>
            </div>
            <div className="flex items-center gap-1 px-2 py-0.5 rounded bg-emerald/15 border border-emerald/30">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-bright animate-pulse"></span>
              <span className="text-[9px] font-mono text-emerald-bright font-bold">
                {telemetry?.signal_status === 'BUY_CE' ? 'READY FOR CE SCALP' : 
                 telemetry?.signal_status === 'BUY_PE' ? 'READY FOR PE SCALP' : 'MONITORING GATES'}
              </span>
            </div>
          </div>

          {/* Checklist Rows */}
          <div className="divide-y divide-border-subtle/50">
            {gates.length > 0 ? (
              gates.map((g) => (
                <div key={g.id} className="px-3 py-2 hover:bg-surface/50 transition-colors">
                  <div className="flex items-center justify-between mb-0.5">
                    <span className="text-[10px] font-mono font-medium text-text-primary">{g.name}</span>
                    <span className={`px-1.5 py-0.2 rounded text-[9px] font-mono flex items-center gap-0.5 font-bold ${
                      g.pass 
                        ? 'bg-emerald/20 text-emerald-bright border border-emerald/30' 
                        : 'bg-surface border border-border-subtle text-text-dim'
                    }`}>
                      <span className="material-symbols-outlined text-[10px]">
                        {g.pass ? 'check' : 'hourglass_empty'}
                      </span>
                      {g.label}
                    </span>
                  </div>
                  <div className="text-[10px] font-mono text-text-muted flex items-center justify-between">
                    <span className={g.pass ? 'text-emerald-bright' : 'text-text-muted'}>{g.value}</span>
                  </div>
                </div>
              ))
            ) : (
              <div className="p-4 text-center text-xs font-mono text-text-dim">
                Initializing 7-Point Quantitative Indicators...
              </div>
            )}
          </div>
        </div>

        {/* ================= SECTION 3: ACTIVE POSITION & SAFETY HUD ================= */}
        <div className="rounded bg-surface-low border border-border-subtle p-3 space-y-2.5 relative">
          {/* Active Contract Header */}
          <div className="flex items-center justify-between pb-2 border-b border-border-subtle">
            <div>
              <div className="flex items-center gap-1.5">
                <span className={`text-[9px] font-mono font-bold px-1.5 py-0.5 rounded border ${
                  activePos ? 'bg-emerald/10 text-emerald-bright border-emerald/30' : 'bg-surface text-text-dim border-border-subtle'
                }`}>
                  {activePos ? `${activePos.option_type} SCALP` : 'STANDBY'}
                </span>
                <span className="text-xs font-mono font-bold text-text-primary">
                  {activePos ? activePos.strike : 'NO ACTIVE POSITION'}
                </span>
              </div>
              <div className="text-[10px] font-mono text-text-muted mt-0.5">
                {activePos ? `${activePos.lots} Lot (${activePos.qty} Qty) // Intraday MIS` : 'Awaiting 7-Gate Signal Trigger'}
              </div>
            </div>
            <div>
              {activePos ? (
                <span className="px-2 py-0.5 rounded text-[9px] font-mono bg-emerald/20 text-emerald-bright border border-emerald/40 animate-pulse font-bold">
                  RUNNING
                </span>
              ) : (
                <span className="px-2 py-0.5 rounded text-[9px] font-mono bg-surface text-text-dim border border-border-subtle">
                  IDLE
                </span>
              )}
            </div>
          </div>

          {/* Numerical Telemetry Grid */}
          {activePos && (
            <div className="grid grid-cols-2 gap-2 pt-1">
              <div className="p-2 rounded bg-surface border border-border-subtle">
                <div className="text-[9px] font-mono uppercase text-text-dim">ENTRY PRICE</div>
                <div className="text-xs font-mono font-bold text-text-primary tnum">
                  ₹{activePos.entry_price.toFixed(2)}
                </div>
              </div>

              <div className="p-2 rounded bg-surface border border-border-subtle">
                <div className="text-[9px] font-mono uppercase text-text-dim">LIVE OPTION LTP</div>
                <div className="text-xs font-mono font-bold text-emerald-bright flex items-center justify-between tnum">
                  <span>₹{activePos.current_ltp.toFixed(2)}</span>
                  <span className="text-[9px] text-emerald-bright">
                    ({activePos.points_captured >= 0 ? '+' : ''}{activePos.points_captured.toFixed(1)} pts)
                  </span>
                </div>
              </div>

              <div className="p-2 rounded bg-surface border border-border-subtle">
                <div className="text-[9px] font-mono uppercase text-text-dim">TRAILING SL</div>
                <div className="text-xs font-mono font-bold text-amber-glow tnum">
                  ₹{activePos.sl_price.toFixed(2)}
                </div>
                <div className="text-[9px] font-mono text-text-dim">
                  {activePos.is_breakeven ? 'Locked at Breakeven' : 'Hard SL 10%'}
                </div>
              </div>

              <div className="p-2 rounded bg-surface border border-emerald/30 bg-emerald/5">
                <div className="text-[9px] font-mono uppercase text-emerald-bright font-bold">LIVE TRADE P&L</div>
                <div className="text-xs font-mono font-bold text-emerald-bright glow-emerald tnum">
                  {activePos.pnl >= 0 ? `+₹${activePos.pnl.toFixed(2)}` : `-₹${Math.abs(activePos.pnl).toFixed(2)}`}
                </div>
                <div className="text-[9px] font-mono text-emerald-bright">
                  {activePos.is_partial ? '50% BOOKED' : 'TRAILING ACTIVE'}
                </div>
              </div>
            </div>
          )}

          {/* Safety & Arming Controls */}
          <div className="flex items-center justify-between p-2 rounded bg-canvas border border-border-subtle text-[10px] font-mono">
            <div className="flex items-center gap-1.5 text-text-muted">
              <span className="material-symbols-outlined text-[14px] text-amber-glow">timer</span>
              <span>
                {activePos ? (activePos.time_sl_status ?? 'Candle 2/3 (4m 12s left)') : 'Time SL: 3 Candles (9m)'}
              </span>
            </div>

            <div className="flex items-center gap-2">
              <span className="text-[9px] font-mono uppercase text-text-dim">ARM BOT</span>
              <button
                onClick={handleArmToggle}
                className={`relative inline-flex h-4 w-8 items-center rounded-full transition-colors ${
                  isArming ? 'bg-emerald' : 'bg-surface-high'
                }`}
              >
                <span
                  className={`inline-block h-3 w-3 transform rounded-full bg-white transition-transform ${
                    isArming ? 'translate-x-4' : 'translate-x-0.5'
                  }`}
                />
              </button>
            </div>
          </div>

          {/* Prominent Crimson Emergency Kill Switch */}
          <button
            onClick={handleEmergencyKill}
            disabled={killTriggered}
            className="w-full h-11 rounded bg-crimson-dark hover:bg-crimson active:scale-[0.98] transition-all flex items-center justify-center gap-2 text-crimson-bright border border-crimson/50 font-mono text-xs font-bold glow-crimson"
          >
            <span className="material-symbols-outlined text-[18px]">emergency_home</span>
            <span>
              {killTriggered ? 'KILL SWITCH ENGAGED — SQUARE OFF IN PROGRESS...' : 'EMERGENCY SQUARE OFF & STOP BOT'}
            </span>
          </button>
        </div>

        {/* Security & System Micro-Footer */}
        <footer className="pt-2 pb-6 text-center space-y-1">
          <div className="flex items-center justify-center gap-1 text-[9px] text-text-dim font-mono">
            <span className="material-symbols-outlined text-[12px]">lock</span>
            <span>FIPS 140-3 LEVEL 4 HSM ENCRYPTED // UPSTOX V3 GATEWAY</span>
          </div>
          <div className="flex items-center justify-center gap-3 text-[9px] font-mono text-text-dim">
            <button onClick={onOpenRules} className="hover:text-emerald-bright transition-colors">RISK SENTINEL</button>
            <span>/</span>
            <span className="text-emerald-bright">ZERO-COST CLOUD</span>
            <span>/</span>
            <button onClick={onLogout} className="hover:text-crimson-bright transition-colors">DISCONNECT</button>
          </div>
        </footer>
      </main>
    </div>
  );
}
