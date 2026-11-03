#!/usr/bin/env python3
"""Genera el cuerpo blanco (se tiñe con el color del humor) y las caras: 5 estilos x 6 humores.
Uso: python3 tools/gen_looks.py mod/src/main/resources/assets/maelity/textures/entity"""
import os, struct, sys, zlib

W, H = 64, 32
OUT = sys.argv[1] if len(sys.argv) > 1 else "."

def png(path, px):
    raw = b"".join(b"\x00" + b"".join(bytes(px[y][x]) for x in range(W)) for y in range(H))
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))

def blank():
    return [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]

# ---- cuerpo: casi blanco con un ruido suave (el color lo pone el humor)
body = blank()
for y in range(H):
    for x in range(W):
        n = ((x * 7 + y * 13) % 5) * 3
        body[y][x] = (244 - n, 244 - n, 244 - n, 255)
png(os.path.join(OUT, "body.png"), body)

DARK = (28, 22, 30, 255)
WHITE = (255, 255, 255, 255)
PINK = (255, 120, 155, 255)
LED = (110, 245, 255, 255)
VISOR = (35, 40, 55, 255)
MOODS = ["friendly", "playful", "sarcastic", "protective", "mysterious", "angry"]

def face(style, mood):
    px = blank()
    def put(c, r, col):
        if 1 <= c <= 8 and 1 <= r <= 8:        # dentro de la cara de la pelotita (8x8 centrales)
            px[r][c] = col
    def rect(c0, r0, c1, r1, col):
        for r in range(r0, r1 + 1):
            for c in range(c0, c1 + 1):
                put(c, r, col)

    # ---------- ojos
    if style == "robot":
        rect(1, 3, 8, 5, VISOR)
        if mood == "playful":    rect(2, 4, 3, 4, LED); rect(6, 3, 7, 4, LED)
        elif mood == "sarcastic": rect(2, 4, 3, 4, LED); rect(6, 4, 7, 4, LED)
        elif mood == "angry":    put(2, 3, LED); put(3, 4, LED); put(7, 3, LED); put(6, 4, LED)
        elif mood == "mysterious": put(3, 4, LED); put(6, 4, LED)
        else:                    rect(2, 3, 3, 4, LED); rect(6, 3, 7, 4, LED)
    else:
        big = style == "cute"
        dot = style == "minimal"
        def eye(c, closed=False, half=False, glow=False):
            if closed:
                rect(c, 4, c + 1, 4, DARK); return
            if dot:
                put(c + (0 if c < 5 else 1), 4, WHITE if glow else DARK); return
            if glow:
                rect(c + (1 if c < 5 else 0), 3, c + (1 if c < 5 else 0), 5, WHITE); return
            if half:
                rect(c, 4, c + 1, 4 + (1 if big else 0), DARK); return
            rect(c, 3, c + 1, 4 + (1 if big else 0), DARK)
            if big: put(c, 3, WHITE)
            if style == "cat" and mood in ("friendly", "protective"):
                rect(c, 3, c + 1, 3, (0, 0, 0, 0)); rect(c, 4, c + 1, 4, DARK)   # ojitos ^ ^
        L, R = 2, 6
        if mood == "playful":      eye(L); eye(R, closed=True)
        elif mood == "sarcastic":  eye(L, half=True); eye(R, half=True); rect(6, 2, 7, 2, DARK) if not dot else None
        elif mood == "mysterious": eye(L, glow=True); eye(R, glow=True)
        else:                      eye(L); eye(R)
        if mood == "protective" and not dot: rect(1, 2, 3, 2, DARK); rect(6, 2, 8, 2, DARK)
        if mood == "angry" and not dot:      put(1, 1, DARK); rect(2, 2, 3, 2, DARK); put(8, 1, DARK); rect(6, 2, 7, 2, DARK)
        if mood == "angry" and dot:          put(2, 3, DARK); put(7, 3, DARK)

    # ---------- boca
    mouth = DARK if style != "robot" else LED
    # en "cute" los ojos son grandes: bajar la boca 1 pixel para que no se pegue a los ojos
    dy = 1 if style == "cute" and mood in ("friendly", "playful", "sarcastic") else 0
    _put, _rect = put, rect
    def put(c, r, col): _put(c, r + dy, col)
    def rect(c0, r0, c1, r1, col):
        for r in range(r0, r1 + 1):
            for c in range(c0, c1 + 1):
                _put(c, r + dy, col)
    if style == "robot" and mood == "playful":
        for i, c in enumerate(range(2, 8)): put(c, 6 + (i % 2), LED)
    elif style == "cat":
        if mood == "angry":      rect(3, 7, 6, 7, DARK); put(2, 8, DARK); put(7, 8, DARK)
        elif mood == "playful":  put(3, 6, DARK); put(4, 7, DARK); put(5, 6, DARK); put(6, 7, DARK); put(5, 7, PINK)
        else:                    put(3, 6, DARK); put(4, 7, DARK); put(5, 6, DARK); put(6, 7, DARK)   # :3
        put(1, 6, DARK); put(8, 6, DARK)                                                              # bigotes
    elif mood == "friendly":   put(2, 6, mouth); put(7, 6, mouth); rect(3, 7, 6, 7, mouth)
    elif mood == "playful":    rect(2, 6, 7, 7, mouth); rect(4, 7, 5, 7, PINK if style != "robot" else LED)
    elif mood == "sarcastic":  rect(5, 6, 7, 6, mouth); rect(3, 7, 4, 7, mouth)
    elif mood == "protective": rect(3, 7, 6, 7, mouth)
    elif mood == "mysterious": rect(4, 7, 5, 7, mouth)
    elif mood == "angry":      rect(3, 7, 6, 7, mouth); put(2, 8, mouth); put(7, 8, mouth)

    if style == "cute" and mood in ("friendly", "playful", "protective"):
        _put(1, 6, PINK); _put(8, 6, PINK)                                                              # mejillas
    return px

for style in ["classic", "cute", "cat", "robot", "minimal"]:
    for mood in MOODS:
        png(os.path.join(OUT, "face", f"{style}_{mood}.png"), face(style, mood))
print("ok")
