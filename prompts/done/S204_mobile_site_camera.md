# S204 — Mobile Site Camera + WhatsApp Integration

## Goal
Turn the mobile BIM OOTB viewer into a **site inspection tool**. Site workers can:
1. View the 3D model on their phone at the construction site
2. Snap a photo of what they're looking at (using the phone's camera)
3. Photo is auto-tagged with GPS coordinates, timestamp, and the currently selected BIM element
4. Send directly to a WhatsApp account (project manager / site supervisor)

## Features

### 1. Camera Snap Button
- New camera icon in the top HUD panel (always visible on mobile)
- Taps opens the phone's rear camera via `navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })`
- Overlay: semi-transparent BIM element info (class, GUID, storey) on the camera preview
- Snap button captures the frame as JPEG

### 2. GPS Tagging
- On snap, read device GPS via `navigator.geolocation.getCurrentPosition()`
- Embed in the photo metadata:
  - Latitude, longitude, accuracy
  - Altitude (if available)
  - Heading / compass bearing (via `DeviceOrientationEvent`)
- Display GPS coordinates on the photo overlay (bottom-left corner)
- Format: `4.5832°N, 103.7714°E` (decimal degrees)

### 3. Timestamp
- Embed capture time in the photo overlay (bottom-right corner)
- Format: `2026-04-20 14:32:05 +0800`
- Also written into EXIF if using canvas-based capture

### 4. BIM Context Overlay
- If an element is selected in the viewer, stamp onto the photo:
  - IFC class (e.g. IfcWall)
  - Element name
  - GUID
  - Building + storey
  - Discipline
- Top of photo, semi-transparent black bar with white text

### 5. WhatsApp Share
- After snap, show preview with all overlays
- "Send to WhatsApp" button uses the Web Share API:
  ```javascript
  navigator.share({
    files: [new File([blob], 'site_photo.jpg', { type: 'image/jpeg' })],
    title: `BIM Site Photo — ${ifcClass} @ ${storey}`,
    text: `${building} / ${storey} / ${ifcClass}\nGPS: ${lat}, ${lng}\n${timestamp}`
  });
  ```
- Falls back to `wa.me` URL scheme if Web Share API unavailable:
  ```
  https://wa.me/?text=BIM+Site+Photo...
  ```
  (text-only fallback — photo must be manually attached)

### 6. Photo Log (Optional)
- Store snapped photos in IndexedDB (`bim_ootb_photos`)
- Each entry: { jpeg_blob, gps, timestamp, element_guid, building, storey }
- Gallery view accessible from HUD panel
- Export as ZIP with metadata CSV

## Technical Notes

### Permissions Required
- Camera: `getUserMedia()` — requires HTTPS (OCI serves over HTTPS)
- GPS: `geolocation` — user prompt on first use
- Compass: `DeviceOrientationEvent` — iOS requires explicit permission request

### Browser Compatibility
| Feature | Chrome Android | Safari iOS | Notes |
|---------|---------------|------------|-------|
| getUserMedia | Yes | Yes (14.3+) | Requires HTTPS |
| Geolocation | Yes | Yes | User prompt |
| Web Share API | Yes | Yes (15+) | Files support varies |
| DeviceOrientation | Yes | Yes (13+) | iOS needs permission click |

### Privacy
- No data sent to any server — all processing is local
- GPS/camera only activated on user tap (never background)
- WhatsApp share is device-to-device (no intermediate server)
- Photo log stays in browser IndexedDB (same as building cache)

## Implementation Plan

1. Add camera icon to HUD (mobile only — `@media max-width: 600px`)
2. Camera preview overlay (full-screen, semi-transparent BIM info)
3. GPS + timestamp capture on snap
4. Canvas composite: photo + overlays → JPEG blob
5. Web Share API → WhatsApp
6. Photo log in IndexedDB (stretch goal)

## Why This Matters

Site supervisors currently:
- Take a photo on their phone
- Open a separate BIM viewer on desktop to find the element
- Manually type the element reference into WhatsApp/email
- No GPS proof of location, no timestamp proof of inspection

With this feature: one tap captures photo + BIM context + GPS + time, sends directly.
**Zero friction site inspection reporting.**

## DO — Testing & Logging

All test output to `deploy/dev/tests/log/`.

### Playwright — `03-walk-sitecam-cycle.spec.js` (existing, extend)

Current state: tests 3.3-3.7 call `openSiteCamera()`/`closeSiteCamera()` via JS on desktop.
Tests pass but exercise DOM state only — no camera/GPS (headless limitation).

**Gaps to fill in a dedicated session:**

| Test | What | §-tag |
|------|------|-------|
| 3.3+ | openSiteCamera hides toolbar, closeSiteCamera restores | `§PW_WALK_TO_CAM` — DONE |
| NEW | Sitecam watermark text present during camera | `§PW_SITECAM_WATERMARK` |
| NEW | Markup buttons (Arrow/Circle/Draw/Text) visible during camera | `§PW_SITECAM_MARKUP` |
| NEW | GPS status element updates (acquiring → lat/lon or unavailable) | `§PW_SITECAM_GPS` |
| NEW | Snap produces canvas composite (check canvas exists, non-zero size) | `§PW_SITECAM_SNAP` |
| NEW | Share button triggers Web Share API or WhatsApp link | `§PW_SITECAM_SHARE` |

Camera/GPS require real hardware — mock via `page.evaluate()`:
```javascript
// Mock GPS for headless
await page.evaluate(() => {
  navigator.geolocation.getCurrentPosition = (cb) =>
    cb({ coords: { latitude: 3.139, longitude: 101.687, accuracy: 10 } });
});
```

### test_all.js — extend §9 or add §18
```javascript
// Verify sitecam.js hides toolbar elements and restores them
ok('sitecam: watermark text', sitecamJs.includes('Site Inspection') || sitecamJs.includes('_TRL'));
ok('sitecam: markup buttons wired', sitecamJs.includes('markup') || sitecamJs.includes('Arrow'));
ok('sitecam: GPS status update', sitecamJs.includes('GPS') && sitecamJs.includes('acquiring'));
```

## DO NOT
- Do not test camera/GPS visually — mock in Playwright, assert DOM state
- Do not modify `deploy/sandbox/` — production
