"""v2 重绘后处理：
  1) 三个面板 (gui_panel / gui_panel_small / gui_header) 用 PIL 把任何残余
     透明像素 alpha-composite 到实色 #1b2230 (钛灰) — 从此四个边/角再无任何
     透明像素，HTML 预览与游戏内 blit 都不会再出现"白边"。
  2) 七个重绘资产统一清掉右下角 "AI生成 WORKBUDDY" 水印：采样该像素上方
     3px 处的颜色覆盖，alpha 保持原值；透明像素保持透明。
  3) 验证面板四角+四边中点的 alpha 是否全部为 255，确认 full-bleed。
幂等：可重复跑（备份原件在 gui/_backup_preclean/）。
"""
from PIL import Image
import os, sys

_HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.join(_HERE, "..", "src", "main", "resources", "assets", "p2p", "textures", "gui")
BG   = (27, 34, 48, 255)  # #1b2230 钛灰

PANELS = ["gui_panel.png", "gui_panel_small.png", "gui_header.png"]
ICONS  = ["ic_type_item.png", "ic_type_fluid.png", "ic_type_energy.png", "p2p_emblem.png"]

WM_W, WM_H = 0.14, 0.12  # 右下角水印覆盖区域宽高比例

def clean_wm_region(pixels, w, h):
    """对右下角 WM 区域每个像素：若非透明，则用其正上方 ~3px 处的颜色覆盖。"""
    rw, rh = int(w * WM_W), int(h * WM_H)
    y0 = h - rh
    x0 = w - rw
    for y in range(y0, h):
        for x in range(x0, w):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            sx, sy = x, max(0, y - 3)
            sr, sg, sb, sa = pixels[sx, sy]
            pixels[x, y] = (sr, sg, sb, a)

def full_bleed_panel(path):
    im = Image.open(path).convert("RGBA")
    bg = Image.new("RGBA", im.size, BG)
    bg.alpha_composite(im)               # 把透明像素填成 #1b2230
    clean_wm_region(bg.load(), *bg.size)
    bg.save(path)
    return bg

def clean_icon(path):
    im = Image.open(path).convert("RGBA")
    clean_wm_region(im.load(), *im.size)
    im.save(path)
    return im

print("=== 面板 full-bleed + 水印清理 ===")
for name in PANELS:
    full_bleed_panel(os.path.join(BASE, name))

print("\n=== 图标水印清理 (保留透明背景) ===")
for name in ICONS:
    clean_icon(os.path.join(BASE, name))

print("\n=== 验证面板四角+四边中点 alpha (必须全 255 才算 full-bleed) ===")
all_ok = True
for name in PANELS:
    im = Image.open(os.path.join(BASE, name)).convert("RGBA")
    W, H = im.size
    pts = {
        "TL": (0, 0), "TR": (W-1, 0),
        "BL": (0, H-1), "BR": (W-1, H-1),
        "TM": (W//2, 0), "LM": (0, H//2),
        "RM": (W-1, H//2), "BM": (W//2, H-1),
    }
    alphas = {k: im.getpixel(v)[3] for k, v in pts.items()}
    ok = all(a == 255 for a in alphas.values())
    all_ok &= ok
    flag = "OK" if ok else "FAIL"
    print(f"  [{flag}] {name:22s} size={W}x{H}  alphas={alphas}")

sys.exit(0 if all_ok else 2)