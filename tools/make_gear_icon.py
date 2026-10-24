from PIL import Image

SRC = "src/main/resources/assets/yuio/textures/gui/settings.png"
DST = "src/main/resources/assets/yuio/textures/gui/gear.png"

img = Image.open(SRC).convert("RGBA")
w, h = img.size
px = img.load()
out = Image.new("RGBA", (w, h))
op = out.load()
for y in range(h):
    for x in range(w):
        r, g, b, a = px[x, y]
        # 描边是黑/深灰, 透明底 alpha=0
        alpha = a if a < 250 else 255 - min(r, g, b)
        op[x, y] = (255, 255, 255, alpha)

out = out.resize((96, 96), Image.LANCZOS)
out.save(DST)
print("saved", DST, out.size)
