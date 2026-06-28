"""Quick Play — curated Apple Music links, played in the native app at full
quality via deep-link navigation + a UIAutomation "Play" invoke.

Presets are stored as JSON. Playback runs in a subprocess so the WinRT/COM
UIAutomation work never touches the FastAPI process.
"""
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

DEFAULT_PRESETS = [
    {
        "id": "random-access-memories",
        "name": "Random Access Memories",
        "subtitle": "Daft Punk",
        "url": "https://music.apple.com/us/album/random-access-memories/617154241",
    },
]


def _path() -> Path:
    return Path(
        os.environ.get("AUDIOCONTROL_PRESETS_FILE")
        or (Path(__file__).parent.parent / "presets.json")
    )


def _load() -> list:
    p = _path()
    if p.exists():
        try:
            return json.loads(p.read_text())
        except Exception:
            pass
    return [dict(x) for x in DEFAULT_PRESETS]


def _save(items: list):
    try:
        _path().write_text(json.dumps(items, indent=2))
    except Exception:
        pass


def _slugify(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-") or "preset"


def list_presets() -> list:
    return _load()


def add_preset(name: str, url: str, subtitle: str = "") -> list:
    items = _load()
    base = _slugify(name)
    pid, n = base, 2
    existing = {p.get("id") for p in items}
    while pid in existing:
        pid = f"{base}-{n}"
        n += 1
    items.append({"id": pid, "name": name, "subtitle": subtitle, "url": url})
    _save(items)
    return items


def delete_preset(pid: str) -> list:
    items = [p for p in _load() if p.get("id") != pid]
    _save(items)
    return items


def _to_deeplink(url: str) -> str:
    """Force the native app: https://music.apple.com/... -> musics://music.apple.com/..."""
    u = url.strip()
    if u.startswith("https://music.apple.com") or u.startswith("http://music.apple.com"):
        return "musics://" + u.split("://", 1)[1]
    return u


def _norm(s: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", s.lower()).strip()


def _get_foreground():
    try:
        import ctypes
        return ctypes.windll.user32.GetForegroundWindow()
    except Exception:
        return None


def _bring_front(hwnd):
    """Reliably foreground a window (tap Alt first to bypass the foreground lock)."""
    if not hwnd:
        return
    try:
        import ctypes
        u = ctypes.windll.user32
        u.keybd_event(0x12, 0, 0, 0)       # Alt down
        u.keybd_event(0x12, 0, 0x0002, 0)  # Alt up
        u.ShowWindow(hwnd, 9)              # SW_RESTORE
        u.SetForegroundWindow(hwnd)
    except Exception:
        pass


def play(url: str, title: str = None) -> dict:
    deeplink = _to_deeplink(url)
    args = [sys.executable, os.path.abspath(__file__), "--play", deeplink]
    if title:
        args.append(title)
    try:
        r = subprocess.run(args, capture_output=True, text=True, timeout=20)
        return {"ok": r.returncode == 0, "detail": (r.stdout or r.stderr).strip()[-300:]}
    except Exception as e:
        return {"ok": False, "detail": str(e)}


def _play_native(url: str, title: str = None) -> int:
    """Navigate via deep link, then start playback.

    For a specific track, double-click its row (Invoke only selects). The match
    target is the title hint (the preset name) if given, else the /song/ slug;
    this makes the common ?i=<id> link form work too (it has no slug). Album/
    playlist links invoke the page's main Play button, which is also the
    fallback if the track row can't be matched.
    """
    import uiautomation as auto

    prev = _get_foreground()
    os.startfile(url)
    win = auto.WindowControl(searchDepth=1, RegexName="Apple Music")
    if not win.Exists(8, 0.5):
        print("Apple Music window not found")
        return 1
    _bring_front(win.NativeWindowHandle)

    is_song = ("/song/" in url) or bool(re.search(r"[?&]i=", url))
    target = None
    if title and title.strip():
        target = _norm(title)
    else:
        m = re.search(r"/song/([^/?]+)", url)
        if m:
            target = _norm(m.group(1))

    if is_song and target:
        deadline = time.time() + 8
        while time.time() < deadline:
            for c, _d in auto.WalkControl(win, maxDepth=12):
                if c.ControlTypeName == "ListItemControl" and c.Name:
                    nm = _norm(c.Name)
                    if target in nm and ("track" in nm or "minute" in nm):
                        c.DoubleClick(simulateMove=False)
                        _bring_front(prev)
                        print("played track:", c.Name)
                        return 0
            time.sleep(0.4)
        print("track row not found; falling back to Play")

    # Album/playlist (or fallback): invoke the page's main Play button.
    deadline = time.time() + 8
    while time.time() < deadline:
        btn = win.ButtonControl(Name="Play")
        if btn.Exists(0, 0):
            try:
                btn.GetInvokePattern().Invoke()
                _bring_front(prev)
                print("invoked Play")
                return 0
            except Exception as e:
                _bring_front(prev)
                print("invoke failed:", e)
                return 1
        time.sleep(0.4)
    _bring_front(prev)
    print("no Play button found (already playing this item?)")
    return 0


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "--play":
        _title = sys.argv[3] if len(sys.argv) >= 4 else None
        sys.exit(_play_native(sys.argv[2], _title))
