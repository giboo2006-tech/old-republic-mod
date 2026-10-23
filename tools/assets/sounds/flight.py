"""PLACEHOLDER sound recipes for free flight and the ship's Milestone 5 events (Milestone 5, W1): the
engine loops of the courier and the interceptor (the client raises their pitch with the ship's
speed), the boost, a thruster's puff, the roar through the atmosphere, a scrape along the ground,
the landing warning, the helm engaging, the nav computer turning the ship back at the orbit's edge,
the shipwright's refit and an upgrade fitted, the disabled ship's alarm and the rescue tug's arrival.

Synthesised from scratch with the helpers in gen_sounds.py (oscillators, filtered noise, simple
reverb); nothing is sampled, and no sound from any game or film is imitated. The file names are the
placeholder names gen_sounds gave these ids in Phase 0 (<group>/<rest of the id>), so sounds.json
does not change. The three loops (ship.flight.engine, ship.interceptor.engine, ship.flight.alarm)
are seamless: every oscillator is its own whole number of cycles over the loop, and every filter,
modulation and reverb is circular. See events_m5.json for the event list and docs/assets/flight.md
for the intent of each sound.
"""
import gen_sounds as g

np = g.np


def tone(f, n, loop=False):
    """A sine over the frequency curve f (a whole number of cycles when loop)."""
    return np.sin(g.phase(np.broadcast_to(np.asarray(f, float), (n,)), n, loop))


def turbine(f, n, loop=False, ratios=(1.0, 2.0, 3.0), amps=(0.6, 0.25, 0.12), detune=(0.0, 0.004, -0.006)):
    """A turbine's whine over the curve f: partials near whole multiples of it, each its own oscillator, so a loop stays seamless."""
    f = np.broadcast_to(np.asarray(f, float), (n,))
    return sum(a * tone(f * r * (1 + d), n, loop) for r, a, d in zip(ratios, amps, detune))


def cycles(t, length, k, offset=0.0):
    """A sine with k whole cycles over a loop of the given length."""
    return np.sin(2 * np.pi * k * t / length + offset)


def g_flight_engine(rng):
    """Seamless 3 s courier engines in flight: a full roar of thrust, a turbine whine, a sub-bass hum
    and a slow flutter of the exhaust; fuller and rougher than the M4 idle."""
    L = 3.0
    t = g.tt(L)
    n = len(t)
    hum = 0.45 * tone(55.0, n, True) + 0.2 * tone(110.0, n, True) + 0.08 * tone(165.0, n, True)
    whine = turbine(640.0 * (1 + 0.004 * cycles(t, L, 2) + 0.002 * cycles(t, L, 5, 1.0)), n, loop=True) * 0.12
    roar = g.lp(g.noise(n, rng), 420, loop=True) * 2.2 + g.bp(g.noise(n, rng), 900, 1.2, loop=True) * 0.35
    flutter = 1 + 0.07 * cycles(t, L, 6) + 0.04 * cycles(t, L, 11, 0.7)
    hiss = g.bp(g.noise(n, rng), 3800, 1.0, loop=True) * 0.05
    return g.reverb(hum + whine + roar * flutter + hiss, rng, 0.8, 0.12, loop=True)


def g_interceptor_engine(rng):
    """Seamless 2 s interceptor engines: two bright turbines a few hertz apart, so they beat, over a
    thin, raspy roar; higher and leaner than the courier's."""
    L = 2.0
    t = g.tt(L)
    n = len(t)
    f = 1050.0 * (1 + 0.003 * cycles(t, L, 3))
    twin = (turbine(f, n, loop=True, ratios=(1.0, 2.0, 3.0, 5.0), amps=(0.5, 0.3, 0.15, 0.06), detune=(0.0, 0.003, -0.004, 0.002))
            + turbine(f * 1.006, n, loop=True) * 0.8)
    rasp = g.bp(g.noise(n, rng), 1400, 1.5, loop=True) * 0.9
    roar = g.lp(g.noise(n, rng), 600, loop=True) * 1.2
    sub = 0.2 * tone(82.0, n, True)
    flutter = 1 + 0.05 * cycles(t, L, 8)
    return g.reverb(twin * 0.12 + (rasp + roar) * flutter + sub, rng, 0.6, 0.1, loop=True)


def g_flight_boost(rng):
    """The boost: a pressure thump, then a brighter, longer plume swelling over a rising whine and the
    crackle of the exhaust."""
    L = 1.8
    t = g.tt(L)
    n = len(t)
    thump = g.thump(t, 140, 42, 0.05, 0.25)
    swell = np.minimum(1, t / 0.25) * np.exp(-np.maximum(t - 0.5, 0) / 0.5)
    plume = g.sweep(g.noise(n, rng), 600 + 1800 * np.clip(t / 0.6, 0, 1), "bp", 1.0, nb=10) * 1.4 * swell
    whine = turbine(700 + 500 * np.clip(t / 0.8, 0, 1), n) * 0.18 * swell
    crackle = g.bp(g.crackle(n, 300 * swell, rng), 2500, 1.2) * 0.6
    return g.reverb(g.sat((thump + plume + whine + crackle) * 0.8, 1.3), rng, 1.0, 0.2)


def g_flight_thruster(rng):
    """A thruster's puff: a valve's click and a short, bright jet of gas."""
    L = 0.5
    t = g.tt(L)
    n = len(t)
    jet = g.bp(g.noise(n, rng), 4200, 0.9) * g.env_ad(t, 0.008, 0.11, 0.01) * 1.6
    body = g.lp(g.noise(n, rng), 900) * g.env_ad(t, 0.004, 0.06, 0.01)
    valve = g.click(t, rng, 0.004, 1.2) * 0.4
    return g.reverb(jet + body + valve, rng, 0.4, 0.12)


def g_flight_atmosphere(rng):
    """Through the atmosphere: a deep roar of air swelling against the hull, buffeting, a rising
    whistle of wind and the creak of the hull in the heat; then it eases off."""
    L = 3.2
    t = g.tt(L)
    n = len(t)
    env = np.minimum(1, t / 0.6) * np.minimum(1, (L - t) / 1.2)
    roar = g.lp(g.noise(n, rng), 300) * 3.0 + g.bp(g.noise(n, rng), 700, 1.0) * 0.8
    buffet = 1 + 0.25 * np.sin(2 * np.pi * 7 * t) * np.sin(2 * np.pi * 1.3 * t + 0.4)
    whistle = g.sweep(g.noise(n, rng), 700 + 900 * np.clip(t / 2.4, 0, 1), "bp", 6.0, nb=12) * 0.6
    creak = g.metal_ring(t, rng, 92, 5, 0.6, 1.1) * 0.5 + g.metal_ring(t, rng, 133, 5, 0.5, 2.0) * 0.35
    return g.reverb(g.sat((roar * buffet + whistle) * env * 0.7, 1.4) + creak, rng, 1.6, 0.25, bright=3000)


def g_flight_impact(rng):
    """The hull strikes the ground and scrapes along it: a heavy crunch, a gritty scrape falling in
    pitch and a ringing panel."""
    L = 1.4
    t = g.tt(L)
    n = len(t)
    hit = g.thump(t, 110, 38, 0.04, 0.22) * 1.2 + g.crack(t, rng, 0.0, 0.02, 600) * 0.8
    scraping = g.env_ad(t, 0.02, 0.35, 0.02)
    grit = g.crackle(n, 1800 * scraping + 50, rng) + g.noise(n, rng) * 0.15
    scrape = g.sweep(grit, 2600 - 1500 * np.clip(t / 0.6, 0, 1), "bp", 1.4, nb=10) * 1.5 * scraping
    ring = g.metal_ring(t, rng, 410, 6, 0.4, 0.01) * 0.35
    return g.reverb(hit + scrape + ring, rng, 0.9, 0.2)


def g_flight_land_warn(rng):
    """The landing is refused: two soft falling chimes from the cockpit, a short low buzz under the
    second."""
    L = 1.0
    t = g.tt(L)
    n = len(t)
    high = g.bell_partials(n, 880.0, g.CHIME_RATIOS[:3], (0.35, 0.18, 0.08), (1.0, 0.25, 0.08), rng)
    low = g.bell_partials(n, 659.25, g.CHIME_RATIOS[:3], (0.45, 0.2, 0.1), (1.0, 0.25, 0.08), rng, t0=0.18)
    buzz = g.lp(g.saw(g.phase(np.full(n, 110.0), n), 110.0), 900) * g.env_ad(t, 0.01, 0.12, 0.18) * 0.12
    return g.reverb((high + low) * 0.5 + buzz, rng, 0.7, 0.2)


def g_flight_helm(rng):
    """The helm engages: the struts fold with two hydraulic clunks, the ramp seals with a thud and a
    hiss, and the engines flare from a murmur to a strong whine."""
    L = 2.4
    t = g.tt(L)
    n = len(t)
    x = np.zeros(n)

    for t0 in (0.05, 0.28):
        x += g.thump(t, 160, 70, 0.03, 0.12, t0) * 0.7 + g.metal_ring(t, rng, 300 + 80 * t0, 5, 0.18, t0) * 0.25

    x += g.thump(t, 90, 40, 0.05, 0.25, 0.62) * 0.9
    seal = g.hp(g.noise(n, rng), 2500) * g.env_ad(t, 0.01, 0.25, 0.66) * 0.3
    flaring = g.sig((t - 1.0) / 0.12) * np.minimum(1, (L - t) / 0.5)
    flare = turbine(500 + 900 * np.clip((t - 0.9) / 1.0, 0, 1), n) * 0.25 * flaring
    roar = g.lp(g.noise(n, rng), 500) * 1.4 * flaring
    return g.reverb(x + seal + flare + roar, rng, 1.0, 0.2)


def g_flight_edge(rng):
    """Turned back at the orbit's edge: the nav computer's three-note chirp, then a long side burst
    of the thrusters as the ship swings round."""
    L = 1.8
    t = g.tt(L)
    n = len(t)
    x = np.zeros(n)

    for t0, f in ((0.0, 1318.5), (0.12, 987.8), (0.24, 1318.5)):
        beep = g.lp(g.pulse(g.phase(np.full(n, f), n), f, 0.4, 12), 3500)
        x += beep * g.env_ad(t, 0.004, 0.07, t0) * 0.35

    burst = g.env_ad(t, 0.05, 0.45, 0.45)
    x += g.bp(g.noise(n, rng), 3000, 0.8) * burst * 1.2 + g.lp(g.noise(n, rng), 600) * burst * 0.8
    return g.reverb(x, rng, 0.9, 0.2)


def g_refit(rng):
    """The shipwright refits the hull: a crane's motor whirs and its gears click, a heavy plate clanks
    into place, welding crackles, and a cloud of steam hisses out."""
    L = 4.0
    t = g.tt(L)
    n = len(t)
    running = g.bell(t, 0.7, 0.35, 0.5)
    motor = (turbine(180 + 60 * np.clip(t / 1.2, 0, 1), n, ratios=(1.0, 2.0, 4.0), amps=(0.5, 0.3, 0.1)) * 0.3
             + g.lp(g.noise(n, rng), 400) * 0.6) * running
    gears = sum(g.click(t, rng, 0.2 + 0.07 * i, 0.8) for i in range(14)) * 0.15
    clank = g.thump(t, 120, 45, 0.04, 0.3, 1.45) + g.metal_ring(t, rng, 220, 7, 0.9, 1.45) * 0.5
    welding = np.clip((t - 1.9) / 0.05, 0, 1) * np.clip((3.2 - t) / 0.1, 0, 1)
    weld = g.bp(g.crackle(n, 900 * welding, rng) + g.noise(n, rng) * 0.08 * welding, 3200, 1.0) * 1.2
    steam = g.hp(g.noise(n, rng), 1800) * g.env_ad(t, 0.03, 0.4, 3.2) * 0.5
    return g.reverb(motor + gears + clank + weld + steam, rng, 1.4, 0.25)


def g_upgrade_fit(rng):
    """An upgrade fitted: a ratchet's quick clicks, a firm clunk as it seats, then a short rising
    chirp as it powers up."""
    L = 1.6
    t = g.tt(L)
    n = len(t)
    ratchet = sum(g.click(t, rng, 0.02 + 0.045 * i, 1.4) * (0.6 + 0.04 * i) for i in range(8)) * 0.3
    clunk = g.thump(t, 150, 60, 0.03, 0.18, 0.42) + g.metal_ring(t, rng, 520, 6, 0.3, 0.42) * 0.35
    chirp = tone(500 + 1300 * np.clip((t - 0.75) / 0.25, 0, 1), n) * g.env_ad(t, 0.02, 0.18, 0.75) * 0.3
    return g.reverb(ratchet + clunk + chirp, rng, 0.8, 0.2)


def g_flight_alarm(rng):
    """Seamless 2 s alarm of a disabled ship: a two-tone klaxon, high then low, every half second,
    through a small cockpit speaker; each tone sounds for 0.3 s and fades in and out, so nothing
    clicks and the long rescue is not a wall of sound."""
    L = 2.0
    t = g.tt(L)
    n = len(t)
    f = np.where(np.floor(t / 0.5).astype(int) % 2 == 0, 620.0, 470.0)
    raw = g.pulse(g.phase(f, n, loop=True), 620.0, 0.35, 12)
    speaker = g.lp(raw, 1800, loop=True) * 0.7 + g.bp(raw, 1100, 1.5, loop=True) * 0.5
    within = t % 0.5
    env = np.clip(within / 0.015, 0, 1) * np.clip((0.3 - within) / 0.05, 0, 1)
    return g.reverb(g.sat(speaker * env, 1.2), rng, 0.5, 0.15, loop=True)


def g_flight_rescue(rng):
    """The rescue tug arrives: its engines swell in from afar, the tow clamps lock on with a heavy
    clank, and the comm chirps twice."""
    L = 3.4
    t = g.tt(L)
    n = len(t)
    approach = np.clip(t / 1.6, 0, 1) ** 2 * np.minimum(1, (L - t) / 1.0)
    engine = (turbine(380 + 260 * np.clip(t / 1.6, 0, 1), n) * 0.2 + g.lp(g.noise(n, rng), 350) * 1.6) * approach
    clamp = g.thump(t, 130, 50, 0.04, 0.3, 1.7) + g.metal_ring(t, rng, 180, 7, 0.7, 1.7) * 0.45 + g.click(t, rng, 1.68, 1.0) * 0.5
    x = engine + clamp

    for t0 in (2.25, 2.42):
        x += tone(1567.98, n) * g.env_ad(t, 0.005, 0.06, t0) * 0.25

    return g.reverb(x, rng, 1.3, 0.25)


FILES = {
    "ship.flight.engine": {"ship/flight_engine": g_flight_engine},
    "ship.flight.boost": {"ship/flight_boost": g_flight_boost},
    "ship.flight.thruster": {"ship/flight_thruster": g_flight_thruster},
    "ship.flight.atmosphere": {"ship/flight_atmosphere": g_flight_atmosphere},
    "ship.flight.impact": {"ship/flight_impact": g_flight_impact},
    "ship.flight.land_warn": {"ship/flight_land_warn": g_flight_land_warn},
    "ship.flight.helm": {"ship/flight_helm": g_flight_helm},
    "ship.flight.edge": {"ship/flight_edge": g_flight_edge},
    "ship.interceptor.engine": {"ship/interceptor_engine": g_interceptor_engine},
    "ship.refit": {"ship/refit": g_refit},
    "ship.upgrade_fit": {"ship/upgrade_fit": g_upgrade_fit},
    "ship.flight.alarm": {"ship/flight_alarm": g_flight_alarm},
    "ship.flight.rescue": {"ship/flight_rescue": g_flight_rescue},
}
