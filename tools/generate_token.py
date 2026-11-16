"""
Quick Daily Token Generator for Option B:
Run this script each morning to get your UPSTOX_ACCESS_TOKEN in 10 seconds.
"""

import sys
import urllib.parse
import requests

def main():
    print("=" * 70)
    print("UPSTOX DAILY ACCESS TOKEN GENERATOR (OPTION B)")
    print("=" * 70)
    
    api_key = input("Enter your UPSTOX_API_KEY: ").strip()
    api_secret = input("Enter your UPSTOX_API_SECRET: ").strip()
    redirect_uri = input("Enter REDIRECT_URI (default: https://127.0.0.1:5000/): ").strip() or "https://127.0.0.1:5000/"

    # Step 1: Login URL
    login_url = f"https://api.upstox.com/v2/login/authorization/dialog?response_type=code&client_id={api_key}&redirect_uri={urllib.parse.quote(redirect_uri, safe='')}"
    
    print("\n1. Open this URL in your web browser:")
    print(f"\n   {login_url}\n")
    print("2. Log in with your Upstox credentials.")
    print("3. After login, your browser will redirect to a URL like:")
    print(f"   {redirect_uri}?code=XXXXXX\n")
    
    auth_input = input("Paste the redirected URL (or just the code): ").strip()
    if not auth_input:
        print("Error: No code provided.")
        return

    # Extract code
    if "code=" in auth_input:
        parsed = urllib.parse.urlparse(auth_input)
        code = urllib.parse.parse_qs(parsed.query).get("code", [auth_input])[0]
    else:
        code = auth_input

    # Step 2: Exchange code for access_token
    token_url = "https://api.upstox.com/v2/login/authorization/token"
    payload = {
        "code": code,
        "client_id": api_key,
        "client_secret": api_secret,
        "redirect_uri": redirect_uri,
        "grant_type": "authorization_code"
    }
    headers = {
        "Accept": "application/json",
        "Content-Type": "application/x-www-form-urlencoded"
    }

    res = requests.post(token_url, data=payload, headers=headers)
    if res.status_code == 200:
        token_data = res.json()
        token = token_data.get("access_token")
        user_name = token_data.get("user_name", "Trader")
        print("\n" + "=" * 70)
        print(f"SUCCESS! Verified session for: {user_name}")
        print("=" * 70)
        print(f"\nYour UPSTOX_ACCESS_TOKEN for today is:\n\n{token}\n")
        print("To use this:")
        print("- In GitHub Actions: When tapping 'Run workflow', paste this token into 'upstox_access_token'")
        print("- Or save it in GitHub Repo Secrets as UPSTOX_ACCESS_TOKEN")
        print("=" * 70)
    else:
        print(f"\nFailed to retrieve access token (HTTP {res.status_code}):")
        print(res.text)

if __name__ == "__main__":
    main()
