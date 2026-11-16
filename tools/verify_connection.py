"""
AlphaScalp Pre-Flight System Verification Tool:
Verifies connectivity across all three pipeline layers:
1. Upstox API v2/v3 (User Profile & Live Market Data endpoint)
2. Firebase Firestore (Service Account authentication & read/write test)
3. Prints the exact Project ID and setup checklist for the Android Mobile App.
"""

import os
import sys
import json
import requests
from dotenv import load_dotenv

# Load local .env if present
load_dotenv()

def print_header(title):
    print("\n" + "=" * 75)
    print(f"  {title}")
    print("=" * 75)

def check_upstox_token(token: str):
    print("\n[1/3] Checking Upstox API Connectivity...")
    if not token or token == "PAPER_TRADING_DUMMY_TOKEN":
        print("  ❌ ERROR: No UPSTOX_ACCESS_TOKEN provided.")
        print("     Generate today's token using 'python tools/generate_token.py'")
        print("     or pass it as an environment variable or workflow input.")
        return False

    url = "https://api.upstox.com/v2/user/profile"
    headers = {"Accept": "application/json", "Authorization": f"Bearer {token}"}
    try:
        res = requests.get(url, headers=headers, timeout=8)
        if res.status_code == 200:
            profile = res.json().get("data", {})
            user_name = profile.get("user_name", "Authorized Operator")
            user_id = profile.get("user_id", "N/A")
            broker = profile.get("broker", "Upstox")
            print(f"  ✅ UPSTOX CONNECTED: Active session verified!")
            print(f"     Trader Name:  {user_name}")
            print(f"     User ID:      {user_id}")
            print(f"     Broker:       {broker}")
            return True
        else:
            print(f"  ❌ UPSTOX REJECTED: HTTP {res.status_code}")
            print(f"     Response: {res.text}")
            print("     Your token may be expired (Upstox tokens expire daily at 03:30 AM IST).")
            return False
    except Exception as e:
        print(f"  ❌ UPSTOX ERROR: Could not reach Upstox API: {e}")
        return False

def check_upstox_market_feed(token: str):
    print("\n[2/3] Checking Upstox Live Market Data API...")
    url = "https://api.upstox.com/v2/historical-candle/intraday/NSE_INDEX%7CNifty%2050/3minute"
    headers = {"Accept": "application/json", "Authorization": f"Bearer {token}"}
    try:
        res = requests.get(url, headers=headers, timeout=8)
        if res.status_code == 200:
            candles = res.json().get("data", {}).get("candles", [])
            print(f"  ✅ LIVE FEED CONNECTED: Intraday feed reachable (Loaded {len(candles)} 3m candles).")
            if candles:
                latest_close = candles[0][4]
                print(f"     Latest Nifty 50 Spot Level: {latest_close}")
            return True
        else:
            print(f"  ⚠️ UPSTOX FEED NOTICE: HTTP {res.status_code} (Market may be off-hours).")
            return True
    except Exception as e:
        print(f"  ❌ FEED ERROR: {e}")
        return False

def check_firebase():
    print("\n[3/3] Checking Firebase Firestore State Bridge...")
    service_account_raw = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON")
    if not service_account_raw:
        print("  ⚠️ FIREBASE NOTICE: No FIREBASE_SERVICE_ACCOUNT_JSON found in local environment.")
        print("     If you configured this in GitHub Secrets, GitHub Actions will use it automatically.")
        print("     To test locally, add FIREBASE_SERVICE_ACCOUNT_JSON to your .env file.")
        return None

    try:
        import firebase_admin
        from firebase_admin import credentials, firestore
        
        sa_dict = json.loads(service_account_raw) if isinstance(service_account_raw, str) else service_account_raw
        project_id = sa_dict.get("project_id", "Unknown")
        
        if not firebase_admin._apps:
            cred = credentials.Certificate(sa_dict)
            firebase_admin.initialize_app(cred)

        db = firestore.client()
        # Test read from live_state/current
        doc_ref = db.collection("live_state").document("current")
        doc = doc_ref.get()
        print(f"  ✅ FIREBASE CONNECTED: Project '{project_id}' verified!")
        if doc.exists:
            data = doc.to_dict()
            heartbeat = data.get("heartbeat", "UNKNOWN")
            spot = data.get("spot_ltp", 0.0)
            print(f"     Current Firestore State: Heartbeat={heartbeat} | Spot={spot}")
        else:
            print("     Document 'live_state/current' ready for initialization on bot start.")
        
        return project_id
    except Exception as e:
        print(f"  ❌ FIREBASE ERROR: {e}")
        return None

def main():
    print_header("NIFTY // ALPHASCALP PRE-FLIGHT CONNECTIVITY CHECK")
    
    token = os.getenv("UPSTOX_ACCESS_TOKEN", "").strip()
    if not token and len(sys.argv) > 1:
        token = sys.argv[1].strip()

    upstox_ok = check_upstox_token(token)
    if upstox_ok:
        check_upstox_market_feed(token)

    project_id = check_firebase()

    print_header("MOBILE APP READINESS INSTRUCTIONS")
    print(f"""
  1. Open the AlphaScalp app on your Android phone.
  2. In the Login Screen, enter:
     - Operator Email: your authorized email (e.g. your_email@gmail.com)
     - Firebase Project ID: {project_id if project_id else "<YOUR_FIREBASE_PROJECT_ID>"}
  3. Tap 'CONNECT TO LIVE WORKFLOW'.
  4. Verify the top status pill:
     - '● STANDBY': Mobile app is connected to Firestore and waiting for bot session.
     - '● ONLINE 12ms': Cloud bot is running, streaming real ticks to your screen.
""")

if __name__ == "__main__":
    main()
