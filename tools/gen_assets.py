#!/usr/bin/env python3
"""Genera las texturas de Maelity (una por personalidad) y el icono de la caja. Requiere Pillow."""
from PIL import Image, ImageDraw
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "maelity", "textures")
os.makedirs(os.path.join(OUT, "entity"), exist_ok=True)
os.makedirs(os.path.join(OUT, "item"), exist_ok=True)

PERSONAS = {  # id: (color RGB, boca/cejas)
    "friendly": (0xFF, 0xD9, 0x3B),
    "playful": (0xFF, 0x9E, 0xD2),
    "tactical": (0x6F, 0xB7, 0xFF),
    "sarcastic": (0xB4, 0x8C, 0xFF),
    "stern": (0xFF, 0x5A, 0x4F),
}
DARK = (40, 28, 12, 255)
WHITE = (255, 255, 255, 255)

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c) + (255,)

def face(img, ox, oy, pid, base):
    """Cara 8x8 en (ox, oy)."""
    px = img.load()
    def put(x, y, col):
        px[ox + x, oy + y] = col
    for y in range(8):
        for x in range(8):
            put(x, y, shade(base, 1.0))
    # ojos
    eyes = {"friendly": [(2, 2), (2, 3), (5, 2), (5, 3)],
            "playful": [(2, 2), (2, 3), (5, 2), (5, 3), (1, 2), (6, 2)],
            "tactical": [(2, 3), (3, 3), (4, 3), (5, 3)],
            "sarcastic": [(2, 3), (3, 3), (5, 2), (5, 3)],
            "stern": [(2, 3), (5, 3)]}[pid]
    for x, y in eyes:
        put(x, y, DARK)
    if pid == "stern":      # cejas enfadadas
        for x, y in [(1, 1), (2, 2), (6, 1), (5, 2)]:
            put(x, y, DARK)
    if pid == "sarcastic":  # ceja levantada
        for x, y in [(5, 1), (6, 1)]:
            put(x, y, DARK)
    mouth = {"friendly": [(1, 5), (2, 6), (3, 6), (4, 6), (5, 6), (6, 5)],
             "playful": [(1, 5), (2, 6), (3, 6), (4, 6), (5, 6), (6, 5)],
             "tactical": [(2, 6), (3, 6), (4, 6), (5, 6)],
             "sarcastic": [(2, 6), (3, 6), (4, 5), (5, 5), (6, 5)],
             "stern": [(1, 6), (2, 5), (3, 5), (4, 5), (5, 5), (6, 6)]}[pid]
    for x, y in mouth:
        put(x, y, DARK)
    if pid == "playful":    # dientes
        for x in range(2, 6):
            put(x, 5, WHITE)

def entity_texture(pid, base):
    img = Image.new("RGBA", (32, 16), shade(base, 1.0))
    d = ImageDraw.Draw(img)
    # Disposición UV de un cubo 8x8x8 en (0,0): top(8,0) bottom(16,0) | w(0,8) front(8,8) e(16,8) back(24,8)
    d.rectangle([8, 0, 15, 7], fill=shade(base, 1.12))
    d.rectangle([16, 0, 23, 7], fill=shade(base, 0.78))
    for x0, f in [(0, 0.92), (16, 0.92), (24, 0.86)]:
        d.rectangle([x0, 8, x0 + 7, 15], fill=shade(base, f))
    face(img, 8, 8, pid, base)
    img.save(os.path.join(OUT, "entity", f"maelity_{pid}.png"))

def box_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    base = PERSONAS["friendly"]
    d.rectangle([2, 5, 13, 14], fill=shade((0x8B, 0x5A, 0x2B), 1.0))      # caja
    d.rectangle([2, 5, 13, 6], fill=shade((0x8B, 0x5A, 0x2B), 1.25))
    d.rectangle([2, 14, 13, 14], fill=shade((0x8B, 0x5A, 0x2B), 0.7))
    d.rectangle([7, 5, 8, 14], fill=shade(base, 1.0))                      # cinta
    d.rectangle([5, 1, 10, 5], fill=shade(base, 1.0))                      # Maelity asomando
    d.point([(6, 3), (9, 3)], fill=DARK)
    d.point([(6, 4), (7, 4), (8, 4), (9, 4)], fill=DARK)
    d.rectangle([0, 0, 0, 0], fill=(0, 0, 0, 0))
    img.save(os.path.join(OUT, "item", "maelity_box.png"))

for pid, c in PERSONAS.items():
    entity_texture(pid, c)
box_icon()
print("ok")
