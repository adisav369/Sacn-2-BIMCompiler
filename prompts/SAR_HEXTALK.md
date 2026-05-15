# ⚠ DO NOT REMOVE
# Scope: HexTalk — Acoustic Composable Communication Protocol
# Read the log after every run.

# ⚠ GENERAL NOTE — READ BEFORE ANY WORK
#
# THIS CODE WAS NOT BUILT SYSTEMATICALLY. Features were piled on in a
# single session without stopping to review, test, or verify on a real
# device. The result: backend logic is solid but the UI is broken in
# multiple ways — wrong touch targets, wrong icons, text not fitting,
# toggles that confuse instead of help. The lesson: DO NOT PILE ON.
# Fix what's there. Test it. Then add the next thing.
#
# BEFORE ANY WORK:
# 1. REVIEW ALL — open dial.html in Chrome DevTools mobile emulator (360×800)
# 2. TEST ALL — whitebox §-log verification, not user testing
# 3. ASK IF NOT SURE — do not guess, do not invent
# 4. UNDERSTAND THE PURPOSE AND NOVELTY — this is NOT a chat app.
#    It is a zero-infrastructure communication system using SOUND WAVES
#    and BLE as carriers. It works underwater, through rubble, in blackouts,
#    with no internet, no pairing, no servers. The 3-ring dial composes
#    structured sentences from a 4096-code dictionary in 10 languages.
#    Nothing like this exists. Treat it with care.
# 5. FIX THE UI FIRST — make it work on a phone, then add features

# HexTalk — Universal Acoustic Communication

## Vision

Phone-to-phone communication using sound waves or BLE as carrier. No internet, no pairing, no infrastructure. Users compose sentences by spinning a 3-ring visual dial, the engine translates to hex codes, transmits acoustically, and the receiver reconstructs readable text instantly — in 10 languages.

Inspired by: Lexigrams (Kanzi, 1970s), maritime signal codes, Chinese telegraph codes. Novel because: composable grammar + acoustic channel + shared dictionary + visual dial + auto-relay mesh. No prior system combines all of these.

## Architecture

### Code Format
- 3 hex digits = 4096 codes (000-FFF)
- 4th hex digit = XOR checksum of first 3 nibbles
- Transmitted as 16 bits + EOT marker
- Multiple codes chained in one burst (separator: 4 zero-bits between codes)

### Transport Channels
| Channel | Bit 0 | Bit 1 | Sync | Bit duration | Use case |
|---------|-------|-------|------|-------------|----------|
| AIR     | 3 kHz | 4.5 kHz | 2 kHz | 60ms | Rooms, rubble, buildings |
| WATER   | 800 Hz | 1.2 kHz | 500 Hz | 100ms | Underwater, submerged |
| BLE     | GATT characteristic write/notify | — | — | Instant | Through walls, 30-100m |

### Packet Structure
```
[PREAMBLE: 6 chirps] [GAP] [CODE1: 16 bits] [SEP: 4 bits] [CODE2: 16 bits] ... [EOT: 8 bits] [END: 3 chirps]
```

BLE packet: `[code4_hi, code4_lo, ...gps_ascii, 0x00]`

### Receiver Protocol
- Adaptive polling: 100ms idle (10 Hz), ramps to 30ms on sync detect
- Preamble lock: 3+ sync tone detections
- Silence detection: 10+ vote windows with no signal = end of packet
- Auto-ACK: 3 ascending tones (distinct from data frequencies)
- Auto-relay: retransmit received codes after 3s delay (dedup by content, 60s TTL)
- Standby resilience: wakeLock + visibilitychange handler + 10s heartbeat

## 3-Ring Grammar Engine

Ring 1 (outer) selects the sentence pattern. Rings 2-3 filter dynamically.

| Pattern | Ring 1 (outer) | Ring 2 (middle) | Ring 3 (inner) |
|---------|---------------|-----------------|----------------|
| STATUS | Who: Me/Team/Children/Survivor | Condition: OK/Trapped/Injured | Modifier: Stable/Urgent/Unconscious |
| REQUEST | Need: Medical/Water/Tools/Boat | What: specific item | Urgency: ASAP/No rush |
| REPORT | Hazard: Fire/Flood/Gas/Collapse | Severity: Active/Contained | Action: Evacuate/Monitor/Cleared |
| NAVIGATE | Direction: Here/North/Up/Down | Detail: Safe/Danger/Rally | Landmark: Stairs/Perimeter |
| SOCIAL | Action: Hello/Coffee/Meeting/Home | Context: specific | When: Now/Later/5min |
| QUICK | Single tap: YES/NO/OK/WAIT/MAYDAY/ROGER (no grammar) | — | — |
| CONFIRM | Response: Alive/Received/Situation | Detail: Better/Worse/Waiting | — |

### Rules
- Ring 2 depends on Ring 1 selection
- Ring 3 depends on Ring 1 + Ring 2 selection
- Each ring shows 4-12 items max (never more)
- Each item is a complete meaningful word/phrase
- One ring active at a time — tap to select, spin to browse
- Centre tap = queue composed sentence
- Never more than 4 hex codes per sentence

### Sentence Assembly
```
Ring 1: STATUS → Ring 2: ME → Ring 3: TRAPPED
→ Display: "TRAPPED IMMOBILE"  →  Hex: 101  →  16 bits, ~1 second TX

Ring 1: REQUEST → Ring 2: WATER → Ring 3: ASAP
→ Display: "NEED WATER + ASAP"  →  Hex: 601 F88  →  32 bits, ~2 seconds TX
```

## Dictionary

377 codes across 31 categories. See `codes.js` for full list.

| Range | Category | Examples |
|-------|----------|---------|
| 0xx | Protocol | ACK, NAK, MAYDAY, PAN-PAN |
| 1xx | Self-report | SOS, TRAPPED, INJURED, AIR LOW |
| 2xx-3xx | Observed + Medical | SURVIVOR FOUND, BLEEDING, FRACTURE |
| 4xx-5xx | Hazard + Structure | FIRE, GAS, COLLAPSE, VOID FOUND |
| 6xx-8xx | Resource + Navigation + Team | NEED MEDICAL, I AM HERE, SEND HELP |
| 9xx-Exx | Confirm + Maritime + Fire + HAZMAT + Infra + Civilian |
| F0x-FFx | Daily life: Social, Response, Office, Food, Home, Numbers, Time, etc. |

## i18n — 10 Languages

🇬🇧 English · 🇲🇾 Malay · 🇨🇳 Chinese · 🇮🇳 Tamil · 🇯🇵 Japanese · 🇰🇷 Korean · 🇸🇦 Arabic · 🇪🇸 Spanish · 🇫🇷 French · 🇹🇭 Thai

`sarText(code)` returns translated short+detail, falls back to English.
Partial translations exist (~20 key codes per language). Expand incrementally by category.

## UI Layout

```
┌──────────────────────────────┐
│ ⌂  [AIR] 🇬🇧  flash   [SND●]│  top bar: home, channel, flag, transport
│                              │
│      ╭──────────────────╮    │
│     │  ╭──────────────╮ │    │  Ring 1: pattern (7 options)
│     │ │  ╭──────────╮ │ │    │  Ring 2: context (4-12)
│     │ │ │  centre  │ │ │    │  Ring 3: detail (4-6)
│     │ │  ╰──────────╯ │ │    │  Centre: tap to queue
│     │  ╰──────────────╯ │    │
│      ╰──────────────────╯    │
│                              │
│    BIG SENTENCE PREVIEW      │  translated text, hex codes, path
│                              │
│  [STOP] [SEND] [GPS] [SOS]  │  action buttons
└──────────────────────────────┘
```

### Buttons
| Button | Function |
|--------|----------|
| STOP | Kill all TX/RX, clear queue, send REST signal |
| SEND | Transmit queue (toggle: tap again to stop) |
| GPS | Send I AM HERE + lat,lon |
| SOS | Loop SOS + GPS until tapped again (pulses red) |

## Files
| File | Purpose |
|------|---------|
| `internal/SAR/codes.js` | 377 codes + SAR_GRAMMAR + SAR_BLE + SAR_I18N + helpers |
| `internal/SAR/dial.html` | Main app — 3-ring walkie-talkie dial (TX + RX) |
| `internal/SAR/beacon.html` | Simple beacon transmitter (legacy) |
| `internal/SAR/listener.html` | Simple receiver (legacy) |
| `internal/SAR/sw.js` | Service worker for offline cache |
| `internal/SAR/README.md` | Full architecture docs |

## OCI Deployment
| Local | Bucket path | Buckets |
|-------|-------------|---------|
| `SAR/*` | `sar/*` | `bim-ootb-full` + `bim-ootb-dev` |

## §-Tag Debug Logging
| Tag | What it proves |
|-----|----------------|
| `§RX-START` | Mic opened, poll rate, AudioContext state |
| `§SYNC-LOCK` | Beacon preamble detected |
| `§RX-DECODE` | Packet decoded: code, validity, bit count |
| `§POLL-FAST/IDLE` | Adaptive polling state change |
| `§BLE-CONNECT/TX/RX` | Bluetooth transport events |
| `§HEARTBEAT` | Periodic AudioContext health check |
| `§VISIBILITY` | Page show/hide + recovery actions |
| `§LANG` | Language switched |

## Prior Art
| System | Year | Composable? | Acoustic? | Phone-native? | Grammar? |
|--------|------|-------------|-----------|---------------|----------|
| Lexigrams | 1970s | Yes | No | No | Basic |
| Maritime flags | 1800s | Limited | No | No | No |
| Chinese telegraph | 1871 | No | Wire | No | No |
| Chirp.io | 2011 | No | Yes | SDK only | No |
| **HexTalk** | **2026** | **Yes** | **Yes (3 channels)** | **Any browser** | **Yes** |
