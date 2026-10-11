// Wizi service worker.
// NETWORK-FIRST: always try the server for the newest files, and only fall
// back to the cache when offline. (The old version was cache-first, which
// kept serving an outdated app.js even after updates.)

const CACHE_NAME = "wizi-shell-v6";
const SHELL_FILES = [
  "./",
  "./index.html",
  "./style.css",
  "./app.js",
  "./commands.js",
  "./memory.js",
  "./manifest.json",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL_FILES)).catch(() => {})
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  const url = new URL(req.url);

  // Only handle same-origin GETs of app files. API calls (chat, summarize,
  // health, api, websocket) go straight to the network, never cached.
  if (req.method !== "GET" || url.origin !== self.location.origin) return;
  if (/^\/(chat|summarize|health|api|ws)/.test(url.pathname)) return;

  event.respondWith(
    fetch(req)
      .then((res) => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(CACHE_NAME).then((c) => c.put(req, copy));
        }
        return res;
      })
      .catch(() => caches.match(req).then((hit) => hit || caches.match("./index.html")))
  );
});
