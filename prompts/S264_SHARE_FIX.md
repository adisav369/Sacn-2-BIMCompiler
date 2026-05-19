# ⚠ DO NOT REMOVE — Scope: S264 Share Sheet Fix
# Read the log after every run. No inventions.

# S264: Fix Share — System Sheet + Camera/Element/Clash State

## Problem

Share is broken in three ways:

1. **WhatsApp-only:** Uses hardcoded `https://wa.me/?text=...` instead of `navigator.share()`. User sees WhatsApp instead of system share sheet (FB, Email, Telegram, SMS, etc.)
2. **No scene state in URL:** Share URL is just `?db=filename.db`. Camera position, picked element, and clash state are NOT captured. Recipient sees default camera.
3. **Tour not shareable:** Cinematic walkthrough state is not in the URL.

## Context

The viewer already parses camera state from URL hash — this works in the address bar:
```
index.html?db=...#bld=TerminalMerged&cx=75&cy=79&cz=52&tx=0&ty=-0&tz=0
```

But `share.js` doesn't construct this hash when generating the share URL.

Clash snag links already deep-link with element GUIDs (see `measure.js` snag QR). The pattern exists — share.js just doesn't use it.

## Task 1: Use navigator.share() with Fallback

Replace WhatsApp/Email hardcode with system share sheet:

```js
async function shareUrl(url, title) {
    if (navigator.share) {
        try {
            await navigator.share({ title: title, url: url });
            console.log('§SHARE native ok');
        } catch(e) {
            if (e.name !== 'AbortError') console.warn('§SHARE native fail', e);
        }
    } else {
        // Desktop fallback: copy to clipboard + show toast
        navigator.clipboard.writeText(url);
        console.log('§SHARE clipboard fallback');
    }
}
```

`navigator.share()` opens the system share sheet on both mobile (WhatsApp, Telegram, FB, SMS, Email, etc.) and desktop (if supported). Fallback = clipboard copy.

**Precedent:** `excel.js` (S209, commit 30f6804c) already uses `navigator.share()` with `navigator.canShare()` guard for Excel file export on mobile. Reuse the same pattern for URL sharing.

**Remove** the hardcoded `sendWhatsApp()` and `sendEmail()` functions. The system share sheet already includes both.

## Task 2: Capture Scene State in Share URL

Build the share URL with camera + element + clash state from the current hash:

```js
function buildShareUrl() {
    var base = A._shareViewerBase || location.origin + location.pathname;
    var dbParam = '?db=' + encodeURIComponent(A._shareDbUrl || '');

    // Camera state
    var cam = A.camera.position;
    var tgt = A.controls ? A.controls.target : cam;
    var hash = '#bld=' + (A.activeBuilding || '') +
        '&cx=' + cam.x.toFixed(0) + '&cy=' + cam.y.toFixed(0) + '&cz=' + cam.z.toFixed(0) +
        '&tx=' + tgt.x.toFixed(0) + '&ty=' + tgt.y.toFixed(0) + '&tz=' + tgt.z.toFixed(0);

    // Picked element (if any)
    if (A.pickedGuid) hash += '&pick=' + A.pickedGuid;

    // Active storey/disc filter
    if (A.activeStoreyFilter) hash += '&storey=' + encodeURIComponent(A.activeStoreyFilter);

    // X-ray state
    if (A.xrayOn) hash += '&xray=1';

    // Time Machine cursor (if active)
    if (A._tmActive && A._tmCursor) hash += '&tm=' + A._tmCursor;

    return base + dbParam + hash;
}
```

The viewer already parses `cx/cy/cz/tx/ty/tz` from the hash on load. Adding `pick`, `storey`, `xray`, `tm` requires small additions to the hash parser in `main.js` or `streaming.js`.

## Task 3: Tour Share Link

When cinematic tour is playing, share should include `&tour=play` in the hash:

```
index.html?db=...#bld=LTU_AHouse&tour=play
```

On load, if `tour=play` is in the hash, auto-start the cinematic tour after streaming completes. The tour is deterministic (generated from building storeys) so no need to encode waypoints — just the trigger.

## Task 4: Share Button Variants

The share sheet should offer context-aware share:

| Context | What's Shared | Hash |
|---|---|---|
| Default (orbiting) | Camera angle + building | `#bld=X&cx=...` |
| Element picked | Camera + highlighted element | `#bld=X&cx=...&pick=GUID` |
| Clash selected | Camera + clash pair | `#bld=X&cx=...&clash=GUID1,GUID2` |
| Tour playing | Building + auto-play tour | `#bld=X&tour=play` |
| Storey filtered | Camera + active filter | `#bld=X&cx=...&storey=Level%201` |

## Verification

**§-tagged logs:**
- `§SHARE_URL state=camera|pick|clash|tour url=...` — what state was captured
- `§SHARE_METHOD native|clipboard|whatsapp|email` — which share path used
- `§SHARE_PARSE pick=GUID storey=X tour=Y` — on load, what state was restored from hash

**Test on mobile:** Share should open system share sheet showing all available apps.
**Test on desktop:** Share should copy to clipboard (or show system dialog on macOS/Windows if supported).

## Files to Modify

| File | Change |
|---|---|
| `deploy/dev/share.js` | Replace WhatsApp/Email with `navigator.share()`, build URL with scene state |
| `deploy/dev/main.js` or `streaming.js` | Parse `pick`, `storey`, `xray`, `tm`, `tour` from URL hash on load |
| `deploy/dev/time_machine.js` | Export `_tmActive` and `_tmCursor` for share URL construction |
| `deploy/dev/index.html` | Update share button if UI changes needed |

## Priority

Task 1 (navigator.share) + Task 2 (camera state) first — immediate impact.
Task 3 (tour) and Task 4 (context variants) follow.
