/* Glassbowl offline service worker — precache the two engine-as-data pages + Glassbowl's
   sql.js + bundle so they work with no network. Scoped to /BIMCompiler/; passes every
   other request straight through, so the rest of the docs site is unaffected.
   Bump CACHE_VERSION on any change to glassbowl.html / glassbowl_gravity.html / the bundle. */
const CACHE_VERSION = 'glassbowl-offline-v1';
const ASSETS = [
  'glassbowl.html',
  'glassbowl_gravity.html',
  'glassbowl_data.db',
  'sqljs/sql-wasm.js',
  'sqljs/sql-wasm.wasm'
];

self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE_VERSION).then(c => c.addAll(ASSETS)).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE_VERSION).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  const url = new URL(e.request.url);
  const mine = url.pathname.includes('/sqljs/') ||
    /\/(glassbowl|glassbowl_gravity|glassbowl_data)/.test(url.pathname);
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
