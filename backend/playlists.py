"""Imported Apple Music playlists.

Scrapes a playlist's tracks from the native app via UIAutomation (no dev
account, no per-track URLs) and plays any track by opening the playlist and
double-clicking its row. Storage is JSON; UIAutomation work runs in subprocesses
so COM never touches the FastAPI process.
"""
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path


def _path() -> Path:
    return Path(
        os.environ.get("AUDIOCONTROL_PLAYLISTS_FILE")
        or (Path(__file__).parent.parent / "playlists.json")
    )


def _load() -> list:
    p = _path()
    if p.exists():
        try:
            return json.loads(p.read_text(encoding="utf-8"))
        except Exception:
            pass
    return []


def _save(items: list):
    try:
        _path().write_text(json.dumps(items, indent=2, ensure_ascii=False), encoding="utf-8")
    except Exception:
        pass


def list_playlists() -> list:
    return _load()


def import_playlist(name: str) -> dict:
    tracks = _scrape_subprocess(name)
    items = [p for p in _load() if p.get("name") != name]
    entry = {"name": name, "tracks": tracks}
    items.append(entry)
    _save(items)
    return {"name": name, "count": len(tracks)}


def delete_playlist(name: str) -> list:
    items = [p for p in _load() if p.get("name") != name]
    _save(items)
    return items


def play_track(playlist: str, title: str) -> dict:
    try:
        r = subprocess.run(
            [sys.executable, os.path.abspath(__file__), "--playtrack", playlist, title],
            capture_output=True, text=True, timeout=30,
        )
        return {"ok": r.returncode == 0, "detail": (r.stdout or r.stderr).strip()[-300:]}
    except Exception as e:
        return {"ok": False, "detail": str(e)}


def _scrape_subprocess(name: str) -> list:
    try:
        r = subprocess.run(
            [sys.executable, os.path.abspath(__file__), "--scrape", name],
            capture_output=True, text=True, timeout=240,
        )
        if r.returncode == 0 and r.stdout.strip():
            return json.loads(r.stdout)
    except Exception:
        pass
    return []


# --- UIAutomation workers (subprocess only) ----------------------------------

def _norm(s: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", s.lower()).strip()


def _open_playlist(auto, win, name):
    side = win.ListItemControl(Name=name)
    if side.Exists(5, 0.5):
        try:
            side.GetInvokePattern().Invoke()
        except Exception:
            side.DoubleClick(simulateMove=False)
        return True
    return False


def _track_rows(auto, win):
    return [c for c, _d in auto.WalkControl(win, maxDepth=12)
            if c.ControlTypeName == "ListItemControl" and c.Name and c.Name.startswith("Track ")]


def _row_fields(auto, row):
    fields = []
    for c, _d in auto.WalkControl(row, maxDepth=5):
        if c.ControlTypeName == "TextControl":
            nm = c.Name or ""
            if not nm or re.match(r"^\d+:\d+$", nm) or nm in ("Favorite", "More", "Favorited"):
                continue
            if any(ord(ch) > 0xFFFF for ch in nm):
                continue
            fields.append(nm)
    return (
        fields[0] if len(fields) > 0 else "",
        fields[1] if len(fields) > 1 else "",
        fields[2] if len(fields) > 2 else "",
    )


def _scrape_native(name: str) -> int:
    import uiautomation as auto

    win = auto.WindowControl(searchDepth=1, RegexName="Apple Music")
    if not win.Exists(8, 0.5):
        print("[]")
        return 1
    win.SetActive()
    _open_playlist(auto, win, name)
    time.sleep(3)

    seen, order, stable = set(), [], 0
    for _ in range(400):
        rows = _track_rows(auto, win)
        before = len(seen)
        last = None
        for r in rows:
            t, a, al = _row_fields(auto, r)
            if not t:
                continue
            key = (t.lower(), a.lower())
            if key not in seen:
                seen.add(key)
                order.append({"title": t, "artist": a, "album": al})
            last = r
        stable = stable + 1 if len(seen) == before else 0
        if stable >= 3:
            break
        if last is not None:
            try:
                last.GetScrollItemPattern().ScrollIntoView()
            except Exception:
                auto.SendKeys("{PageDown}")
        time.sleep(0.5)

    print(json.dumps(order, ensure_ascii=False))
    return 0


def _find_row(auto, win, target):
    """Return the visible track row whose TITLE field matches target, or None."""
    for r in _track_rows(auto, win):
        title, _a, _al = _row_fields(auto, r)
        if title and target in _norm(title):
            return r
    return None


def _title_element(auto, row):
    """The title's TextControl (left of the row) — the safe double-click target."""
    for c, _d in auto.WalkControl(row, maxDepth=5):
        if c.ControlTypeName == "TextControl":
            nm = c.Name or ""
            if not nm or re.match(r"^\d+:\d+$", nm) or nm in ("Favorite", "More", "Favorited"):
                continue
            if any(ord(ch) > 0xFFFF for ch in nm):
                continue
            return c  # first valid text in the row is the title
    return None


def _playtrack_native(name: str, title: str) -> int:
    import uiautomation as auto

    target = _norm(title)
    win = auto.WindowControl(searchDepth=1, RegexName="Apple Music")
    if not win.Exists(8, 0.5):
        print("no window")
        return 1
    win.SetActive()
    _open_playlist(auto, win, name)
    time.sleep(3)

    deadline = time.time() + 20
    while time.time() < deadline:
        row = _find_row(auto, win, target)
        if row is not None:
            # Bring it fully into view, then RE-FIND a fresh element and click
            # it. If scrolling pushed it out, loop again (never click a stale
            # element — that's what mis-fired onto the wrong track).
            try:
                row.GetScrollItemPattern().ScrollIntoView()
                time.sleep(0.4)
            except Exception:
                pass
            fresh = _find_row(auto, win, target)
            if fresh is not None:
                tel = _title_element(auto, fresh)
                el = tel or fresh
                try:
                    el.DoubleClick(simulateMove=False)
                    print("played:", tel.Name if tel else fresh.Name)
                    return 0
                except Exception as e:
                    print("click error:", e)
        else:
            rows = _track_rows(auto, win)
            if rows:
                try:
                    rows[-1].GetScrollItemPattern().ScrollIntoView()
                except Exception:
                    auto.SendKeys("{PageDown}")
        time.sleep(0.3)

    print("track not found:", title)
    return 2


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "--scrape":
        sys.exit(_scrape_native(sys.argv[2]))
    elif len(sys.argv) >= 4 and sys.argv[1] == "--playtrack":
        sys.exit(_playtrack_native(sys.argv[2], sys.argv[3]))
