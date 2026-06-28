from pydantic import BaseModel


class FilterState(BaseModel):
    freq: int
    bypass: bool


class ChannelState(BaseModel):
    gain: float           # -24.0 to 12.0, step 0.5
    hpf: FilterState      # freq 20-400, step 5
    lpf: FilterState      # freq 40-500, step 5


class DspState(BaseModel):
    master_gain: float    # -60.0 to -25.0 (safety cap), step 1.0
    mute: bool            # master mute
    mains: ChannelState   # outputs 0,1 (Main L/R)
    subs: ChannelState    # outputs 2,3 (Sub L/R)


DEFAULTS = DspState(
    master_gain=-45.0,
    mute=False,
    # Mains start flat/bypassed — the app owns them but doesn't filter by default.
    mains=ChannelState(
        gain=0.0,
        hpf=FilterState(freq=80, bypass=True),
        lpf=FilterState(freq=120, bypass=True),
    ),
    # Subs: high-passed subsonic + low-passed crossover, both engaged.
    subs=ChannelState(
        gain=4.0,
        hpf=FilterState(freq=45, bypass=False),
        lpf=FilterState(freq=200, bypass=False),
    ),
)

GROUPS = ("mains", "subs")


class GainRequest(BaseModel):
    value: float


class MuteRequest(BaseModel):
    value: bool


class QuickPlayItem(BaseModel):
    name: str
    url: str
    subtitle: str = ""


class PlayRequest(BaseModel):
    url: str


class FilterRequest(BaseModel):
    freq: int | None = None
    bypass: bool | None = None
