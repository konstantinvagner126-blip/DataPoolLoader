#!/usr/bin/env python3

from __future__ import annotations

import math
import shutil
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parent
MASTER_SIZE = 1024
MASTER_PATH = ROOT / "icon-master.png"
LINUX_ICON_PATH = ROOT / "icon.png"
WINDOWS_ICON_PATH = ROOT / "icon.ico"
MAC_ICON_PATH = ROOT / "icon.icns"
ICONSET_DIR = ROOT / "icon.iconset"


def load_font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/System/Library/Fonts/Supplemental/Menlo Bold.ttf",
        "/System/Library/Fonts/Supplemental/Menlo.ttc",
    ]
    for candidate in candidates:
        path = Path(candidate)
        if path.exists():
            try:
                return ImageFont.truetype(str(path), size=size)
            except OSError:
                continue
    return ImageFont.load_default()


def vertical_gradient(size: int, top: tuple[int, int, int], bottom: tuple[int, int, int]) -> Image.Image:
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pixels = image.load()
    for y in range(size):
        ratio = y / float(size - 1)
        color = tuple(int(top[i] + (bottom[i] - top[i]) * ratio) for i in range(3)) + (255,)
        for x in range(size):
            pixels[x, y] = color
    return image


def draw_icon(size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gradient = vertical_gradient(size, (17, 24, 39), (15, 37, 59))

    card_mask = Image.new("L", (size, size), 0)
    mask_draw = ImageDraw.Draw(card_mask)
    inset = int(size * 0.06)
    radius = int(size * 0.22)
    mask_draw.rounded_rectangle(
        (inset, inset, size - inset, size - inset),
        radius=radius,
        fill=255,
    )
    canvas.paste(gradient, (0, 0), card_mask)

    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.rounded_rectangle(
        (inset, inset, size - inset, size - inset),
        radius=radius,
        outline=(255, 255, 255, 28),
        width=max(4, size // 120),
    )
    glow = glow.filter(ImageFilter.GaussianBlur(size // 80))
    canvas.alpha_composite(glow)

    draw = ImageDraw.Draw(canvas)

    center_box = (
        int(size * 0.43),
        int(size * 0.29),
        int(size * 0.84),
        int(size * 0.71),
    )
    shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.rounded_rectangle(
        center_box,
        radius=int(size * 0.1),
        fill=(6, 12, 23, 170),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(size // 30))
    canvas.alpha_composite(shadow)

    draw.rounded_rectangle(
        center_box,
        radius=int(size * 0.1),
        fill=(12, 19, 33, 255),
        outline=(96, 165, 250, 40),
        width=max(3, size // 170),
    )

    line_specs = [
        ((0.18, 0.24), (0.42, 0.37), (249, 115, 22)),
        ((0.18, 0.50), (0.42, 0.50), (37, 99, 235)),
        ((0.18, 0.76), (0.42, 0.63), (20, 184, 166)),
    ]
    line_width = max(14, size // 36)
    for start_ratio, end_ratio, color in line_specs:
        start = (int(size * start_ratio[0]), int(size * start_ratio[1]))
        end = (int(size * end_ratio[0]), int(size * end_ratio[1]))
        mid = (int(size * 0.28), start[1])

        layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        layer_draw = ImageDraw.Draw(layer)
        layer_draw.line((start, mid, end), fill=color + (255,), width=line_width, joint="curve")
        layer = layer.filter(ImageFilter.GaussianBlur(size // 120))
        canvas.alpha_composite(layer)

        draw.line((start, mid, end), fill=color + (255,), width=line_width, joint="curve")

        orb_radius = size // 22
        outer = [
            start[0] - orb_radius,
            start[1] - orb_radius,
            start[0] + orb_radius,
            start[1] + orb_radius,
        ]
        draw.ellipse(outer, fill=color + (255,))
        inner_radius = int(orb_radius * 0.52)
        inner = [
            start[0] - inner_radius,
            start[1] - inner_radius,
            start[0] + inner_radius,
            start[1] + inner_radius,
        ]
        draw.ellipse(inner, fill=(255, 255, 255, 185))

    font = load_font(int(size * 0.165))
    label = "LTP"
    bbox = draw.textbbox((0, 0), label, font=font)
    text_w = bbox[2] - bbox[0]
    text_h = bbox[3] - bbox[1]
    text_x = (center_box[0] + center_box[2] - text_w) / 2
    text_y = (center_box[1] + center_box[3] - text_h) / 2 - size * 0.015
    draw.text((text_x, text_y), label, font=font, fill=(248, 250, 252, 255))

    subtitle_font = load_font(int(size * 0.043))
    subtitle = "MICROSERVICES"
    subtitle_bbox = draw.textbbox((0, 0), subtitle, font=subtitle_font)
    subtitle_w = subtitle_bbox[2] - subtitle_bbox[0]
    subtitle_x = (center_box[0] + center_box[2] - subtitle_w) / 2
    subtitle_y = center_box[3] - size * 0.15
    draw.text((subtitle_x, subtitle_y), subtitle, font=subtitle_font, fill=(148, 163, 184, 255))

    accent = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    accent_draw = ImageDraw.Draw(accent)
    accent_draw.arc(
        (inset + size * 0.04, inset + size * 0.04, size - inset - size * 0.04, size - inset - size * 0.04),
        start=200,
        end=338,
        fill=(59, 130, 246, 70),
        width=max(6, size // 120),
    )
    accent = accent.filter(ImageFilter.GaussianBlur(size // 100))
    canvas.alpha_composite(accent)

    return canvas


def save_pngs(master: Image.Image) -> None:
    MASTER_PATH.unlink(missing_ok=True)
    master.save(MASTER_PATH)

    linux = master.resize((512, 512), Image.Resampling.LANCZOS)
    linux.save(LINUX_ICON_PATH)


def save_ico(master: Image.Image) -> None:
    sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    WINDOWS_ICON_PATH.unlink(missing_ok=True)
    master.save(
        WINDOWS_ICON_PATH,
        format="ICO",
        sizes=sizes,
    )


def save_icns(master: Image.Image) -> None:
    if ICONSET_DIR.exists():
        shutil.rmtree(ICONSET_DIR)
    ICONSET_DIR.mkdir(parents=True)

    for base in (16, 32, 128, 256, 512):
        normal = master.resize((base, base), Image.Resampling.LANCZOS)
        retina = master.resize((base * 2, base * 2), Image.Resampling.LANCZOS)
        normal.save(ICONSET_DIR / f"icon_{base}x{base}.png")
        retina.save(ICONSET_DIR / f"icon_{base}x{base}@2x.png")

    MAC_ICON_PATH.unlink(missing_ok=True)
    subprocess.run(
        ["iconutil", "-c", "icns", str(ICONSET_DIR), "-o", str(MAC_ICON_PATH)],
        check=True,
    )
    shutil.rmtree(ICONSET_DIR)


def main() -> None:
    master = draw_icon(MASTER_SIZE)
    save_pngs(master)
    save_ico(master)
    save_icns(master)
    print(f"Generated icons in {ROOT}")


if __name__ == "__main__":
    main()
