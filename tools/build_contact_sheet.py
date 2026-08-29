"""合成一张 contact sheet — 把全-bleed 主面板当底，把 3 个类型图标 + emblem + 频道图标 + 过滤图标
放到面板上，证明透明图标融入深色面板无任何白边 / halo。

输出：p2p_contact_sheet.png (1024x1024)
"""
from PIL import Image
import os

_HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.join(_HERE, "..", "src", "main", "resources", "assets", "p2p", "textures", "gui")
OUT  = os.path.join(_HERE, "..", "p2p_contact_sheet.png")

W, H = 1024, 1024
sheet = Image.open(os.path.join(BASE, "gui_panel.png")).convert("RGBA").resize((W, H))


def paste_center(img, cx, cy, size):
    """将 PNG 缩放到 size 并以 (cx, cy) 为中心贴到 sheet 上 (透明背景自动混合)。"""
    icon = Image.open(os.path.join(BASE, img)).convert("RGBA").resize((size, size), Image.LANCZOS)
    x = cx - size // 2
    y = cy - size // 2
    sheet.alpha_composite(icon, (x, y))


# 顶部 Header 横幅
header = Image.open(os.path.join(BASE, "gui_header.png")).convert("RGBA").resize((W, 140), Image.LANCZOS)
sheet.alpha_composite(header, (0, 30))

# Emblem 嵌入 Header 左
paste_center("p2p_emblem.png", 80, 100, 96)

# 频道图标 + 过滤图标在 Header 右
paste_center("ic_channel.png", 700, 100, 72)
paste_center("ic_filter.png",  800, 100, 72)
paste_center("ic_network.png", 900, 100, 72)

# 三个类型图标 (item/fluid/energy) 居中下方 — 这是重点验证
y_row = 560
spacing = 280
start_x = (W - spacing * 2) // 2
for i, name in enumerate(["ic_type_item.png", "ic_type_fluid.png", "ic_type_energy.png"]):
    paste_center(name, start_x + i * spacing, y_row, 220)

# 三个图标下方一行小字 (用 PIL ImageDraw)
from PIL import ImageDraw, ImageFont
draw = ImageDraw.Draw(sheet)
try:
    font_big = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 28)
    font_sm  = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 18)
except OSError:
    font_big = ImageFont.load_default()
    font_sm  = ImageFont.load_default()
# 找带 CJK 的字体渲染中文
def _load_cjk(size):
    for p in ["C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/msyh.ttf",
              "C:/Windows/Fonts/simhei.ttf", "C:/Windows/Fonts/simsun.ttc",
              "C:/Windows/Fonts/NotoSansCJK-Regular.ttc"]:
        if os.path.exists(p):
            try: return ImageFont.truetype(p, size)
            except OSError: pass
    return font_big
font_cjk = _load_cjk(22)
labels = [("ITEM",   "物品"),
          ("FLUID",  "流体"),
          ("ENERGY", "能量")]
for i, (en, cn) in enumerate(labels):
    cx = start_x + i * spacing
    en_box = draw.textbbox((0, 0), en, font=font_big)
    en_w = en_box[2] - en_box[0]
    draw.text((cx - en_w // 2, y_row + 130), en, fill=(45, 212, 238, 255), font=font_big)
    cn_box = draw.textbbox((0, 0), cn, font=font_cjk)
    cn_w = cn_box[2] - cn_box[0]
    draw.text((cx - cn_w // 2, y_row + 168), cn, fill=(160, 200, 220, 255), font=font_cjk)

# 底部说明
draw.text((40, 960), "P2P GUI v2.1 — full-bleed panels · halo-free transparent icons",
          fill=(180, 200, 220, 255), font=font_sm)

sheet.convert("RGB").save(OUT, optimize=True)
print(f"saved: {OUT}  ({os.path.getsize(OUT)//1024} KB)")