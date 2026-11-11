import io
import math

from PIL import Image, ImageDraw

from . import palette

SIZE = 128
SUPER = 4
PLACEHOLDER_SHA256 = "19488d704863efa0f60f8e0ec108bb5246324fd53f9c81a955c429ceae4613b8"

BG = "#1b1d21"
GRID = "#23262b"
EDGE = "#30343a"
ARM = "#2b2e34"
ARM_EDGE = "#3d4148"
PROP = "#2a2d33"
RING = "#7cc242"
HUB = "#c9cdd2"
BODY = "#22252a"
STONE = "#8a8c8f"
STONE_DARK = "#5c5e61"
STONE_LIGHT = "#a9abad"
MOUTH = "#111214"
SHAFT = "#d8cfb8"
HEAD = "#c9cdd2"
FLETCH = "#7cc242"


def c(value):
    return palette.rgb(value) + (255,)


def s(*values):
    return [v * SUPER for v in values]


def rounded(draw, box, radius, fill, outline=None, width=0):
    draw.rounded_rectangle(s(*box), radius=radius * SUPER, fill=c(fill), outline=c(outline) if outline else None,
                           width=width * SUPER)


def arm(draw, x0, y0, x1, y1, width):
    draw.line(s(x0, y0, x1, y1), fill=c(ARM_EDGE), width=int((width + 2) * SUPER))
    draw.line(s(x0, y0, x1, y1), fill=c(ARM), width=int(width * SUPER))


def prop(draw, cx, cy, r):
    draw.ellipse(s(cx - r, cy - r, cx + r, cy + r), fill=c(PROP))
    draw.ellipse(s(cx - r, cy - r, cx + r, cy + r), outline=c(RING), width=int(3.2 * SUPER))
    for k in range(2):
        a = math.radians(35 + 180 * k)
        draw.line(s(cx, cy, cx + math.cos(a) * (r - 5), cy + math.sin(a) * (r - 5)), fill=c("#3a3e45"), width=int(4 * SUPER))
    draw.ellipse(s(cx - 5, cy - 5, cx + 5, cy + 5), fill=c(HUB))
    draw.ellipse(s(cx - 2, cy - 2, cx + 2, cy + 2), fill=c("#5c6168"))


def dispenser(draw, x0, y0, x1, y1):
    rounded(draw, (x0, y0, x1, y1), 2.5, STONE, STONE_DARK, 1)
    rows = 4
    h = (y1 - y0) / rows
    for i in range(1, rows):
        y = y0 + i * h
        draw.line(s(x0 + 1.5, y, x1 - 1.5, y), fill=c(STONE_DARK), width=int(1.0 * SUPER))
    for i in range(rows):
        off = 0.5 if i % 2 else 0.0
        for j in range(1, 3):
            x = x0 + (x1 - x0) * (j / 3.0 - off / 3.0)
            if x0 + 2 < x < x1 - 2:
                draw.line(s(x, y0 + i * h + 1, x, y0 + (i + 1) * h - 1), fill=c(STONE_DARK), width=int(1.0 * SUPER))
    draw.line(s(x0 + 2, y0 + 1.6, x1 - 2, y0 + 1.6), fill=c(STONE_LIGHT), width=int(1.0 * SUPER))
    mw, mh = (x1 - x0) * 0.36, (y1 - y0) * 0.34
    cx, cy = (x0 + x1) / 2, y0 + (y1 - y0) * 0.36
    rounded(draw, (cx - mw / 2 - 1.5, cy - mh / 2 - 1.5, cx + mw / 2 + 1.5, cy + mh / 2 + 1.5), 1.5, "#2c2e32")
    rounded(draw, (cx - mw / 2, cy - mh / 2, cx + mw / 2, cy + mh / 2), 1.0, MOUTH)


def arrow(draw, x, y_tail, y_tip):
    draw.line(s(x, y_tail, x, y_tip + 6), fill=c(SHAFT), width=int(2.4 * SUPER))
    draw.polygon(s(x, y_tip, x - 5, y_tip + 8, x + 5, y_tip + 8), fill=c(HEAD))
    for sx in (-1, 1):
        draw.polygon(s(x, y_tail - 6, x + sx * 4.5, y_tail - 1, x + sx * 4.5, y_tail + 3, x, y_tail - 2), fill=c(FLETCH))


def render():
    big = SIZE * SUPER
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    rounded(draw, (2, 2, SIZE - 2, SIZE - 2), 20, BG, EDGE, 1)
    for i in range(1, 8):
        v = i * SIZE / 8
        draw.line(s(v, 6, v, SIZE - 6), fill=c(GRID), width=SUPER)
        draw.line(s(6, v, SIZE - 6, v), fill=c(GRID), width=SUPER)
    props = [(31, 52), (97, 52), (31, 106), (97, 106)]
    centre = (64, 80)
    for px, py in props:
        prop(draw, px, py, 17)
    for px, py in props:
        arm(draw, centre[0], centre[1], px, py, 8)
        draw.ellipse(s(px - 6, py - 6, px + 6, py + 6), fill=c("#3a3e45"))
        draw.ellipse(s(px - 3.5, py - 3.5, px + 3.5, py + 3.5), fill=c(HUB))
    rounded(draw, (54, 62, 74, 102), 5, BODY, "#3a3e45", 1)
    draw.line(s(59, 96, 69, 96), fill=c(RING), width=int(2 * SUPER))
    dispenser(draw, 47, 36, 81, 62)
    arrow(draw, 64, 30, 6)
    small = img.resize((SIZE, SIZE), Image.LANCZOS)
    return small


def png_bytes():
    out = io.BytesIO()
    render().save(out, format="PNG", optimize=True)
    return out.getvalue()
