"""
Firebase Firestore Real-Time Synchronization Engine:
- Pushes throttled live bot telemetry to 'live_state/current' every 3-4 seconds
  (guarantees < 7,000 writes/day, well below Firebase Spark free tier limit of 20,000 writes).
- Listens to 'control/settings' for real-time mobile Kill Switch & Arming toggles.
- Logs completed trade records to 'trade_history' collection.
- Includes a memory-based mock store for local running when credentials are not configured.
"""

import time
import logging
from datetime import datetime
from typing import Dict, Any, Optional, Callable
import firebase_admin
from firebase_admin import credentials, firestore
from bot.config import TradingConfig, IST

logger = logging.getLogger("FirebaseSync")

class FirebaseBridge:
    def __init__(self, config: TradingConfig):
        self.config = config
        self.session_id: str = getattr(config, "session_id", "NSE_NIFTY")
        self.segment: str = getattr(config, "segment", "NSE")
        self.db = None
        self.is_connected: bool = False
        self.last_sync_time: float = 0.0
        self.sync_throttle_seconds: float = 3.5  # Stays under 20k writes/day
        self.control_listener = None
        
        # In-memory mirror for fallback/mock
        self.mock_state: Dict[str, Any] = {}
        self.mock_control: Dict[str, Any] = {"kill_switch": False, "armed": True}
        self.mock_trades: list = []

    def initialize(self):
        """Initializes Firebase Admin SDK using service account credentials."""
        if self.config.firebase_service_account:
            try:
                # Check if already initialized
                if not firebase_admin._apps:
                    cred = credentials.Certificate(self.config.firebase_service_account)
                    firebase_admin.initialize_app(cred)
                self.db = firestore.client()
                self.is_connected = True
                logger.info("Firebase Firestore connected successfully via Service Account.")
            except Exception as e:
                logger.error(f"Failed to initialize Firebase Admin SDK: {e}. Running with in-memory sync.")
                self.is_connected = False
        else:
            logger.warning("[SAFETY-NOTICE] No FIREBASE_SERVICE_ACCOUNT_JSON provided. Operating with in-memory state buffer.")
            self.is_connected = False

    def listen_to_control_settings(
        self,
        on_kill_switch: Callable[[], None],
        on_arm_toggle: Callable[[bool], None]
    ):
        """Attaches a real-time Firestore listener to 'controls/{session_id}' for instant mobile commands."""
        if not self.is_connected or not self.db:
            logger.info("Firestore not connected; control listener running in fallback mode.")
            return

        def on_snapshot(doc_snapshot, changes, read_time):
            for doc in doc_snapshot:
                data = doc.to_dict() or {}
                logger.info(f"Received Firestore control update for {self.session_id}: {data}")
                
                # Check Kill Switch
                if data.get("kill_switch") is True:
                    logger.critical(f"KILL SWITCH TRIGGERED FROM MOBILE FOR {self.session_id}!")
                    on_kill_switch()
                    # Reset kill_switch flag to prevent infinite loops
                    try:
                        self.db.collection("controls").document(self.session_id).update({"kill_switch": False, "armed": False})
                        if self.session_id == "NSE_NIFTY":
                            self.db.collection("control").document("settings").update({"kill_switch": False, "armed": False})
                    except Exception as e:
                        logger.error(f"Error resetting kill_switch flag: {e}")

                # Check Arm / Disarm Bot
                if "armed" in data:
                    is_armed = bool(data.get("armed"))
                    on_arm_toggle(is_armed)

        try:
            # Primary multi-session document: controls/{session_id}
            doc_ref = self.db.collection("controls").document(self.session_id)
            if not doc_ref.get().exists:
                doc_ref.set({"kill_switch": False, "armed": True, "updated_at": firestore.SERVER_TIMESTAMP})
            self.control_listener = doc_ref.on_snapshot(on_snapshot)
            logger.info(f"Listening to real-time 'controls/{self.session_id}' on Firestore.")
        except Exception as e:
            logger.error(f"Failed to attach Firestore control listener: {e}")

    def sync_live_state(self, state: Dict[str, Any], force: bool = False):
        """Pushes current bot telemetry to 'sessions/{session_id}' with strict throttling."""
        now = time.time()
        if not force and (now - self.last_sync_time) < self.sync_throttle_seconds:
            return # Skip write to respect 20k daily quota

        self.last_sync_time = now
        state["last_updated_at"] = datetime.now(IST).strftime("%Y-%m-%d %H:%M:%S IST")
        state["session_id"] = self.session_id
        state["segment"] = self.segment
        self.mock_state = state

        if self.is_connected and self.db:
            try:
                # 1. Multi-session isolated state write: sessions/{session_id}
                self.db.collection("sessions").document(self.session_id).set(state, merge=True)
                
                # 2. Backwards-compatibility write for legacy NSE single-session readers
                if self.session_id == "NSE_NIFTY":
                    self.db.collection("live_state").document("current").set(state, merge=True)
            except Exception as e:
                logger.error(f"Firestore state write error: {e}")

    def record_completed_trade(self, trade_data: Dict[str, Any]):
        """Persists a closed trade to 'trades' collection partitioned with session metadata."""
        trade_data["session_id"] = self.session_id
        trade_data["segment"] = self.segment
        self.mock_trades.insert(0, trade_data)
        if self.is_connected and self.db:
            try:
                trade_id = trade_data.get("id", f"TR_{int(time.time())}")
                # Primary root trades collection
                self.db.collection("trades").document(trade_id).set(trade_data)
                # Legacy mirror
                self.db.collection("trade_history").document(trade_id).set(trade_data)
                logger.info(f"Persisted trade {trade_id} [{self.session_id}] to Firestore 'trades'.")
            except Exception as e:
                logger.error(f"Failed to record trade to Firestore: {e}")

    def close(self):
        """Clean teardown of listeners and connections."""
        if self.control_listener:
            try:
                self.control_listener.unsubscribe()
            except Exception:
                pass
        logger.info("Firebase sync bridge closed cleanly.")
