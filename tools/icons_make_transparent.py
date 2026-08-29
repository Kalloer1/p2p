"""v2 第二轮后处理：
  3 张类型图标 (item/fluid/energy) 的背景是 AI 生成的实色不透明白色，
  必须 chroma-key 改成透明，否则放到游戏深色面板上就是刺眼白方块。
  emblem 的背景是不透明近黑色，角落洪泛填透明。

  策略：
  - 白底图：RGB 三通道均 > 阈值 的像素 → alpha=0
            (cyan #2dd4ee = (45,212,238) 因 R=45 低于阈值，安全保留)
  - emblem：四角洪泛，色差 ≤ tolerance 的连续像素 → alpha=0
            (钛灰 #1b2230 = (27,34,48) 与角落 (1,3,3) 色差 > tolerance，
             图标主体安全保留)
幂等：可重复跑。
"""
from PIL import Image
import os
from collections import deque

_HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.join(_HERE, "..", "src", "main", "resources", "assets", "p2p", "textures", "gui")
WHITE_ICONS = ["ic_type_item.png", "ic_type_fluid.png", "ic_type_energy.png"]
EMBLEM = "p2p_emblem.png"
WHITE_THR = 220        # R,G,B 三通道均 > 此值 → 当白底
FLOOD_TOL = 18         # 色差容忍 (0-255)，越大越激进


def white_chroma(path):
    """把"近白/浅灰光晕"键为透明。
    双重判定，避免误伤亮色图标主体：
      a) 纯白：R,G,B 均 > 180
      b) 低饱和+亮：max(R,G,B) - min(R,G,B) < 25 且 min > 150
         (cyan #2dd4ee=(45,212,238) 通道差 193 >> 25，安全)
         (钛灰 #1b2230=(27,34,48) min=27 < 150，安全)
    已透明的像素 (a==0) 跳过，幂等。
    """
    im = Image.open(path).convert("RGBA")
    px = im.load()
    W, H = im.size
    n = 0
    for y in range(H):
        for x in range(W):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            mx, mn = max(r, g, b), min(r, g, b)
            near_white = (r > 180 and g > 180 and b > 180)
            low_sat_bright = ((mx - mn) < 25 and mn > 150)
            if near_white or low_sat_bright:
                px[x, y] = (r, g, b, 0)
                n += 1
    im.save(path)
    print(f"[halo-key] {path}: {n} px → transparent")


def flood_corners(path):
    im = Image.open(path).convert("RGBA")
    px = im.load()
    W, H = im.size
    bg = px[0, 0][:3]
    visited = [[False] * W for _ in range(H)]
    q = deque()
    for sx, sy in [(0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)]:
        if not visited[sy][sx]:
            visited[sy][sx] = True
            q.append((sx, sy))
    n = 0
    while q:
        x, y = q.popleft()
        r, g, b, a = px[x, y]
        if abs(r - bg[0]) <= FLOOD_TOL and abs(g - bg[1]) <= FLOOD_TOL and abs(b - bg[2]) <= FLOOD_TOL:
            if a > 0:
                px[x, y] = (r, g, b, 0)
                n += 1
            for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                nx, ny = x + dx, y + dy
                if 0 <= nx < W and 0 <= ny < H and not visited[ny][nx]:
                    visited[ny][nx] = True
                    q.append((nx, ny))
    im.save(path)
    print(f"[flood] {path}: {n} px → transparent (bg={bg}, tol={FLOOD_TOL})")


print("=== 类型图标白底→透明 (chroma key) ===")
for n in WHITE_ICONS:
    white_chroma(os.path.join(BASE, n))

print("\n=== emblem 角落洪泛填透明 ===")
flood_corners(os.path.join(BASE, EMBLEM))

# 验证
print("\n=== 验证四角色素 (应当 alpha=0 即透明) ===")
ok = True
for n in WHITE_ICONS + [EMBLEM]:
    im = Image.open(os.path.join(BASE, n)).convert("RGBA")
    W, H = im.size
    corners = [im.getpixel((0, 0)), im.getpixel((W-1, 0)),
               im.getpixel((0, H-1)), im.getpixel((W-1, H-1))]
    a_vals = [c[3] for c in corners]
    flag = "OK" if all(a == 0 for a in a_vals) else "FAIL"
    ok &= (flag == "OK")
    print(f"  [{flag}] {n:22s} corners_alpha={a_vals}")
import sys
sys.exit(0 if ok else 2)