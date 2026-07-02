"""Render VibeFlow Play-Store assets to match the in-app adaptive icon:
dark-navy diagonal gradient + blue->purple->amber voice-waveform mark.
Outputs: ic_play_512.png (icon), feature_graphic_1024x500.png."""
import os, math
from PIL import Image, ImageDraw, ImageFont

OUT = r"C:\Users\vamsy\Documents\vibeflow_mobile\dist\play-assets"
os.makedirs(OUT, exist_ok=True)

SS = 2  # supersample for crisp edges
NAVY0, NAVY1 = (27, 42, 74), (14, 23, 48)        # bg gradient
BLUE, PURPLE, AMBER = (59, 111, 232), (138, 92, 246), (245, 165, 36)

def lerp(a, b, t): return tuple(int(a[i] + (b[i]-a[i])*t) for i in range(3))

def diag_gradient(w, h, c0, c1):
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x/w + y/h) / 2
            px[x, y] = lerp(c0, c1, t)
    return img

def hgrad(w, h, stops):
    """Horizontal gradient RGB from (offset,color) stops."""
    img = Image.new("RGB", (w, h)); px = img.load()
    for x in range(w):
        t = x/(w-1)
        # find segment
        for i in range(len(stops)-1):
            o0, c0 = stops[i]; o1, c1 = stops[i+1]
            if o0 <= t <= o1:
                col = lerp(c0, c1, (t-o0)/(o1-o0) if o1>o0 else 0); break
        else:
            col = stops[-1][1]
        for y in range(h): px[x, y] = col
    return img

def capsule(draw, x, y1, y2, w, fill=255):
    r = w/2
    draw.rectangle([x-r, y1, x+r, y2], fill=fill)
    draw.ellipse([x-r, y1-r, x+r, y1+r], fill=fill)
    draw.ellipse([x-r, y2-r, x+r, y2+r], fill=fill)

def bezier(p0, p1, p2, p3, n=40):
    pts = []
    for i in range(n+1):
        t = i/n; u = 1-t
        x = u*u*u*p0[0] + 3*u*u*t*p1[0] + 3*u*t*t*p2[0] + t*t*t*p3[0]
        y = u*u*u*p0[1] + 3*u*u*t*p1[1] + 3*u*t*t*p2[1] + t*t*t*p3[1]
        pts.append((x, y));
    return pts

def thick_polyline(draw, pts, w, fill):
    r = w/2
    for (x, y) in pts: draw.ellipse([x-r, y-r, x+r, y+r], fill=fill)
    draw.line(pts, fill=fill, width=int(w))

def draw_waveform(canvas, cx, cy, scale, stroke=20):
    """Draw the mark centered at (cx,cy) on RGB `canvas`. Viewport is 256, pivot 128."""
    def T(px, py): return (cx + (px-128)*scale, cy + (py-128)*scale)
    w = stroke*scale
    # bounding box of the bars in viewport space ~ x:62..196
    bars = [(72,106,150), (110,78,178), (148,54,202), (186,88,168)]
    big = canvas.size[0]
    mask = Image.new("L", canvas.size, 0); md = ImageDraw.Draw(mask)
    xs = []
    for (bx, y1, y2) in bars:
        X, _ = T(bx, 0); _, Y1 = T(0, y1); _, Y2 = T(0, y2)
        xs.append(X); capsule(md, X, Y1, Y2, w)
    # gradient fill through the bar mask
    x0, x1 = min(xs)-w, max(xs)+w
    grad = Image.new("RGB", canvas.size, BLUE)
    g = hgrad(int(x1-x0), canvas.size[1], [(0.0, BLUE), (0.55, PURPLE), (1.0, AMBER)])
    grad.paste(g, (int(x0), 0))
    canvas.paste(grad, (0, 0), mask)
    # amber flourish (bezier) drawn on top
    fl = [T(204,128), T(220,128), T(222,110), T(234,100)]
    fd = ImageDraw.Draw(canvas)
    thick_polyline(fd, bezier(*fl, n=48), w, AMBER)

def font(size):
    for p in (r"C:\Windows\Fonts\segoeuib.ttf", r"C:\Windows\Fonts\arialbd.ttf"):
        if os.path.exists(p): return ImageFont.truetype(p, size)
    return ImageFont.load_default()

# ---- 1) Play Store icon 512x512 ----
S = 512*SS
icon = diag_gradient(S, S, NAVY0, NAVY1)
draw_waveform(icon, S//2, S//2, scale=(S/256)*0.62, stroke=22)
icon = icon.resize((512, 512), Image.LANCZOS)
icon.save(os.path.join(OUT, "ic_play_512.png"))

# ---- 2) Feature graphic 1024x500 ----
W, H = 1024*SS, 500*SS
fg = diag_gradient(W, H, NAVY0, NAVY1)
draw_waveform(fg, int(W*0.17), H//2, scale=(H/256)*0.58, stroke=20)
d = ImageDraw.Draw(fg)
tx = int(W*0.31); margin = int(W*0.05)
avail = W - tx - margin
tsize = 168*SS; title_f = font(tsize)
while d.textlength("VibeFlow", font=title_f) > avail and tsize > 40:
    tsize -= 4*SS; title_f = font(tsize)
sub_f = font(int(tsize*0.40))
tb = d.textbbox((0, 0), "VibeFlow", font=title_f)
sb = d.textbbox((0, 0), "Polish with AI", font=sub_f)
th = tb[3]-tb[1]; sh = sb[3]-sb[1]; gap = int(22*SS)
y0 = (H - (th + gap + sh)) // 2
d.text((tx, y0 - tb[1]), "VibeFlow", font=title_f, fill=(255, 255, 255))
d.text((tx, y0 + th + gap - sb[1]), "Polish with AI", font=sub_f, fill=(176, 190, 222))
fg = fg.resize((1024, 500), Image.LANCZOS)
fg.save(os.path.join(OUT, "feature_graphic_1024x500.png"))

print("wrote:", os.listdir(OUT))
