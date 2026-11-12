#!/usr/bin/env python3
"""Generate side-by-side reference vs. device screenshots for visual QA.

Usage: python scripts/make-compare.py
Requires Pillow.
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
REF_DIR = ROOT.parent / "截图"
DEV_DIR = ROOT / "qa" / "device"
OUT_DIR = ROOT / "qa"
OUT_DIR.mkdir(exist_ok=True)

PAIRS = [
    ("1.jpg", "01-today.png", "Today"),
    ("2.jpg", "02-games.png", "Games"),
    ("3.jpg", "03-apps.png", "Apps"),
    ("4.jpg", "04-arcade.png", "Arcade"),
    ("5.jpg", "05-search.png", "Search"),
    ("6.jpg", "06-detail-top.png", "Detail Top"),
    ("7.jpg", "07-detail-scrolled.png", "Detail Scrolled"),
    ("8.jpg", "08-account.png", "Account Sheet"),
]

H = 900
GAP = 24
HEADER = 56


def resize_to_height(path: Path, height: int) -> Image.Image:
    im = Image.open(path).convert("RGB")
    return im.resize((round(im.width * height / im.height), height), Image.LANCZOS)


def main() -> None:
    thumbs = []
    for ref_name, dev_name, label in PAIRS:
        ref_path = REF_DIR / ref_name
        dev_path = DEV_DIR / dev_name
        if not ref_path.exists() or not dev_path.exists():
            print(f"skip (missing): {ref_name} / {dev_name}")
            continue

        a = resize_to_height(ref_path, H)
        b = resize_to_height(dev_path, H)
        canvas = Image.new("RGB", (a.width + b.width + GAP, H + HEADER), (18, 18, 20))
        draw = ImageDraw.Draw(canvas)
        draw.text((12, 18), f"REFERENCE  {ref_name}   {label}", fill=(235, 235, 240))
        draw.text((a.width + GAP + 12, 18), f"DEVICE  {dev_name}", fill=(235, 235, 240))
        canvas.paste(a, (0, HEADER))
        canvas.paste(b, (a.width + GAP, HEADER))
        out = OUT_DIR / f"compare-{ref_name.replace('.jpg', '')}-{label.lower().replace(' ', '-')}.png"
        canvas.save(out)
        print("saved", out.name)

        thumb_h = 300
        t = canvas.resize((round(canvas.width * thumb_h / canvas.height), thumb_h), Image.LANCZOS)
        thumbs.append(t)

    if thumbs:
        cell_w = max(t.width for t in thumbs)
        cell_h = max(t.height for t in thumbs) + 34
        cols = 2
        rows = (len(thumbs) + cols - 1) // cols
        grid = Image.new("RGB", (cols * cell_w, rows * cell_h), (12, 12, 14))
        gd = ImageDraw.Draw(grid)
        for i, t in enumerate(thumbs):
            x = (i % cols) * cell_w
            y = (i // cols) * cell_h
            gd.text((x + 8, y + 8), f"{i + 1}. {PAIRS[i][2]}", fill=(230, 230, 235))
            grid.paste(t, (x, y + 30))
        grid.save(OUT_DIR / "compare-all.png")
        print("saved compare-all.png")


if __name__ == "__main__":
    main()
