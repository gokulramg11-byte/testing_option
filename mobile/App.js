import React, { useState, useEffect } from 'react';
import { 
  View, 
  Text, 
  TouchableOpacity, 
  StyleSheet, 
  StatusBar,
  SafeAreaView 
} from 'react-native';
import { Colors } from './src/theme';
import { db, isFirebaseConfigured } from './src/firebase';
import { doc, onSnapshot, collection, query, orderBy, limit } from 'firebase/firestore';

import LoginScreen from './src/screens/LoginScreen';
import CockpitScreen from './src/screens/CockpitScreen';
import TradeLogScreen from './src/screens/TradeLogScreen';
import RiskRulesModal from './src/components/RiskRulesModal';

export default function App() {
  const [user, setUser] = useState(null);
  const [currentTab, setCurrentTab] = useState('COCKPIT'); // 'COCKPIT' | 'LOGS'
  const [showRulesModal, setShowRulesModal] = useState(false);

  // Live real-time telemetry state
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

  // Firestore Real-Time Listener or Fallback Simulation
  useEffect(() => {
    if (isFirebaseConfigured && db && user) {
      // 1. Live State Listener
      const stateDoc = doc(db, 'live_state', 'current');
      const unsubState = onSnapshot(stateDoc, (snap) => {
        if (snap.exists()) {
          setTelemetry((prev) => ({ ...prev, ...snap.data() }));
        }
      });

      // 2. Trade History Listener
      const q = query(collection(db, 'trade_history'), orderBy('timestamp', 'desc'), limit(50));
      const unsubTrades = onSnapshot(q, (snap) => {
        const fetched = [];
        snap.forEach((d) => fetched.push(d.data()));
        if (fetched.length > 0) setTradeLogs(fetched);
      });

      return () => {
        unsubState();
        unsubTrades();
      };
    } else {
      // High-Fidelity Local Simulation
      const interval = setInterval(() => {
        setTelemetry((prev) => {
          const delta = (Math.random() - 0.45) * 1.5;
          const newSpot = Math.round((prev.spot_ltp + delta) * 100) / 100;
          let newPos = prev.active_position ? { ...prev.active_position } : null;
          if (newPos) {
            const optDelta = Math.round(delta * 0.52 * 100) / 100;
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

  if (!user) {
    return (
      <View style={styles.root}>
        <StatusBar barStyle="light-content" backgroundColor={Colors.canvas} />
        <LoginScreen onLoginSuccess={setUser} />
      </View>
    );
  }

  return (
    <View style={styles.root}>
      <StatusBar barStyle="light-content" backgroundColor={Colors.canvas} />
      
      {/* Screen Body */}
      <View style={styles.screenContainer}>
        {currentTab === 'COCKPIT' ? (
          <CockpitScreen 
            telemetry={telemetry}
            onOpenRules={() => setShowRulesModal(true)}
          />
        ) : (
          <TradeLogScreen 
            trades={tradeLogs}
          />
        )}
      </View>

      {/* Bottom Mobile Navigation Dock */}
      <SafeAreaView style={styles.dockWrapper}>
        <View style={styles.bottomDock}>
          <TouchableOpacity 
            style={[styles.dockTab, currentTab === 'COCKPIT' && styles.dockTabActive]}
            onPress={() => setCurrentTab('COCKPIT')}
          >
            <Text style={styles.dockIcon}>⚡</Text>
            <Text style={[styles.dockLabel, currentTab === 'COCKPIT' && styles.dockLabelActive]}>
              COCKPIT
            </Text>
          </TouchableOpacity>

          <TouchableOpacity 
            style={styles.dockTab}
            onPress={() => setShowRulesModal(true)}
          >
            <Text style={styles.dockIcon}>🛡️</Text>
            <Text style={styles.dockLabel}>SENTINEL</Text>
          </TouchableOpacity>

          <TouchableOpacity 
            style={[styles.dockTab, currentTab === 'LOGS' && styles.dockTabActive]}
            onPress={() => setCurrentTab('LOGS')}
          >
            <Text style={styles.dockIcon}>📜</Text>
            <Text style={[styles.dockLabel, currentTab === 'LOGS' && styles.dockLabelActive]}>
              TRADE LOGS
            </Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>

      {/* Risk Rules Modal */}
      <RiskRulesModal
        visible={showRulesModal}
        onClose={() => setShowRulesModal(false)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: Colors.canvas,
  },
  screenContainer: {
    flex: 1,
  },
  dockWrapper: {
    backgroundColor: Colors.canvas,
    borderTopWidth: 1,
    borderTopColor: Colors.borderSubtle,
  },
  bottomDock: {
    flexDirection: 'row',
    height: 52,
    alignItems: 'center',
    justifyContent: 'space-around',
    backgroundColor: Colors.canvas,
  },
  dockTab: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    height: '100%',
  },
  dockTabActive: {
    borderTopWidth: 2,
    borderTopColor: Colors.emeraldBright,
  },
  dockIcon: {
    fontSize: 16,
  },
  dockLabel: {
    fontFamily: 'monospace',
    fontSize: 9,
    color: Colors.textMuted,
    marginTop: 2,
    letterSpacing: 0.5,
  },
  dockLabelActive: {
    color: Colors.emeraldBright,
    fontWeight: 'bold',
  },
});
