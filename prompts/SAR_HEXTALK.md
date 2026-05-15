# ⚠ DO NOT REMOVE
# Scope: HexTalk — Acoustic Composable Communication Protocol
# Read the log after every run.

# HexTalk Spec — Universal Acoustic Communication

## Vision
A phone-to-phone communication system using sound waves as carrier. No internet, no pairing, no infrastructure. Users compose sentences by spinning a visual dial, the engine translates to hex codes, transmits acoustically, and the receiver reconstructs readable text instantly.

Inspired by: Lexigrams (Kanzi, 1970s), maritime signal codes, Chinese telegraph codes. Novel because: composable grammar + acoustic channel + shared dictionary + visual dial.

## Architecture

### Code Format
- 3 hex digits = 4096 single codes (000-FFF)
- 4th hex digit = XOR checksum of first 3 nibbles
- Transmitted as 16 bits + EOT marker
- Multiple codes chained in one burst (separator: 4 zero-bits between codes)

### Acoustic Channels
| Channel | Bit 0 | Bit 1 | Sync | Bit duration | Use case |
|---------|-------|-------|------|-------------|----------|
| AIR     | 3 kHz | 4.5 kHz | 2 kHz | 60ms | Rooms, rubble, buildings |
| WATER   | 800 Hz | 1.2 kHz | 500 Hz | 100ms | Underwater, submerged |
| HF      | 15 kHz | 18 kHz | 12 kHz | 100ms | Inaudible, office/social |

### Packet Structure
```
[PREAMBLE: 6 chirps] [GAP] [CODE1: 16 bits] [SEP: 4 bits] [CODE2: 16 bits] ... [EOT: 8 bits] [END: 3 chirps]
```

### Receiver Protocol
- Poll FFT at half bit-rate (2 samples per bit, majority vote)
- Preamble lock: 3+ sync tone detections
- Silence detection: 10+ vote windows with no signal = end of packet
- Auto-ACK: 3 ascending tones (distinct from data frequencies)
- Auto-relay: retransmit received codes after 3s delay (dedup by content, 60s TTL)

## Grammar Engine

### Sentence Patterns
The first ring selects the sentence pattern. Rings 2-3 change dynamically based on the pattern.

| Pattern | Ring 1 (outer) | Ring 2 (middle) | Ring 3 (inner) |
|---------|---------------|-----------------|----------------|
| STATUS | Who: Me/You/Team/Kids/Someone | Condition: OK/Trapped/Injured/Sick | Modifier: Now/Stable/Urgent/Floor N |
| REQUEST | Need: Resource prefix | What: Water/Medical/Tools/Air/Food | Urgency: ASAP/No rush/When possible |
| SOCIAL | Action: Coffee/Lunch/Meet/Call | With: You/Team/Everyone | When: Now/Later/Tomorrow/5min |
| NAVIGATE | Where: North/South/Up/Down/Here | Distance: Near/Far/Floor N | Landmark: Door/Stairs/Corner |
| REPORT | Subject: Fire/Flood/Gas/Collapse | Severity: Active/Contained/Clear | Action: Evacuate/Monitor/Cleared |
| QUICK | (single code, no grammar) | — | — |
| ACKNOWLEDGE | Response: Yes/No/OK/Wait/Roger | — | — |

### Ring Filtering Rules
- Ring 2 options depend on Ring 1 selection
- Ring 3 options depend on Ring 1 + Ring 2 selection
- Inner ring always shows 4-12 items max (never more)
- Each item is a complete meaningful word/phrase
- User spins to browse alternatives, text updates live

### Sentence Assembly
```
Ring 1: ME (subject)
Ring 2: TRAPPED (condition)
Ring 3: STABLE (modifier)
→ Display: "I am trapped, stable condition"
→ Hex: 103 (TRAPPED_STABLE)
→ Single code, 16 bits, ~1 second TX

Ring 1: NEED (request)
Ring 2: WATER (resource)
Ring 3: URGENT (urgency)
→ Display: "Need water urgently"
→ Hex: 601 F88 (NEED_WATER + ASAP)
→ Two codes, 32 bits, ~2 seconds TX
```

### Smart Mapping
The grammar engine maps natural sentence slots to the most efficient hex sequence:
- If a single code covers the full meaning → send 1 code (16 bits)
- If 2-3 codes needed → chain them (32-48 bits)
- Never more than 4 codes per sentence
- Engine picks codes, user just picks meaning from the dial

### Toggle / Alternatives
While composing, user can swipe the LAST ring to browse alternatives:
- Other two rings stay locked
- Large text preview updates live
- When satisfied, tap SEND
- Or tap centre to queue and compose the next sentence segment

## Dictionary Structure

### Code Ranges
| Range | Category | Count | Description |
|-------|----------|-------|-------------|
| 000-00F | Protocol | 16 | ACK, NAK, REPEAT, MAYDAY, CANCEL... |
| 100-1FF | Self-report | 16+ | SOS, TRAPPED, INJURED, OK... |
| 200-2FF | Observed | 12+ | SURVIVOR FOUND, VOICE HEARD... |
| 300-3FF | Medical | 16+ | BLEEDING, FRACTURE, BURNS... |
| 400-4FF | Hazard | 13+ | FIRE, GAS, FLOOD, RADIATION... |
| 500-5FF | Structure | 10+ | COLLAPSE, VOID, ACCESS... |
| 600-6FF | Resource | 13+ | NEED MEDICAL, WATER, HELICOPTER... |
| 700-7FF | Navigation | 13+ | I AM HERE, NORTH, RALLY POINT... |
| 800-8FF | Team | 13+ | SEND HELP, EN ROUTE, ETA... |
| 900-9FF | Confirm | 7+ | ALIVE, EXTRACTED, WORSE... |
| A00-AFF | Maritime | 11+ | MAN OVERBOARD, DIVER... |
| B00-BFF | Fire | 7+ | SPREADING, FLASHOVER... |
| C00-CFF | HAZMAT | 6+ | CHEMICAL, BIOLOGICAL... |
| D00-DFF | Infrastructure | 6+ | POWER OUT, ROAD BLOCKED... |
| E00-EFF | Civilian | 6+ | EVACUATING, MISSING... |
| F00-F0F | Social | 16 | HELLO, BYE, THANKS, SORRY... |
| F10-F1F | Response | 16 | YES, NO, MAYBE, OK, BUSY... |
| F20-F2F | Office | 16 | MEETING, REPORT, DEADLINE... |
| F30-F3F | Food | 10 | COFFEE?, LUNCH?, MY TREAT... |
| F40-F4F | Logistics | 16 | ARRIVING, ETA, WHERE ARE YOU?... |
| F50-F5F | Status | 13 | HAPPY, STRESSED, TIRED... |
| F60-F6F | Home | 13 | COMING HOME, DINNER READY... |
| F70-F7F | Numbers | 15 | 0-9, 10, 100, 1000, MANY, FEW |
| F80-F8F | Time | 14 | NOW, TODAY, ASAP, MORNING... |
| F90-F9F | Action | 16 | CALL ME, COME HERE, OPEN DOOR... |
| FA0-FAF | Gaming | 12 | GG, READY, GO, FOLLOW ME... |
| FB0-FBF | Transport | 12 | BUS, TRAIN, DRIVING, LANDED... |
| FC0-FCF | Weather | 8 | RAIN, STORM, HOT, COLD... |
| FD0-FDF | Shopping | 9 | BUY, HOW MUCH?, PAID... |
| FE0-FEF | Education | 10 | CLASS, EXAM, HOMEWORK... |
| FF0-FFF | Custom | 16 | User-defined + PING/PONG/HEARTBEAT |

### Super-Groups (Dial outer ring)
| Group | Colour | Contains |
|-------|--------|----------|
| EMERGENCY | Red #f44 | System, Self, Observed, Medical, Confirm |
| HAZARD | Orange #fa0 | Hazard, Structure, Fire, HAZMAT, Infra |
| HELP | Yellow #ff0 | Resource, Team, Location, Maritime, Civilian |
| SOCIAL | Green #0f0 | Social, Response, Status, Gaming, Custom |
| WORK | Blue #48f | Office, Action, Time, Education, Number |
| LIFE | Cyan #0af | Food, Logistics, Home, Transport, Weather, Shopping |

## DB Size Analysis

### Current: Pure JS dictionary (codes.js)
- 340 codes × ~80 bytes each = ~27 KB
- Full 4096 codes × ~80 bytes = ~328 KB
- Grammar rules (ring filtering) = ~10 KB
- **Total: under 350 KB** — fits in a single JS file, no DB needed

### When would we need a DB?
| Scenario | Size | Storage |
|----------|------|---------|
| 4096 codes, static | 350 KB | JS file (current) |
| 4096 codes + user favourites + history | 500 KB | localStorage |
| Multi-language (10 languages × 4096) | 3.5 MB | IndexedDB |
| Custom user dictionaries + sharing | 5-10 MB | IndexedDB |
| Audio samples for codes (pre-recorded) | 50-100 MB | Overboard |

### Verdict
**No DB needed until we add multi-language or user-generated dictionaries.** Current approach (single JS file loaded once, cached by service worker) is correct. localStorage for favourites/history. IndexedDB only if we ship 10+ languages.

**Overboard threshold: >10 MB.** If the dictionary + grammar + favourites exceeds 10 MB, we've over-engineered. The beauty of hex codes is that they're tiny. Keep it that way.

## UI Spec

### Mobile (Primary)
```
┌────────────────────────────┐
│ ⋮ [AIR●]    sentence text  │  top bar
│                            │
│      ╭──────────────╮      │
│     │  ╭──────────╮  │     │  Ring 1: sentence pattern / super-group
│     │ │  ╭──────╮ │  │     │  Ring 2: contextual options
│     │ │ │ Ring3 │ │  │     │  Ring 3: final detail (4-12 items)
│     │ │  ╰──────╯ │  │     │
│     │  ╰──────────╯  │     │
│      ╰──────────────╯      │
│                            │
│    BIG LIVE TEXT PREVIEW    │  updates as you spin
│    hex · severity           │
│    [queued sentence]        │  ticker tape
│                            │
│  (CLR)  (SEND)  (GPS) (SOS)│  round knobs
└────────────────────────────┘
```

### Desktop (Future)
```
┌──────────────────────────────────────────┐
│ ⋮ [AIR●]                                │
├────────────────────┬─────────────────────┤
│                    │    ╭────────────╮   │
│  BIG SENTENCE      │   │  ╭──────╮  │   │
│  DISPLAY           │   │ │      │  │   │
│                    │   │ │ Dial │  │   │
│  [queued codes]    │   │  ╰──────╯  │   │
│                    │    ╰────────────╯   │
│  Received messages │                     │
│  with GO buttons   │  (CLR)(SEND)(GPS)   │
└────────────────────┴─────────────────────┘
Left 50%: display     Right 50%: dial + knobs
```

### Interaction Model
1. **Spin outer ring** → selects pattern/super-group (6 options, big readable)
2. **Spin middle ring** → contextual slot 2 (8-12 options, filtered by ring 1)
3. **Spin inner ring** → final detail (4-12 options, filtered by rings 1+2)
4. **Live preview** updates at bottom with full readable sentence
5. **Swipe inner ring** to toggle alternatives (others stay locked)
6. **Tap centre** → queue the sentence (compose multi-part message)
7. **Tap SEND** → transmit (auto-sends current if queue empty)
8. **Any button = stop current** (implicit stop-first)

### Toggle Behaviour
- SEND is a toggle: idle=SEND, active=STOP
- SOS is a toggle: idle=SOS, active=STOP
- Any action stops whatever is currently transmitting
- CLR stops + clears queue + sends REST to receiver

## Protocol Features

### Walkie-Talkie Mode
- Both sides use same page (dial.html)
- Mic opens on first touch (mobile gesture requirement)
- Background RX listener runs when not transmitting
- Self-mute during TX (prevents echo)
- Auto-resume listening after TX completes

### ACK / Handshake
- Receiver sends 3 ascending tones as ACK
- Sender detects ACK via FFT, shows "ACK RECEIVED" banner
- First message from either side = handshake

### Auto-Relay (Mesh)
- Received codes retransmitted after 3s delay
- Dedup by content (prevents echo loops, 60s TTL)
- Extends range through chain of phones

### GPS
- One-tap GPS button sends code 700 (I AM HERE) + lat,lon
- Receiver shows green "GO" button → opens Google Maps
- GPS coordinates as ASCII after the 16-bit code

## Files
| File | Purpose |
|------|---------|
| `internal/SAR/codes.js` | Dictionary (4096 codes) + grammar rules + super-groups |
| `internal/SAR/dial.html` | Main app — walkie-talkie dial interface |
| `internal/SAR/beacon.html` | Simple beacon (legacy, pre-dial) |
| `internal/SAR/listener.html` | Simple listener (legacy, pre-dial) |
| `internal/SAR/README.md` | Project description |

## OCI Deployment
| Local | Bucket path |
|-------|-------------|
| `SAR/codes.js` | `sar/codes.js` |
| `SAR/dial.html` | `sar/dial.html` |
| `SAR/beacon.html` | `sar/beacon.html` |
| `SAR/listener.html` | `sar/listener.html` |

Bucket: `bim-ootb-dev`, region: `ap-kulai-2`

## Session Resume — Known Issues (2026-05-16)

The 3-ring grammar engine and BLE transport were built this session. The core logic
(codes.js grammar, BLE pack/unpack, i18n, standby resilience) is **whitebox-verified
and deployed**. However the **dial.html UI has multiple UX problems** that need a
full design review before further feature work. READ THIS BEFORE CODING.

### Critical UX Issues
1. **Inner rings untouchable on mobile** — 3 concentric rings on a 360px phone make
   Ring 3 (~45px radius) impossible for fingers. FIX: one ring active at a time.
   Tap ring zone to select, spin only spins active ring. Sequential flow R1→R2→R3.
2. **Bottom knobs have wrong/dumb icons** — emoji stop sign (🛑), pin (📍), "S.O.S"
   text are the wrong symbols. Use CORRECT standard icons: octagon stop, crosshair
   for GPS, international SOS symbol. Or just use clean TEXT labels — no emojis.
   Layout: STOP = red, SEND = green prominent, GPS = blue, SOS = red pulsing.
3. **Text not filling ring arcs** — outer ring labels cramped despite thin band.
   Font sizing formulas need tuning per ring. Test on actual phone widths (360-414px).
4. **Flag chooser not translating ring labels** — `sarText()` hooks exist in HUD but
   the 3-ring `drawRing()` uses `item.label` / `item.key` directly, not translated.
   The grammar keys (ME, TRAPPED, etc.) need i18n mapping.
5. **BT/Sonar toggle barely visible** — pill shape with dot+label is there but easy
   to miss. Needs better placement or integration into channel bar.
6. **Home icon goes to ootb-dev landing** — should go to bim-ootb.com or be configurable.
7. **Full 10-language translations incomplete** — codes.js SAR_I18N only has ~20 key
   codes per language. Background generation hit token limit. Do it incrementally:
   one language at a time, or batch by category (System, Self, Medical, etc.).

### What Works (verified by §-logs + Node tests)
- codes.js: 377 codes, encode/decode round-trip, 7 grammar patterns, ring filtering
- BLE: pack/unpack, service UUID, TX/RX characteristics
- Standby: wakeLock + visibility handler + 10s heartbeat + adaptive polling (100ms idle)
- Acoustic TX/RX: FSK air/water, sync lock, vote-based decode, auto-relay mesh
- i18n: 10 language flags, sarText() fallback chain, partial translations
- Service worker: offline cache for all SAR files

8. **SOS/SEND toggle text swap is unintuitive** — pressing SOS changes button to
   "STOP" and changes SEND to "STOP SOS active". Confusing. FIX: SOS should stay
   as SOS but pulse red when active. A separate STOP action (or tap SOS again)
   stops it. Don't mutate other buttons.
9. **General: lots of things not right** — the UI was built code-first without
   testing on a real phone. Next session MUST open Chrome DevTools mobile emulator
   (360×800) and visually verify every element before deploying.

### Design Approach for Next Session
1. **Read README.md** — full architecture description
2. **Open dial.html on a real phone** (or Chrome DevTools mobile emulator 360×800)
3. **Fix touch first** — single-active-ring model, generous tap zones
4. **Fix knobs second** — clear layout, proper sizing
5. **Then polish** — text sizing, translations, toggle visibility
6. **Whitebox test from §-logs** before asking user to test
7. Deploy to both `bim-ootb-full` and `bim-ootb-dev` buckets under `sar/` prefix

### Files Modified This Session
- `internal/SAR/codes.js` — added SAR_GRAMMAR, SAR_BLE, SAR_I18N, sarText(), BLE pack/unpack
- `internal/SAR/dial.html` — 3-ring engine, BLE transport, flag chooser, standby resilience
- `internal/SAR/beacon.html` — standby resilience, §-logging
- `internal/SAR/listener.html` — standby resilience, adaptive polling, §-logging
- `internal/SAR/sw.js` — NEW, service worker for offline cache
- `internal/SAR/README.md` — full rewrite with current architecture

## Prior Art & Novelty

| System | Year | Composable? | Acoustic? | Phone-native? | Grammar? |
|--------|------|-------------|-----------|---------------|----------|
| Lexigrams | 1970s | Yes | No | No | Basic |
| Maritime flags | 1800s | Limited | No | No | No |
| Chinese telegraph | 1871 | No | Wire | No | No |
| Emoji | 2010s | Informal | No | Text only | No |
| Chirp.io | 2011 | No | Yes | SDK only | No |
| Google Nearby | 2017 | No | Yes (ultrasonic) | Android only | No |
| **HexTalk** | **2026** | **Yes** | **Yes (3 channels)** | **Any browser** | **Yes** |

Novel combination: composable grammar + acoustic FSK + zero infrastructure + visual dial + shared dictionary + auto-relay mesh. No prior system combines all of these.
