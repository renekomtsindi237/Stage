"""Génère les icônes launcher Android depuis MicroRecouv.png."""

from __future__ import annotations

from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[5]
SOURCES = (
    REPO / "MicroRecouv.png",
    REPO / "frontend" / "src" / "assets" / "logo.png",
    REPO / "mobile" / "assets" / "images" / "logo.png",
)

DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

FOREGROUND = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}


def source_logo() -> Path:
    for path in SOURCES:
        if path.is_file():
            return path
    raise FileNotFoundError("MicroRecouv.png introuvable")


def square_from_logo(src: Image.Image, size: int, pad_ratio: float = 0.12) -> Image.Image:
    bg = src.getpixel((0, 0))
    canvas = Image.new("RGBA", (size, size), bg)
    inner = max(1, int(size * (1 - 2 * pad_ratio)))
    fitted = src.copy()
    fitted.thumbnail((inner, inner), Image.Resampling.LANCZOS)
    canvas.paste(fitted, ((size - fitted.width) // 2, (size - fitted.height) // 2), fitted)
    return canvas


def main() -> None:
    src = Image.open(source_logo()).convert("RGBA")
    for folder, size in DENSITIES.items():
        dest = HERE / folder
        dest.mkdir(parents=True, exist_ok=True)
        icon = square_from_logo(src, size)
        icon.save(dest / "ic_launcher.png", format="PNG")
        icon.save(dest / "ic_launcher_round.png", format="PNG")
    for folder, size in FOREGROUND.items():
        dest = HERE / folder
        dest.mkdir(parents=True, exist_ok=True)
        square_from_logo(src, size, pad_ratio=0.18).save(
            dest / "ic_launcher_foreground.png", format="PNG"
        )
    adaptive = HERE / "mipmap-anydpi-v26"
    adaptive.mkdir(parents=True, exist_ok=True)
    adaptive.joinpath("ic_launcher.xml").write_text(
        """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launch_bg"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
""",
        encoding="utf-8",
    )
    adaptive.joinpath("ic_launcher_round.xml").write_text(
        adaptive.joinpath("ic_launcher.xml").read_text(encoding="utf-8"),
        encoding="utf-8",
    )
    print("icônes Android générées depuis", source_logo())


if __name__ == "__main__":
    main()
