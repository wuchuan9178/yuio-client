"""把用户给的黑色描边图标转成白色 RGBA 96x96 图标。

透明底图的 alpha 直接取源 alpha 通道, RGB 全白。
"""

import os

from PIL import Image

ATTACH = r"C:\Users\Administrator\.dsh\attachments\v1\objects"
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src/main/resources/assets/yuio/textures/gui")

SOURCES = {
    "cat_combat": r"e4\e4f37c9c5b40797839022004c2756d5632eb5580760e77b1544e6a9cf82c2aaa",
    "cat_player": r"71\711643517f486f33765faa49922bfa0071225d88903e2a0d71407a8284caa99e",
    "cat_movement": r"b2\b29cb7fc4c4aa670d1429fb7644109c1df09c5ece0a6ba571d25bd49d9049970",
    "cat_render": r"61\61059d9a1140175da250d3f807b2fb22251e3699418f33af13c1824e55e49594",
    "cat_world": r"86\867024524b1f0a3647a97a7519a489a83b638471a37846e9aca9f412b5d65041",
    "cat_misc": r"ce\cef86639f0780d064d48908a2435617d84f8de02c6c724b91662740851090de8",
    "search": r"ca\cab1603cfcdf43a0d02f35bd373c9aa31d4ce9b3ba450eba45e9dc8950925282",
}


def convert(src, dst):
    image = Image.open(src).convert("RGBA")
    alpha = image.getchannel("A")
    white = Image.new("RGBA", image.size, (255, 255, 255, 0))
    white.putalpha(alpha)
    white = white.resize((96, 96), Image.LANCZOS)
    white.save(dst)
    print("wrote", dst)


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, rel in SOURCES.items():
        convert(os.path.join(ATTACH, rel), os.path.join(OUT, name + ".png"))


if __name__ == "__main__":
    main()
