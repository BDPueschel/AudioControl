# Android APK plan

Goal: make the tablet and phone feel like **first-class citizens** — an
installable Android app, not just a browser bookmark pointed at the panel.

The panel is already a single-file web app (`frontend/index.html`) served by
FastAPI on the laptop. So the fastest path to an APK is to **wrap the existing
web UI**, not rebuild it natively.

## Approaches (fastest → most work)

1. **Trusted Web Activity (TWA) / PWA** — *recommended first cut.*
   Add a web app manifest + service worker to make the panel a PWA, then wrap
   it with [Bubblewrap](https://github.com/GoogleChromeLabs/bubblewrap) to emit
   a signed APK that launches full-screen with no browser chrome. Lightest lift;
   reuses 100% of the current UI. Caveat: a TWA expects an HTTPS origin and
   Digital Asset Links — over a plain-HTTP tailnet host this may need a plain
   WebView wrapper instead (see #2).

2. **Capacitor / WebView wrapper** — wrap `index.html` in a native shell
   (Capacitor or a bare `WebView`). Works fine over HTTP on the tailnet, gives
   us a real APK + app icon + splash, and leaves room to add native bits later
   (haptics, keep-awake, status bar styling). Slightly more setup than #1.

3. **Fully native UI** — biggest lift, only worth it if the WebView ever feels
   laggy on the slider drags. Not needed to start.

**Decision for the mac mini session:** start with #2 (Capacitor WebView) unless
we decide to stand up HTTPS on the panel, in which case #1 (TWA/PWA) is cleaner.

## Things to wire in regardless of approach

- **Server discovery.** The panel is reached at the Tailscale hostname
  (`poolroom-syn.taildbeee4.ts.net:8080`). Don't hardcode it in a way that
  breaks off-tailnet — bake it in *and* expose a settings field to point the
  app at a different host:port.
- **Connection state UX.** When the laptop/server is down (it runs headless),
  the app should show a clear "can't reach panel" state with a retry, not a
  blank WebView.
- **Keep-awake / orientation.** For a wall-mounted tablet, prevent sleep and
  consider locking orientation.
- **Safety cap is server-side.** Master gain is clamped to −20 dB in the
  backend, so the native shell doesn't need to re-enforce it — but the slider
  range in the UI already matches.

## Build env (mac mini)

Need Android SDK + JDK + (Node for Capacitor/Bubblewrap). `git pull` first —
the panel and backend are already committed; this doc is the starting point.
