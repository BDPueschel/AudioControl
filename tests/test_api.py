import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'backend'))

from fastapi.testclient import TestClient
from server import app

client = TestClient(app)


def test_health():
    r = client.get("/api/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


def test_get_state_returns_defaults():
    r = client.get("/api/state")
    assert r.status_code == 200
    s = r.json()
    assert s["sub_gain"] == 4.0
    assert s["hpf"]["freq"] == 45
    assert s["lpf"]["freq"] == 200


def test_set_master_gain():
    r = client.post("/api/master-gain", json={"value": -25.0})
    assert r.status_code == 200
    assert r.json()["master_gain"] == -25.0


def test_master_gain_clamps():
    r = client.post("/api/master-gain", json={"value": 10.0})
    assert r.json()["master_gain"] == 0.0
    r = client.post("/api/master-gain", json={"value": -100.0})
    assert r.json()["master_gain"] == -60.0


def test_set_sub_gain():
    r = client.post("/api/sub-gain", json={"value": 6.5})
    assert r.status_code == 200
    assert r.json()["sub_gain"] == 6.5


def test_sub_gain_clamps():
    r = client.post("/api/sub-gain", json={"value": 20.0})
    assert r.json()["sub_gain"] == 12.0


def test_set_hpf_freq():
    r = client.post("/api/hpf", json={"freq": 60})
    assert r.status_code == 200
    assert r.json()["hpf"]["freq"] == 60


def test_set_hpf_bypass():
    r = client.post("/api/hpf", json={"bypass": True})
    assert r.status_code == 200
    assert r.json()["hpf"]["bypass"] is True


def test_set_lpf_freq():
    r = client.post("/api/lpf", json={"freq": 150})
    assert r.status_code == 200
    assert r.json()["lpf"]["freq"] == 150


def test_overlap_guard_hpf_cant_exceed_lpf():
    client.post("/api/lpf", json={"freq": 100})
    r = client.post("/api/hpf", json={"freq": 95})
    assert r.json()["hpf"]["freq"] == 90  # clamped to lpf - 10


def test_overlap_guard_lpf_cant_go_below_hpf():
    client.post("/api/hpf", json={"freq": 80})
    r = client.post("/api/lpf", json={"freq": 85})
    assert r.json()["lpf"]["freq"] == 90  # clamped to hpf + 10


def test_reset_preserves_master_volume():
    client.post("/api/master-gain", json={"value": -30.0})
    client.post("/api/sub-gain", json={"value": 8.0})
    client.post("/api/hpf", json={"freq": 60})
    r = client.post("/api/reset")
    s = r.json()
    assert s["master_gain"] == -30.0  # preserved
    assert s["sub_gain"] == 4.0       # reset to default
    assert s["hpf"]["freq"] == 45     # reset to default
    assert s["lpf"]["freq"] == 200    # reset to default


def test_reset_clears_bypass():
    client.post("/api/hpf", json={"bypass": True})
    r = client.post("/api/reset")
    assert r.json()["hpf"]["bypass"] is False
