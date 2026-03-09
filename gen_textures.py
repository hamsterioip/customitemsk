import struct, zlib, os

def write_png(path, pixels, width, height):
    sig = b'\x89PNG\r\n\x1a\n'
    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    ihdr = chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
    rows = b''
    for row in pixels:
        rows += b'\x00'
        for (r,g,b,a) in row:
            rows += bytes([r,g,b,a])
    idat = chunk(b'IDAT', zlib.compress(rows))
    iend = chunk(b'IEND', b'')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(sig + ihdr + idat + iend)

base = "C:/Users/Koon/Downloads/customitemsk-template-1.21.11/src/main/resources/assets/customitemsk/textures/item"

# Color aliases
T  = (0,0,0,0)         # transparent
BK = (20,14,10,255)    # near-black
DB = (45,28,16,255)    # dark brown
CB = (160,90,40,255)   # copper bright
CO = (120,65,28,255)   # copper shadow
GE = (30,110,220,255)  # blue gem
GD = (15,60,150,255)   # blue gem dark
GH = (140,200,255,255) # gem highlight
CH = (0,200,255,255)   # chain bright
CS = (0,140,200,255)   # chain shadow
WH = (220,240,255,255) # white spark

# ─────────────────────────────────────────────
# STORM BOW (16x16)
# Dark mechanical bow, copper limbs, blue gem, blue chain string
# ─────────────────────────────────────────────
bow = [
    [T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [T,  T,  BK, BK, T,  T,  T,  T,  T,  T,  T,  BK, BK, T,  T,  T ],
    [T,  BK, DB, DB, BK, T,  T,  T,  T,  T,  BK, DB, DB, BK, T,  T ],
    [T,  BK, CB, CO, DB, BK, T,  T,  T,  BK, DB, CO, CB, BK, T,  T ],
    [T,  BK, CO, DB, DB, BK, CH, T,  T,  CH, BK, DB, DB, CO, BK, T ],
    [T,  BK, DB, CO, BK, CH, CS, T,  T,  CS, CH, BK, CO, DB, BK, T ],
    [T,  T,  BK, BK, CH, CS, GE, GH, GH, GE, CS, CH, BK, BK, T,  T ],
    [T,  T,  T,  CH, CS, GE, GH, WH, WH, GH, GE, CS, CH, T,  T,  T ],
    [T,  T,  T,  CH, CS, GE, GH, WH, WH, GH, GE, CS, CH, T,  T,  T ],
    [T,  T,  BK, BK, CH, CS, GE, GH, GH, GE, CS, CH, BK, BK, T,  T ],
    [T,  BK, DB, CO, BK, CH, CS, T,  T,  CS, CH, BK, CO, DB, BK, T ],
    [T,  BK, CO, DB, DB, BK, CH, T,  T,  CH, BK, DB, DB, CO, BK, T ],
    [T,  BK, CB, CO, DB, BK, T,  T,  T,  BK, DB, CO, CB, BK, T,  T ],
    [T,  BK, DB, DB, BK, T,  T,  T,  T,  T,  BK, DB, DB, BK, T,  T ],
    [T,  T,  BK, BK, T,  T,  T,  T,  T,  T,  T,  BK, BK, T,  T,  T ],
    [T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
]
write_png(f"{base}/storm_bow.png", bow, 16, 16)
write_png(f"{base}/storm_bow_pulling_0.png", bow, 16, 16)
write_png(f"{base}/storm_bow_pulling_1.png", bow, 16, 16)
write_png(f"{base}/storm_bow_pulling_2.png", bow, 16, 16)
print("Storm Bow done")

# ─────────────────────────────────────────────
# BLADES OF SATAN (16x16)
# Diagonal dark blade, red glow, ornate guard, red gem
# ─────────────────────────────────────────────
NB = (12,8,8,255)      # near-black blade
BD = (28,16,16,255)    # blade dark
RG = (200,0,0,255)     # red glow
RL = (255,40,40,255)   # red outline bright
RD = (120,0,0,255)     # red shadow
RR = (80,10,10,255)    # rune slot
SI = (140,135,140,255) # silver guard
SH = (185,180,185,255) # silver highlight
GR = (180,0,0,255)     # guard gem
GP = (255,90,90,255)   # gem highlight
CP = (120,80,40,255)   # copper pommel
HD = (60,40,20,255)    # handle dark

bos = [
    [T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  RL, RG, T ],
    [T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  RG, BD, RD, T ],
    [T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  RL, BD, NB, RG, T ],
    [T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  RG, BD, RR, BD, RD, T ],
    [T,  T,  T,  T,  T,  T,  T,  T,  T,  RL, BD, RR, BD, RG, T,  T ],
    [T,  T,  T,  T,  T,  T,  T,  T,  RG, BD, RR, BD, RG, T,  T,  T ],
    [T,  T,  T,  T,  T,  T,  T,  RL, BD, RR, BD, RL, T,  T,  T,  T ],
    [T,  T,  T,  SI, SI, T,  RG, SI, SH, SH, SI, T,  T,  T,  T,  T ],
    [T,  T,  SI, SH, GR, SI, GP, GR, SI, T,  T,  T,  T,  T,  T,  T ],
    [T,  SI, SI, T,  T,  CP, HD, CP, T,  T,  T,  T,  T,  T,  T,  T ],
    [T,  T,  T,  T,  HD, CP, HD, T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [T,  T,  T,  CP, HD, CP, T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [T,  T,  HD, CP, HD, T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [T,  CP, HD, CP, T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [T,  HD, CP, T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [CP, HD, T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
]
write_png(f"{base}/blades_of_satan.png", bos, 16, 16)
print("Blades of Satan done")

# ─────────────────────────────────────────────
# SWORD OF LIGHT (16x16)
# White/silver blade, golden runes, holy glow, sun pommel
# ─────────────────────────────────────────────
WB = (240,245,255,255) # white blade
WW = (255,255,255,255) # blade highlight
WS = (195,205,220,255) # blade shadow
GO = (220,180,50,255)  # gold
GS = (155,115,25,255)  # gold shadow
RU = (200,168,55,255)  # golden rune
SL = (185,190,205,255) # silver handle
SR = (155,160,175,255) # silver shadow

sol = [
    [T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  WW, WB, T ],
    [T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  WW, WB, WS, T ],
    [T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  WW, WB, RU, WS, T ],
    [T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  WB, WW, RU, WB, WS, T ],
    [T,  T,  T,  T,  T,  T,  T,  T,  T,  WW, WB, RU, WB, WS, T,  T ],
    [T,  T,  T,  T,  T,  T,  T,  T,  WW, WB, RU, WB, WS, T,  T,  T ],
    [T,  T,  T,  T,  T,  T,  T,  WB, WW, RU, WB, WS, T,  T,  T,  T ],
    [T,  T,  GO, GO, T,  T,  GO, SL, GO, GS, GO, T,  T,  T,  T,  T ],
    [T,  GO, GS, GO, GO, SL, GO, GS, SL, T,  T,  T,  T,  T,  T,  T ],
    [T,  T,  T,  T,  SL, SR, SL, T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [T,  T,  T,  SL, SR, SL, T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [T,  T,  SR, SL, SR, T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [T,  SL, SR, SL, T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [GO, GS, GO, T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [GS, GO, T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [GO, T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
]
write_png(f"{base}/sword_of_light.png", sol, 16, 16)
print("Sword of Light done")

# ─────────────────────────────────────────────
# EXECUTIONER'S SWORD (16x16)
# Dark heavy blade, red runes, dark guard, pink gem, round pommel
# ─────────────────────────────────────────────
NB = (18,16,18,255)    # near-black
BM = (40,34,40,255)    # blade mid
BL = (58,52,58,255)    # blade light
RR = (165,22,22,255)   # red rune
RS = (90,10,10,255)    # rune shadow
GY = (72,65,72,255)    # guard grey
GL = (105,98,105,255)  # guard light
PK = (195,100,130,255) # pink gem
PL = (240,160,180,255) # gem light
SL = (62,56,62,255)    # handle
SH = (92,86,92,255)    # handle highlight

exs = [
    [T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  NB, BM, T ],
    [T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  NB, BM, BL, T ],
    [T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  NB, BM, RR, BL, T ],
    [T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  NB, BM, RS, BM, BL, T ],
    [T,  T,  T,  T,  T,  T,  T,  T,  T,  NB, BM, RR, BM, BL, T,  T ],
    [T,  T,  T,  T,  T,  T,  T,  T,  NB, BM, RS, BM, NB, T,  T,  T ],
    [T,  T,  T,  T,  T,  T,  T,  NB, BM, RR, BM, NB, T,  T,  T,  T ],
    [T,  T,  T,  GL, GY, GL, NB, GY, GL, GY, NB, T,  T,  T,  T,  T ],
    [T,  T,  GY, GL, PK, GL, PL, PK, GL, T,  T,  T,  T,  T,  T,  T ],
    [T,  T,  T,  T,  SH, SL, SH, T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [T,  T,  T,  SL, SH, SL, T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [T,  T,  SH, SL, SH, T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [T,  SL, SH, SL, T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [GY, GL, GY, T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [GL, GY, T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
    [GY, T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T,  T ],
]
write_png(f"{base}/executioners_sword.png", exs, 16, 16)
print("Executioner's Sword done")
print("\nAll 7 textures written!")
