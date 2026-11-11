from . import common, decals, display, palette, parts

import accessories
import paint

CLAMP_LENGTH = 6.0


def materials():
    common.setup()
    igniter = common.material("md_accent", paint.plastic(palette.ACCENT, 0.04), "accessory", "accessory", palette.ACCENT)
    return igniter


def cradle(w, h, d):
    y0 = parts.HINGE_CLEARANCE_MM
    plate = 0.1 * h
    inner = 0.58 * w
    lip = 0.05 * w
    centre_y = y0 + plate + inner / 2.0
    return y0, plate, inner, lip, centre_y


def clamp_positions(d):
    return -0.28 * d, 0.16 * d


def build(key, definition):
    igniter = materials()
    w, h, d = parts.size_of(definition)
    tpu = "tpu_black"
    m = display.new_model(key)
    accessories.camera_fingers(m, tpu)
    hd = d / 2.0
    y0, plate, inner, lip, cy = cradle(w, h, d)
    base_w = 0.56 * w
    m.box(-base_w / 2, y0, -hd + 3.0, base_w / 2, y0 + plate, hd - 6.0, tpu, name="cradle_plate")
    m.box(-0.18 * w, y0 - 0.8, -6.0, 0.18 * w, y0, 6.0, tpu, name="hinge_block", skip=("up",))
    arm = (w - inner) / 2.0 - 0.3
    arm_top = y0 + 0.88 * h
    for cz in clamp_positions(d):
        z0, z1 = cz - CLAMP_LENGTH / 2, cz + CLAMP_LENGTH / 2
        m.box(-w / 2 + 0.3, y0, z0, w / 2 - 0.3, y0 + plate + 0.8, z1, tpu, name="clamp_base")
        for sx in (-1, 1):
            xa, xb = sx * inner / 2, sx * (inner / 2 + arm)
            m.box(min(xa, xb), y0 + plate, z0, max(xa, xb), arm_top, z1, tpu, name="clamp_arm",
                  decals={"north": decals.clamp_ribs, "south": decals.clamp_ribs})
            xl = sx * (inner / 2 - lip)
            m.box(min(xa, xl), arm_top - 2.2, z0, max(xa, xl), arm_top, z1, tpu, name="clamp_lip")
            sx0, sx1 = sx * 0.6, sx * (inner / 2 - 0.2)
            m.box(min(sx0, sx1), y0 + plate + 0.8, z0 + 0.6, max(sx0, sx1), y0 + plate + 2.4, z1 - 0.6, tpu,
                  name="clamp_saddle", rot=(0, 0, sx * 28.0), origin=(0.0, y0 + plate + 0.8, cz))
    m.box(-0.14 * w, y0 + plate, -hd + 3.0, 0.14 * w, y0 + plate + 0.2 * h, -hd + 6.0, tpu, name="nose_stop")
    iz0, iz1 = hd - 7.0, hd
    iw = 0.4 * w
    itop = cy + 3.0
    m.box(-iw / 2, y0, iz0, iw / 2, itop, iz1, igniter, name="igniter", decals={"up": decals.igniter_top})
    for sx in (-1, 1):
        m.box(sx * 0.08 * w - 0.6, cy - 1.2, iz0 - 0.8, sx * 0.08 * w + 0.6, cy + 1.2, iz0, "copper", name="igniter_contact")
    m.box(0.1 * w - 0.8, itop, iz0 + 2.4, 0.1 * w + 0.8, itop + 0.6, iz0 + 4.0, "led_red", name="igniter_led")
    return m
