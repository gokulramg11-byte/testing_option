import React, { useState, useEffect } from 'react';
import { db, isFirebaseConfigured, auth, logoutUser } from './firebase';
import { doc, onSnapshot, collection, query, orderBy, limit } from 'firebase/firestore';
import { onAuthStateChanged } from 'firebase/auth';

import LoginScreen from './components/LoginScreen';
import CockpitScreen from './components/CockpitScreen';
import TradeLogScreen from './components/TradeLogScreen';
import RiskRulesModal from './components/RiskRulesModal';

export default function App() {
  const [user, setUser] = useState(null);
  const [currentTab, setCurrentTab] = useState('COCKPIT'); // 'COCKPIT' | 'ORDERS' | 'SYSTEM'
  const [showRulesModal, setShowRulesModal] = useState(false);
  
  // Real-time telemetry state
  const [telemetry, setTelemetry] = useState({
    heartbeat: 'ONLINE',
    mode: 'PAPER',
    armed: true,
    latency_ms: 12,
    index_name: 'NIFTY 50',
    spot_ltp: 24350.25,
    spot_change: 125.40,
    spot_change_pct: 0.52,
    net_daily_mtm: 3450.00,
    win_rate: 75.0,
    total_trades: '3/4',
    consecutive_sl: 0,
    max_consecutive_sl: 3,
    signal_status: 'BUY_CE',
    gates: [
      { id: 1, name: '1. Active Time Window', pass: true, label: 'PASS', value: '10:15 IST (Active Session 09:30-12:00)' },
      { id: 2, name: '2. Spot > VWAP & 9 EMA', pass: true, label: 'PASS', value: 'Spot 24350 > VWAP 24310 & EMA 24325' },
      { id: 3, name: '3. 9 EMA Pullback Touch', pass: true, label: 'PASS', value: 'Prev Candle Low 24322 <= EMA 24325' },
      { id: 4, name: '4. Trigger Candle Close', pass: true, label: 'PASS', value: 'Closed Green +14.5 pts above 9 EMA' },
      { id: 5, name: '5. Stoch RSI Cross', pass: true, label: 'PASS', value: '%K 28.4 crossed %D 22.1 (Oversold < 30)' },
      { id: 6, name: '6. 1.5x Volume Spike', pass: true, label: 'PASS', value: '182.4K vol > 1.5x SMA 112K' },
      { id: 7, name: '7. Option VWAP & OI Filter', pass: true, label: 'PASS', value: 'Call > VWAP (154.2 > 148.0) & Call OI -12.4% Short Covering' }
    ],
    active_position: {
      active: true,
      strike: 'NIFTY 24350 CE 21-NOV',
      option_type: 'CE',
      entry_price: 142.50,
      current_ltp: 168.20,
      sl_price: 152.00,
      pnl: 1927.50,
      points_captured: 25.70,
      lots: 1,
      qty: 75,
      entry_time: '10:18:02',
      time_sl_status: '4m 12s remaining (Candle 2/3)',
      is_breakeven: true,
      is_partial: false
    }
  });

  const [tradeLogs, setTradeLogs] = useState([]);

  // Auth observer
  useEffect(() => {
    if (isFirebaseConfigured && auth) {
      const unsubscribe = onAuthStateChanged(auth, (currentUser) => {
        setUser(currentUser);
      });
      return () => unsubscribe();
    }
  }, []);

  // Real-time Firestore sync
  useEffect(() => {
    if (isFirebaseConfigured && db && user) {
      // 1. Subscribe to 'live_state/current'
      const stateDocRef = doc(db, 'live_state', 'current');
      const unsubState = onSnapshot(stateDocRef, (snap) => {
        if (snap.exists()) {
          setTelemetry((prev) => ({ ...prev, ...snap.data() }));
        }
      }, (err) => console.warn('Firestore live_state listener error:', err));

      // 2. Subscribe to 'trade_history' collection
      const q = query(collection(db, 'trade_history'), orderBy('timestamp', 'desc'), limit(50));
      const unsubTrades = onSnapshot(q, (snap) => {
        const fetched = [];
        snap.forEach((d) => fetched.push(d.data()));
        if (fetched.length > 0) {
          setTradeLogs(fetched);
        }
      }, (err) => console.warn('Firestore trade_history listener error:', err));

      return () => {
        unsubState();
        unsubTrades();
      };
    } else if (!isFirebaseConfigured) {
      // High-fidelity background simulation for instant local testing
      const interval = setInterval(() => {
        setTelemetry((prev) => {
          const delta = (Math.random() - 0.45) * 1.5;
          const newSpot = Math.round((prev.spot_ltp + delta) * 100) / 100;
          let newPos = prev.active_position ? { ...prev.active_position } : null;
          if (newPos) {
            const optDelta = Math.round(delta * 0.5 * 100) / 100;
            newPos.current_ltp = Math.max(30, Math.round((newPos.current_ltp + optDelta) * 100) / 100);
            newPos.points_captured = Math.round((newPos.current_ltp - newPos.entry_price) * 100) / 100;
            newPos.pnl = Math.round(newPos.points_captured * newPos.qty * 100) / 100;
          }
          return {
            ...prev,
            spot_ltp: newSpot,
            active_position: newPos,
            net_daily_mtm: 3450.00 + (newPos ? newPos.pnl - 1927.50 : 0)
          };
        });
      }, 2000);
      return () => clearInterval(interval);
    }
  }, [user]);

  const handleLogout = async () => {
    await logoutUser();
    setUser(null);
  };

  // If operator not authenticated, show Login Screen
  if (!user) {
    return <LoginScreen onLoginSuccess={setUser} />;
  }

  return (
    <div className="bg-canvas min-h-screen">
      {/* Screen Router */}
      {currentTab === 'COCKPIT' && (
        <CockpitScreen 
          telemetry={telemetry} 
          onOpenRules={() => setShowRulesModal(true)}
          onLogout={handleLogout}
        />
      )}

      {currentTab === 'ORDERS' && (
        <TradeLogScreen 
          trades={tradeLogs} 
          telemetry={telemetry}
        />
      )}

      {/* ================= SECTION 4: BOTTOM NAVIGATION DOCK ================= */}
      <nav className="fixed bottom-0 left-0 w-full z-50 flex justify-around items-center h-14 bg-canvas/95 backdrop-blur border-t border-border-subtle max-w-lg mx-auto left-0 right-0">
        {/* Cockpit / Execute */}
        <button
          onClick={() => setCurrentTab('COCKPIT')}
          className={`flex flex-col items-center justify-center py-1 w-1/3 h-full transition-colors ${
            currentTab === 'COCKPIT' 
              ? 'text-emerald-bright border-t-2 border-emerald-bright font-bold' 
              : 'text-text-muted hover:text-text-primary'
          }`}
        >
          <span className="material-symbols-outlined text-[20px]">bolt</span>
          <span className="text-[10px] font-mono mt-0.5 tracking-wider uppercase">COCKPIT</span>
        </button>

        {/* Risk Sentinel / Rules */}
        <button
          onClick={() => setShowRulesModal(true)}
          className="flex flex-col items-center justify-center py-1 w-1/3 h-full text-text-muted hover:text-text-primary transition-colors"
        >
          <span className="material-symbols-outlined text-[20px]">security</span>
          <span className="text-[10px] font-mono mt-0.5 tracking-wider uppercase">SENTINEL</span>
        </button>

        {/* Orders & Logs */}
        <button
          onClick={() => setCurrentTab('ORDERS')}
          className={`flex flex-col items-center justify-center py-1 w-1/3 h-full transition-colors ${
            currentTab === 'ORDERS' 
              ? 'text-emerald-bright border-t-2 border-emerald-bright font-bold' 
              : 'text-text-muted hover:text-text-primary'
          }`}
        >
          <span className="material-symbols-outlined text-[20px]">receipt_long</span>
          <span className="text-[10px] font-mono mt-0.5 tracking-wider uppercase">TRADE LOGS</span>
        </button>
      </nav>

      {/* Risk Rules & Circuit Breaker Modal */}
      <RiskRulesModal
        isOpen={showRulesModal}
        onClose={() => setShowRulesModal(false)}
        telemetry={telemetry}
      />
    </div>
  );
}
