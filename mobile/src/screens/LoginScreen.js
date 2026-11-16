import React, { useState } from 'react';
import { 
  View, 
  Text, 
  TextInput, 
  TouchableOpacity, 
  StyleSheet, 
  SafeAreaView 
} from 'react-native';
import { Colors } from '../theme';

export default function LoginScreen({ onLoginSuccess }) {
  const [email, setEmail] = useState('operator@alphascalp.quant');

  return (
    <SafeAreaView style={styles.container}>
      {/* Top Telemetry Header */}
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <Text style={styles.headerIcon}>⚡</Text>
          <Text style={styles.headerTitle}>NIFTY // ALPHASCALP</Text>
        </View>
        <View style={styles.latencyBadge}>
          <View style={styles.pulseDot} />
          <Text style={styles.latencyText}>12MS GATEWAY</Text>
        </View>
      </View>

      {/* Main Authentication Card */}
      <View style={styles.content}>
        <View style={styles.card}>
          {/* Security Shield Icon */}
          <View style={styles.shieldContainer}>
            <View style={styles.shieldCircle}>
              <Text style={styles.shieldIcon}>🛡️</Text>
            </View>
            <View style={styles.securityPill}>
              <View style={styles.greenDot} />
              <Text style={styles.securityPillText}>BIOMETRIC & OAUTH SECURITY</Text>
            </View>
            <Text style={styles.cardTitle}>Algorithmic Scalping Cockpit</Text>
            <Text style={styles.cardSubtitle}>
              Dedicated single-operator execution terminal for Nifty 50 & BankNifty.
            </Text>
          </View>

          {/* Status Indicators */}
          <View style={styles.pillRow}>
            <View style={styles.statusBox}>
              <Text style={styles.boxLabel}>UPSTOX V3 API</Text>
              <Text style={styles.boxValueGreen}>● READY (12ms)</Text>
            </View>
            <View style={styles.statusBox}>
              <Text style={styles.boxLabel}>SAFETY GUARD</Text>
              <Text style={styles.boxValueAmber}>● PAPER MODE ON</Text>
            </View>
          </View>

          {/* Operator Email Field */}
          <View style={styles.inputGroup}>
            <Text style={styles.inputLabel}>AUTHORIZED OPERATOR EMAIL</Text>
            <View style={styles.inputWrapper}>
              <TextInput
                style={styles.textInput}
                value={email}
                onChangeText={setEmail}
                placeholder="operator@alphascalp.quant"
                placeholderTextColor={Colors.textDim}
                keyboardType="email-address"
                autoCapitalize="none"
              />
              <Text style={styles.checkIcon}>✓</Text>
            </View>
          </View>

          {/* Authenticate Button */}
          <TouchableOpacity 
            style={styles.authButton}
            activeOpacity={0.8}
            onPress={() => onLoginSuccess({ email })}
          >
            <Text style={styles.authButtonText}>AUTHENTICATE OPERATOR SESSION</Text>
          </TouchableOpacity>

          <Text style={styles.zeroConfigNote}>
            [Zero-Config Mode] Tap to enter live simulated scalping cockpit
          </Text>
        </View>
      </View>

      {/* Footer Security Note */}
      <View style={styles.footer}>
        <Text style={styles.footerText}>
          🔒 FIPS 140-3 LEVEL 4 HSM ENCRYPTED // UPSTOX V3 GATEWAY
        </Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.canvas,
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 24,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 8,
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  headerIcon: {
    fontSize: 16,
    color: Colors.emeraldBright,
    marginRight: 6,
  },
  headerTitle: {
    fontFamily: 'monospace',
    fontSize: 12,
    fontWeight: 'bold',
    color: Colors.emeraldBright,
    letterSpacing: 1,
  },
  latencyBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.surface,
    borderColor: Colors.borderSubtle,
    borderWidth: 1,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  pulseDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: Colors.emeraldBright,
    marginRight: 6,
  },
  latencyText: {
    fontFamily: 'monospace',
    fontSize: 10,
    fontWeight: 'bold',
    color: Colors.emeraldBright,
  },
  content: {
    flex: 1,
    justifyContent: 'center',
    paddingVertical: 16,
  },
  card: {
    backgroundColor: Colors.surfaceLow,
    borderColor: Colors.borderSubtle,
    borderWidth: 1,
    borderRadius: 8,
    padding: 20,
  },
  shieldContainer: {
    alignItems: 'center',
    marginBottom: 20,
  },
  shieldCircle: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: Colors.surface,
    borderColor: Colors.emerald,
    borderWidth: 1,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 12,
  },
  shieldIcon: {
    fontSize: 24,
  },
  securityPill: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(16, 185, 129, 0.1)',
    borderColor: 'rgba(16, 185, 129, 0.3)',
    borderWidth: 1,
    paddingHorizontal: 10,
    paddingVertical: 3,
    borderRadius: 12,
    marginBottom: 10,
  },
  greenDot: {
    width: 5,
    height: 5,
    borderRadius: 2.5,
    backgroundColor: Colors.emeraldBright,
    marginRight: 6,
  },
  securityPillText: {
    fontFamily: 'monospace',
    fontSize: 9,
    fontWeight: 'bold',
    color: Colors.emeraldBright,
    letterSpacing: 0.5,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: Colors.textPrimary,
    marginBottom: 6,
    textAlign: 'center',
  },
  cardSubtitle: {
    fontSize: 12,
    color: Colors.textMuted,
    textAlign: 'center',
    lineHeight: 18,
  },
  pillRow: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 18,
  },
  statusBox: {
    flex: 1,
    backgroundColor: Colors.surface,
    borderColor: Colors.borderSubtle,
    borderWidth: 1,
    padding: 10,
    borderRadius: 4,
  },
  boxLabel: {
    fontFamily: 'monospace',
    fontSize: 9,
    color: Colors.textDim,
    letterSpacing: 0.5,
  },
  boxValueGreen: {
    fontFamily: 'monospace',
    fontSize: 11,
    fontWeight: 'bold',
    color: Colors.emeraldBright,
    marginTop: 4,
  },
  boxValueAmber: {
    fontFamily: 'monospace',
    fontSize: 11,
    fontWeight: 'bold',
    color: Colors.amber,
    marginTop: 4,
  },
  inputGroup: {
    marginBottom: 18,
  },
  inputLabel: {
    fontFamily: 'monospace',
    fontSize: 10,
    color: Colors.textMuted,
    letterSpacing: 0.5,
    marginBottom: 6,
  },
  inputWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Colors.canvas,
    borderColor: Colors.borderSubtle,
    borderWidth: 1,
    borderRadius: 4,
    paddingHorizontal: 12,
    height: 44,
  },
  textInput: {
    flex: 1,
    fontFamily: 'monospace',
    fontSize: 12,
    color: Colors.textPrimary,
  },
  checkIcon: {
    fontSize: 14,
    fontWeight: 'bold',
    color: Colors.emeraldBright,
  },
  authButton: {
    backgroundColor: Colors.emerald,
    height: 46,
    borderRadius: 4,
    justifyContent: 'center',
    alignItems: 'center',
  },
  authButtonText: {
    fontFamily: 'monospace',
    fontSize: 12,
    fontWeight: 'bold',
    color: Colors.canvas,
    letterSpacing: 0.5,
  },
  zeroConfigNote: {
    fontFamily: 'monospace',
    fontSize: 9,
    color: Colors.amber,
    textAlign: 'center',
    marginTop: 10,
  },
  footer: {
    alignItems: 'center',
  },
  footerText: {
    fontFamily: 'monospace',
    fontSize: 9,
    color: Colors.textDim,
  },
});
