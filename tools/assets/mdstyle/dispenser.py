from . import common, decals, display, palette, parts

import accessories
import paint
from stacks import lead

VANILLA_FRONT = "minecraft:block/dispenser_front"
VANILLA_FACES = {"front_plate": {"north": ("front", VANILLA_FRONT)}}


def materials():
    common.setup()
    body = common.material("md_accent", paint.plastic(palette.ACCENT, 0.04), "accessory", "accessory", palette.ACCENT)
    stone = common.material("md_stone", decals.stone)
    return body, stone


def build(key, definition):
    servo, stone = materials()
    w, h, d = parts.size_of(definition)
    housing = "tpu_black"
    m = display.new_model(key)
    hw = w / 2.0
    plate_t = 0.12 * d
    m.box(-hw, -h, -d, hw, 0.0, -d + plate_t, stone, name="front_plate", skip=("north",), lowres={"south": 0.35})
    inset = 0.1 * w
    top = -0.2 * h
    floor = -0.9 * h
    m.box(-hw + inset, floor, -d + plate_t, hw - inset, top, -0.06 * d, housing, name="housing", skip=("north",),
          decals={"east": decals.slot_windows(4), "west": decals.slot_windows(4)}, lowres={"south": 0.5})
    m.box(-hw + inset + 1.5, floor - 1.2, -0.6 * d, hw - inset - 1.5, floor, -0.2 * d, housing, name="housing_foot",
          skip=("up",))
    sw, sd = 0.34 * w, 0.42 * d
    sz0 = -0.66 * d
    m.box(-sw / 2, top, sz0, sw / 2, top + 0.14 * h, sz0 + sd, servo, name="servo",
          decals={"east": accessories.servo_label, "west": accessories.servo_label})
    for sz in (sz0 - 2.2, sz0 + sd):
        m.box(-sw / 2, top + 0.06 * h, sz, sw / 2, top + 0.08 * h, sz + 2.2, servo, name="servo_tab")
    m.cylinder(0.0, sz0 + 0.25 * sd, top + 0.14 * h, top + 0.14 * h + 2.0, 0.2 * sw, "white", name="servo_gear", bevel=0.3)
    m.box(-1.0, top + 0.14 * h + 2.0, sz0 + 0.25 * sd - 0.12 * sd, 1.0, top + 0.14 * h + 3.2, sz0 + 0.25 * sd + 0.12 * sd,
          "white", name="servo_horn")
    tab_x0, tab_x1 = 0.24 * w, 0.4 * w
    for sx in (-1, 1):
        x0, x1 = sx * tab_x0, sx * tab_x1
        m.box(min(x0, x1), top, -0.3 * d, max(x0, x1), -2.6, -0.06 * d, housing, name="tab_riser")
        m.box(min(x0, x1), -2.6, -0.3 * d, max(x0, x1), 0.0, 0.0, housing, name="tab")
    lead(m, 0.25 * sw, top + 0.05 * h, sz0 + sd, -0.02 * d, 0.9, "wire_red")
    lead(m, 0.25 * sw - 1.2, top + 0.05 * h, sz0 + sd, -0.02 * d, 0.9, "wire_black")
    lead(m, 0.25 * sw - 2.4, top + 0.05 * h, sz0 + sd, -0.02 * d, 0.9, "wire_yellow")
    return m
