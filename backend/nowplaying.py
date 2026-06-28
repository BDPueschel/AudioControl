"""System Media Transport Controls (SMTC) bridge.

Reads and controls whatever app owns the current Windows media session
(Apple Music, etc.). Controlling the desktop app means full-quality playback
is preserved — unlike a browser/MusicKit player.

Degrades gracefully: if the WinRT packages or a Windows session aren't
available, endpoints still return a well-formed payload with available=False.
"""
import asyncio
import base64

try:
    from winrt.windows.media.control import (
        GlobalSystemMediaTransportControlsSessionManager as _Manager,
    )
    from winrt.windows.storage.streams import DataReader as _DataReader
    _AVAILABLE = True
except Exception:
    _AVAILABLE = False

_STATUS = {0: "closed", 1: "opened", 2: "changing", 3: "stopped", 4: "playing", 5: "paused"}
_ACTIONS = ("playpause", "play", "pause", "next", "previous")

# Album art is keyed by track so we only decode the thumbnail when it changes.
_art_cache = {"key": None, "data": None}


async def _current_session():
    mgr = await _Manager.request_async()
    return mgr.get_current_session()


async def _read_thumbnail(props):
    ref = props.thumbnail
    if ref is None:
        return None
    stream = await ref.open_read_async()
    size = stream.size
    if not size:
        return None
    reader = _DataReader(stream)
    await reader.load_async(size)
    data = bytes(reader.read_bytes(size))
    content_type = stream.content_type or "image/png"
    return "data:%s;base64,%s" % (content_type, base64.b64encode(data).decode())


async def _get_async():
    sess = await _current_session()
    if sess is None:
        return {"available": True, "playing_session": False}
    info = sess.get_playback_info()
    c = info.controls
    try:
        props = await sess.try_get_media_properties_async()
        title, artist, album = props.title, props.artist, props.album_title
    except Exception:
        props, title, artist, album = None, "", "", ""

    key = (artist, title, album)
    art = None
    if props is not None:
        if _art_cache["key"] == key:
            art = _art_cache["data"]
        else:
            try:
                art = await _read_thumbnail(props)
            except Exception:
                art = None
            _art_cache["key"], _art_cache["data"] = key, art

    return {
        "available": True,
        "playing_session": True,
        "app": sess.source_app_user_model_id,
        "title": title,
        "artist": artist,
        "album": album,
        "status": _STATUS.get(int(info.playback_status), "unknown"),
        "can_play": c.is_play_enabled,
        "can_pause": c.is_pause_enabled,
        "can_next": c.is_next_enabled,
        "can_prev": c.is_previous_enabled,
        "art": art,
    }


async def _action_async(action):
    sess = await _current_session()
    if sess is not None:
        if action == "playpause":
            await sess.try_toggle_play_pause_async()
        elif action == "play":
            await sess.try_play_async()
        elif action == "pause":
            await sess.try_pause_async()
        elif action == "next":
            await sess.try_skip_next_async()
        elif action == "previous":
            await sess.try_skip_previous_async()
    return await _get_async()


def available() -> bool:
    return _AVAILABLE


def get() -> dict:
    if not _AVAILABLE:
        return {"available": False, "playing_session": False}
    try:
        return asyncio.run(_get_async())
    except Exception as e:
        return {"available": True, "playing_session": False, "error": str(e)}


def action(name: str) -> dict:
    if not _AVAILABLE:
        return {"available": False, "playing_session": False}
    try:
        return asyncio.run(_action_async(name))
    except Exception as e:
        return {"available": True, "playing_session": False, "error": str(e)}
