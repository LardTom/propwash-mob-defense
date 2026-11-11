from . import palette, paths

import accessories
import addon
import kit
import paint
import paintable

_ready = {"done": False}


def setup(namespace=paths.NS):
    kit.NAMESPACE = namespace
    if not _ready["done"]:
        addon.register_materials()
        accessories.register_materials()
        _ready["done"] = True


def clamp(value, low, high):
    return max(low, min(high, value))


def material(name, painter, category=None, channel=None, base=None):
    if not name.startswith(palette.PREFIX):
        raise ValueError(f"eigenes Material {name} braucht das Präfix {palette.PREFIX}")
    paint.register(name, painter)
    if category and channel and base:
        paintable.MATERIALS[(category, name)] = (channel, base)
    return name


def channel_of(category, name):
    return paintable.MATERIALS.get((category, name))
