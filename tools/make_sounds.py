#!/usr/bin/env python3
"""Generates the placeholder sound effects in assets/sounds. Run: python3 tools/make_sounds.py

Everything is synthesised from sine waves and noise, so the sounds are original and reproducible.
"""
import math
import os
import random
import struct
import wave

RATE = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "sounds")
rng = random.Random(7)


def silence(seconds):
    return [0.0] * int(RATE * seconds)


def tone(freq, seconds, amp=1.0, decay=0.0, harmonics=(1.0,), glide_to=None, vibrato=0.0):
    n = int(RATE * seconds)
    out, phase = [], 0.0
    for i in range(n):
        t = i / RATE
        f = freq if glide_to is None else freq + (glide_to - freq) * (i / n)
        f *= 1 + vibrato * math.sin(2 * math.pi * 6 * t)
        phase += 2 * math.pi * f / RATE
        v = sum(h * math.sin(phase * (k + 1)) for k, h in enumerate(harmonics))
        out.append(amp * v * math.exp(-decay * t))
    return out


def noise(seconds, amp=1.0, decay=0.0, smooth=0.0):
    """White noise; smooth in 0..1 makes it darker (a one-pole low-pass)."""
    n = int(RATE * seconds)
    out, last = [], 0.0
    for i in range(n):
        last = last * smooth + rng.uniform(-1, 1) * (1 - smooth)
        out.append(amp * last * math.exp(-decay * i / RATE))
    return out


def mix(*tracks):
    n = max(len(t) for t in tracks)
    return [sum(t[i] for t in tracks if i < len(t)) for i in range(n)]


def concat(*tracks):
    return [s for t in tracks for s in t]


def envelope(samples, attack=0.005, release=0.02):
    n = len(samples)
    a, r = int(RATE * attack), int(RATE * release)
    out = []
    for i, s in enumerate(samples):
        g = 1.0
        if i < a:
            g = i / a
        if i > n - r:
            g = min(g, (n - i) / r)
        out.append(s * g)
    return out


def save(name, samples, volume=0.8):
    peak = max(1e-9, max(abs(s) for s in samples))
    data = b"".join(struct.pack("<h", int(max(-1, min(1, s / peak * volume)) * 32767)) for s in samples)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(data)


def main():
    os.makedirs(OUT, exist_ok=True)
    save("footstep", envelope(mix(tone(90, 0.08, decay=40), noise(0.08, 0.5, decay=60, smooth=0.85))), 0.5)
    save("door", envelope(mix(tone(310, 0.38, 0.5, harmonics=(1, 0.6, 0.4, 0.3), glide_to=430, vibrato=0.03),
                              noise(0.38, 0.25, smooth=0.6)), release=0.08), 0.5)
    save("takedown", envelope(mix(tone(60, 0.3, decay=12), noise(0.3, 0.9, decay=25, smooth=0.9))), 0.9)
    save("pickup", envelope(noise(0.22, 1, smooth=0.75), attack=0.06, release=0.1), 0.4)
    save("hide", envelope(concat(noise(0.03, 1, decay=80), silence(0.05), mix(tone(110, 0.15, decay=25), noise(0.15, 0.6, decay=30, smooth=0.9)))), 0.6)
    save("coin_throw", envelope(noise(0.14, 1, smooth=0.3), attack=0.03, release=0.08), 0.25)
    clink = mix(tone(2400, 0.3, decay=14), tone(3150, 0.3, 0.7, decay=18), tone(4700, 0.3, 0.4, decay=24))
    save("coin_land", concat(clink[: int(RATE * 0.09)], clink), 0.6)
    save("suspicious", envelope(tone(210, 0.28, harmonics=(1, 0.5, 0.25), glide_to=320, vibrato=0.01), release=0.06), 0.45)
    save("alarmed", envelope(concat(tone(620, 0.16, harmonics=(1, 0, 0.33, 0, 0.2)), tone(830, 0.22, harmonics=(1, 0, 0.33, 0, 0.2))), release=0.04), 0.55)
    save("body_found", envelope(tone(98, 0.7, harmonics=(1, 0.7, 0.5, 0.3), decay=3), release=0.2), 0.6)
    save("target_down", envelope(mix(tone(55, 1.2, decay=3), tone(110, 1.2, 0.4, harmonics=(1, 0.3), decay=2), noise(0.25, 0.8, decay=18, smooth=0.9)), release=0.3), 0.8)
    notes = [523.25, 659.25, 783.99, 1046.5]
    save("complete", envelope(concat(*[tone(f, 0.18 if i < 3 else 0.6, harmonics=(1, 0.3, 0.1), decay=3) for i, f in enumerate(notes)]), release=0.2), 0.6)
    save("caught", envelope(concat(*[tone(f, 0.25 if i < 2 else 0.6, harmonics=(1, 0.5, 0.3), decay=2) for i, f in enumerate([392, 349.23, 293.66])]), release=0.25), 0.6)

    # Ambient loop: a low drone with slow breathing and dark wind. 8 s with whole cycles, so it loops cleanly.
    seconds = 8.0
    n = int(RATE * seconds)
    wind = noise(seconds + 1, 1, smooth=0.97)
    fade = int(RATE * 1)
    wind = [wind[i] * (i / fade if i < fade else 1) + (wind[n + i] * (1 - i / fade) if i < fade else 0) for i in range(n)]
    drone = []
    for i in range(n):
        t = i / RATE
        lfo = 0.6 + 0.4 * math.sin(2 * math.pi * 0.25 * t)
        drone.append(lfo * (math.sin(2 * math.pi * 55 * t) + 0.5 * math.sin(2 * math.pi * 82.5 * t)) + 1.5 * wind[i])
    save("ambient", drone, 0.5)


if __name__ == "__main__":
    main()
