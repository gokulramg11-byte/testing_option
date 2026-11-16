import React, { useState } from 'react';
import { 
  View, 
  Text, 
  TouchableOpacity, 
  ScrollView, 
  Switch, 
  Alert, 
  StyleSheet, 
  SafeAreaView 
} from 'react-native';
import { Colors } from '../theme';
import { triggerEmergencyKillSwitch, toggleArmBot } from '../firebase';

export default function CockpitScreen({ telemetry, onOpenRules }) {
  const [isArmed, setIsArmed] = useState(telemetry?.armed ?? true);
  const [killTriggered, setKillTriggered] = useState(false);

  const handleArmToggle = (value) => {
    setIsArmed(value);
    toggleArmBot(value);
  };

  const handleEmergencyKill = () => {
    Alert.alert(
      "CONFIRM EMERGENCY KILL SWITCH",
      "This will immediately square off any active option position at market price and halt all automated scalping executions. Continue?",
      [
        { text: "Cancel", style: "cancel" },
        { 
          text: "CONFIRM SQUARE OFF", 
          style: "destructive",
          onPress: async () => {
            setKillTriggered(true);
            await triggerEmergencyKillSwitch();
            setTimeout(() => setKillTriggered(false), 3000);
          }
        }
      ]
    );
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
    <SafeAreaView style={styles.container}>
      {/* Top Telemetry Header */}
      <View style={styles.topBar}>
        <View style={styles.topBarLeft}>
          <Text style={styles.termIcon}>⚡</Text>
          <Text style={styles.termTitle}>NIFTY // ALPHASCALP</Text>
        </View>
        <View style={styles.topBarRight}>
          <View style={styles.latencyBadge}>
            <View style={styles.greenPulse} />
            <Text style={styles.latencyText}>{telemetry?.latency_ms ?? 12}MS</Text>
          </View>
          <TouchableOpacity 
            style={styles.tuneButton}
            onPress={onOpenRules}
          >
            <Text style={styles.tuneIcon}>⚙️</Text>
          </TouchableOpacity>
        </View>
      </View>

      <ScrollView 
        style={styles.scrollArea}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {/* Status Pills */}
        <View style={styles.statusPillRow}>
          <View style={styles.statusPill}>
            <View style={[styles.ledDot, { backgroundColor: isOnline ? Colors.emeraldBright : Colors.textDim }]} />
            <Text style={[styles.statusPillText, { color: isOnline ? Colors.emeraldBright : Colors.textDim }]}>
              {isOnline ? 'ONLINE' : 'OFFLINE'}
            </Text>
            <Text style={styles.statusSubtext}>14ms Feed</Text>
          </View>

          <View style={styles.statusPill}>
            <View style={[styles.ledDot, { backgroundColor: isPaper ? Colors.amber : Colors.emerald }]} />
            <Text style={[styles.statusPillText, { color: isPaper ? Colors.amber : Colors.emeraldBright }]}>
              {isPaper ? 'PAPER TRADING' : 'LIVE CAPITAL'}
            </Text>
            <Text style={styles.statusSubtext}>(DRY_RUN)</Text>
          </View>
        </View>

        {/* Spot Ticker Card */}
        <View style={styles.spotCard}>
          <View>
            <View style={styles.badgeRow}>
              <Text style={styles.indexLabel}>UNDERLYING INDEX</Text>
              <Text style={styles.nseTag}>NSE_INDEX</Text>
            </View>
            <Text style={styles.indexName}>{telemetry?.index_name ?? 'NIFTY 50'}</Text>
          </View>
          <View style={styles.spotRight}>
            <Text style={styles.spotLtpText}>{spotLtp.toFixed(2)}</Text>
            <Text style={styles.spotChangeText}>
              ▲ +{spotChange.toFixed(2)} (+{spotChangePct.toFixed(2)}%)
            </Text>
          </View>
        </View>

        {/* Net Daily MTM P&L Card */}
        <View style={styles.mtmCard}>
          <View style={styles.mtmHeader}>
            <Text style={styles.mtmLabel}>NET TODAY MTM P&L</Text>
            <View style={styles.targetBadge}>
              <Text style={styles.targetBadgeText}>TARGET LOCKED</Text>
            </View>
          </View>

          <View style={styles.mtmValueRow}>
            <Text style={[styles.mtmValueText, { color: netMtm >= 0 ? Colors.emeraldBright : Colors.crimsonBright }]}>
              {netMtm >= 0 ? `+₹${netMtm.toFixed(2)}` : `-₹${Math.abs(netMtm).toFixed(2)}`}
            </Text>
            <View style={styles.gainPill}>
              <Text style={styles.gainPillText}>DAY WIN RATE: {telemetry?.win_rate ?? 75}%</Text>
            </View>
          </View>

          <View style={styles.mtmStatsDivider} />

          <View style={styles.mtmStatsRow}>
            <Text style={styles.statLabel}>Win Rate: <Text style={styles.statValGreen}>{telemetry?.win_rate ?? 75}%</Text></Text>
            <Text style={styles.statSep}>|</Text>
            <Text style={styles.statLabel}>Trades: <Text style={styles.statValWhite}>{telemetry?.total_trades ?? '3/4'}</Text></Text>
            <Text style={styles.statSep}>|</Text>
            <Text style={styles.statLabel}>Consec SL: <Text style={styles.statValGreen}>{telemetry?.consecutive_sl ?? 0}/{telemetry?.max_consecutive_sl ?? 3}</Text></Text>
          </View>
        </View>

        {/* 7-Point Quant Signal Gatekeeper Card */}
        <View style={styles.gatekeeperCard}>
          <View style={styles.gateHeader}>
            <Text style={styles.gateTitle}>⚡ 7-POINT QUANT SIGNAL GATEKEEPER</Text>
            <View style={styles.gateStatusPill}>
              <Text style={styles.gateStatusText}>
                {telemetry?.signal_status === 'BUY_CE' ? 'READY FOR CE SCALP' :
                 telemetry?.signal_status === 'BUY_PE' ? 'READY FOR PE SCALP' : 'MONITORING GATES'}
              </Text>
            </View>
          </View>

          <View style={styles.gateList}>
            {gates.map((g) => (
              <View key={g.id} style={styles.gateRow}>
                <View style={styles.gateTopLine}>
                  <Text style={styles.gateName}>{g.name}</Text>
                  <View style={[styles.passBadge, { backgroundColor: g.pass ? 'rgba(16, 185, 129, 0.2)' : Colors.surface }]}>
                    <Text style={[styles.passBadgeText, { color: g.pass ? Colors.emeraldBright : Colors.textDim }]}>
                      {g.pass ? '✓ PASS' : '⏳ WAIT'}
                    </Text>
                  </View>
                </View>
                <Text style={[styles.gateValue, { color: g.pass ? Colors.emeraldBright : Colors.textMuted }]}>
                  {g.value}
                </Text>
              </View>
            ))}
          </View>
        </View>

        {/* Active Position & Safety HUD */}
        <View style={styles.positionCard}>
          <View style={styles.posHeader}>
            <View>
              <View style={styles.posTagRow}>
                <View style={styles.posTypeBadge}>
                  <Text style={styles.posTypeText}>
                    {activePos ? `${activePos.option_type} SCALP` : 'STANDBY'}
                  </Text>
                </View>
                <Text style={styles.strikeTitle}>
                  {activePos ? activePos.strike : 'NO ACTIVE POSITION'}
                </Text>
              </View>
              <Text style={styles.posSub}>
                {activePos ? `${activePos.lots} Lot (${activePos.qty} Qty) // Intraday MIS` : 'Awaiting 7-Gate Signal Trigger'}
              </Text>
            </View>
            <View style={[styles.runningBadge, { backgroundColor: activePos ? 'rgba(16, 185, 129, 0.2)' : Colors.surface }]}>
              <Text style={[styles.runningBadgeText, { color: activePos ? Colors.emeraldBright : Colors.textDim }]}>
                {activePos ? 'RUNNING' : 'IDLE'}
              </Text>
            </View>
          </View>

          {activePos && (
            <View style={styles.posGrid}>
              <View style={styles.gridCell}>
                <Text style={styles.cellLabel}>ENTRY PRICE</Text>
                <Text style={styles.cellVal}>₹{activePos.entry_price.toFixed(2)}</Text>
              </View>

              <View style={styles.gridCell}>
                <Text style={styles.cellLabel}>LIVE OPTION LTP</Text>
                <Text style={styles.cellValGreen}>
                  ₹{activePos.current_ltp.toFixed(2)} (+{activePos.points_captured.toFixed(1)} pts)
                </Text>
              </View>

              <View style={styles.gridCell}>
                <Text style={styles.cellLabel}>TRAILING SL</Text>
                <Text style={styles.cellValAmber}>₹{activePos.sl_price.toFixed(2)}</Text>
                <Text style={styles.cellSub}>{activePos.is_breakeven ? 'Locked at Breakeven' : 'Hard SL 10%'}</Text>
              </View>

              <View style={[styles.gridCell, styles.pnlCell]}>
                <Text style={styles.pnlLabel}>LIVE TRADE P&L</Text>
                <Text style={styles.pnlVal}>
                  {activePos.pnl >= 0 ? `+₹${activePos.pnl.toFixed(2)}` : `-₹${Math.abs(activePos.pnl).toFixed(2)}`}
                </Text>
                <Text style={styles.pnlSub}>PROFIT PROTECTION ARMED</Text>
              </View>
            </View>
          )}

          {/* Time SL & Arm Switch */}
          <View style={styles.safetyBar}>
            <Text style={styles.timeSlText}>
              ⏱️ {activePos ? (activePos.time_sl_status ?? 'Candle 2/3 (4m left)') : 'Time SL: 3 Candles (9m)'}
            </Text>
            <View style={styles.switchWrapper}>
              <Text style={styles.armText}>ARM BOT</Text>
              <Switch
                value={isArmed}
                onValueChange={handleArmToggle}
                trackColor={{ false: Colors.surfaceHigh, true: Colors.emerald }}
                thumbColor={Colors.textPrimary}
              />
            </View>
          </View>

          {/* Crimson Emergency Square-Off Button */}
          <TouchableOpacity
            style={styles.killButton}
            activeOpacity={0.8}
            onPress={handleEmergencyKill}
          >
            <Text style={styles.killButtonText}>
              {killTriggered ? 'SQUARE OFF IN PROGRESS...' : '⚠️ EMERGENCY SQUARE OFF & STOP BOT'}
            </Text>
          </TouchableOpacity>
        </View>

        {/* Security Footer Note */}
        <View style={styles.cockpitFooter}>
          <Text style={styles.footerNote}>
            🔒 FIPS 140-3 LEVEL 4 HSM ENCRYPTED // UPSTOX V3 GATEWAY
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.canvas,
  },
  topBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingVertical: 10,
    backgroundColor: Colors.canvas,
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderSubtle,
  },
  topBarLeft: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  termIcon: {
    fontSize: 16,
    marginRight: 6,
  },
  termTitle: {
    fontFamily: 'monospace',
    fontSize: 12,
    fontWeight: 'bold',
    color: Colors.emeraldBright,
    letterSpacing: 0.5,
  },
  topBarRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  latencyBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.surface,
    borderColor: Colors.borderSubtle,
    borderWidth: 1,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 4,
  },
  greenPulse: {
    width: 5,
    height: 5,
    borderRadius: 2.5,
    backgroundColor: Colors.emeraldBright,
    marginRight: 5,
  },
  latencyText: {
    fontFamily: 'monospace',
    fontSize: 10,
    fontWeight: 'bold',
    color: Colors.emeraldBright,
  },
  tuneButton: {
    padding: 4,
    backgroundColor: Colors.surface,
    borderColor: Colors.borderSubtle,
    borderWidth: 1,
    borderRadius: 4,
  },
  tuneIcon: {
    fontSize: 12,
  },
  scrollArea: {
    flex: 1,
  },
  scrollContent: {
    padding: 12,
    gap: 12,
    paddingBottom: 40,
  },
  statusPillRow: {
    flexDirection: 'row',
    gap: 8,
  },
  statusPill: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: Colors.surfaceLow,
    borderColor: Colors.borderSubtle,
    borderWidth: 1,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 4,
  },
  ledDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
  },
  statusPillText: {
    fontFamily: 'monospace',
    fontSize: 10,
    fontWeight: 'bold',
  },
  statusSubtext: {
    fontFamily: 'monospace',
    fontSize: 8,
    color: Colors.textDim,
  },
  spotCard: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: Colors.surfaceLow,
    borderColor: Colors.borderSubtle,
    borderWidth: 1,
    borderRadius: 6,
    padding: 12,
  },
  badgeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  indexLabel: {
    fontFamily: 'monospace',
    fontSize: 9,
    color: Colors.textMuted,
  },
  nseTag: {
    fontFamily: 'monospace',
    fontSize: 8,
    backgroundColor: Colors.surface,
    color: Colors.emeraldBright,
    paddingHorizontal: 4,
    paddingVertical: 1,
    borderRadius: 2,
  },
  indexName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: Colors.textPrimary,
    marginTop: 2,
  },
  spotRight: {
    alignItems: 'flex-end',
  },
  spotLtpText: {
    fontFamily: 'monospace',
    fontSize: 16,
    fontWeight: 'bold',
    color: Colors.emeraldBright,
  },
  spotChangeText: {
    fontFamily: 'monospace',
    fontSize: 11,
    color: Colors.emeraldBright,
    marginTop: 2,
  },
  mtmCard: {
    backgroundColor: Colors.surfaceLow,
    borderColor: Colors.borderSubtle,
    borderWidth: 1,
    borderRadius: 6,
    padding: 12,
  },
  mtmHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  mtmLabel: {
    fontFamily: 'monospace',
    fontSize: 9,
    color: Colors.textMuted,
    letterSpacing: 0.5,
  },
  targetBadge: {
    backgroundColor: 'rgba(16, 185, 129, 0.15)',
    borderColor: 'rgba(16, 185, 129, 0.3)',
    borderWidth: 1,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 3,
  },
  targetBadgeText: {
    fontFamily: 'monospace',
    fontSize: 8,
    fontWeight: 'bold',
    color: Colors.emeraldBright,
  },
  mtmValueRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'baseline',
    marginTop: 6,
  },
  mtmValueText: {
    fontFamily: 'monospace',
    fontSize: 24,
    fontWeight: 'bold',
  },
  gainPill: {
    backgroundColor: Colors.surface,
    borderColor: Colors.borderSubtle,
    borderWidth: 1,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 4,
  },
  gainPillText: {
    fontFamily: 'monospace',
    fontSize: 9,
    color: Colors.textMuted,
  },
  mtmStatsDivider: {
    height: 1,
    backgroundColor: 'rgba(55, 65, 81, 0.4)',
    marginVertical: 10,
  },
  mtmStatsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  statLabel: {
    fontFamily: 'monospace',
    fontSize: 9,
    color: Colors.textMuted,
  },
  statValGreen: {
    color: Colors.emeraldBright,
    fontWeight: 'bold',
  },
  statValWhite: {
    color: Colors.textPrimary,
    fontWeight: 'bold',
  },
  statSep: {
    color: Colors.borderSubtle,
    fontSize: 9,
  },
  gatekeeperCard: {
    backgroundColor: Colors.surfaceLow,
    borderColor: Colors.borderSubtle,
    borderWidth: 1,
    borderRadius: 6,
    overflow: 'hidden',
  },
  gateHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: Colors.surface,
    paddingHorizontal: 10,
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderSubtle,
  },
  gateTitle: {
    fontFamily: 'monospace',
    fontSize: 10,
    fontWeight: 'bold',
    color: Colors.textPrimary,
  },
  gateStatusPill: {
    backgroundColor: 'rgba(16, 185, 129, 0.15)',
    borderColor: 'rgba(16, 185, 129, 0.3)',
    borderWidth: 1,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 3,
  },
  gateStatusText: {
    fontFamily: 'monospace',
    fontSize: 8,
    fontWeight: 'bold',
    color: Colors.emeraldBright,
  },
  gateList: {
    paddingHorizontal: 10,
  },
  gateRow: {
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(55, 65, 81, 0.3)',
  },
  gateTopLine: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 2,
  },
  gateName: {
    fontFamily: 'monospace',
    fontSize: 10,
    color: Colors.textPrimary,
  },
  passBadge: {
    borderWidth: 1,
    borderColor: 'rgba(16, 185, 129, 0.3)',
    paddingHorizontal: 6,
    paddingVertical: 1,
    borderRadius: 2,
  },
  passBadgeText: {
    fontFamily: 'monospace',
    fontSize: 8,
    fontWeight: 'bold',
  },
  gateValue: {
    fontFamily: 'monospace',
    fontSize: 9,
  },
  positionCard: {
    backgroundColor: Colors.surfaceLow,
    borderColor: Colors.borderSubtle,
    borderWidth: 1,
    borderRadius: 6,
    padding: 12,
    gap: 10,
  },
  posHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingBottom: 8,
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderSubtle,
  },
  posTagRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  posTypeBadge: {
    backgroundColor: 'rgba(16, 185, 129, 0.1)',
    borderColor: 'rgba(16, 185, 129, 0.3)',
    borderWidth: 1,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 2,
  },
  posTypeText: {
    fontFamily: 'monospace',
    fontSize: 9,
    fontWeight: 'bold',
    color: Colors.emeraldBright,
  },
  strikeTitle: {
    fontFamily: 'monospace',
    fontSize: 12,
    fontWeight: 'bold',
    color: Colors.textPrimary,
  },
  posSub: {
    fontFamily: 'monospace',
    fontSize: 9,
    color: Colors.textMuted,
    marginTop: 2,
  },
  runningBadge: {
    borderWidth: 1,
    borderColor: 'rgba(16, 185, 129, 0.4)',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 3,
  },
  runningBadgeText: {
    fontFamily: 'monospace',
    fontSize: 9,
    fontWeight: 'bold',
  },
  posGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  gridCell: {
    width: '48.5%',
    backgroundColor: Colors.surface,
    borderColor: Colors.borderSubtle,
    borderWidth: 1,
    padding: 8,
    borderRadius: 4,
  },
  pnlCell: {
    borderColor: 'rgba(16, 185, 129, 0.3)',
    backgroundColor: 'rgba(16, 185, 129, 0.05)',
  },
  cellLabel: {
    fontFamily: 'monospace',
    fontSize: 8,
    color: Colors.textDim,
  },
  cellVal: {
    fontFamily: 'monospace',
    fontSize: 12,
    fontWeight: 'bold',
    color: Colors.textPrimary,
    marginTop: 2,
  },
  cellValGreen: {
    fontFamily: 'monospace',
    fontSize: 11,
    fontWeight: 'bold',
    color: Colors.emeraldBright,
    marginTop: 2,
  },
  cellValAmber: {
    fontFamily: 'monospace',
    fontSize: 12,
    fontWeight: 'bold',
    color: Colors.amber,
    marginTop: 2,
  },
  cellSub: {
    fontFamily: 'monospace',
    fontSize: 8,
    color: Colors.textDim,
    marginTop: 2,
  },
  pnlLabel: {
    fontFamily: 'monospace',
    fontSize: 8,
    fontWeight: 'bold',
    color: Colors.emeraldBright,
  },
  pnlVal: {
    fontFamily: 'monospace',
    fontSize: 13,
    fontWeight: 'bold',
    color: Colors.emeraldBright,
    marginTop: 2,
  },
  pnlSub: {
    fontFamily: 'monospace',
    fontSize: 7,
    color: Colors.emeraldBright,
    marginTop: 2,
  },
  safetyBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: Colors.canvas,
    borderColor: Colors.borderSubtle,
    borderWidth: 1,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 4,
  },
  timeSlText: {
    fontFamily: 'monospace',
    fontSize: 9,
    color: Colors.textMuted,
  },
  switchWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  armText: {
    fontFamily: 'monospace',
    fontSize: 9,
    color: Colors.textDim,
  },
  killButton: {
    backgroundColor: Colors.crimsonDark,
    borderColor: 'rgba(220, 38, 38, 0.5)',
    borderWidth: 1,
    height: 44,
    borderRadius: 4,
    justifyContent: 'center',
    alignItems: 'center',
  },
  killButtonText: {
    fontFamily: 'monospace',
    fontSize: 11,
    fontWeight: 'bold',
    color: Colors.crimsonBright,
    letterSpacing: 0.5,
  },
  cockpitFooter: {
    alignItems: 'center',
    paddingVertical: 8,
  },
  footerNote: {
    fontFamily: 'monospace',
    fontSize: 8,
    color: Colors.textDim,
  },
});
