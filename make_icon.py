"""Draws the Control TV icon: blue-violet gradient tile with a white D-pad ring and OK dot."""
import sys
from PIL import Image, ImageDraw, ImageFilter

S = 2048  # supersampled canvas, scaled down at the end


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def render(rounded):
    top, bottom = (79, 140, 255), (123, 92, 255)
    img = Image.new("RGB", (S, S))
    px = img.load()
    for y in range(S):
        for x in range(S):
            px[x, y] = lerp(top, bottom, (x + y) / (2 * S))

    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    c = S / 2
    white = (255, 255, 255, 255)

    # Soft shadow under the ring.
    shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ds = ImageDraw.Draw(shadow)
    r = S * 0.30
    ds.ellipse([c - r, c - r + S * 0.025, c + r, c + r + S * 0.025], fill=(20, 10, 80, 110))
    shadow = shadow.filter(ImageFilter.GaussianBlur(S * 0.03))

    # Ring.
    w = S * 0.045
    d.ellipse([c - r, c - r, c + r, c + r], outline=white, width=round(w))

    # Chevrons on the ring's four sides.
    a = S * 0.055  # half-width of a chevron
    off = r - S * 0.105
    lw = round(S * 0.032)
    for dx, dy in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        tip = (c + dx * (off + a * 0.5), c + dy * (off + a * 0.5))
        base = (c + dx * (off - a * 0.4), c + dy * (off - a * 0.4))
        px_, py_ = -dy, dx  # perpendicular
        p1 = (base[0] + px_ * a, base[1] + py_ * a)
        p2 = (base[0] - px_ * a, base[1] - py_ * a)
        d.line([p1, tip, p2], fill=white, width=lw, joint="curve")
        for p in (p1, tip, p2):
            d.ellipse([p[0] - lw / 2, p[1] - lw / 2, p[0] + lw / 2, p[1] + lw / 2], fill=white)

    # OK dot.
    ok = S * 0.085
    d.ellipse([c - ok, c - ok, c + ok, c + ok], fill=white)

    img = Image.alpha_composite(img.convert("RGBA"), shadow)
    img = Image.alpha_composite(img, layer)

    if rounded:
        mask = Image.new("L", (S, S), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=round(S * 0.225), fill=255)
        img.putalpha(mask)
    return img


if __name__ == "__main__":
    out = sys.argv[1]
    full = render(rounded=False)   # maskable: launcher applies its own shape
    for size in (192, 512):
        full.resize((size, size), Image.LANCZOS).save(f"{out}/assets/icon-{size}.png", optimize=True)
    full.resize((180, 180), Image.LANCZOS).save(f"{out}/assets/apple-touch-icon.png", optimize=True)
    round_ = render(rounded=True)
    round_.resize((192, 192), Image.LANCZOS).save(f"{out}/res/mipmap-xxxhdpi/ic_launcher.png", optimize=True)
    round_.resize((512, 512), Image.LANCZOS).save(f"{out}/preview.png")
