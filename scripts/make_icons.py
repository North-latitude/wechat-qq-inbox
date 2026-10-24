"""Generate PWA icons from the same source mark as the Android launcher icon.

Usage:
    python scripts/make_icons.py
"""
from __future__ import annotations

import base64
import io
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "branding" / "logo-source.jpg"
OUT = ROOT / "web" / "public" / "icons"


def cropped_logo() -> Image.Image:
    image = Image.open(SRC).convert("RGB")
    gray = image.convert("L")
    box = gray.point(lambda value: 255 if value < 245 else 0).getbbox()
    if box is None:
        raise RuntimeError("logo source is blank")
    left, top, right, bottom = box
    margin = 12
    logo = image.crop(
        (
            max(0, left - margin),
            max(0, top - margin),
            min(image.width, right + margin),
            min(image.height, bottom + margin),
        )
    )
    pixels = logo.load()
    for y in range(logo.height):
        for x in range(logo.width):
            if min(pixels[x, y]) >= 238:
                pixels[x, y] = (255, 255, 255)
    return logo


def make_icon(size: int, ratio: float) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    logo = cropped_logo()
    scale = min((size * ratio) / logo.width, (size * ratio) / logo.height)
    logo = logo.resize(
        (max(1, int(logo.width * scale)), max(1, int(logo.height * scale))),
        Image.Resampling.LANCZOS,
    )
    canvas.alpha_composite(
        logo.convert("RGBA"),
        ((size - logo.width) // 2, (size - logo.height) // 2),
    )
    return canvas


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for size, name in {
        180: "icon-180.png",
        192: "icon-192.png",
        512: "icon-512.png",
    }.items():
        make_icon(size, 0.62).convert("RGB").save(OUT / name, optimize=True)
    make_icon(512, 0.54).convert("RGB").save(OUT / "icon-512-maskable.png", optimize=True)

    icon = make_icon(512, 0.62).convert("RGB")
    buffer = io.BytesIO()
    icon.save(buffer, format="PNG", optimize=True)
    encoded = base64.b64encode(buffer.getvalue()).decode("ascii")
    (OUT / "icon.svg").write_text(
        '<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" '
        'viewBox="0 0 512 512">'
        '<rect width="512" height="512" fill="#ffffff"/>'
        f'<image href="data:image/png;base64,{encoded}" width="512" height="512"/>'
        "</svg>",
        encoding="utf-8",
    )
    print("wrote white-background PWA icons")


if __name__ == "__main__":
    main()
