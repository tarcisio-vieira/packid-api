const CACHE_NAME = "vsgi-shell-v2";
const OFFLINE_URL = new URL("offline.html", self.registration.scope).toString();

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.add(OFFLINE_URL)));
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (event) => {
  if (event.request.mode !== "navigate") return;

  event.respondWith((async () => {
    try {
      const response = await fetch(event.request);
      if (response.status === 404 || response.status >= 500) {
        return (await caches.match(OFFLINE_URL)) || response;
      }
      return response;
    } catch {
      return (await caches.match(OFFLINE_URL)) || Response.error();
    }
  })());
});
