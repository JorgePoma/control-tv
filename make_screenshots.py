"""Turns raw phone screenshots into docs/screenshots.png: cropped below the browser bar, framed as phones, side by side."""
import sys
from PIL import Image, ImageDraw, ImageFilter

CROP_TOP = 275        # status bar + browser address bar (hides IP, notifications, battery)
PHONE_W = 430         # screen width in the final image
BEZEL = 14
RADIUS = 46
GAP = 64
PAD = 48


def rounded(size, r):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius=r, fill=255)
    return m


def phone(path, height):
    shot = Image.open(path).convert("RGB")
    shot = shot.crop((0, CROP_TOP, shot.width, shot.height))
    scale = PHONE_W / shot.width
    shot = shot.resize((PHONE_W, round(shot.height * scale)), Image.LANCZOS)
    shot = shot.crop((0, 0, PHONE_W, height))

    w, h = PHONE_W + 2 * BEZEL, height + 2 * BEZEL
    body = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    frame = Image.new("RGBA", (w, h), (42, 44, 49, 255))
    body.paste(frame, (0, 0), rounded((w, h), RADIUS + BEZEL))
    body.paste(shot, (BEZEL, BEZEL), rounded(shot.size, RADIUS))
    return body


if __name__ == "__main__":
    out, *shots = sys.argv[1:]
    # Same visible height for every phone: the shortest cropped screenshot.
    heights = []
    for p in shots:
        im = Image.open(p)
        heights.append(round((im.height - CROP_TOP) * PHONE_W / im.width))
    height = min(min(heights), 860)
    phones = [phone(p, height) for p in shots]

    pw, ph = phones[0].size
    W = PAD * 2 + pw * len(phones) + GAP * (len(phones) - 1)
    H = PAD * 2 + ph
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for i in range(len(phones)):
        x = PAD + i * (pw + GAP)
        s = Image.new("RGBA", (pw, ph), (0, 0, 0, 120))
        shadow.paste(s, (x, PAD + 14), rounded((pw, ph), RADIUS + BEZEL))
    canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(18)))

    for i, p in enumerate(phones):
        canvas.alpha_composite(p, (PAD + i * (pw + GAP), PAD))
    canvas.save(out, optimize=True)
    print(out, canvas.size)
