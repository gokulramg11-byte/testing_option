/**
 * Firebase Client SDK Configuration & Service Bridge:
 * Provides Google OAuth authentication and Firestore real-time onSnapshot hooks.
 * Automatically falls back to high-fidelity live telemetry simulation when env vars are unpopulated.
 */

import { initializeApp, getApps } from "firebase/app";
import { getAuth, GoogleAuthProvider, signInWithPopup, signOut } from "firebase/auth";
import { 
  getFirestore, 
  doc, 
  onSnapshot, 
  setDoc, 
  collection, 
  query, 
  orderBy, 
  limit, 
  serverTimestamp 
} from "firebase/firestore";

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || ""
};

export const isFirebaseConfigured = Boolean(
  firebaseConfig.apiKey && 
  firebaseConfig.projectId && 
  firebaseConfig.projectId !== "YOUR_PROJECT_ID"
);

let app = null;
let auth = null;
let googleProvider = null;
let db = null;

if (isFirebaseConfigured) {
  try {
    app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApps()[0];
    auth = getAuth(app);
    googleProvider = new GoogleAuthProvider();
    db = getFirestore(app);
  } catch (err) {
    console.warn("Firebase client initialization failed:", err);
  }
}

export { auth, googleProvider, db };

/**
 * Trigger Google OAuth Login
 */
export async function loginWithGoogle() {
  if (!isFirebaseConfigured || !auth) {
    // Return mock operator session for zero-config testing
    return {
      displayName: "Authorized Quant Operator",
      email: "operator@alphascalp.quant",
      photoURL: null,
      isDemo: true
    };
  }
  const result = await signInWithPopup(auth, googleProvider);
  return result.user;
}

/**
 * Logout session
 */
export async function logoutUser() {
  if (auth) {
    await signOut(auth);
  }
}

/**
 * Send Kill Switch command to Firestore 'control/settings'
 */
export async function triggerEmergencyKillSwitch() {
  if (isFirebaseConfigured && db) {
    const docRef = doc(db, "control", "settings");
    await setDoc(docRef, {
      kill_switch: true,
      armed: false,
      triggered_at: serverTimestamp()
    }, { merge: true });
  }
}

/**
 * Toggle Arm / Disarm Bot
 */
export async function toggleArmBot(newArmedState) {
  if (isFirebaseConfigured && db) {
    const docRef = doc(db, "control", "settings");
    await setDoc(docRef, {
      armed: newArmedState,
      updated_at: serverTimestamp()
    }, { merge: true });
  }
}
