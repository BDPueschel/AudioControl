import os

# Force the in-memory mock device for the whole test session. Without this the
# app persists to repo/state.json and tests leak mutated state across runs
# (e.g. a left-over hpf freq), making default-state assertions order/run
# dependent. Must be set before backend.server.app is imported by any test.
os.environ.setdefault("AUDIOCONTROL_MOCK", "1")
