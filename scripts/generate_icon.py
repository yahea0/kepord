import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont


def find_bold_font(size: int) -> ImageFont.FreeTypeFont:
    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
        "/usr/share/fonts/truetype/ubuntu/Ubuntu-B.ttf",
        "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                pass
    for root, _, files in os.walk("/usr/share/fonts"):
        for fname in files:
            if "bold" in fname.lower() and fname.endswith((".ttf", ".otf")):
                try:
                    return ImageFont.truetype(os.path.join(root, fname), size)
                except Exception:
                    pass
    return ImageFont.load_default()


def build_background(size: int = 1024) -> Image.Image:
    img = Image.new("RGBA", (size, size), (4, 4, 6, 255))
    cx, cy = size * 0.5, size * 0.48
    max_r = size * 0.72

    grad = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grad)
    steps = 48
    for i in range(steps, 0, -1):
        t = i / float(steps)
        r = max_r * t
        lum = int(28 * (1.0 - t) ** 1.8 + 3)
        gd.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(lum, lum, lum + 3, 255))
    img = Image.alpha_composite(img, grad)

    grid = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(grid)
    vp_x, vp_y = size * 0.5, -size * 0.25
    for i in range(-14, 15):
        bx = vp_x + (i / 14.0) * size * 1.4
        alpha = max(14, int(40 * (1.0 - abs(i) / 16.0)))
        gdraw.line([(vp_x, vp_y), (bx, size)], fill=(255, 255, 255, alpha), width=2)
    for r_idx in range(1, 16):
        t = r_idx / 15.0
        y = size * (t * t)
        alpha = int(16 + 32 * t)
        gdraw.line([(0, y), (size, y)], fill=(245, 245, 250, alpha), width=2)

    return Image.alpha_composite(img, grid)


def draw_glyph_or_legend(draw: ImageDraw.ImageDraw, box, legend: str, font, color):
    x0, y0, x1, y1 = box
    cx, cy = (x0 + x1) * 0.5, (y0 + y1) * 0.5
    w, h = x1 - x0, y1 - y0

    if legend == "__SPACEBAR__":
        bar_w = w * 0.54
        bar_h = max(6.0, h * 0.12)
        draw.rounded_rectangle(
            [cx - bar_w * 0.5, cy - bar_h * 0.5, cx + bar_w * 0.5, cy + bar_h * 0.5],
            radius=bar_h * 0.5,
            fill=color,
        )
        return

    if legend == "__SHIFT__":
        sw, sh = w * 0.25, h * 0.26
        pts = [
            (cx, cy - sh),
            (cx + sw, cy),
            (cx + sw * 0.42, cy),
            (cx + sw * 0.42, cy + sh * 0.85),
            (cx - sw * 0.42, cy + sh * 0.85),
            (cx - sw * 0.42, cy),
            (cx - sw, cy),
        ]
        draw.polygon(pts, fill=color)
        return

    if legend == "__ENTER__":
        ew, eh = w * 0.25, h * 0.20
        draw.line(
            [(cx + ew, cy - eh * 0.8), (cx + ew, cy + eh * 0.35), (cx - ew * 0.65, cy + eh * 0.35)],
            fill=color,
            width=max(5, int(w * 0.075)),
        )
        pts = [
            (cx - ew, cy + eh * 0.35),
            (cx - ew * 0.22, cy - eh * 0.25),
            (cx - ew * 0.22, cy + eh * 0.95),
        ]
        draw.polygon(pts, fill=color)
        return

    bbox = draw.textbbox((0, 0), legend, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    tx = cx - tw * 0.5 - bbox[0]
    ty = cy - th * 0.5 - bbox[1]
    draw.text((tx, ty), legend, font=font, fill=color)


def build_foreground(size: int = 1024) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    kb_l, kb_t = int(size * 0.24), int(size * 0.29)
    kb_r, kb_b = int(size * 0.76), int(size * 0.71)
    chassis_r = 42

    shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle(
        [kb_l - 8, kb_t + 14, kb_r + 8, kb_b + 34],
        radius=chassis_r + 6,
        fill=(0, 0, 0, 215),
    )
    canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(20)))

    base = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bd = ImageDraw.Draw(base)
    bd.rounded_rectangle(
        [kb_l, kb_t + 16, kb_r, kb_b + 20],
        radius=chassis_r,
        fill=(18, 18, 22, 255),
        outline=(90, 90, 102, 255),
        width=3,
    )
    bd.rounded_rectangle(
        [kb_l, kb_t, kb_r, kb_b],
        radius=chassis_r,
        fill=(8, 8, 11, 255),
        outline=(230, 230, 240, 235),
        width=4,
    )
    pad = 18
    bd.rounded_rectangle(
        [kb_l + pad, kb_t + pad, kb_r - pad, kb_b - pad],
        radius=24,
        fill=(3, 3, 5, 255),
        outline=(44, 44, 54, 255),
        width=2,
    )
    canvas = Image.alpha_composite(canvas, base)

    rows = [
        [("Q", 1.0), ("W", 1.0), ("E", 1.0), ("R", 1.0)],
        [("A", 1.0), ("K", 1.25), ("D", 1.0), ("__ENTER__", 1.15)],
        [("__SHIFT__", 1.15), ("__SPACEBAR__", 2.55), ("AR", 1.05)],
    ]

    well_l, well_t = kb_l + pad + 12, kb_t + pad + 12
    well_r, well_b = kb_r - pad - 12, kb_b - pad - 12
    well_w, well_h = well_r - well_l, well_b - well_t
    gap_x, gap_y = 12, 13
    row_h = (well_h - gap_y * (len(rows) - 1)) / len(rows)

    keys_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    kd = ImageDraw.Draw(keys_layer)
    glow_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gld = ImageDraw.Draw(glow_layer)
    face_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    fd = ImageDraw.Draw(face_layer)

    font_big = find_bold_font(50)
    font_med = find_bold_font(38)
    font_small = find_bold_font(30)

    for r_idx, row in enumerate(rows):
        total_w = sum(w for _, w in row)
        avail_w = well_w - gap_x * (len(row) - 1)
        cy0 = well_t + r_idx * (row_h + gap_y)
        cy1 = cy0 + row_h
        cx0 = well_l
        for legend, weight in row:
            kw = (weight / total_w) * avail_w
            kx0, kx1 = cx0, cx0 + kw
            is_hero = legend == "K"

            kd.rounded_rectangle([kx0, cy0 + 7, kx1, cy1 + 9], radius=16, fill=(0, 0, 0, 235))
            kd.rounded_rectangle(
                [kx0, cy0 + 5, kx1, cy1 + 5],
                radius=16,
                fill=(10, 10, 14, 255),
                outline=(58, 58, 70, 255),
                width=2,
            )
            top_fill = (26, 26, 33, 255) if is_hero else (17, 17, 22, 255)
            rim_col = (255, 255, 255, 240) if is_hero else (125, 125, 142, 215)
            kd.rounded_rectangle(
                [kx0, cy0, kx1, cy1 - 2],
                radius=16,
                fill=top_fill,
                outline=rim_col,
                width=3 if is_hero else 2,
            )
            kd.rounded_rectangle(
                [kx0 + 6, cy0 + 5, kx1 - 6, cy1 - 10],
                radius=11,
                fill=(12, 12, 16, 255),
                outline=(40, 40, 50, 255),
                width=1,
            )

            if is_hero:
                fnt = font_big
            elif len(legend) > 1 and not legend.startswith("__"):
                fnt = font_small
            else:
                fnt = font_med

            text_box = (kx0, cy0 + 2, kx1, cy1 - 10)
            draw_glyph_or_legend(
                fd,
                (text_box[0] + 2, text_box[1] + 3, text_box[2] + 2, text_box[3] + 3),
                legend,
                fnt,
                (65, 65, 78, 255),
            )
            draw_glyph_or_legend(gld, text_box, legend, fnt, (255, 255, 255, 255))
            draw_glyph_or_legend(fd, text_box, legend, fnt, (255, 255, 255, 255))

            cx0 += kw + gap_x

    canvas = Image.alpha_composite(canvas, keys_layer)
    canvas = Image.alpha_composite(canvas, glow_layer.filter(ImageFilter.GaussianBlur(14)))
    canvas = Image.alpha_composite(canvas, glow_layer.filter(ImageFilter.GaussianBlur(5)))
    canvas = Image.alpha_composite(canvas, face_layer)
    return canvas


def apply_mask(img: Image.Image, rounded_square: bool) -> Image.Image:
    s = img.size[0]
    mask = Image.new("L", (s, s), 0)
    md = ImageDraw.Draw(mask)
    if rounded_square:
        md.rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.22), fill=255)
    else:
        md.ellipse([0, 0, s - 1, s - 1], fill=255)
    out = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def main():
    res_dir = os.path.join("app", "src", "main", "res")

    for old_xml in (
        os.path.join(res_dir, "drawable", "ic_launcher_background.xml"),
        os.path.join(res_dir, "drawable", "ic_launcher_foreground.xml"),
    ):
        if os.path.exists(old_xml):
            os.remove(old_xml)

    bg_master = build_background(1024)
    fg_master = build_foreground(1024)

    nodpi_dir = os.path.join(res_dir, "drawable-nodpi")
    os.makedirs(nodpi_dir, exist_ok=True)
    bg_master.resize((432, 432), Image.Resampling.LANCZOS).save(
        os.path.join(nodpi_dir, "ic_launcher_background.png")
    )
    fg_master.resize((432, 432), Image.Resampling.LANCZOS).save(
        os.path.join(nodpi_dir, "ic_launcher_foreground.png")
    )

    crop_m = int(1024 * 0.167)
    composed = (
        Image.alpha_composite(bg_master, fg_master)
        .crop((crop_m, crop_m, 1024 - crop_m, 1024 - crop_m))
        .resize((512, 512), Image.Resampling.LANCZOS)
    )

    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, px in densities.items():
        out_dir = os.path.join(res_dir, folder)
        os.makedirs(out_dir, exist_ok=True)
        sq = apply_mask(composed, True).resize((px, px), Image.Resampling.LANCZOS)
        rnd = apply_mask(composed, False).resize((px, px), Image.Resampling.LANCZOS)
        sq.save(os.path.join(out_dir, "ic_launcher.png"))
        rnd.save(os.path.join(out_dir, "ic_launcher_round.png"))

    print("Kepord 3D Mechanical Keyboard icon generated successfully.")


if __name__ == "__main__":
    main()
