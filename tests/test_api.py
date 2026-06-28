from fastapi.testclient import TestClient
from backend.server import app

client = TestClient(app)


def test_health():
    r = client.get("/api/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


def test_get_state_returns_defaults():
    s = client.get("/api/state").json()
    assert s["subs"]["gain"] == 4.0
    assert s["subs"]["hpf"]["freq"] == 45
    assert s["subs"]["lpf"]["freq"] == 200
    assert s["mains"]["gain"] == 0.0
    assert s["mains"]["hpf"]["bypass"] is True
    assert s["mains"]["lpf"]["bypass"] is True
    assert s["mute"] is False


def test_mute_toggle():
    assert client.post("/api/mute", json={"value": True}).json()["mute"] is True
    assert client.post("/api/mute", json={"value": False}).json()["mute"] is False


def test_nowplaying_endpoint():
    r = client.get("/api/nowplaying")
    assert r.status_code == 200
    assert "available" in r.json()


def test_nowplaying_unknown_action_404():
    assert client.post("/api/nowplaying/frobnicate").status_code == 404


def test_master_gain_caps_at_minus_25():
    assert client.post("/api/master-gain", json={"value": 10.0}).json()["master_gain"] == -25.0
    assert client.post("/api/master-gain", json={"value": -100.0}).json()["master_gain"] == -60.0


def test_set_subs_gain():
    assert client.post("/api/subs/gain", json={"value": 6.5}).json()["subs"]["gain"] == 6.5


def test_subs_gain_clamps():
    assert client.post("/api/subs/gain", json={"value": 20.0}).json()["subs"]["gain"] == 12.0


def test_set_mains_gain():
    assert client.post("/api/mains/gain", json={"value": -3.0}).json()["mains"]["gain"] == -3.0


def test_set_subs_hpf_freq():
    assert client.post("/api/subs/hpf", json={"freq": 60}).json()["subs"]["hpf"]["freq"] == 60


def test_set_subs_hpf_bypass():
    assert client.post("/api/subs/hpf", json={"bypass": True}).json()["subs"]["hpf"]["bypass"] is True


def test_set_mains_lpf_freq():
    assert client.post("/api/mains/lpf", json={"freq": 150}).json()["mains"]["lpf"]["freq"] == 150


def test_overlap_guard_hpf_cant_exceed_lpf():
    client.post("/api/subs/lpf", json={"freq": 100})
    assert client.post("/api/subs/hpf", json={"freq": 95}).json()["subs"]["hpf"]["freq"] == 90


def test_overlap_guard_lpf_cant_go_below_hpf():
    client.post("/api/subs/hpf", json={"freq": 80})
    assert client.post("/api/subs/lpf", json={"freq": 85}).json()["subs"]["lpf"]["freq"] == 90


def test_groups_are_independent():
    client.post("/api/mains/hpf", json={"freq": 70, "bypass": False})
    client.post("/api/subs/hpf", json={"freq": 50, "bypass": False})
    s = client.get("/api/state").json()
    assert s["mains"]["hpf"]["freq"] == 70
    assert s["subs"]["hpf"]["freq"] == 50


def test_unknown_group_returns_404():
    assert client.post("/api/center/gain", json={"value": 0.0}).status_code == 404


def test_reset_preserves_master_resets_channels():
    client.post("/api/master-gain", json={"value": -30.0})
    client.post("/api/subs/gain", json={"value": 8.0})
    s = client.post("/api/reset").json()
    assert s["master_gain"] == -30.0      # preserved
    assert s["subs"]["gain"] == 4.0       # reset to default
    assert s["mains"]["hpf"]["bypass"] is True
