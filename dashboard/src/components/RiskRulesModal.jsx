import React from 'react';

export default function RiskRulesModal({ isOpen, onClose, telemetry }) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
      <div className="w-full max-w-sm rounded-lg bg-surface-low border border-border-subtle p-4 text-text-primary space-y-4 shadow-2xl">
        <div className="flex items-center justify-between pb-2 border-b border-border-subtle">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-emerald-bright text-[18px]">security</span>
            <span className="text-xs font-mono font-bold uppercase tracking-wider text-text-primary">
              RISK PARAMETERS & CIRCUIT BREAKERS
            </span>
          </div>
          <button 
            onClick={onClose}
            className="text-text-muted hover:text-text-primary material-symbols-outlined text-[18px]"
          >
            close
          </button>
        </div>

        <div className="space-y-2.5 text-[11px] font-mono">
          <div className="flex justify-between p-2 rounded bg-surface border border-border-subtle">
            <span className="text-text-muted">Index & Timeframe:</span>
            <span className="text-text-primary font-bold">NIFTY 50 // 3-Minute Candles</span>
          </div>

          <div className="flex justify-between p-2 rounded bg-surface border border-border-subtle">
            <span className="text-text-muted">Max Risk Per Trade:</span>
            <span className="text-emerald-bright font-bold">₹1,500.00 (Hard Cap)</span>
          </div>

          <div className="flex justify-between p-2 rounded bg-surface border border-border-subtle">
            <span className="text-text-muted">Daily Max Loss Limit:</span>
            <span className="text-crimson-bright font-bold">₹4,000.00 (Circuit Halt)</span>
          </div>

          <div className="flex justify-between p-2 rounded bg-surface border border-border-subtle">
            <span className="text-text-muted">Max Consecutive SL:</span>
            <span className="text-amber-glow font-bold">3 Trades</span>
          </div>

          <div className="flex justify-between p-2 rounded bg-surface border border-border-subtle">
            <span className="text-text-muted">Active Time Windows:</span>
            <span className="text-text-primary font-bold">09:30–12:00 & 13:30–15:10</span>
          </div>

          <div className="flex justify-between p-2 rounded bg-surface border border-border-subtle">
            <span className="text-text-muted">Auto Square-Off & Cutoff:</span>
            <span className="text-amber-glow font-bold">15:10 IST (6-Hr Guard)</span>
          </div>

          <div className="flex justify-between p-2 rounded bg-surface border border-border-subtle">
            <span className="text-text-muted">Initial Hard Stop-Loss:</span>
            <span className="text-text-primary font-bold">10% Option Premium</span>
          </div>

          <div className="flex justify-between p-2 rounded bg-surface border border-border-subtle">
            <span className="text-text-muted">Time-Based Exit:</span>
            <span className="text-text-primary font-bold">3 Closed Candles (9 Mins)</span>
          </div>

          <div className="flex justify-between p-2 rounded bg-surface border border-border-subtle">
            <span className="text-text-muted">Trailing Mechanism:</span>
            <span className="text-text-primary font-bold">Breakeven @ +10pts, Trail @ +15pts</span>
          </div>
        </div>

        <div className="pt-2">
          <button
            onClick={onClose}
            className="w-full h-10 rounded bg-emerald hover:bg-emerald-bright text-canvas font-mono font-bold text-xs transition-colors"
          >
            ACKNOWLEDGE & RETURN
          </button>
        </div>
      </div>
    </div>
  );
}
