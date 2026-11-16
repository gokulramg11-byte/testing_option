# Design System: Nifty AlphaScalp — Obsidian Terminal
**Project ID:** 873942343444045751
**Generated via:** Google Stitch MCP (Design System: `assets/4b71cc45e33940eda3545e0b466baae2`)

---

## 1. Visual Theme & Atmosphere
The **Obsidian Terminal** design system is engineered for mission-critical, ultra-low latency derivatives scalping and automated algorithmic deployment. It combines surgical precision with zero optical fatigue during continuous high-stress Indian market trading sessions (09:15–15:30 IST).
- **Core Aesthetic:** Cybernetic quant telemetry, minimalist tactical dark surfaces, calibrated luminescent status indicators.
- **Design Philosophy:** Raw data density without clutter; optical hierarchy where prices, profit deltas, gatekeeper criteria, and safety kill switches are instantly recognizable.
- **Elevation & Depth:** Rejects exaggerated drop shadows in favor of precise tonal layering (`#0B0F19` canvas -> `#111827` module cards -> `#161F30` active panels) with crisp 1px hairline borders (`#1F2937`).

---

## 2. Color Palette & Roles

| Semantic Role | Color Name | Hex Code | Visual & Functional Purpose |
| :--- | :--- | :--- | :--- |
| **Canvas Root** | Deep Obsidian Slate | `#0B0F19` | Primary viewport background; zero glare |
| **Surface Containers** | Dark Graphite | `#111827` | Structural cards, modules, telemetry decks |
| **Active Surface** | Elevated Navy Slate | `#161F30` | Hover states, active inputs, inner stat wells |
| **Hairline Dividers** | Muted Steel Border | `#1F2937` | 1px clean separation lines without visual noise |
| **Primary Telemetry** | Quant Emerald | `#10B981` | Bullish gates (`PASS`), positive MTM P&L, live heartbeat |
| **Secondary Guard** | Amber Warning | `#F59E0B` | Simulation mode (`PAPER TRADING`), time SL decay warnings |
| **Kill / Danger** | Crimson Red | `#EF4444` / `#DC2626` | Emergency Square-off button, Stop-Loss triggered, Daily circuit breaker |
| **System Info** | Electric Sky | `#38BDF8` | API round-trip latency, order execution hashes, active contracts |
| **Text Primary** | Crisp Terminal White | `#DFE2F1` | Primary titles, instrument names, high-contrast labels |
| **Text Muted** | Slate Telemetry Gray | `#86948A` | Secondary parameters, inactive indicators, timestamps |

---

## 3. Typography Rules
- **Display & Headlines:** `Geist`, `Inter`, or `system-ui` sans-serif (`font-sans`, weights: 500, 600, 700) for structural titles, section headers, and dialogs.
- **Numbers, Telemetry & Tickers:** `JetBrains Mono` or monospace (`font-mono`, weights: 400, 500, 700) with `font-variant-numeric: tabular-nums` to ensure zero layout shift during real-time tick streaming.
- **Labels & Microcopy:** `uppercase tracking-wider font-mono text-[10px]` for gatekeeper states, order types, and time countdowns.

---

## 4. Component Stylings
- **Buttons:**
  - *Emergency Kill Switch:* Crimson Red (`#DC2626`), high-contrast bold white/salmon text, subtle glow (`box-shadow: 0 0 12px rgba(220, 38, 38, 0.35)`), active scale feedback (`scale-[0.98]`).
  - *Primary Actions:* Emerald Green (`#10B981`), dark text (`#0B0F19`), font `JetBrains Mono` medium.
  - *Secondary / Tooling:* Surface `#161F30`, 1px border `#1F2937`, text `#D1D5DB`.
- **Status Chips & Pills:**
  - Compact height (20px–24px), uppercase font, rounded 4px (`rounded-md`).
  - Pulsing LED indicators (green for online, amber for paper mode, crimson for halted).
- **Cards & Data Modules:**
  - 1px hairline border `#1F2937`, dark graphite surface `#111827`, subtle ambient backdrop blur.
- **7-Point Gatekeeper Card:**
  - Two-column / compact stacked list displaying status badge (`PASS` in emerald or `WAIT` in muted outline) and exact numeric values (e.g. `Spot 24350 > VWAP 24310`).

---

## 5. Layout Principles
- **Mobile-First Single Stream (`< 768px`):** Strict vertical flow with sticky top telemetry bar, main scroll area with compact 8px/12px gutters, and fixed bottom navigation dock.
- **Zero Shift Architecture:** Fixed height cards for live metrics so arriving WebSocket ticks do not cause viewport reflows.
- **Spring Physics:** `stiffness: 100, damping: 20` for micro-animations and status updates.

---

## 6. Generated Screens Reference
1. **Screen 1 — Login & Security Gate** (`projects/873942343444045751/screens/03cb300c55fd4bcdaa5b4e8cae6575c5`)
2. **Screen 2 — Live Scalping Cockpit** (`projects/873942343444045751/screens/cb04b42e0f7f46288abe3762bba4257c`)
3. **Screen 3 — Trade Log & Performance Analytics** (`projects/873942343444045751/screens/29575acade7549b88a6eee082baf06ad`)
