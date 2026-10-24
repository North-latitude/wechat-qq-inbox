"""Generate launcher icons and the in-app brand mark from branding/logo-source.jpg.

Run with Pillow available:
    python scripts/make_brand_icons.py
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "branding" / "logo-source.jpg"
ANDROID_RES = ROOT / "android" / "app" / "src" / "main" / "res"


def cropped_logo() -> Image.Image:
    image = Image.open(SRC).convert("RGB")
    gray = image.convert("L")
    # The supplied source has an off-white JPEG background. Find the mark itself.
    mask = gray.point(lambda value: 255 if value < 245 else 0)
    box = mask.getbbox()
    if box is None:
        raise RuntimeError("logo source is blank")
    left, top, right, bottom = box
    margin = 12
    box = (
        max(0, left - margin),
        max(0, top - margin),
        min(image.width, right + margin),
        min(image.height, bottom + margin),
    )
    logo = image.crop(box)
    # The supplied JPEG has a slightly warm off-white background. Normalize
    # only near-white pixels to pure white so the launcher background is clean.
    pixels = logo.load()
    for y in range(logo.height):
        for x in range(logo.width):
            r, g, b = pixels[x, y]
            if min(r, g, b) >= 238:
                pixels[x, y] = (255, 255, 255)
    return logo


def make_foreground(size: int, logo_ratio: float) -> Image.Image:
    tile = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    logo = cropped_logo()
    max_width = int(size * logo_ratio)
    max_height = int(size * logo_ratio)
    scale = min(max_width / logo.width, max_height / logo.height)
    logo = logo.resize(
        (max(1, int(logo.width * scale)), max(1, int(logo.height * scale))),
        Image.Resampling.LANCZOS,
    )
    position = ((size - logo.width) // 2, (size - logo.height) // 2)
    tile.alpha_composite(logo.convert("RGBA"), position)
    return tile


def main() -> None:
    for density, size in {
        "mdpi": 108,
        "hdpi": 162,
        "xhdpi": 216,
        "xxhdpi": 324,
        "xxxhdpi": 432,
    }.items():
        directory = ANDROID_RES / f"mipmap-{density}"
        directory.mkdir(parents=True, exist_ok=True)
        # Keep the mark inside the adaptive-icon safe zone. The adaptive
        # background is pure white, as specified; no coloured tile or border.
        make_foreground(size, logo_ratio=0.58).save(
            directory / "ic_launcher_foreground.png"
        )

    brand = make_foreground(512, logo_ratio=0.92)
    (ANDROID_RES / "drawable-nodpi").mkdir(parents=True, exist_ok=True)
    brand.save(ANDROID_RES / "drawable-nodpi" / "brand_logo.png")
    print("wrote white-background launcher foregrounds and brand_logo.png")


if __name__ == "__main__":
    main()
