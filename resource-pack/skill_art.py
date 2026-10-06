"""Pixel art for weapon skill icons that would read poorly as a font symbol (16x16 masks, 1 = drawn).

Skills listed in SKILL_ART get this picture instead of the symbol from Skill.java (the symbol is
still used on the action bar). The spear pictures are built from the same spear so the set reads as one weapon.
"""
import math

from PIL import Image, ImageDraw

S = 16


def _new():
    mask = Image.new("1", (S, S), 0)
    return mask, ImageDraw.Draw(mask)


def _spear(d, start, tip, head=5, half=2.5, width=2):
    """A spear from start to tip: a shaft and a leaf-shaped head at the tip."""
    (x0, y0), (x1, y1) = start, tip
    length = math.hypot(x1 - x0, y1 - y0)
    ux, uy = (x1 - x0) / length, (y1 - y0) / length
    px, py = -uy, ux
    bx, by = x1 - ux * head, y1 - uy * head
    d.line([(x0, y0), (bx, by)], fill=1, width=width)
    d.polygon([(x1, y1), (bx + px * half, by + py * half), (bx - ux * 1.2, by - uy * 1.2), (bx - px * half, by - py * half)], fill=1)


def _arrowhead(d, point, direction, size=3.5):
    x, y = point
    dx, dy = direction
    n = math.hypot(dx, dy)
    dx, dy = dx / n, dy / n
    px, py = -dy, dx
    d.polygon([(x + dx * size, y + dy * size), (x + px * size * 0.8, y + py * size * 0.8), (x - px * size * 0.8, y - py * size * 0.8)], fill=1)


def impale():
    """A spear thrust to the right with speed lines behind it."""
    mask, d = _new()
    _spear(d, (4, 7.5), (15, 7.5), head=5, half=3)
    d.line([(0, 3), (5, 3)], fill=1)
    d.line([(0, 12), (5, 12)], fill=1)
    d.line([(0, 7), (2, 7)], fill=1, width=2)
    return mask


def dragoon_dive():
    """A spear plunging straight down onto the ground, with speed lines."""
    mask, d = _new()
    _spear(d, (7.5, 0), (7.5, 12), head=5, half=3)
    d.line([(2, 0), (2, 6)], fill=1)
    d.line([(13, 0), (13, 6)], fill=1)
    d.line([(0, 15), (15, 15)], fill=1)
    for x, y in ((3, 13), (12, 13), (2, 12), (13, 12), (5, 14), (10, 14)):
        d.point((x, y), fill=1)
    return mask


def lance_charge():
    """A jousting lance: a long cone with a hand guard, pointing right."""
    mask, d = _new()
    d.polygon([(4, 4), (15, 7), (15, 8), (4, 11)], fill=1)
    d.line([(3, 2), (3, 13)], fill=1, width=2)
    d.line([(0, 7.5), (2, 7.5)], fill=1, width=2)
    return mask


def javelin():
    """A spear flying up and to the right, with a dotted flight path behind it."""
    mask, d = _new()
    _spear(d, (5, 11), (15, 1), head=5, half=2.5)
    for x, y in ((0, 15), (2, 14), (3, 12)):
        d.point((x, y), fill=1)
        d.point((x + 1, y), fill=1)
    return mask


def pole_vault():
    """A spear planted upright and an arc vaulting over it."""
    mask, d = _new()
    _spear(d, (2.5, 15), (2.5, 3), head=5, half=2.5)
    d.arc((5, 3, 19, 27), start=190, end=295, fill=1, width=1)
    _arrowhead(d, (14, 7), (0.6, 1), size=3)
    return mask


def sweeping_arc():
    """A spear head swinging round in a near-full circle."""
    mask, d = _new()
    d.arc((1, 1, 14, 14), start=200, end=120, fill=1, width=2)
    angle = math.radians(120)
    cx, cy, r = 7.5, 7.5, 6.5
    point = (cx + r * math.cos(angle), cy + r * math.sin(angle))
    _arrowhead(d, point, (-math.sin(angle) * -1, math.cos(angle) * 1), size=3.5)
    d.rectangle((6, 6, 9, 9), fill=1)
    return mask


def spear_wall():
    """Three spears planted side by side, braced with a crossbar."""
    mask, d = _new()
    for x in (2.5, 7.5, 12.5):
        _spear(d, (x, 15), (x, 0), head=6, half=2.6, width=1)
    d.line([(0, 11), (15, 11)], fill=1)
    return mask


def thrust_flurry():
    """Three quick thrusts stacked and staggered, all pointing right."""
    mask, d = _new()
    for y, x in ((2.5, 4), (7.5, 0), (12.5, 4)):
        _spear(d, (x, y), (x + 11, y), head=4, half=2, width=1)
    return mask


def pinning_throw():
    """A spear stuck into the ground at an angle, with a burst where it pins."""
    mask, d = _new()
    _spear(d, (14, 0), (6, 13), head=5, half=2.5)
    d.line([(0, 15), (15, 15)], fill=1)
    for a, b in (((1, 10), (3, 12)), ((10, 10), (12, 8)), ((1, 15), (1, 15))):
        d.line([a, b], fill=1)
    d.line([(2, 13), (3, 13)], fill=1)
    d.line([(10, 13), (12, 13)], fill=1)
    return mask


def dragon_leap():
    """A high leap arc landing spear-first, with impact sparks."""
    mask, d = _new()
    d.arc((0, 3, 12, 27), start=185, end=320, fill=1, width=2)
    _spear(d, (8, 6), (13, 14), head=4, half=2)
    for (x0, y0), (x1, y1) in (((15, 11), (15, 13)), ((10, 15), (11, 15)), ((15, 15), (15, 15))):
        d.line([(x0, y0), (x1, y1)], fill=1)
    return mask


def gungnir():
    """A giant spear falling point-first, shining, onto a ring of light."""
    mask, d = _new()
    d.line([(7.5, 0), (7.5, 6)], fill=1, width=2)
    d.line([(4, 5), (11, 5)], fill=1, width=2)  # crossguard
    d.polygon([(7.5, 15), (3, 8), (7.5, 6), (12, 8)], fill=1)  # big head
    for (x0, y0), (x1, y1) in (((1, 1), (3, 3)), ((14, 1), (12, 3)), ((0, 8), (1, 8)), ((14, 8), (15, 8))):
        d.line([(x0, y0), (x1, y1)], fill=1)
    d.point((1, 14), fill=1)
    d.point((14, 14), fill=1)
    d.point((2, 15), fill=1)
    d.point((13, 15), fill=1)
    return mask


def wind_arrow():
    """An arrow flying right, bursting into gust swirls where it lands."""
    mask, d = _new()
    d.line([(0, 8), (8, 8)], fill=1, width=2)             # shaft
    _arrowhead(d, (9, 8.5), (1, 0), size=3)
    d.line([(0, 5), (2, 7)], fill=1)                      # fletching
    d.line([(0, 11), (2, 9)], fill=1)
    d.arc((9, 1, 15, 7), 200, 90, fill=1)                 # gust swirls
    d.arc((10, 10, 15, 15), 270, 160, fill=1)
    d.arc((12, 5, 16, 11), 300, 120, fill=1)
    return mask


SKILL_ART = {
    "wind_arrow": wind_arrow,
    "impale": impale, "dragoon_dive": dragoon_dive, "lance_charge": lance_charge, "javelin": javelin,
    "pole_vault": pole_vault, "sweeping_arc": sweeping_arc, "spear_wall": spear_wall, "thrust_flurry": thrust_flurry,
    "pinning_throw": pinning_throw, "dragon_leap": dragon_leap, "gungnir": gungnir,
}


# ============================================================ Augments (12x12, drawn on a gem badge)

def _small():
    mask = Image.new("1", (12, 12), 0)
    return mask, ImageDraw.Draw(mask)


def aug_brutal():
    """Two thick upward chevrons: more power."""
    mask, d = _small()
    for y in (1, 6):
        d.line([(1, y + 5), (6, y)], fill=1, width=2)
        d.line([(6, y), (11, y + 5)], fill=1, width=2)
    return mask


def aug_swift():
    """A double chevron pointing right, with a speed line."""
    mask, d = _small()
    for x in (1, 6):
        d.line([(x, 1), (x + 4, 6)], fill=1, width=2)
        d.line([(x + 4, 6), (x, 11)], fill=1, width=2)
    return mask


def aug_execute():
    """A skull."""
    mask, d = _small()
    d.ellipse((1, 0, 11, 9), fill=1)
    d.rectangle((3, 8, 9, 11), fill=1)
    for box in ((3, 3, 5, 5), (7, 3, 9, 5)):
        d.rectangle(box, fill=0)
    d.point([(5, 10), (7, 10)], fill=0)
    return mask


def aug_searing():
    """A flame."""
    mask, d = _small()
    d.polygon([(6, 0), (10, 6), (10, 9), (8, 11), (4, 11), (2, 9), (2, 6), (4, 4), (5, 6)], fill=1)
    d.polygon([(6, 6), (8, 9), (6, 11), (4, 9)], fill=0)
    return mask


def aug_frost():
    """A snowflake."""
    mask, d = _small()
    d.line([(6, 0), (6, 11)], fill=1, width=2)
    d.line([(1, 3), (11, 9)], fill=1)
    d.line([(1, 9), (11, 3)], fill=1)
    for a, b in (((4, 1), (6, 3)), ((8, 1), (6, 3)), ((4, 10), (6, 8)), ((8, 10), (6, 8))):
        d.line([a, b], fill=1)
    return mask


def aug_venom():
    """A drop of poison."""
    mask, d = _small()
    d.polygon([(6, 0), (10, 6), (6, 11), (2, 6)], fill=1)
    d.ellipse((2, 4, 10, 11), fill=1)
    d.rectangle((4, 6, 5, 7), fill=0)
    return mask


def aug_sunder():
    """A shield with a crack through it."""
    mask, d = _small()
    d.polygon([(1, 0), (11, 0), (11, 6), (6, 11), (1, 6)], fill=1)
    d.line([(6, 0), (4, 4), (7, 6), (5, 11)], fill=0, width=1)
    return mask


def aug_vampiric():
    """A heart with a fang bite."""
    mask, d = _small()
    d.ellipse((0, 1, 6, 7), fill=1)
    d.ellipse((5, 1, 11, 7), fill=1)
    d.polygon([(0, 5), (11, 5), (6, 11)], fill=1)
    d.polygon([(4, 0), (5, 4), (6, 0)], fill=0)
    return mask


def aug_reaping():
    """A gravestone with a cross: kills pay off."""
    mask, d = _small()
    d.ellipse((2, 0, 10, 7), fill=1)
    d.rectangle((2, 4, 10, 10), fill=1)
    d.rectangle((0, 10, 11, 11), fill=1)
    d.line([(6, 2), (6, 7)], fill=0)
    d.line([(4, 4), (8, 4)], fill=0)
    return mask


def aug_echo():
    """Sound rings spreading out."""
    mask, d = _small()
    d.ellipse((0, 4, 3, 7), fill=1)
    d.arc((-2, 1, 7, 10), start=300, end=60, fill=1)
    d.arc((-4, -1, 10, 12), start=300, end=60, fill=1)
    d.arc((-6, -3, 13, 14), start=305, end=55, fill=1)
    return mask


def aug_guarded():
    """A solid shield with a cross."""
    mask, d = _small()
    d.polygon([(1, 0), (11, 0), (11, 6), (6, 11), (1, 6)], fill=1)
    d.line([(6, 2), (6, 8)], fill=0)
    d.line([(3, 4), (9, 4)], fill=0)
    return mask


def aug_fleet():
    """A wing."""
    mask, d = _small()
    d.polygon([(0, 9), (11, 0), (11, 3), (8, 6), (10, 6), (6, 9), (8, 9), (3, 11)], fill=1)
    return mask


def aug_mending():
    """A plus: healing."""
    mask, d = _small()
    d.rectangle((4, 0, 7, 11), fill=1)
    d.rectangle((0, 4, 11, 7), fill=1)
    return mask


def aug_vigor():
    """A sword pointing up with a power burst."""
    mask, d = _small()
    d.line([(6, 0), (6, 8)], fill=1, width=2)
    d.line([(3, 8), (9, 8)], fill=1, width=2)
    d.line([(6, 9), (6, 11)], fill=1)
    d.point([(1, 2), (11, 2), (2, 5), (10, 5)], fill=1)
    return mask


AUGMENT_ART = {
    "brutal": aug_brutal, "swift": aug_swift, "execute": aug_execute, "searing": aug_searing, "frost": aug_frost,
    "venom": aug_venom, "sunder": aug_sunder, "vampiric": aug_vampiric, "reaping": aug_reaping, "echo": aug_echo,
    "guarded": aug_guarded, "fleet": aug_fleet, "mending": aug_mending, "vigor": aug_vigor,
}
AUGMENT_COLORS = {
    "brutal": 0xB03A2E, "swift": 0x3498DB, "execute": 0x5B2C6F, "searing": 0xE67E22, "frost": 0x5DADE2,
    "venom": 0x27AE60, "sunder": 0x707B7C, "vampiric": 0x922B21, "reaping": 0x34495E, "echo": 0x17A589,
    "guarded": 0x839192, "fleet": 0xD4AC0D, "mending": 0xEC7063, "vigor": 0xCA6F1E,
}


# ============================================================ Lancer path abilities (16x16)

def lancer_skewer():
    """A spear piercing straight through a target ring."""
    mask, d = _new()
    d.ellipse((2, 3, 12, 13), outline=1, width=2)
    _spear(d, (0, 15), (15, 0), head=5, half=2.5, width=2)
    return mask


def lancer_momentum_strike():
    """A spear driving forward with motion lines and an impact star."""
    mask, d = _new()
    _spear(d, (0, 12), (11, 4), head=4, half=2)
    d.line([(0, 6), (4, 3)], fill=1)
    d.line([(3, 15), (8, 11)], fill=1)
    for p in ((13, 2), (15, 4), (14, 6), (12, 0)):
        d.line([(12.5, 3.5), p], fill=1)
    return mask


def lancer_long_reach():
    """A very long spear with a double-headed range arrow under it."""
    mask, d = _new()
    _spear(d, (0, 5), (15, 5), head=4, half=2.5)
    d.line([(1, 12), (14, 12)], fill=1)
    _arrowhead(d, (14, 12), (1, 0), size=2.5)
    _arrowhead(d, (1, 12), (-1, 0), size=2.5)
    return mask


def lancer_cavalier():
    """A horse head."""
    mask, d = _new()
    d.polygon([(5, 1), (9, 0), (13, 5), (15, 9), (13, 11), (10, 9), (9, 15), (3, 15), (3, 8), (5, 4)], fill=1)
    d.polygon([(5, 1), (6, -1), (7, 1)], fill=1)
    d.point((10, 4), fill=0)
    return mask


def lancer_charge_line():
    """A spear charging along a dashed line."""
    mask, d = _new()
    _spear(d, (5, 8), (15, 8), head=4, half=2.5)
    for x in (0, 3):
        d.line([(x, 8), (x + 1, 8)], fill=1, width=2)
    d.line([(4, 3), (9, 3)], fill=1)
    d.line([(4, 13), (9, 13)], fill=1)
    return mask


def lancer_war_banner():
    """A banner on a spear shaft."""
    mask, d = _new()
    d.line([(3, 1), (3, 15)], fill=1, width=2)
    d.polygon([(2.5, 0), (4.5, 0), (3.5, -2)], fill=1)
    d.polygon([(5, 2), (14, 2), (14, 11), (9.5, 8), (5, 11)], fill=1)
    d.rectangle((8, 4, 11, 6), fill=0)
    return mask


LANCER_ART = {
    "skewer": lancer_skewer, "momentum_strike": lancer_momentum_strike, "long_reach": lancer_long_reach,
    "cavalier": lancer_cavalier, "charge_line": lancer_charge_line, "war_banner": lancer_war_banner,
}


# ============================================================ Smithing perk motifs (16x16)

def motif_hammer():
    mask, d = _new()
    d.polygon([(2, 3), (9, -1), (12, 4), (5, 8)], fill=1)
    d.line([(8, 6), (14, 15)], fill=1, width=2)
    return mask


def motif_template():
    mask, d = _new()
    d.rectangle((2, 1, 13, 14), outline=1, width=1)
    d.rectangle((5, 4, 10, 11), fill=1)
    d.rectangle((7, 6, 8, 9), fill=0)
    for p in ((3, 2), (12, 2), (3, 13), (12, 13)):
        d.point(p, fill=1)
    return mask


def motif_chestplate():
    mask, d = _new()
    d.polygon([(1, 2), (5, 0), (8, 3), (11, 0), (15, 2), (13, 6), (13, 14), (3, 14), (3, 6)], fill=1)
    d.line([(8, 5), (8, 12)], fill=0)
    return mask


def motif_plus():
    mask, d = _new()
    d.rectangle((6, 1, 9, 14), fill=1)
    d.rectangle((1, 6, 14, 9), fill=1)
    return mask


MOTIF_DRAWN = {"hammer": motif_hammer, "template": motif_template, "chestplate": motif_chestplate, "plus": motif_plus}
