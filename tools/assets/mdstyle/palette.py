import paintable

CHANNELS = dict(paintable.CHANNELS)
PREFIX = "md_"

ACCENT = "#6aa336"
TPU = "#262628"
TPU_RED = "#d6333a"
CARBON = "#1b1d20"
STONE = "#7e8083"
STONE_LIGHT = "#9a9c9e"
STONE_DARK = "#4f5154"
MORTAR = "#3c3e41"
LABEL = "#e8e8e4"
COPPER = "#c8743c"

PAINT_GROUPS = {("tpu", TPU), ("tpu", TPU_RED), ("accessory", ACCENT)}
TPU_MATERIALS = {TPU: "tpu_black", TPU_RED: "tpu_red"}


def rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def hexs(values):
    return "#%02x%02x%02x" % tuple(max(0, min(255, int(round(v)))) for v in values)


def fit(colour, channel):
    reference = CHANNELS[channel] - 2
    r, g, b = rgb(colour)
    top = max(r, g, b, 1)
    if top <= reference:
        return colour
    k = reference / top
    return hexs((r * k, g * k, b * k))


def mix(a, b, t):
    ra, rb = rgb(a), rgb(b)
    return hexs(tuple(x * (1 - t) + y * t for x, y in zip(ra, rb)))


def scale(colour, factor):
    return hexs(tuple(c * factor for c in rgb(colour)))


def luminance(colour):
    r, g, b = rgb(colour)
    return 0.299 * r + 0.587 * g + 0.114 * b
