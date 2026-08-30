"""Génère les icônes PNG et ICO à partir de MicroRecouv.png."""

from __future__ import annotations

from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
SOURCES = (
    REPO / "MicroRecouv.png",
    REPO / "frontend" / "src" / "assets" / "logo.png",
)


def source_logo() -> Path:
    for path in SOURCES:
        if path.is_file():
            return path
    raise FileNotFoundError("MicroRecouv.png introuvable (racine du dépôt ou frontend/src/assets/logo.png)")


def square_from_logo(src: Image.Image, size: int) -> Image.Image:
    bg = src.getpixel((0, 0))
    canvas = Image.new("RGBA", (size, size), bg)
    fitted = src.copy()
    fitted.thumbnail((size, size), Image.Resampling.LANCZOS)
    canvas.paste(fitted, ((size - fitted.width) // 2, (size - fitted.height) // 2), fitted)
    return canvas


def main() -> None:
    src = Image.open(source_logo()).convert("RGBA")
    variants = {
        "32x32.png": 32,
        "128x128.png": 128,
        "128x128@2x.png": 256,
        "icon.png": 512,
        "app-icon.png": 1024,
    }
    for name, size in variants.items():
        square_from_logo(src, size).save(HERE / name, format="PNG")

    ico_sizes = [16, 24, 32, 48, 64, 128, 256]
    ico_images = [square_from_logo(src, size) for size in ico_sizes]
    ico_images[-1].save(
        HERE / "icon.ico",
        format="ICO",
        sizes=[(s, s) for s in ico_sizes],
        append_images=ico_images[:-1],
    )
    print("icônes générées depuis", source_logo(), "vers", HERE)


if __name__ == "__main__":
    main()
