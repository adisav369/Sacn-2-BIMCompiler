// Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
// SPDX-License-Identifier: MIT
/* Glassbowl offline service worker — precache the two engine-as-data pages + Glassbowl's
   sql.js + bundle so they work with no network. Scoped to /BIMCompiler/; passes every
   other request straight through, so the rest of the docs site is unaffected.
   Bump CACHE_VERSION on any change to glassbowl.html / glassbowl_gravity.html / the bundle. */
const CACHE_VERSION = 'glassbowl-offline-v10';
const ASSETS = [
  'glassbowl.html',
  'glassbowl_gravity.html',
  'glassbowl_data.db',
  'help_overlay.js',
  'kernel_ops.js',
  'crud_overlay.js',
  'report_overlay.js',
  'help_ops.json',
  'crud_ops.json',
  'sqljs/sql-wasm.js',
  'sqljs/sql-wasm.wasm'
];

// install is INSTANT — no precache here (that's what caused upfront load lag).
self.addEventListener('install', () => self.skipWaiting());

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE_VERSION).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

// the page asks for the full precache LATER (on idle, a few seconds after load) — off the critical path,
// so the user feels no lag. Until then, the fetch handler below still caches whatever is actually used.
self.addEventListener('message', e => {
  if (e.data && e.data.type === 'precache') {
    e.waitUntil(caches.open(CACHE_VERSION).then(c => c.addAll(ASSETS)).catch(() => {}));
  }
  // W-SWUPDATE: the page's "tap to refresh" toast posts this so the PARKED (waiting) worker
  // takes over on demand — then the page's controllerchange handler does a single guarded reload.
  if (e.data && e.data.type === 'skipWaiting') self.skipWaiting();
});

self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  const url = new URL(e.request.url);
  const mine = url.pathname.includes('/sqljs/') ||
    /\/(glassbowl|glassbowl_gravity|glassbowl_data|help_overlay|kernel_ops|crud_overlay|report_overlay|help_ops|crud_ops)/.test(url.pathname);
  if (!mine) return; // not ours → let the network/site handle it normally
  // cache-first (offline-capable), then fill the cache on first network hit
  e.respondWith(
    caches.match(e.request).then(hit => hit || fetch(e.request).then(resp => {
      const clone = resp.clone();
      caches.open(CACHE_VERSION).then(c => c.put(e.request, clone));
      return resp;
    }).catch(() => caches.match(e.request)))
  );
});
