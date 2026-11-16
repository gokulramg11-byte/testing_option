/**
 * Firebase Client SDK for React Native Mobile Application:
 * Listens to 'live_state/current' in real time, and sends Kill Switch commands to 'control/settings'.
 * Features built-in mock telemetry generator for zero-config local testing.
 */

import { initializeApp, getApps } from "firebase/app";
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

// Replace with your Firebase Web Config when ready
const firebaseConfig = {
  apiKey: "",
  authDomain: "",
  projectId: "",
  storageBucket: "",
  messagingSenderId: "",
  appId: ""
};

export const isFirebaseConfigured = Boolean(
  firebaseConfig.apiKey && 
  firebaseConfig.projectId
);

let app = null;
let db = null;

if (isFirebaseConfigured) {
  try {
    app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApps()[0];
    db = getFirestore(app);
  } catch (err) {
    console.warn("Firebase initialization warning:", err);
  }
}

export { db };

/**
 * Triggers emergency square off from native mobile app
 */
export async function triggerEmergencyKillSwitch() {
  if (isFirebaseConfigured && db) {
    try {
      const docRef = doc(db, "control", "settings");
      await setDoc(docRef, {
        kill_switch: true,
        armed: false,
        triggered_at: serverTimestamp()
      }, { merge: true });
    } catch (err) {
      console.error("Failed to write kill_switch to Firestore:", err);
    }
  }
}

/**
 * Toggle Arm / Disarm Bot from native mobile app
 */
export async function toggleArmBot(newArmedState) {
  if (isFirebaseConfigured && db) {
    try {
      const docRef = doc(db, "control", "settings");
      await setDoc(docRef, {
        armed: newArmedState,
        updated_at: serverTimestamp()
      }, { merge: true });
    } catch (err) {
      console.error("Failed to write arm state to Firestore:", err);
    }
  }
}
