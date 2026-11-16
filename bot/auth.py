"""
Upstox Authentication Module:
Handles automated daily OAuth2 token generation using TOTP (pyotp) + PIN,
with seamless fallback to pre-generated UPSTOX_ACCESS_TOKEN.
"""

import logging
import urllib.parse
from typing import Optional
import requests
import pyotp
from bot.config import TradingConfig

logger = logging.getLogger("AuthManager")

class UpstoxAuthManager:
    def __init__(self, config: TradingConfig):
        self.config = config
        self.access_token: Optional[str] = config.upstox_access_token

    def validate_existing_token(self, token: str) -> bool:
        """Checks if an existing token is still valid with Upstox API."""
        url = "https://api.upstox.com/v2/user/profile"
        headers = {
            "Accept": "application/json",
            "Authorization": f"Bearer {token}"
        }
        try:
            res = requests.get(url, headers=headers, timeout=8)
            if res.status_code == 200:
                data = res.json()
                user_name = data.get("data", {}).get("user_name", "Authorized Trader")
                logger.info(f"Verified active Upstox session for user: {user_name}")
                return True
            else:
                logger.warning(f"Existing access token rejected: HTTP {res.status_code} - {res.text}")
                return False
        except Exception as e:
            logger.warning(f"Failed to validate existing token: {e}")
            return False

    def generate_token_via_totp(self) -> Optional[str]:
        """
        Attempts headless automated OAuth2 authentication using Upstox Login API,
        Mobile Number, pyotp TOTP generation, and 6-digit User PIN.
        """
        if not (self.config.upstox_api_key and self.config.upstox_api_secret and 
                self.config.upstox_mobile and self.config.upstox_pin and self.config.upstox_totp_secret):
            logger.info("Missing full automated credentials (mobile/pin/totp). Checking pre-configured token...")
            return None

        try:
            session = requests.Session()
            totp = pyotp.TOTP(self.config.upstox_totp_secret.replace(" ", ""))
            current_otp = totp.now()
            logger.info("Generated time-synchronized TOTP successfully.")

            # Step 1: Request OTP verification via Upstox Mobile Login API
            login_url = "https://api.upstox.com/v2/login/authorization/dialog"
            login_params = {
                "client_id": self.config.upstox_api_key,
                "redirect_uri": self.config.upstox_redirect_uri,
                "response_type": "code"
            }
            res1 = session.get(login_url, params=login_params, timeout=10)
            if res1.status_code not in (200, 302):
                logger.error(f"Login dialog initiation failed: HTTP {res1.status_code}")
                return None

            # Step 2: Send Mobile Number & TOTP
            otp_url = "https://api.upstox.com/v2/login/v2/totp"
            otp_payload = {
                "mobile": self.config.upstox_mobile,
                "totp": current_otp
            }
            res2 = session.post(otp_url, json=otp_payload, timeout=10)
            res2_data = res2.json() if res2.status_code == 200 else {}
            
            # Step 3: Send 2FA PIN
            pin_url = "https://api.upstox.com/v2/login/v2/pin"
            pin_payload = {
                "pin": self.config.upstox_pin
            }
            res3 = session.post(pin_url, json=pin_payload, timeout=10)
            
            # Extract authorization code from redirect URL
            auth_code = None
            if "Location" in res3.headers:
                loc = res3.headers["Location"]
                parsed = urllib.parse.urlparse(loc)
                auth_code = urllib.parse.parse_qs(parsed.query).get("code", [None])[0]
            elif res3.status_code == 200 and "data" in res3.json():
                redirect_url = res3.json()["data"].get("redirect_url")
                if redirect_url:
                    parsed = urllib.parse.urlparse(redirect_url)
                    auth_code = urllib.parse.parse_qs(parsed.query).get("code", [None])[0]

            if not auth_code:
                logger.warning("Could not extract auth_code from headless automated flow.")
                return None

            # Step 4: Exchange auth_code for Access Token
            token_url = "https://api.upstox.com/v2/login/authorization/token"
            token_data = {
                "code": auth_code,
                "client_id": self.config.upstox_api_key,
                "client_secret": self.config.upstox_api_secret,
                "redirect_uri": self.config.upstox_redirect_uri,
                "grant_type": "authorization_code"
            }
            headers = {"Accept": "application/json", "Content-Type": "application/x-www-form-urlencoded"}
            res4 = requests.post(token_url, data=token_data, headers=headers, timeout=10)
            if res4.status_code == 200:
                data = res4.json()
                token = data.get("access_token")
                logger.info("Successfully generated new Upstox access_token via automated TOTP.")
                return token
            else:
                logger.error(f"Failed to exchange auth_code for token: {res4.text}")
                return None
        except Exception as e:
            logger.error(f"Automated TOTP authentication encountered error: {e}")
            return None

    def get_valid_token(self) -> str:
        """Returns a verified access token or raises RuntimeError."""
        # 1. Test existing token if supplied (Option B: Manual Daily Token)
        if self.access_token:
            if self.validate_existing_token(self.access_token):
                logger.info("Authenticated successfully using UPSTOX_ACCESS_TOKEN (Option B: Manual Token Mode).")
                return self.access_token
            logger.warning("Provided UPSTOX_ACCESS_TOKEN is invalid or expired.")

        # 2. Attempt automated generation
        new_token = self.generate_token_via_totp()
        if new_token:
            self.access_token = new_token
            return new_token

        # 3. If in DRY_RUN mode, allow a mock/paper token if credentials aren't yet active
        if self.config.dry_run and not self.access_token:
            logger.warning("[SAFETY-NOTICE] Running in PAPER TRADING (DRY_RUN) mode without live Upstox session. Virtual feed will initialize.")
            self.access_token = "PAPER_TRADING_DUMMY_TOKEN"
            return self.access_token

        raise RuntimeError("No valid Upstox Access Token could be resolved. Please set UPSTOX_ACCESS_TOKEN or configure UPSTOX_API_KEY, UPSTOX_API_SECRET, UPSTOX_MOBILE, UPSTOX_PIN, UPSTOX_TOTP_SECRET.")
