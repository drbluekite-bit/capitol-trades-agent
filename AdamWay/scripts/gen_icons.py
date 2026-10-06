"""Generates the Adam Way launcher icon set (black background, white "AW" monogram).

Run once with: python3 scripts/gen_icons.py
Requires Pillow (pip install pillow). Not part of the Gradle build.
"""
from PIL import Image, ImageDraw, ImageFont
import os

FONT_PATH = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
OUT_ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")


def make_icon(size: int) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    draw = ImageDraw.Draw(img)
    text = "AW"
    font_size = int(size * 0.46)
    font = ImageFont.truetype(FONT_PATH, font_size)
    bbox = draw.textbbox((0, 0), text, font=font)
    w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (size - w) / 2 - bbox[0]
    y = (size - h) / 2 - bbox[1]
    draw.text((x, y), text, font=font, fill=(255, 255, 255, 255))
    return img


def main():
    for folder, size in SIZES.items():
        out_dir = os.path.join(OUT_ROOT, folder)
        os.makedirs(out_dir, exist_ok=True)
        icon = make_icon(size)
        icon.save(os.path.join(out_dir, "ic_launcher.png"))
        icon.save(os.path.join(out_dir, "ic_launcher_round.png"))
    # A larger master copy used as the in-app logo / feature graphic.
    master = make_icon(512)
    master_dir = os.path.join(OUT_ROOT, "drawable")
    os.makedirs(master_dir, exist_ok=True)
    master.save(os.path.join(master_dir, "adam_way_logo.png"))
    print("Icons written.")


if __name__ == "__main__":
    main()
