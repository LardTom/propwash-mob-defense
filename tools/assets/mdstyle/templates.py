from . import booster, dispenser, parts, spear

TEMPLATES = {
    "dispenser_mount": ("front", dispenser.build),
    "spear_mount": ("bottom", spear.build),
    "rocket_booster": ("top", booster.build),
}

KEYS = tuple(TEMPLATES)


def build(key, definition):
    if key not in TEMPLATES:
        raise KeyError(f"accessory/{key}: keine Vorlage in mdstyle")
    slot, builder = TEMPLATES[key]
    found = parts.slot_of(definition)
    if found != slot:
        raise ValueError(f"accessory/{key}: Halterung {found}, die Vorlage erwartet {slot}")
    return builder(key, definition)
