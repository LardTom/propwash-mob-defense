import numpy as np

from . import palette

import paint
from paint import hexc


def named(fn, *parts):
    fn.__name__ = "_".join(str(p) for p in parts)
    return fn


def stone(region, ctx):
    h, w = region.shape[:2]
    base = hexc(palette.STONE)
    paint.fill(region, hexc(palette.MORTAR))
    rng = ctx.rng
    y = 0
    row = 0
    while y < h:
        rh = int(rng.integers(3, 6)) if h >= 6 else h
        x = -int(rng.integers(0, 5)) if row % 2 else 0
        while x < w:
            cw = int(rng.integers(4, 9)) if w >= 8 else w
            tone = 0.84 + 0.26 * float(rng.random())
            x0, x1 = max(0, x), min(w, x + cw - 1 if w >= 8 else x + cw)
            y0, y1 = y, min(h, y + rh - 1 if h >= 6 else y + rh)
            if x1 > x0 and y1 > y0:
                region[y0:y1, x0:x1, :3] = paint.shade(base, tone)
                region[y0, x0:x1, :3] = paint.shade(base, tone * 1.14)
                if x1 - x0 >= 3 and y1 - y0 >= 3 and float(rng.random()) < 0.35:
                    sx = x0 + int(rng.integers(1, x1 - x0 - 1))
                    sy = y0 + int(rng.integers(1, y1 - y0 - 1))
                    region[sy, sx, :3] = paint.shade(base, tone * 0.82)
            x += cw
        y += rh
        row += 1
    paint.noise(region, ctx, 0.03)
    if ctx.side == "top":
        paint.edges(region, hexc(palette.STONE_LIGHT), hexc(palette.STONE_DARK))
    elif ctx.side == "side" and h >= 3:
        region[0, :, :3] = hexc(palette.STONE_LIGHT)
        region[h - 1, :, :3] = hexc(palette.STONE_DARK)
    elif ctx.side == "bottom":
        region[..., :3] = paint.shade(region[..., :3], 0.85)


def slot_windows(count=4):
    def decal(region, ctx):
        h, w = region.shape[:2]
        if w < 4 * count or h < 6:
            return
        y0, y1 = int(round(h * 0.2)), int(round(h * 0.2)) + max(2, h // 8)
        span = int(round(w * 0.6))
        start = int(round(w * 0.2))
        step = max(3, span // count)
        for i in range(count):
            x = start + i * step
            paint.rect(region, x, y0, x + step - 1, y1, hexc("#111214"))
            paint.px(region, x + (step - 1) // 2, y0 + (y1 - y0) // 2, hexc("#9fe06a"))

    return named(decal, "slot_windows", count)


def rail_marks(region, ctx):
    h, w = region.shape[:2]
    if w < 6 or h < 6:
        return
    for frac in (0.25, 0.75):
        y = int(round(h * frac))
        paint.rect(region, max(0, w // 2 - 1), y, min(w, w // 2 + 1), y + 1, hexc("#5a5f66"))


def igniter_top(region, ctx):
    h, w = region.shape[:2]
    if w < 4 or h < 4:
        return
    paint.edges(region, hexc(palette.scale(palette.ACCENT, 1.18)), hexc(palette.scale(palette.ACCENT, 0.7)))
    stripe = max(1, h // 4)
    for x in range(1, w - 1):
        if (x // 2) % 2 == 0:
            region[h - 1 - stripe:h - 1, x, :3] = hexc("#e8b923")
        else:
            region[h - 1 - stripe:h - 1, x, :3] = hexc("#1c1d20")


def clamp_ribs(region, ctx):
    h, w = region.shape[:2]
    if h < 4:
        return
    for y in range(1, h - 1, 2):
        region[y, :, :3] = paint.mix(region[y, :, :3], np.array(hexc("#3a3b3f"), dtype=np.float64), 0.45)
