/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
// sw.js — Service Worker for offline support (S232, S239 cache versioning)
// Network-first for .html/.js (always fresh on deploy).
// Cache-first for heavy assets (.wasm, images). DB files skip SW (IndexedDB handles them).
//
// DEPLOY: bump CACHE_VERSION on every OCI upload. Old caches are purged on activate.
const CACHE_VERSION = 'v269';
const CACHE_NAME = 'bim-ootb-' + CACHE_VERSION;

// Local copies of vendor libs — single-origin, no CDN dependency
const LOCAL_LIBS = [
  'lib/three.min.js',
  'lib/OrbitControls.js',
  'lib/sql-wasm.js',
  'lib/sql-wasm.wasm',
  'lib/xlsx.full.min.js',
];

// CDN fallback URLs — cached opportunistically if loader falls back to them
const CDN_ASSETS = [
  'https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js',
  'https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js',
  'https://cdn.jsdelivr.net/npm/rtree-sql.js@1.7.0/dist/sql-wasm.js',
  'https://cdn.jsdelivr.net/npm/rtree-sql.js@1.7.0/dist/sql-wasm.wasm',
  'https://cdn.sheetjs.com/xlsx-0.20.3/package/dist/xlsx.full.min.js',
];

// Local files to precache on install — viewer works fully offline after first visit.
// DB files are NOT here — they're cached in IndexedDB by A.cachedFetch().
const PRECACHE_ASSETS = [
  // Entry points
  'index.html',
  'boq_charts.html',
  'mep_report.html',
  '2d.html',
  'offline.html',
  'manifest.webmanifest',
  // Core viewer modules (order matches index.html script tags)
  'config.js',
  'helpers.js',
  'loader.js',
  'scene.js',
  'streaming.js',
  'panels.js',
  'tools.js',
  'picking.js',
  'tour.js',
  'measure.js',
  'sitecam.js',
  'issues.js',
  'excel.js',
  'walk.js',
  'city.js',
  'rates.js',
  'locale_loader.js',
  'nlp.js',
  'semantic_enrichment.js',
  'scene_to_db.js',
  'import_db_builder.js',
  'diff.js',
  'variation_order.js',
  'import.js',
  'main.js',
  // Workers (fetched on demand by import/export flows)
  'import_worker.js',
  'ifc_export_worker.js',
  'mesh_import_worker.js',
  // Lazy-loaded modules
  'navigate.js',
  'wizard.js',
  'section_cut.js',
  'dxf-parser.js',
  'dxf_export.js',
  'elevation.js',
  'grid_dims.js',
  'title_block.js',
  // Config files
  'clash_rules.json',
  'rates/cidb2024_my.json',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache =>
      cache.addAll([...PRECACHE_ASSETS, ...LOCAL_LIBS])
    )
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  // Purge ALL caches that don't match current CACHE_VERSION
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

// Returns true for URLs that should use network-first strategy
function isNetworkFirst(url) {
  // Strip ?v=N query string before checking extension — HTML uses ?v= cache busters
  var base = url.split('?')[0];
  // Local .html and .js files change on every deploy — always try network first
  if (base.endsWith('.html') || base.endsWith('.js')) {
    // lib/ files are versioned and immutable — keep them cache-first
    if (base.includes('/lib/')) return false;
    // CDN fallback assets are also immutable — keep them cache-first
    for (const cdn of CDN_ASSETS) {
      if (url === cdn || base === cdn) return false;
    }
    return true;
  }
  // Navigation requests (e.g. bare URL without extension)
  return false;
}

self.addEventListener('fetch', (event) => {
  const url = event.request.url;

  // Skip non-GET requests
  if (event.request.method !== 'GET') return;

  // Skip DB file fetches — handled by IndexedDB in cachedFetch()
  if (url.split('?')[0].endsWith('.db')) return;

  // Navigation requests always network-first
  if (event.request.mode === 'navigate') {
    event.respondWith(networkFirst(event.request));
    return;
  }

  // Network-first for local .html and .js — always get fresh on deploy
  if (isNetworkFirst(url)) {
    event.respondWith(networkFirst(event.request));
    return;
  }

  // Cache-first for CDN libs, .wasm, images, CSS — these are immutable or change rarely
  event.respondWith(cacheFirst(event.request));
});

// Try network, fall back to cache (for files that change on deploy)
function networkFirst(request) {
  // Strip ?v=N query string for cache matching — HTML references main.js?v=11
  // but precache stores main.js. Both should match.
  var cacheUrl = request.url.split('?')[0];
  return fetch(request)
    .then(resp => {
      if (resp && resp.status === 200) {
        const clone = resp.clone();
        caches.open(CACHE_NAME).then(c => c.put(cacheUrl, clone));
      }
      return resp;
    })
    .catch(() => caches.match(cacheUrl).then(r => {
      if (r) return r;
      // Only return offline.html for navigation/HTML requests — not for .js
      if (request.url.endsWith('.js')) return new Response('', { status: 503 });
      return caches.match('offline.html');
    }));
}

// Try cache, fall back to network (for heavy/immutable assets)
function cacheFirst(request) {
  return caches.match(request).then(cached => {
    if (cached) return cached;
    return fetch(request).then(resp => {
      if (!resp || resp.status !== 200) return resp;
      const clone = resp.clone();
      caches.open(CACHE_NAME).then(c => c.put(request, clone));
      return resp;
    });
  });
}
