import glob
import os

from PIL import Image

ROOT = r"C:\Users\Administrator\.dsh\attachments\v1\objects"
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   r"src\main\resources\assets\yuio\textures\gui")

SOURCES = {
    "back": "dffae96",
    "mp_add": "62a1fb44",
    "mp_connect": "728f7549",
    "mp_join": "e3f3302b",
    "mp_edit": "5b001afe",
    "mp_delete": "bd49e283",
    "mp_refresh": "8d817e62",
    "mp_back": "7ef00407",
}


def white_shape(path):
    image = Image.open(path).convert("RGBA")
    pixels = image.load()
    size = image.size
    out = Image.new("RGBA", size, (255, 255, 255, 0))
    target = out.load()

    for y in range(size[1]):
        for x in range(size[0]):
            r, g, b, a = pixels[x, y]
            if a > 0:
                target[x, y] = (255, 255, 255, a)

    return out


def main():
    os.makedirs(OUT, exist_ok=True)

    made = {}
    for name, prefix in SOURCES.items():
        matches = glob.glob(os.path.join(ROOT, prefix[:2], prefix + "*"))
        if not matches:
            raise SystemExit("missing source for " + name)
        image = white_shape(matches[0]).resize((96, 96), Image.LANCZOS)
        path = os.path.join(OUT, name + ".png")
        image.save(path)
        made[name] = path

    for source in [n for n in made if n.startswith("mp_")]:
        copy = os.path.join(OUT, "sp_" + source[3:] + ".png")
        Image.open(made[source]).save(copy)

    for path in sorted(glob.glob(os.path.join(OUT, "*.png"))):
        print(os.path.basename(path), os.path.getsize(path))


main()
