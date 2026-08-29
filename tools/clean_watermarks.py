"""
Auto-remove the "AI生成 WORKBUDDY" watermark stamped in the bottom-right of
AI-generated P2P GUI textures.

Strategy: for each PNG, sample-fill the bottom-right rectangle (last 14% width
x last 12% height) by copying the pixel color from 4px above the watermark top.
This makes the watermark region match what's directly above it:
  - For transparent-bg icons -> the region becomes transparent (watermark gone).
  - For dark-bg panels/emblem -> the region becomes the panel's dark fill
    (watermark visually replaced by the surrounding dark).

Safe: backs up originals to gui/_backup_preclean/ first, then writes cleaned
PNGs in place. Re-runnable (detects existing backups).

Usage (from the mod root):
    python tools/clean_watermarks.py
"""

from __future__ import annotations

import shutil
import sys
from pathlib import Path

from PIL import Image

GUI_DIR = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "assets" / "p2p" / "textures" / "gui"
BACKUP_DIR = GUI_DIR / "_backup_preclean"

# Region to clean: bottom-right 14% width x 12% height.
XRATIO = 0.86   # keep 0..0.86 = first 86%, clean from 0.86..1.0
YRATIO = 0.88   # keep 0..0.88 = first 88%, clean from 0.88..1.0
SAMPLE_OFFSET_Y = 4  # pixels above watermark top to sample from


def clean_one(path: Path) -> tuple[int, int]:
    """Clean a single PNG. Returns (width, height)."""
    img = Image.open(path).convert("RGBA")
    w, h = img.size
    pixels = img.load()

    x0 = int(w * XRATIO)
    y0 = int(h * YRATIO)

    for x in range(x0, w):
        sy = max(0, y0 - SAMPLE_OFFSET_Y)
        src = pixels[x, sy]
        for y in range(y0, h):
            pixels[x, y] = src

    img.save(path, "PNG", optimize=True)
    return w, h


def main() -> int:
    if not GUI_DIR.is_dir():
        print(f"GUI dir not found: {GUI_DIR}", file=sys.stderr)
        return 1

    pngs = sorted(p for p in GUI_DIR.glob("*.png") if p.is_file())
    if not pngs:
        print(f"No PNGs in {GUI_DIR}")
        return 0

    # First-run backup
    if not BACKUP_DIR.exists():
        BACKUP_DIR.mkdir(parents=True, exist_ok=True)
        for p in pngs:
            shutil.copy2(p, BACKUP_DIR / p.name)
        print(f"Backed up {len(pngs)} originals -> {BACKUP_DIR.name}/")

    print(f"Cleaning {len(pngs)} textures in {GUI_DIR.name}/ ...")
    for p in pngs:
        w, h = clean_one(p)
        print(f"  cleaned {p.name} ({w}x{h})")

    print("Done.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())