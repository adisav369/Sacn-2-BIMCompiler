# HexTalk — Zero-Infrastructure Communication via Sound & Bluetooth

## What is this?

A phone-to-phone communication system using **sound waves** or **BLE** as the data carrier. No WiFi, no cell signal, no internet. Users compose sentences by spinning a 3-ring visual dial, the engine translates to hex codes, transmits acoustically (or via BLE), and the receiver reconstructs readable text instantly — in 10 languages.

Inspired by: Lexigrams (Kanzi, 1970s), maritime signal codes, Chinese telegraph codes.

## Files

| File | Purpose |
|------|---------|
| `dial.html` | Main app — 3-ring walkie-talkie dial (TX + RX) |
| `beacon.html` | Simple beacon transmitter (legacy, pre-dial) |
| `listener.html` | Simple receiver (legacy, pre-dial) |
| `codes.js` | 377 codes + grammar engine + i18n (10 languages) + BLE protocol |
| `sw.js` | Service worker for offline caching |

## 3-Ring Grammar Engine

Ring 1 (outer) selects the sentence **pattern**. Rings 2-3 change dynamically.

| Pattern | Ring 1 | Ring 2 (filtered) | Ring 3 (filtered) |
|---------|--------|-------------------|-------------------|
| STATUS | Who | Condition | Modifier |
| REQUEST | Need | What | Urgency |
| REPORT | Hazard type | Severity | Action |
| NAVIGATE | Direction | Detail | Landmark |
| SOCIAL | Action | Who/what | When |
| QUICK | Single tap (YES/NO/OK/MAYDAY...) | — | — |
| CONFIRM | Response | Detail | — |

Each ring shows **4-12 items max**. Never more than 4 hex codes per sentence.

## Transport Modes

### Sonar (default) — green indicator
- **Air/Rubble**: 3kHz / 4.5kHz FSK, 60ms/bit
- **Underwater**: 800Hz / 1.2kHz FSK, 100ms/bit
- Range: 5-50m air, 50-300m+ underwater

### Bluetooth (toggle) — blue indicator
- BLE GATT: custom SAR service UUID
- Packet: 2 bytes (code) + GPS string
- Range: 30-100m, through walls
- Needs BLE peripheral (ESP32/Meshtastic node)

## 10 Languages

Tap the flag icon in the top bar to switch. All ring labels and HUD text update.

🇬🇧 English · 🇲🇾 Malay · 🇨🇳 Chinese · 🇮🇳 Tamil · 🇯🇵 Japanese · 🇰🇷 Korean · 🇸🇦 Arabic · 🇪🇸 Spanish · 🇫🇷 French · 🇹🇭 Thai

## UI Layout

```
┌──────────────────────────────┐
│ ⌂  [AIR] 🇬🇧  flash   [SND●]│  top bar
│                              │
│      ╭──────────────────╮    │
│     │  ╭──────────────╮ │    │  Ring 1: pattern (7 options)
│     │ │  ╭──────────╮ │ │    │  Ring 2: context (4-12)
│     │ │ │  centre  │ │ │    │  Ring 3: detail (4-6)
│     │ │  ╰──────────╯ │ │    │  Centre: tap to queue
│     │  ╰──────────────╯ │    │
│      ╰──────────────────╯    │
│                              │
│    BIG SENTENCE PREVIEW      │
│    hex codes · path           │
│                              │
│  (🛑)  (SEND)  (📍)  (S.O.S)│  knobs
└──────────────────────────────┘
```

## Buttons

| Button | Action |
|--------|--------|
| 🛑 STOP | Kill all TX/RX, clear queue, send REST |
| SEND | Transmit queue (toggle: tap again = stop) |
| 📍 GPS | Send I AM HERE + coordinates |
| S.O.S | Loop SOS + GPS until stopped |
| ⌂ | Home — back to landing page |
| Flag | Toggle language picker |
| SND/BT | Toggle sonar / bluetooth transport |

## Standby Resilience (Blackout Mode)

For resting phone in receiver mode:
- **Adaptive polling**: 10Hz idle (saves 50-70% CPU), ramps to 30Hz on signal
- **WakeLock**: keeps screen alive, re-acquires on visibility change
- **AudioContext heartbeat**: 10s check, auto-revives dead mic
- **Service worker**: full offline cache after first load

## §-Tag Debug Logging

Open browser console to see whitebox logs:

| Tag | What it proves |
|-----|----------------|
| `§RX-START` | Mic opened, poll rate, AudioContext state |
| `§RX-STOP` | Clean resource shutdown |
| `§SYNC-LOCK` | Beacon preamble detected |
| `§RX-DECODE` | Packet decoded: code, validity, bit count |
| `§POLL-FAST` | Ramp to fast polling (signal detected) |
| `§POLL-IDLE` | Ramp back to idle (packet done) |
| `§BLE-CONNECT` | BLE device paired and subscribed |
| `§BLE-RX` | Code received via Bluetooth |
| `§BLE-TX` | Code sent via Bluetooth |
| `§HEARTBEAT` | Periodic AudioContext health check |
| `§VISIBILITY` | Page show/hide, AudioContext state |
| `§LANG` | Language switched |
| `§TRX` | Transport mode toggled |

## Live URLs

| Bucket | URL |
|--------|-----|
| ootb-full | `https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-full/o/sar/dial.html` |
| ootb-dev | `https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-dev/o/sar/dial.html` |

## Size

| Component | Raw | Gzipped |
|-----------|-----|---------|
| codes.js (377 codes + grammar + i18n) | ~70 KB | ~12 KB |
| dial.html | ~57 KB | ~10 KB |
| sw.js | ~1 KB | <1 KB |
| **Total app** | **~130 KB** | **~25 KB** |
