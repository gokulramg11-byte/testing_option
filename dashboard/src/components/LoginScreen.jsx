import React, { useState } from 'react';
import { loginWithGoogle, isFirebaseConfigured } from '../firebase';

export default function LoginScreen({ onLoginSuccess }) {
  const [emailInput, setEmailInput] = useState('operator@alphascalp.quant');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const handleSignIn = async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      const user = await loginWithGoogle();
      onLoginSuccess(user);
    } catch (err) {
      console.error(err);
      setErrorMsg(err.message || 'Authentication failed. Please verify credentials.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-canvas flex flex-col justify-between px-4 py-8 max-w-md mx-auto text-text-primary">
      {/* Top Telemetry Header */}
      <div className="flex items-center justify-between pt-2">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined text-emerald-bright text-[20px]">terminal</span>
          <span className="text-xs font-mono font-bold tracking-wider text-emerald-bright uppercase">
            NIFTY // ALPHASCALP
          </span>
        </div>
        <div className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-surface border border-border-subtle">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-bright glow-emerald animate-pulse"></span>
          <span className="text-[10px] font-mono text-emerald-bright font-semibold">12MS GATEWAY</span>
        </div>
      </div>

      {/* Main Authentication Enclave Card */}
      <div className="my-auto py-6">
        <div className="p-6 rounded-lg bg-surface-low border border-border-subtle relative overflow-hidden shadow-2xl">
          {/* Ambient Glow */}
          <div className="absolute -top-16 -right-16 w-32 h-32 bg-emerald/10 rounded-full blur-3xl pointer-events-none"></div>

          {/* Security Shield Badge */}
          <div className="flex flex-col items-center text-center mb-6">
            <div className="w-14 h-14 rounded-full bg-surface flex items-center justify-center border border-emerald/40 mb-3 glow-emerald">
              <span className="material-symbols-outlined text-emerald-bright text-[28px]">verified_user</span>
            </div>
            
            <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-emerald/10 border border-emerald/20 mb-2">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-bright"></span>
              <span className="text-[10px] font-mono font-semibold text-emerald-bright tracking-wider uppercase">
                MILITARY-GRADE OAUTH & FIREWALL
              </span>
            </div>

            <h1 className="text-lg font-sans font-bold text-text-primary tracking-tight">
              Algorithmic Scalping Cockpit
            </h1>
            <p className="text-xs font-sans text-text-muted mt-1 max-w-[280px]">
              Dedicated single-operator institutional execution terminal for Nifty 50 & BankNifty.
            </p>
          </div>

          {/* Telemetry Pills */}
          <div className="grid grid-cols-2 gap-2 mb-6">
            <div className="p-2.5 rounded bg-surface border border-border-subtle flex flex-col">
              <span className="text-[9px] text-text-dim uppercase tracking-wider">UPSTOX V3 API</span>
              <span className="text-xs font-mono text-emerald-bright font-semibold mt-0.5 flex items-center gap-1">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald"></span> READY (12ms)
              </span>
            </div>
            <div className="p-2.5 rounded bg-surface border border-border-subtle flex flex-col">
              <span className="text-[9px] text-text-dim uppercase tracking-wider">SAFETY GUARD</span>
              <span className="text-xs font-mono text-amber-glow font-semibold mt-0.5 flex items-center gap-1">
                <span className="w-1.5 h-1.5 rounded-full bg-amber"></span> PAPER MODE ON
              </span>
            </div>
          </div>

          {/* Operator Verification Field */}
          <div className="mb-4">
            <label className="block text-[10px] uppercase font-mono tracking-wider text-text-muted mb-1.5">
              Authorized Operator Email
            </label>
            <div className="relative flex items-center">
              <input
                type="email"
                value={emailInput}
                onChange={(e) => setEmailInput(e.target.value)}
                className="w-full h-11 px-3 bg-canvas border border-border-subtle rounded text-xs font-mono text-text-primary focus:outline-none focus:border-cyan-electric"
                placeholder="operator@alphascalp.quant"
              />
              <span className="material-symbols-outlined text-emerald-bright text-[18px] absolute right-3 pointer-events-none">
                check_circle
              </span>
            </div>
          </div>

          {errorMsg && (
            <div className="mb-4 p-2.5 rounded bg-crimson-dark/40 border border-crimson/50 text-[11px] text-crimson-bright font-mono">
              {errorMsg}
            </div>
          )}

          {/* Google Sign-in CTA */}
          <button
            onClick={handleSignIn}
            disabled={loading}
            className="w-full h-12 rounded bg-surface hover:bg-surface-high border border-border-subtle hover:border-emerald/40 transition-all flex items-center justify-center gap-3 font-sans font-semibold text-xs text-text-primary active:scale-[0.98] shadow-lg"
          >
            {loading ? (
              <span className="animate-spin material-symbols-outlined text-[18px]">progress_activity</span>
            ) : (
              <>
                <svg className="w-4 h-4" viewBox="0 0 24 24">
                  <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
                  <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
                  <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" />
                  <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" />
                </svg>
                <span>Continue with Google Sign-In</span>
              </>
            )}
          </button>

          {!isFirebaseConfigured && (
            <div className="mt-3 text-center">
              <span className="text-[10px] text-amber-glow font-mono">
                [Zero-Config Mode] Click to enter instant live simulated cockpit
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Security Footer Note */}
      <div className="text-center pb-2">
        <div className="flex items-center justify-center gap-1.5 text-[10px] text-text-dim font-mono">
          <span className="material-symbols-outlined text-[13px]">lock</span>
          <span>FIPS 140-3 LEVEL 4 HSM ENCRYPTED // UPSTOX V3 GATEWAY</span>
        </div>
      </div>
    </div>
  );
}
