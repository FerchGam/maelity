#!/usr/bin/env python3
"""Genera las 6 texturas de Maelity (64x32, una por personalidad) sin dependencias externas."""
import struct, sys, zlib, os

W, H = 64, 32
OUT = sys.argv[1] if len(sys.argv) > 1 else "."

def png(path, px):
    raw = b"".join(b"\x00" + b"".join(bytes(px[y][x]) for x in range(W)) for y in range(H))
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(data)

DARK = (40, 30, 10, 255)
WHITE = (255, 255, 255, 255)
PINK = (230, 90, 120, 255)

MOODS = {
    "friendly":   dict(base=(255, 214, 10),  edge=(220, 170, 0)),
    "playful":    dict(base=(255, 224, 40),  edge=(225, 180, 10)),
    "sarcastic":  dict(base=(235, 200, 30),  edge=(200, 160, 0)),
    "protective": dict(base=(255, 196, 0),   edge=(210, 150, 0)),
    "mysterious": dict(base=(120, 90, 170),  edge=(80, 55, 120)),
    "angry":      dict(base=(235, 95, 35),   edge=(180, 60, 10)),
}

def face(mood, put):
    """put(col,row,color) con col,row en 0..9 sobre la cara frontal 10x10."""
    def rect(c0, r0, c1, r1, col):
        for r in range(r0, r1 + 1):
            for c in range(c0, c1 + 1):
                put(c, r, col)
    if mood == "friendly":
        rect(2, 3, 3, 4, DARK); rect(6, 3, 7, 4, DARK)
        put(2, 6, DARK); put(7, 6, DARK); rect(3, 7, 6, 7, DARK)
    elif mood == "playful":
        rect(2, 3, 3, 4, DARK); rect(6, 4, 7, 4, DARK)             # guino
        rect(2, 6, 7, 7, DARK); rect(4, 7, 5, 7, PINK)             # sonrisa grande con lengua
    elif mood == "sarcastic":
        rect(2, 4, 3, 4, DARK); rect(6, 4, 7, 4, DARK)             # parpados caidos
        rect(6, 2, 7, 2, DARK)                                     # ceja levantada
        rect(5, 6, 7, 6, DARK); rect(3, 7, 4, 7, DARK)             # media sonrisa
    elif mood == "protective":
        rect(2, 4, 3, 5, DARK); rect(6, 4, 7, 5, DARK)
        rect(1, 2, 3, 2, DARK); rect(6, 2, 8, 2, DARK)             # cejas serias
        rect(3, 7, 6, 7, DARK)
    elif mood == "mysterious":
        rect(3, 3, 3, 5, WHITE); rect(6, 3, 6, 5, WHITE)           # ojos brillantes verticales
        rect(4, 7, 5, 7, DARK)
    elif mood == "angry":
        rect(2, 4, 3, 5, DARK); rect(6, 4, 7, 5, DARK)
        put(1, 2, DARK); rect(2, 3, 3, 3, DARK); put(8, 2, DARK); rect(6, 3, 7, 3, DARK)   # cejas fruncidas
        rect(3, 7, 6, 7, DARK); put(2, 8, DARK); put(7, 8, DARK)                           # ceno

for name, cfg in MOODS.items():
    px = [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]
    base = cfg["base"] + (255,)
    edge = cfg["edge"] + (255,)
    # region del cubo 10x10x10: x 0..39, y 0..19
    for y in range(20):
        for x in range(40):
            px[y][x] = base
    # bordes de cada cara (da relieve)
    faces = [(10, 0), (20, 0), (0, 10), (10, 10), (20, 10), (30, 10)]
    for fx, fy in faces:
        for i in range(10):
            for (x, y) in ((fx + i, fy), (fx + i, fy + 9), (fx, fy + i), (fx + 9, fy + i)):
                px[y][x] = edge
    face(name, lambda c, r, col: px[10 + r].__setitem__(10 + c, col))
    png(os.path.join(OUT, f"maelity_{name}.png"), px)
    print("ok", name)
