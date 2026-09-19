#!/usr/bin/env python3
"""P1 战斗手感验收 GIF：用 game/atlas 图集帧 + pack.json 坐标合成演示动画。
（宣传/验收素材；非游戏内截图。真实运行画面以真机 + logcat MistboundFPS 为准。）"""
import json, os
import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ATLAS = os.path.join(ROOT, 'android', 'assets', 'game', 'atlas')
OUT = os.path.join(ROOT, 'docs', 'media', 'p1_combat_feel.gif')

pack = json.load(open(os.path.join(ATLAS, 'pack.json')))
pages = {p['file']: Image.open(os.path.join(ATLAS, p['file'])).convert('RGBA') for p in pack['pages']}
REG = pack['regions']


def frame(name, flip=False):
    r = REG[name]
    img = pages[f"page-{r['page']}.png"].crop((r['x'], r['y'], r['x'] + r['w'], r['y'] + r['h']))
    if flip:
        img = img.transpose(Image.FLIP_LEFT_RIGHT)
    return img


def anim_names(anim, flip):
    """返回 [(name, flip)]；动画整体镜像时逐帧翻转并交换 pivot。"""
    out = []
    for n in anim:
        out.append((n, flip))
    return out


def pivot_of(name, flip):
    px, py = REG[name]['pivot']
    px /= REG[name]['w']; py /= REG[name]['h']
    return (1 - px, py) if flip else (px, py)


W, H, GROUND = 480, 270, 210
FPS = 30


def blit(canvas, name, x, y, flip=False, alpha=1.0):
    """x,y = 脚底 pivot 世界坐标。"""
    img = frame(name, flip)
    px, py = pivot_of(name, flip)
    if alpha < 1:
        a = img.getchannel('A').point(lambda v: int(v * alpha))
        img.putalpha(a)
    canvas.alpha_composite(img, (int(x - px * img.width), int(y - py * img.height)))


def bg():
    c = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(c)
    for yy in range(H):
        t = yy / H
        col = (int(6 + 14 * t), int(8 + 16 * t), int(18 + 26 * t), 255)
        d.line([(0, yy), (W, yy)], fill=col)
    # 星星/雾
    rng = np.random.default_rng(7)
    for _ in range(40):
        x, y = rng.integers(0, W), rng.integers(0, 150)
        d.point((x, y), fill=(90, 110, 160, 120))
    d.rectangle([0, GROUND, W, H], fill=(30, 33, 56, 255))
    d.rectangle([0, GROUND, W, GROUND + 3], fill=(92, 102, 148, 255))
    for x in range(40, W, 90):
        d.rectangle([x, GROUND - 8, x + 6, GROUND], fill=(24, 27, 46, 255))
    return c


def text(canvas, s, x, y, big=False, gold=False):
    d = ImageDraw.Draw(canvas)
    # 像素感：用默认字体放大
    from PIL import ImageFont
    size = 16 if big else 11
    try:
        f = ImageFont.load_default(size)
    except TypeError:
        f = ImageFont.load_default()
    col = (255, 220, 120) if gold else (242, 246, 255)
    d.text((x + 1, y + 1), s, font=f, fill=(10, 12, 24))
    d.text((x, y), s, font=f, fill=col)


# ---- 时间轴（帧 → 绘制函数）----
frames = []


class Hero:
    def __init__(self):
        self.x, self.y = 140, GROUND
        self.flip = False
        self.clock = 0.0

    def draw(self, c, name):
        blit(c, name, self.x, self.y, self.flip)


hero = Hero()
fx_list = []   # (name, x, y, flip, born, life, alpha)
nums = []      # (text, x, y, born, crit)


def spawn_fx(name, x, y, flip=False, life=0.25):
    fx_list.append([name, x, y, flip, len(frames), life, 1.0])


def spawn_num(s, x, y, crit=False):
    nums.append([s, x, y, len(frames), crit])


def advance(t_sec):
    """把 hero.clock 推进并返回当前 idle/run/... 帧名。"""
    pass


IDLE = [f"hero_awaken_idle_{i}" for i in range(5)]
ROLL = [f"hero_roll_{i}" for i in range(7)]
WRAITH = ["wraith_idle_0", "wraith_idle_1"]
ATK1, ATK2, ATK3 = ["hero_poses_2"], ["hero_awaken_atk_0", "hero_awaken_atk_2"], ["hero_awaken_atk_1", "hero_awaken_atk_3"]

hero_state = ('hero_awaken_idle_0', 0)
wraith_x, wraith_alive, wraith_flash, wraith_dying = 320, True, -99, -99


def add(seg_name, frames_n, on_frame=None):
    """按 fps 播放 seg_name 帧序列 frames_n 帧。"""
    global hero_state
    for i in range(frames_n):
        nm = seg_name[min(i, len(seg_name) - 1)]
        hero_state = (nm, i)
        if on_frame:
            on_frame(i)
        step()


def step():
    """渲染一帧 GIF。"""
    c = bg()
    t = len(frames)
    # 敌人
    global wraith_alive
    if wraith_alive:
        hurt_t = t - wraith_flash if wraith_flash >= 0 else 999
        die_t = t - wraith_dying if wraith_dying >= 0 else -1
        if die_t < 0:
            nm = "wraith_hurt_0" if hurt_t < 5 else WRAITH[(t // 12) % 2]
            alpha = 1.0
        elif die_t < 12:
            nm, alpha = "wraith_die_0", 1 - die_t / 12
        else:
            wraith_alive = False
            nm, alpha = None, 0
        if nm is not None:
            blit(c, nm, wraith_x, GROUND, flip=True, alpha=alpha)
    # 特效
    for f in fx_list[:]:
        name, x, y, flip, born, life, _ = f
        age = (t - born) / FPS
        if age > life:
            fx_list.remove(f)
            continue
        blit(c, name, x, y, flip)
    # 残影
    for g in ghosts[:]:
        _, x, y, born, _ = g
        age = (t - born) / FPS
        if age > 0.22:
            ghosts.remove(g)
            continue
        nm = ROLL[min(int(age * 24), len(ROLL) - 1)]
        blit(c, nm, x, y, hero.flip, alpha=0.35 * (1 - age / 0.22))
    # 飘字
    for n in nums[:]:
        s, x, y, born, crit = n
        age = (t - born) / FPS
        if age > 0.7:
            nums.remove(n)
            continue
        yy = y - age * 34 + 0.5 * 40 * age * age * 0
        text(c, s, x, yy, big=crit, gold=crit)
    # 主角
    hero.draw(c, hero_state[0])
    frames.append(c)


ghosts = []

# ---- 脚本 ----
# 1) 跑向敌人（0.9s）
for i in range(27):
    hero.x += 3.4
    hero_state = (IDLE[(i // 3) % 5], i)
    step()
# 2) 攻击一段 24
def on1(i):
    if i == 0:
        spawn_fx("vfx_slash_s_0", hero.x + 26, GROUND - 12, life=0.2)
    if i == 3:
        spawn_num("-24", wraith_x - 6, GROUND - 44)
        globals()['wraith_flash'] = len(frames)
add(ATK1, 9, on_frame=on1)
# 3) 攻击二段 26
def on2(i):
    if i == 0:
        spawn_fx("vfx_slash_m_0", hero.x + 28, GROUND - 12, life=0.2)
    if i == 3:
        spawn_num("-26", wraith_x - 6, GROUND - 44)
        globals()['wraith_flash'] = len(frames)
add(ATK2, 11, on_frame=on2)
# 4) 攻击三段 36 → 暴击 63 金色 + hitstar
def on3(i):
    if i == 0:
        spawn_fx("vfx_slash_b_0", hero.x + 30, GROUND - 12, life=0.22)
    if i == 4:
        spawn_num("-63", wraith_x - 10, GROUND - 48, crit=True)
        spawn_fx("vfx_hit_star_0", wraith_x - 4, GROUND - 14, life=0.2)
        globals()['wraith_flash'] = len(frames)
        globals()['wraith_dying'] = len(frames)
add(ATK3, 14, on_frame=on3)
# 5) 敌人消散
for _ in range(10):
    step()
# 6) 翻滚 + 残影（回跑）
for i in range(21):
    hero.x -= 4.2
    hero_state = (ROLL[min(i // 3, 6)], i)
    if i % 2 == 0:
        ghosts.append([None, hero.x, GROUND, len(frames), None])
    step()
# 7) 待机呼吸收尾
for i in range(20):
    hero_state = (IDLE[(i // 5) % 5], i)
    step()

# ---- 导出 GIF（全局统一调色板，避免逐帧调色板跳变）----
os.makedirs(os.path.dirname(OUT), exist_ok=True)
# 调色板源：拼接 4 个代表帧（含金色暴击/白字/剑气/敌人全部颜色）
samples = [frames[i].convert('RGB') for i in (0, len(frames) // 3, 2 * len(frames) // 3, len(frames) - 2)]
collage = Image.new('RGB', (W, H * len(samples)))
for i, sm in enumerate(samples):
    collage.paste(sm, (0, i * H))
master = collage.quantize(colors=255, method=Image.MEDIANCUT, dither=Image.Dither.NONE)
pils = [f.convert('RGB').quantize(palette=master, dither=Image.Dither.NONE) for f in frames]
pils[0].save(OUT, save_all=True, append_images=pils[1:], duration=int(1000 / FPS), loop=0, optimize=True)
print(f"gif: {len(pils)} frames -> {OUT} ({os.path.getsize(OUT)//1024}KB)")
