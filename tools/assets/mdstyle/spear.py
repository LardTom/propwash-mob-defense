from . import common, decals, display, palette, parts

import paint

CLAMP_LENGTH = 8.0
BORE = 7.0


def materials():
    common.setup()
    lever = common.material("md_accent", paint.plastic(palette.ACCENT, 0.04), "accessory", "accessory", palette.ACCENT)
    return lever


def axis(w, h, d):
    rail = 0.14 * h
    bridge = 0.24 * h
    return 0.0, -(rail + bridge + BORE / 2.0)


def clamp_positions(d):
    return -0.32 * d, 0.26 * d


def build(key, definition):
    lever = materials()
    w, h, d = parts.size_of(definition)
    clamp = palette.TPU_MATERIALS[palette.TPU_RED]
    m = display.new_model(key)
    hd = d / 2.0
    rail_h = 0.14 * h
    rail_w = 0.58 * w
    m.box(-rail_w / 2, -rail_h, -hd, rail_w / 2, 0.0, hd, "carbon", name="rail", decals={"down": decals.rail_marks})
    for z in (-hd, hd - 2.0):
        m.box(-rail_w / 2 - 0.4, -rail_h - 0.8, z, rail_w / 2 + 0.4, 0.0, z + 2.0, clamp, name="rail_cap")
    ax, ay = axis(w, h, d)
    outer = 0.8 * w
    cheek = (outer - BORE) / 2.0
    bridge_top = -rail_h
    bore_top = ay + BORE / 2.0
    bore_bottom = ay - BORE / 2.0
    bottom = -h + 0.06 * h
    for cz in clamp_positions(d):
        z0, z1 = cz - CLAMP_LENGTH / 2, cz + CLAMP_LENGTH / 2
        m.box(-outer / 2, bore_top, z0, outer / 2, bridge_top, z1, clamp, name="clamp_bridge",
              decals={"north": decals.clamp_ribs, "south": decals.clamp_ribs})
        for sx in (-1, 1):
            x0, x1 = sx * BORE / 2, sx * outer / 2
            m.box(min(x0, x1), bore_bottom, z0, max(x0, x1), bore_top, z1, clamp, name="clamp_cheek")
        m.box(-outer / 2, bottom, z0, outer / 2, bore_bottom, z1, clamp, name="clamp_base")
        m.box(-BORE / 2, bore_bottom, z0 + 1.0, BORE / 2, bore_bottom + 0.6, z1 - 1.0, "rubber", name="clamp_pad")
        lx0 = outer / 2
        lx1 = w / 2 - 0.4
        m.box(lx0, ay - 2.2, cz - 2.0, lx1, ay + 2.2, cz + 2.0, lever, name="clamp_lever")
        m.cylinder_x(lx0 + 0.6, ay, cz, 1.6, 2.4, "steel", name="clamp_pivot")
    return m
