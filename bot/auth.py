"""
Upstox Authentication Module:
Handles automated daily OAuth2 token generation using TOTP (pyotp) + PIN,
with seamless fallback to pre-generated UPSTOX_ACCESS_TOKEN.
"""

import json
import logging
import urllib.parse
from typing import Optional, List, Tuple
import requests
import pyotp
from bot.config import TradingConfig

logger = logging.getLogger("AuthManager")


def sanitize_token(raw_token: Optional[str]) -> Optional[str]:
    """
    Cleans and normalizes an Upstox access token string.
    Handles:
    - JSON payloads (e.g. {"access_token": "..."} or {"data": {"access_token": "..."}})
    - Accidental quotes ('...' or "...")
    - Accidental 'Bearer ' prefix
    - Whitespace and newlines
    """
    if not raw_token:
        return None
    token = str(raw_token).strip()
    if not token:
        return None

    # Handle accidental pasting of raw JSON response from Upstox API
    if token.startswith("{") and token.endswith("}"):
        try:
            parsed = json.loads(token)
            if isinstance(parsed, dict):
                if "access_token" in parsed and isinstance(parsed["access_token"], str):
                    token = parsed["access_token"].strip()
                elif "data" in parsed and isinstance(parsed["data"], dict) and "access_token" in parsed["data"]:
                    token = str(parsed["data"]["access_token"]).strip()
        except Exception:
            pass

    # Strip surrounding single or double quotes
    while (token.startswith('"') and token.endswith('"')) or (token.startswith("'") and token.endswith("'")):
        token = token[1:-1].strip()

    # Strip Bearer / bearer prefix if present
    if token.lower().startswith("bearer "):
        token = token[7:].strip()

    # Strip newlines and carriage returns
    token = token.replace("\r", "").replace("\n", "").strip()

    # Re-strip quotes in case they were inside Bearer
    while (token.startswith('"') and token.endswith('"')) or (token.startswith("'") and token.endswith("'")):
        token = token[1:-1].strip()

    return token if token else None


def mask_token(token: Optional[str]) -> str:
    """Returns a masked representation of the token for secure logging."""
    if not token:
        return "<EMPTY>"
    if len(token) <= 12:
        return f"{token[:2]}...{token[-2:]} (len={len(token)})"
    return f"{token[:6]}...{token[-4:]} (len={len(token)})"


class UpstoxAuthManager:
    def __init__(self, config: TradingConfig):
        self.config = config
        self.access_token: Optional[str] = sanitize_token(config.upstox_access_token)

    def validate_existing_token(self, token: str, source_label: str = "Candidate Token") -> bool:
        """
        Validates the access token against Upstox v2 user profile API.
        Logs detailed HTTP response information on rejection for diagnostics.
        """
        clean_tok = sanitize_token(token)
        if not clean_tok:
            logger.warning(f"[{source_label}] is empty after sanitization.")
            return False

        masked = mask_token(clean_tok)
        logger.info(f"Validating Upstox session with [{source_label}]: {masked}...")

        url = "https://api.upstox.com/v2/user/profile"
        headers = {
            "Accept": "application/json",
            "Authorization": f"Bearer {clean_tok}"
        }
        try:
            res = requests.get(url, headers=headers, timeout=10)
            if res.status_code == 200:
                data = res.json()
                profile = data.get("data", {})
                user_name = profile.get("user_name", "Authorized Trader")
                user_id = profile.get("user_id", "")
                broker = profile.get("broker", "UPSTOX")
                logger.info(
                    f"Verified active Upstox session! User: '{user_name}' | ID: '{user_id}' | Broker: {broker} "
                    f"via [{source_label}]."
                )
                return True
            else:
                resp_text = res.text.strip().replace("\n", " ")
                if len(resp_text) > 250:
                    resp_text = resp_text[:250] + "..."
                logger.warning(
                    f"Upstox API rejected [{source_label}] ({masked}): "
                    f"HTTP {res.status_code} - {resp_text}"
                )
                return False
        except Exception as e:
            logger.warning(f"Network error while validating [{source_label}] against Upstox API: {e}")
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
        """
        Returns a verified access token or raises a descriptive RuntimeError.
        Evaluates candidate tokens in order:
        1. Workflow Dispatch Input (`UPSTOX_ACCESS_TOKEN_INPUT`)
        2. GitHub Encrypted Secret (`UPSTOX_ACCESS_TOKEN_SECRET`)
        3. Standard Token Environment (`UPSTOX_ACCESS_TOKEN`)
        4. Automated Headless TOTP OAuth2 Flow
        """
        # Step 1: Collect candidates
        raw_candidates: List[Tuple[str, Optional[str]]] = [
            ("Workflow Dispatch Input", getattr(self.config, "upstox_access_token_input", None)),
            ("GitHub Secret (UPSTOX_ACCESS_TOKEN)", getattr(self.config, "upstox_access_token_secret", None)),
            ("Direct Env UPSTOX_ACCESS_TOKEN", getattr(self.config, "upstox_access_token", None)),
        ]

        # Filter and deduplicate
        tested_tokens: List[Tuple[str, str]] = []
        for label, raw_tok in raw_candidates:
            clean_tok = sanitize_token(raw_tok)
            if clean_tok and not any(clean_tok == t[1] for t in tested_tokens):
                tested_tokens.append((label, clean_tok))

        # Test each manual candidate
        for label, clean_tok in tested_tokens:
            if self.validate_existing_token(clean_tok, source_label=label):
                self.access_token = clean_tok
                self.config.upstox_access_token = clean_tok
                logger.info(f"Authenticated successfully using [{label}] (Option B: Manual Daily Token).")
                return clean_tok

        # Step 2: Attempt automated generation via TOTP
        logger.info("Attempting automated headless OAuth2 login via TOTP...")
        new_token = self.generate_token_via_totp()
        if new_token:
            clean_new = sanitize_token(new_token)
            if clean_new and self.validate_existing_token(clean_new, source_label="Automated TOTP"):
                self.access_token = clean_new
                self.config.upstox_access_token = clean_new
                return clean_new

        # Step 3: All options failed
        error_details = [
            "CRITICAL: No valid Upstox Access Token could be established.",
            f"- Checked {len(tested_tokens)} manual token candidate(s); all were rejected by Upstox API.",
            "- Remember: Upstox access tokens are valid for only 1 trading day and expire daily at 03:30 AM IST.",
            "",
            "RESOLUTION STEPS:",
            "1. Generate today's fresh Upstox Access Token from the Upstox Developer Portal (or run `python tools/generate_token.py`).",
            "2. Update the GitHub Repository Secret: Settings -> Secrets and variables -> Actions -> `UPSTOX_ACCESS_TOKEN`.",
            "3. If triggering manually via 'Run workflow', you can also paste the token directly into the 'upstox_access_token' field.",
            "   (Make sure there are no extra spaces or quotation marks, and that any previous expired token in that box is replaced!)"
        ]
        full_err = "\n".join(error_details)
        logger.error(full_err)
        raise RuntimeError(full_err)
