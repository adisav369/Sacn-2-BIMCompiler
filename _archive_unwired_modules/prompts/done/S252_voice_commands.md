# ⚠ DO NOT REMOVE — S252 Voice Commands (Mobile)
# Scope: nlp.js only — no other files unless wiring a new command
# Read the log after every run. Exit code is not evidence.

---

## S252 — Voice-Activated Commands for Mobile

### Problem
Desktop users have keyboard shortcuts (G, X, C, SC, SU, ?, Tab, arrows).
Mobile users have none — they must tap through toolbar buttons to find commands.
The 🎤 mic button already exists (nlp.js, Web Speech API, browser-native, zero cost).
It currently handles NLP queries ("count doors", "show structure").
It does NOT handle viewer commands ("screenshot", "section cut", "clash matrix").

### Concept
Voice-driven command palette — visual + spoken, not fire-and-forget.
User sees what was recognized, navigates with voice, confirms explicitly.

```
Tap 🎤 → mic ON, NLP bar shows
  ↓
User speaks "screenshot" → transcript appears in search box
  ↓
Filtered command list appears below (like ? palette)
  ↓
User says "down" / "up" → cursor moves in list
  ↓
User says "yes" → highlighted command executes, mic OFF
  ↓
(or timeout 5s silence → mic OFF for safety)
```

**Safety:** mic auto-off after confirm ("yes") or timeout. No accidental commands.

Mobile equivalent of the `?` command palette:
- Desktop: press `?` → type → Enter
- Mobile: tap 🎤 → speak → see results → "yes" → runs

---

## Spec §1 — Command Intent Map

Add a command intent layer BEFORE the existing NLP query parser.
If speech matches a command intent, run it immediately. Otherwise fall through
to the existing `executeQuery()` for data queries.

```js
var _voiceCommands = {
  // key: array of trigger phrases (fuzzy matched)
  'screenshot':    ['screenshot', 'take screenshot', 'screen capture', 'capture'],
  'xray':          ['x-ray', 'sunglasses', 'glass mode', 'transparent'],
  'section':       ['section cut', 'section', 'cut'],
  'grid':          ['2d grid', 'floor plan', '2d plan', 'grid view', '2d'],
  'clash':         ['clash matrix', 'clash', 'clash detection'],
  'measure':       ['measure', 'distance', 'ruler'],
  'find':          ['find', 'search', 'navigate'],
  'analytics':     ['analytics', '4d', '5d', 'cost', 'quantity'],
  'fullscreen':    ['fullscreen', 'full screen'],
  'help':          ['help', 'commands', 'what can I say']
};

var _voiceActions = {
  'screenshot':  function() { if (A.screenshot) A.screenshot(); },
  'xray':        function() { A.toggleXray(); },
  'section':     function() { var b = document.getElementById('section-btn'); if (b) b.click(); },
  'grid':        function() { if (typeof window.open2DPlans === 'function') window.open2DPlans(); },
  'clash':       function() { if (A._loadClashRules) A._loadClashRules(function(r) { A._showClashMatrix(r, document.body); }); },
  'measure':     function() { if (A.toggleMeasure) A.toggleMeasure(); },
  'find':        function() { if (A.openFindPanel) A.openFindPanel(''); },
  'analytics':   function() { if (A.export4D5D) A.export4D5D(); },
  'fullscreen':  function() { A.toggleFullscreen(); },
  'help':        function() { _showVoiceHelp(); }
};
```

### §1.1 — Voice Navigation Meta-Commands

These words control the palette, not the viewer:

| Spoken | Action |
|--------|--------|
| "down" / "next" | Move cursor down in filtered list |
| "up" / "previous" | Move cursor up |
| "yes" / "go" / "ok" | Execute highlighted command, mic OFF |
| "cancel" / "stop" | Close palette, mic OFF |
| "help" / "what can I say" | Show all available commands |

Meta-commands are checked FIRST, before command matching.

### §1.2 — Matching logic

```js
function matchVoiceCommand(transcript) {
  var t = transcript.toLowerCase().trim();
  var bestCmd = null, bestScore = 0;
  Object.keys(_voiceCommands).forEach(function(cmd) {
    _voiceCommands[cmd].forEach(function(phrase) {
      if (t.indexOf(phrase) >= 0) {
        // Longer phrase = better match (avoid "cut" matching "section cut")
        if (phrase.length > bestScore) {
          bestScore = phrase.length;
          bestCmd = cmd;
        }
      }
    });
  });
  return bestCmd;
}
```

Insert in the existing voice result handler:
```js
// BEFORE existing executeQuery call:
var voiceCmd = matchVoiceCommand(transcript);
if (voiceCmd) {
  console.log('§VOICE_CMD cmd=' + voiceCmd + ' transcript=' + transcript);
  _voiceActions[voiceCmd]();
  A.status.textContent = '🎤 ' + voiceCmd;
  return;
}
// ELSE: fall through to existing NLP query pipeline
```

---

## Spec §2 — Voice Help ("What can I say?")

When user says "help" or "what can I say", show a toast listing available voice commands.
Reuse the existing `_showToast()` pattern from nlp.js.

```
┌─────────────────────────────────┐
│  🎤 Voice Commands              │
│                                 │
│  "Screenshot"                   │
│  "Section cut"                  │
│  "2D plan"                      │
│  "Clash matrix"                 │
│  "Measure"                      │
│  "Find"                         │
│  "X-ray"                        │
│  "Analytics"                    │
│  "Fullscreen"                   │
│  "Help"                         │
│                                 │
│  Plus any data query:           │
│  "count doors", "total cost"... │
└─────────────────────────────────┘
```

Auto-dismiss after 5 seconds or tap.

---

## Spec §3 — Voice Feedback

After a command runs, briefly flash the status bar with `🎤 screenshot` / `🎤 clash` etc.
Use existing `A.status.textContent` pattern — no new DOM elements.

For failed recognition (no match), show: `🎤 "..." — try "help" for commands`

---

## Spec §4 — Mobile Guard (reverse of S251)

S251 keyboard shortcuts skip mobile (`if (_isMobile) return`).
Voice commands work on BOTH mobile and desktop — no platform guard.
Web Speech API is available on Chrome Android, Safari iOS, Chrome desktop.
Feature detection already in place: `HAS_VOICE` (nlp.js line 13).

---

## Spec §5 — Shared Command Registry

The command actions in `_voiceActions` are the SAME functions as `_shortcuts` in scene.js.
To avoid duplication, voice commands should call the same global functions:
- `window.open2DPlans()` — already global
- `window.toggleMeasure()` — already global
- `A.screenshot()` — on APP object
- etc.

No new wiring needed — just call the existing globals.

---

## Spec §6 — NLP Chip Update

Add voice command examples to the example chips row (nlp.js line 498):
```js
const EXAMPLES = ['count doors', 'floor 1 walls', 'total cost',
                  'screenshot', 'clash matrix', 'section cut'];
```

---

## Spec §7 — §-log Witnesses

| Tag | When |
|-----|------|
| `§VOICE_CMD cmd=X transcript=Y` | Voice command matched and executed |
| `§VOICE_QUERY transcript=Y` | No command match, fell through to NLP query |
| `§VOICE_HELP shown` | Voice help toast displayed |

---

## Spec §8 — Files to Change

| File | Change |
|------|--------|
| `deploy/dev/nlp.js` | Add `_voiceCommands` map, `matchVoiceCommand()`, insert before `executeQuery()` |

ONE file only. All command functions already exist as globals.

---

## Spec §9 — Accessibility Symmetry

| Platform | Discovery | Execution |
|----------|-----------|-----------|
| Desktop  | Press `?` → command palette | Type shortcut key (SC, G, etc.) |
| Mobile   | Tap 🎤 → say "help" | Speak command ("screenshot") |
| Both     | 🛟 button → command palette | Click/tap command in palette |

Three paths, same commands, zero duplication.

---

## What NOT to do

- Do NOT add a server or AI API — Web Speech API is browser-native, free, offline-capable
- Do NOT remove existing NLP query capability — commands are added BEFORE, queries fall through
- Do NOT add new JS files — everything goes in nlp.js
- Do NOT change the mic button — it already works, just extend what happens with the result
- Do NOT add keyboard shortcuts for mobile — that's S251's domain and excluded mobile on purpose
