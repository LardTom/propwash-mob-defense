from PIL import Image, ImageDraw, ImageFont

BG = (46, 49, 56, 255)
TEXT = (230, 232, 236, 255)
SUB = (160, 166, 176, 255)
ACCENT = (255, 187, 0, 255)
FONTS = ("DejaVuSans.ttf", "Arial.ttf", "Helvetica.ttc", "/System/Library/Fonts/Supplemental/Arial.ttf",
         "/System/Library/Fonts/Helvetica.ttc")
_fonts = {}


def font(size):
    if size not in _fonts:
        chosen = None
        for name in FONTS:
            try:
                chosen = ImageFont.truetype(name, size)
                break
            except OSError:
                continue
        _fonts[size] = chosen or ImageFont.load_default()
    return _fonts[size]


def label(img, xy, text, size=12, fill=TEXT):
    ImageDraw.Draw(img).text(xy, text, fill=fill, font=font(size))


def grid(cells, columns, title=None, bg=BG):
    rows = (len(cells) + columns - 1) // columns
    w = max(c.width for c in cells)
    h = max(c.height for c in cells)
    head = 14 + 20 * (title.count("\n") + 1) if title else 0
    out = Image.new("RGBA", (columns * w, rows * h + head), bg)
    if title:
        label(out, (8, 8), title, 14)
    for i, img in enumerate(cells):
        out.paste(img, ((i % columns) * w, head + (i // columns) * h))
    return out


def stack(images, bg=BG):
    w = max(i.width for i in images)
    out = Image.new("RGBA", (w, sum(i.height for i in images)), bg)
    y = 0
    for img in images:
        out.paste(img, (0, y))
        y += img.height
    return out


def heading(width, text, size=14, bg=BG):
    head = Image.new("RGBA", (width, (size + 6) * (text.count("\n") + 1) + 12), bg)
    label(head, (8, 8), text, size)
    return head
