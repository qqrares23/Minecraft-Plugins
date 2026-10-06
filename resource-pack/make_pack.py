#!/usr/bin/env python3
"""Builds the server resource pack: one 32x32 badge icon per skill, path, profession, enchantment and weapon.

Each icon is a coloured badge (colour and rim read straight from the Java enums) with a pixel-art
picture from ability_art.py, enchant_art.py, glyph_art.py, perk_art.py or skill_art.py; a new
skill, profession or enchantment needs a picture there or the build stops. Output: build/pack.zip and build/preview.png.
"""
import json
import os
import re
import zipfile
from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFont
from ability_art import ART
from enchant_art import ENCHANT_ART
from glyph_art import PROFESSION_ART, SKILL_GRIDS, TALENT_ART, TALENT_COLORS, WEAPON_ART
from perk_art import MOTIFS, PERK_MOTIF
from skill_art import AUGMENT_ART, AUGMENT_COLORS, LANCER_ART, MOTIF_DRAWN, SKILL_ART

NAMESPACE = "raresb"
SIZE = 32
# Resource pack format of Minecraft 26.3 (version.json in the server jar: resource_major).
PACK_FORMAT = 97
PLUGINS = ".."

FONTS = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/truetype/ancient-scripts/Symbola_hint.ttf",
    "/usr/share/fonts/truetype/noto/NotoSansSymbols2-Regular.ttf",
    "/usr/share/fonts/truetype/noto/NotoSansSymbols-Regular.ttf",
    "/usr/share/fonts/truetype/noto/NotoSansMath-Regular.ttf",
]
FONTS = [f for f in FONTS if os.path.exists(f)]
CMAPS = {f: TTFont(f, lazy=True).getBestCmap() for f in FONTS}

WEAPON_COLORS = {
    "SWORD": 0x3F7FD0, "AXE": 0xC0392B, "SHIELD": 0x8794A1, "BOW": 0x3FA65B, "CROSSBOW": 0xE08A2B,
    "TRIDENT": 0x1FA5A0, "MACE": 0x8E5BBF, "PICKAXE": 0x5D6D7E, "WOODCUTTING": 0x8D6E63,
    "HOE": 0x9CCC65, "SHOVEL": 0xA1887F, "FISHING_ROD": 0x4FC3F7, "SPEAR": 0xB8860B,
}
PATH_SYMBOLS = {"duelist": "sword", "warbringer": "axe", "ranger": "bow", "arbalist": "crossbow", "tidecaller": "trident",
                "juggernaut": "mace", "sentinel": "shield", "shadow": "dagger_moon", "beastmaster": "paw", "pyromancer": "flame",
                "frost_warden": "snowflake", "lancer": "spear"}
WEAPON_SYMBOLS = {
    "SWORD": "sword", "AXE": "axe", "SHIELD": "shield", "BOW": "bow", "CROSSBOW": "crosshair", "TRIDENT": "trident", "MACE": "mace",
    "PICKAXE": "pickaxe", "WOODCUTTING": "tree", "HOE": "hoe", "SHOVEL": "shovel", "FISHING_ROD": "fishing_rod", "SPEAR": "spear",
}
SILVER = (215, 220, 230)
TIER_RIMS = {"10": (205, 127, 50), "20": (215, 220, 230), "30": (255, 200, 60), "40": (80, 220, 140), "50": (200, 120, 255)}
GOLD = (255, 200, 60)


def rgb(value):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255)


def shade(color, factor):
    """factor < 1 darkens, > 1 lightens towards white."""
    if factor <= 1:
        return tuple(int(c * factor) for c in color)
    return tuple(int(c + (255 - c) * (factor - 1)) for c in color)


def font_for(symbol):
    for path in FONTS:
        if all(ord(ch) in CMAPS[path] for ch in symbol):
            return path
    raise SystemExit(f"no font has {symbol!r} (U+{ord(symbol[0]):04X})")


def glyph_mask(symbol, box):
    """The symbol as a hard-edged (pixel-art) mask, as large as fits in a box x box square."""
    path = font_for(symbol)
    best = None
    for size in range(40, 7, -1):
        font = ImageFont.truetype(path, size)
        left, top, right, bottom = font.getbbox(symbol)
        if right - left <= box and bottom - top <= box:
            best = (font, left, top, right - left, bottom - top)
            break
    font, left, top, width, height = best
    mask = Image.new("1", (width, height), 0)
    draw = ImageDraw.Draw(mask)
    draw.fontmode = "1"  # no anti-aliasing
    draw.text((-left, -top), symbol, fill=1, font=font)
    return mask


def drawn_shovel():
    """A shovel lying diagonally: D-grip top-right, spade blade bottom-left."""
    mask = Image.new("1", (16, 16), 0)
    d = ImageDraw.Draw(mask)
    d.rectangle((12, 0, 15, 3), outline=1)             # D-grip
    d.line([(12, 4), (7, 9)], fill=1, width=2)         # handle
    d.polygon([(0, 15), (0, 10), (3, 7), (8, 12), (5, 15)], fill=1)  # spade blade, tip at the corner
    d.point([(5, 10), (6, 10)], fill=1)                # where blade meets handle
    return mask


def drawn_fishing_rod():
    """A bent rod from bottom-left to top-right, with the line hanging down to a hook."""
    mask = Image.new("1", (16, 16), 0)
    d = ImageDraw.Draw(mask)
    d.line([(0, 15), (4, 10), (8, 5), (12, 2), (14, 1)], fill=1, width=2)  # rod
    d.line([(1, 13), (3, 15)], fill=1)                 # reel
    d.line([(14, 2), (14, 11)], fill=1)                # line
    d.line([(14, 12), (14, 14), (12, 14), (12, 12)], fill=1)  # hook
    return mask


def drawn_crosshair():
    """A crosshair: thin ring, four lines with a gap in the middle, a centre dot."""
    mask = Image.new("1", (16, 16), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse((2, 2, 13, 13), outline=1, width=1)
    for box in ((7, 0, 8, 5), (7, 10, 8, 15), (0, 7, 5, 8), (10, 7, 15, 8)):
        d.rectangle(box, fill=1)
    d.rectangle((7, 7, 8, 8), fill=1)
    return mask


def drawn_mace():
    """A mace: handle bottom-left, round spiked head top-right."""
    mask = Image.new("1", (16, 16), 0)
    d = ImageDraw.Draw(mask)
    d.line([(1, 14), (8, 7)], fill=1, width=2)         # handle
    d.point([(0, 15), (0, 14), (1, 15)], fill=1)       # pommel
    d.ellipse((7, 2, 13, 8), fill=1)                   # head
    for x, y in ((10, 0), (10, 1), (15, 5), (14, 5), (5, 5), (6, 5), (10, 10), (10, 9), (14, 1), (6, 9)):
        d.point((x, y), fill=1)                        # spikes
    return mask


def drawn_spear():
    """A spear: long shaft bottom-left to top-right, a leaf-shaped head at the top."""
    mask = Image.new("1", (16, 16), 0)
    d = ImageDraw.Draw(mask)
    d.line([(0, 15), (10, 5)], fill=1, width=1)        # shaft
    d.line([(1, 15), (11, 5)], fill=1, width=1)
    d.polygon([(15, 0), (9, 3), (11, 5), (12, 7)], fill=1)  # head
    d.line([(8, 5), (11, 8)], fill=1)                  # binding below the head
    return mask


def drawn_sword():
    """A sword: tip top-right, crossguard and grip bottom-left."""
    mask = Image.new("1", (16, 16), 0)
    d = ImageDraw.Draw(mask)
    d.line([(15, 0), (6, 9)], fill=1, width=2)         # blade
    d.line([(3, 8), (7, 12)], fill=1, width=2)         # crossguard
    d.line([(4, 11), (1, 14)], fill=1)                 # grip
    d.rectangle((0, 14, 1, 15), fill=1)                # pommel
    return mask


def drawn_axe():
    """An axe: handle bottom-left to top-right, a curved blade fanning out to the top-left."""
    mask = Image.new("1", (16, 16), 0)
    d = ImageDraw.Draw(mask)
    d.pieslice((2, -3, 18, 13), 170, 280, fill=1)      # blade, curved edge
    d.line([(1, 14), (13, 2)], fill=1, width=2)        # handle
    d.rectangle((13, 0, 15, 2), fill=1)                # butt of the head
    return mask


def drawn_bow():
    """A bow bent towards the top-left, its string, and a nocked arrow."""
    mask = Image.new("1", (16, 16), 0)
    d = ImageDraw.Draw(mask)
    d.arc((1, 1, 29, 29), 180, 270, fill=1, width=2)   # limbs
    d.line([(2, 15), (15, 2)], fill=1)                 # string
    d.line([(4, 4), (12, 12)], fill=1)                 # arrow shaft
    d.polygon([(3, 3), (6, 3), (3, 6)], fill=1)        # arrowhead
    d.line([(11, 13), (13, 13), (13, 11)], fill=1)     # fletching
    return mask


def drawn_crossbow():
    """A crossbow seen from above: curved bow, string, stock and bolt."""
    mask = Image.new("1", (16, 16), 0)
    d = ImageDraw.Draw(mask)
    d.arc((0, 2, 15, 12), 180, 360, fill=1, width=2)   # bow
    d.line([(1, 7), (7, 10), (8, 10), (14, 7)], fill=1)  # string
    d.rectangle((7, 4, 8, 15), fill=1)                 # stock
    d.polygon([(7, 0), (8, 0), (10, 3), (5, 3)], fill=1)  # bolt head
    return mask


def drawn_trident():
    """A trident: three prongs on a long shaft."""
    mask = Image.new("1", (16, 16), 0)
    d = ImageDraw.Draw(mask)
    d.rectangle((7, 5, 8, 15), fill=1)                 # shaft
    d.rectangle((2, 5, 13, 6), fill=1)                 # crossbar
    for x in (2, 7, 12):
        d.rectangle((x, 1, x + 1, 5), fill=1)          # prongs
    d.point([(2, 0), (7, 0), (8, 0), (13, 0)], fill=1)
    return mask


def drawn_shield():
    """A heater shield with a cross."""
    mask = Image.new("1", (16, 16), 0)
    d = ImageDraw.Draw(mask)
    d.polygon([(1, 0), (14, 0), (14, 8), (8, 15), (7, 15), (1, 8)], outline=1)
    d.polygon([(2, 1), (13, 1), (13, 8), (8, 14), (7, 14), (2, 8)], outline=1)
    d.rectangle((7, 3, 8, 12), fill=1)
    d.rectangle((4, 5, 11, 6), fill=1)
    return mask


def drawn_dagger_moon():
    """A crescent moon behind a dagger."""
    mask = Image.new("1", (16, 16), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse((0, 0, 11, 11), fill=1)
    d.ellipse((3, -2, 14, 9), fill=0)                  # crescent
    d.line([(15, 4), (8, 11)], fill=1, width=2)        # blade
    d.line([(6, 9), (9, 12)], fill=1)                  # guard
    d.line([(7, 12), (5, 14)], fill=1)                 # grip
    d.point((4, 15), fill=1)
    return mask


def drawn_paw():
    """A paw print."""
    mask = Image.new("1", (16, 16), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse((3, 7, 12, 15), fill=1)                  # pad
    for box in ((0, 4, 3, 8), (3, 0, 6, 4), (9, 0, 12, 4), (12, 4, 15, 8)):
        d.ellipse(box, fill=1)                         # toes
    return mask


def drawn_flame():
    """A flame with a hollow core."""
    mask = Image.new("1", (16, 16), 0)
    d = ImageDraw.Draw(mask)
    d.polygon([(8, 0), (11, 4), (13, 3), (14, 9), (12, 14), (9, 15), (6, 15), (3, 14), (1, 9), (3, 5), (5, 7), (6, 3)], fill=1)
    d.polygon([(8, 7), (10, 10), (10, 13), (8, 14), (6, 13), (6, 10)], fill=0)
    return mask


def drawn_snowflake():
    """A six-armed snowflake."""
    import math
    mask = Image.new("1", (16, 16), 0)
    d = ImageDraw.Draw(mask)
    cx = cy = 7.5
    for i in range(6):
        a = math.pi / 3 * i + math.pi / 2
        end = (round(cx + math.cos(a) * 7.5), round(cy - math.sin(a) * 7.5))
        d.line([(round(cx), round(cy)), end], fill=1)
        mid = (cx + math.cos(a) * 4.5, cy - math.sin(a) * 4.5)
        for side in (-0.9, 0.9):
            b = a + side
            d.line([(round(mid[0]), round(mid[1])), (round(mid[0] + math.cos(b) * 2.5), round(mid[1] - math.sin(b) * 2.5))], fill=1)
    d.rectangle((6, 6, 9, 9), fill=1)
    return mask


# Icons drawn as pixel art instead of a font symbol.
DRAWN = {"spear": drawn_spear, "shovel": drawn_shovel, "fishing_rod": drawn_fishing_rod, "crosshair": drawn_crosshair, "mace": drawn_mace,
         "sword": drawn_sword, "axe": drawn_axe, "bow": drawn_bow, "crossbow": drawn_crossbow, "trident": drawn_trident,
         "shield": drawn_shield, "dagger_moon": drawn_dagger_moon, "paw": drawn_paw, "flame": drawn_flame,
         "snowflake": drawn_snowflake}
DRAWN.update(MOTIF_DRAWN)
DRAWN.update({"pickaxe": lambda: grid_mask(WEAPON_ART["pickaxe"]), "tree": lambda: grid_mask(WEAPON_ART["woodcutting"]),
              "hoe": lambda: grid_mask(WEAPON_ART["hoe"])})


def art_mask(name):
    """A path ability's picture: a pixel-art grid from ability_art.py, or a drawing from skill_art.py (Lancer)."""
    if name in LANCER_ART:
        return LANCER_ART[name]()
    return grid_mask(ART[name])


def perk_mask(perk):
    """The picture of a profession perk (perk_art.py)."""
    motif = PERK_MOTIF[perk]
    if motif.startswith("art:"):
        return art_mask(motif[4:])
    if motif.startswith("drawn:"):
        return DRAWN[motif[6:]]()
    return grid_mask(MOTIFS[motif])


def grid_mask(grid):
    """A '#'/'.' grid as a mask."""
    rows = [row for row in grid.strip("\n").split("\n")]
    mask = Image.new("1", (max(len(row) for row in rows), len(rows)), 0)
    for y, row in enumerate(rows):
        for x, cell in enumerate(row):
            if cell == "#":
                mask.putpixel((x, y), 1)
    return mask


def shape_mask(shape):
    mask = Image.new("L", (SIZE, SIZE), 0)
    draw = ImageDraw.Draw(mask)
    if shape == "tile":
        draw.rounded_rectangle((1, 1, SIZE - 2, SIZE - 2), radius=5, fill=255)
    elif shape == "circle":
        draw.ellipse((1, 1, SIZE - 2, SIZE - 2), fill=255)
    elif shape == "gem":
        middle = SIZE // 2
        draw.polygon([(middle - 1, 1), (middle, 1), (SIZE - 2, middle - 1), (SIZE - 2, middle), (middle, SIZE - 2),
                      (middle - 1, SIZE - 2), (1, middle), (1, middle - 1)], fill=255)
    elif shape == "hex":
        draw.polygon([(8, 1), (SIZE - 9, 1), (SIZE - 2, SIZE // 2 - 1), (SIZE - 2, SIZE // 2), (SIZE - 9, SIZE - 2),
                      (8, SIZE - 2), (1, SIZE // 2), (1, SIZE // 2 - 1)], fill=255)
    elif shape == "shield":
        draw.polygon([(3, 2), (SIZE - 4, 2), (SIZE - 4, 17), (SIZE // 2, SIZE - 2), (SIZE // 2 - 1, SIZE - 2), (3, 17)], fill=255)
    return mask.point(lambda v: 255 if v > 127 else 0)


def badge(symbol, color, shape="tile", rim=None):
    color = rgb(color) if isinstance(color, int) else color
    if sum(color) / 3 > 165:
        color = shade(color, 0.78)  # keep the white symbol readable on pale colours
    rim = rim or shade(color, 0.45)
    outer = shape_mask(shape)
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = image.load()
    op = outer.load()

    def inside(x, y, margin):
        return all(0 <= x + dx < SIZE and 0 <= y + dy < SIZE and op[x + dx, y + dy]
                   for dx in range(-margin, margin + 1) for dy in range(-margin, margin + 1))

    for y in range(SIZE):
        for x in range(SIZE):
            if not op[x, y]:
                continue
            if not inside(x, y, 1):
                px[x, y] = (*shade(rim, 0.55), 255)  # outline
            elif not inside(x, y, 2):
                px[x, y] = (*rim, 255)  # rim
            else:
                # Face: lighter at the top, darker at the bottom, with a bright top-left edge.
                face = shade(color, 1.22 - 0.5 * y / SIZE)
                if not inside(x - 1, y - 1, 2) and inside(x, y, 2):
                    face = shade(face, 1.35)
                px[x, y] = (*face, 255)

    box = {"tile": 18, "gem": 13}.get(shape, 16)
    if isinstance(symbol, Image.Image):
        mask = symbol
    else:
        mask = DRAWN[symbol]() if symbol in DRAWN else glyph_mask(symbol, box)
    ox = (SIZE - mask.width) // 2
    oy = (SIZE - mask.height) // 2 - (2 if shape == "shield" else 0)
    shadow = (*shade(color, 0.3), 255)
    for dx, dy, fill in ((1, 1, shadow), (0, 1, shadow), (1, 0, shadow), (0, 0, (255, 255, 255, 255))):
        image.paste(Image.new("RGBA", mask.size, fill), (ox + dx, oy + dy), mask)
    return image


def read(path):
    with open(os.path.join(PLUGINS, path), encoding="utf-8") as source:
        return source.read()


def icons():
    """(item model path, image) for everything that gets an icon."""
    result = []
    skill = re.compile(r'^\s+([A-Z_]+)\("[^"]*", "[^"]*", "([^"]+)", Weapon\.([A-Z_]+), [^,]+, Kind\.([A-Z]+)', re.M)
    for name, symbol, weapon, kind in skill.findall(read("weapon-skills/src/main/java/com/raresb/weaponskills/Skill.java")):
        rim = GOLD if kind == "ULTIMATE" else None
        if name.lower() in SKILL_ART:
            picture = SKILL_ART[name.lower()]()
        elif name.lower() in SKILL_GRIDS:
            picture = grid_mask(SKILL_GRIDS[name.lower()])
        else:
            raise SystemExit(f"no pixel art for skill {name.lower()} in skill_art.py / glyph_art.py")
        result.append((f"skill/{name.lower()}", badge(picture, WEAPON_COLORS[weapon], "tile", rim)))
    path = re.compile(r'^\s+([A-Z_]+)\("[^"]*", Weapon\.[A-Z_]+, 0x([0-9A-Fa-f]{6})', re.M)
    path_colors = {}
    for name, color in path.findall(read("paths/src/main/java/com/raresb/paths/Path.java")):
        path_colors[name] = int(color, 16)
        result.append((f"path/{name.lower()}", badge(PATH_SYMBOLS[name.lower()], int(color, 16), "shield", GOLD)))
    # Path abilities: the path's colour; rim by slot (plain = on-hit, silver = always-on, gold = M2 skill or spell).
    ability = re.compile(r'^\s+([A-Z_]+)\(Path\.([A-Z_]+), Path\.Slot\.([A-Z_]+),', re.M)
    for name, owner, slot in ability.findall(read("paths/src/main/java/com/raresb/paths/Ability.java")):
        if name.lower() not in ART and name.lower() not in LANCER_ART:
            raise SystemExit(f"no pixel art for ability {name.lower()} in ability_art.py")
        rim = {"PASSIVE": SILVER, "SKILL": GOLD, "SPELL": GOLD}.get(slot)
        result.append((f"ability/{name.lower()}", badge(art_mask(name.lower()), path_colors[owner], "tile", rim)))
    # Path talents (Path.Talent): a shield badge like the paths, silver rim.
    talent_enum = read("paths/src/main/java/com/raresb/paths/Path.java").split("enum Talent", 1)[1]
    for name in re.findall(r'^\s+([A-Z_]+)\("', talent_enum, re.M):
        if name.lower() not in TALENT_ART:
            raise SystemExit(f"no pixel art for talent {name.lower()} in glyph_art.py")
        result.append((f"talent/{name.lower()}", badge(grid_mask(TALENT_ART[name.lower()]), TALENT_COLORS[name.lower()], "shield", SILVER)))
    profession = re.compile(r'^\s+([A-Z_]+)\("[^"]*", "([^"]+)", 0x([0-9A-Fa-f]{6})', re.M)
    profession_colors = {}
    for name, symbol, color in profession.findall(read("professions/src/main/java/com/raresb/professions/Profession.java")):
        profession_colors[name] = int(color, 16)
        if name.lower() not in PROFESSION_ART:
            raise SystemExit(f"no pixel art for profession {name.lower()} in glyph_art.py")
        result.append((f"profession/{name.lower()}", badge(grid_mask(PROFESSION_ART[name.lower()]), int(color, 16), "circle")))
    # Profession perks: the profession's colour; rim by tier (bronze 10 ... amethyst 50).
    perk = re.compile(r'^\s+([A-Z_]+)\(Profession\.([A-Z_]+), (\d+), "', re.M)
    for name, owner, tier in perk.findall(read("professions/src/main/java/com/raresb/professions/Perk.java")):
        if name.lower() not in PERK_MOTIF:
            raise SystemExit(f"no picture for perk {name.lower()} in perk_art.py")
        result.append((f"perk/{name.lower()}", badge(perk_mask(name.lower()), profession_colors[owner], "circle", TIER_RIMS[tier])))
    # Enchantments: pixel art from enchant_art.py on a gem in the enchantment's colour; gold rim = treasure.
    enchant = re.compile(r'^\s+([A-Z_]+)\("[^"]*", 0x([0-9A-Fa-f]{6}), Target\.[A-Z_]+, Category\.[A-Z_]+, (?:\d+,\s*){6}(true,\s*)?"', re.M)
    for name, color, treasure in enchant.findall(read("magic-enchants/src/main/java/com/raresb/magicenchants/MagicEnchant.java")):
        if name.lower() not in ENCHANT_ART:
            raise SystemExit(f"no picture for enchantment {name.lower()} in enchant_art.py")
        rim = GOLD if treasure else None
        result.append((f"enchant/{name.lower()}", badge(grid_mask(ENCHANT_ART[name.lower()]), int(color, 16), "gem", rim)))
    for weapon, symbol in WEAPON_SYMBOLS.items():
        result.append((f"weapon/{weapon.lower()}", badge(symbol, WEAPON_COLORS[weapon], "hex", (225, 225, 235))))
    # Skill augments (WeaponSkills Augment.java): one gem badge per augment type.
    augment = re.compile(r'^\s+([A-Z_]+)\("[^"]*", Material\.', re.M)
    for name in augment.findall(read("weapon-skills/src/main/java/com/raresb/weaponskills/Augment.java")):
        if name.lower() not in AUGMENT_ART:
            raise SystemExit(f"no picture for augment {name.lower()} in skill_art.py")
        result.append((f"augment/{name.lower()}", badge(AUGMENT_ART[name.lower()](), AUGMENT_COLORS[name.lower()], "gem", GOLD)))
    return result


def main():
    os.makedirs("build", exist_ok=True)
    made = icons()
    with zipfile.ZipFile("build/pack.zip", "w", zipfile.ZIP_DEFLATED) as pack:
        def add(name, data):
            # Fixed timestamps: the same icons always give the same zip (and the same SHA-1).
            pack.writestr(zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0)), data, zipfile.ZIP_DEFLATED)

        add("pack.mcmeta", json.dumps({"pack": {
            "description": f"Server icons: skills, paths, professions, enchantments ({len(made)})",
            "min_format": PACK_FORMAT, "max_format": PACK_FORMAT}}, indent=2))
        for model, image in made:
            image.save("build/icon.png")
            with open("build/icon.png", "rb") as png:
                add(f"assets/{NAMESPACE}/textures/item/{model}.png", png.read())
            add(f"assets/{NAMESPACE}/models/item/{model}.json", json.dumps(
                {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NAMESPACE}:item/{model}"}}))
            add(f"assets/{NAMESPACE}/items/{model}.json", json.dumps(
                {"model": {"type": "minecraft:model", "model": f"{NAMESPACE}:item/{model}"}}))
        os.remove("build/icon.png")
        made[0][1].resize((64, 64), Image.NEAREST).save("build/pack.png")
        with open("build/pack.png", "rb") as png:
            add("pack.png", png.read())
        os.remove("build/pack.png")

    # A contact sheet to eyeball the result (3x, on a dark inventory-like background).
    columns, scale, gap = 12, 3, 6
    rows = (len(made) + columns - 1) // columns
    cell = SIZE * scale + gap
    sheet = Image.new("RGBA", (columns * cell + gap, rows * cell + gap), (48, 48, 52, 255))
    for index, (_, image) in enumerate(made):
        big = image.resize((SIZE * scale, SIZE * scale), Image.NEAREST)
        sheet.paste(big, (gap + index % columns * cell, gap + index // columns * cell), big)
    sheet.save("build/preview.png")
    print(f"{len(made)} icons")


if __name__ == "__main__":
    main()
