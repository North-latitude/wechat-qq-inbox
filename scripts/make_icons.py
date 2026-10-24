"""Generate PWA icons with Pillow.

Usage: python scripts/make_icons.py
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "web" / "public" / "icons"


def rounded(draw, box, radius, fill):
    draw.rounded_rectangle(box, radius=radius, fill=fill)


def draw_icon(size: int, maskable: bool = False) -> Image.Image:
    scale = 2
    canvas = Image.new("RGBA", (size * scale, size * scale), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    s = size * scale

    if maskable:
        draw.rectangle((0, 0, s, s), fill="#0f766e")
        inset = int(s * 0.09)
    else:
        rounded(draw, (0, 0, s, s), int(s * 0.226), "#0f766e")
        inset = 0

    unit = s / 512
    pad = inset

    # Back bubble (white)
    rounded(
        draw,
        (pad + int(78 * unit), pad + int(100 * unit), pad + int(318 * unit), pad + int(278 * unit)),
        int(36 * unit),
        "#ffffff",
    )
    draw.polygon(
        [
            (pad + int(138 * unit), pad + int(260 * unit)),
            (pad + int(138 * unit), pad + int(330 * unit)),
            (pad + int(196 * unit), pad + int(278 * unit)),
        ],
        fill="#ffffff",
    )

    # Front bubble (mint)
    rounded(
        draw,
        (pad + int(200 * unit), pad + int(208 * unit), pad + int(432 * unit), pad + int(382 * unit)),
        int(34 * unit),
        "#ccfbf1",
    )
    draw.polygon(
        [
            (pad + int(292 * unit), pad + int(366 * unit)),
            (pad + int(292 * unit), pad + int(430 * unit)),
            (pad + int(344 * unit), pad + int(382 * unit)),
        ],
        fill="#ccfbf1",
    )

    # Amber unread dot
    draw.ellipse(
        (pad + int(356 * unit), pad + int(72 * unit), pad + int(438 * unit), pad + int(154 * unit)),
        fill="#f59e0b",
    )
    draw.line(
        [
            (pad + int(378 * unit), pad + int(113 * unit)),
            (pad + int(392 * unit), pad + int(127 * unit)),
            (pad + int(418 * unit), pad + int(98 * unit)),
        ],
        fill="#ffffff",
        width=max(2, int(11 * unit)),
        joint="curve",
    )

    return canvas.resize((size, size), Image.Resampling.LANCZOS)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for size in (180, 192, 512):
        draw_icon(size).save(OUT / f"icon-{size}.png")
    draw_icon(512, maskable=True).save(OUT / "icon-512-maskable.png")
    print("wrote icons to", OUT)


if __name__ == "__main__":
    main()
