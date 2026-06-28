from pydantic import BaseModel


class FilterState(BaseModel):
    freq: int
    bypass: bool


class DspState(BaseModel):
    master_gain: float    # -60.0 to -25.0 (safety cap), step 1.0
    sub_gain: float       # -24.0 to 12.0, step 0.5
    hpf: FilterState      # freq 20-400, step 5
    lpf: FilterState      # freq 40-500, step 5


DEFAULTS = DspState(
    master_gain=-45.0,
    sub_gain=4.0,
    hpf=FilterState(freq=45, bypass=False),
    lpf=FilterState(freq=200, bypass=False),
)


class GainRequest(BaseModel):
    value: float


class FilterRequest(BaseModel):
    freq: int | None = None
    bypass: bool | None = None
