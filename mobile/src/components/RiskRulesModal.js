import React from 'react';
import { 
  Modal, 
  View, 
  Text, 
  TouchableOpacity, 
  StyleSheet 
} from 'react-native';
import { Colors } from '../theme';

export default function RiskRulesModal({ visible, onClose }) {
  const rules = [
    { label: "Index & Candle", val: "NIFTY 50 // 3-Minute Candles" },
    { label: "Max Risk Per Trade", val: "₹1,500.00 (Hard Cap)", green: true },
    { label: "Daily Max Loss", val: "₹4,000.00 (Circuit Halt)", red: true },
    { label: "Max Consecutive SL", val: "3 Trades", amber: true },
    { label: "Active IST Windows", val: "09:30–12:00 & 13:30–15:10" },
    { label: "Auto Square-Off", val: "15:10 IST (6-Hr Guard)", amber: true },
    { label: "Initial Stop-Loss", val: "10% Option Premium" },
    { label: "Time Stop-Loss", val: "3 Closed Candles (9 Mins)" },
    { label: "Profit Trailing", val: "Breakeven @ +10pts, Trail @ +15pts" },
  ];

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={onClose}
    >
      <View style={styles.overlay}>
        <View style={styles.modalCard}>
          <View style={styles.header}>
            <Text style={styles.title}>🛡️ RISK RULES & CIRCUIT BREAKERS</Text>
            <TouchableOpacity onPress={onClose}>
              <Text style={styles.closeIcon}>✕</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.rulesList}>
            {rules.map((r, i) => (
              <View key={i} style={styles.ruleRow}>
                <Text style={styles.ruleLabel}>{r.label}:</Text>
                <Text style={[
                  styles.ruleVal,
                  r.green && { color: Colors.emeraldBright },
                  r.red && { color: Colors.crimsonBright },
                  r.amber && { color: Colors.amber }
                ]}>
                  {r.val}
                </Text>
              </View>
            ))}
          </View>

          <TouchableOpacity 
            style={styles.ackButton}
            onPress={onClose}
          >
            <Text style={styles.ackText}>ACKNOWLEDGE & CLOSE</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.85)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  modalCard: {
    width: '100%',
    backgroundColor: Colors.surfaceLow,
    borderColor: Colors.borderSubtle,
    borderWidth: 1,
    borderRadius: 8,
    padding: 16,
    gap: 14,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: Colors.borderSubtle,
    paddingBottom: 10,
  },
  title: {
    fontFamily: 'monospace',
    fontSize: 11,
    fontWeight: 'bold',
    color: Colors.textPrimary,
  },
  closeIcon: {
    color: Colors.textMuted,
    fontSize: 16,
    fontWeight: 'bold',
  },
  rulesList: {
    gap: 8,
  },
  ruleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    backgroundColor: Colors.surface,
    borderColor: Colors.borderSubtle,
    borderWidth: 1,
    padding: 8,
    borderRadius: 4,
  },
  ruleLabel: {
    fontFamily: 'monospace',
    fontSize: 9,
    color: Colors.textMuted,
  },
  ruleVal: {
    fontFamily: 'monospace',
    fontSize: 9,
    fontWeight: 'bold',
    color: Colors.textPrimary,
  },
  ackButton: {
    backgroundColor: Colors.emerald,
    height: 40,
    borderRadius: 4,
    justifyContent: 'center',
    alignItems: 'center',
  },
  ackText: {
    fontFamily: 'monospace',
    fontSize: 11,
    fontWeight: 'bold',
    color: Colors.canvas,
  },
});
