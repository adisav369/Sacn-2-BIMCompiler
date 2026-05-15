// SAR Service Worker — offline-first cache
const CACHE = 'sar-v1';
const ASSETS = [
    './',
    'beacon.html',
    'listener.html',
    'dial.html',
    'codes.js',
];

self.addEventListener('install', e => {
    e.waitUntil(caches.open(CACHE).then(c => c.addAll(ASSETS)));
    self.skipWaiting();
});

self.addEventListener('activate', e => {
    e.waitUntil(caches.keys().then(keys =>
        Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))
    ));
    self.clients.claim();
});

// Cache-first: serve from cache, fall back to network, cache new fetches
self.addEventListener('fetch', e => {
    e.respondWith(
        caches.match(e.request).then(cached => {
            if (cached) return cached;
            return fetch(e.request).then(resp => {
                if (resp.ok && e.request.url.startsWith(self.location.origin)) {
                    const clone = resp.clone();
                    caches.open(CACHE).then(c => c.put(e.request, clone));
                }
                return resp;
            });
        }).catch(() => new Response('Offline — no cached version', { status: 503 }))
    );
});
