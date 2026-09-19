#!/usr/bin/env python3
"""
《雾境遗章》P1 素材管线：
  AI 帧表(assets/*.png, 无alpha, 烘底 #0D1220 变体)
    → 投影分割自动测量网格 + 逐行人工校准(sheet_defs.json)
    → 抠底成 alpha → 按世界比例烘焙缩放(BOX)
    → 主连通域求 pivot(脚底中心)
    → shelf 装箱(页≤2048, padding2) → 每页中位切分量化(palette≤64)
    → android/assets/game/atlas/page-N.png + pack.json
    → core/src/main/resources/frames.json (动画=帧序列+fps+loop+pivot)
    → 预览条/GIF(tools/pipeline/preview/)
确定性输出：同输入同输出。CI 不运行本管线（产物已入库）。
"""
import json, os, sys
from collections import Counter
import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, 'assets')
OUT_ATLAS = os.path.join(ROOT, 'android', 'assets', 'game', 'atlas')
OUT_FRAMES = os.path.join(ROOT, 'core', 'src', 'main', 'resources', 'frames.json')
OUT_PREVIEW = os.path.join(ROOT, 'tools', 'pipeline', 'preview')

PAGE_MAX = 2048
PAD = 2
PALETTE_MAX = 64


# ---------- 测量 ----------
def bg_mode(a):
    flat = a[::3, ::3].reshape(-1, 3)
    q = (flat // 4) * 4
    colors, counts = np.unique(q, axis=0, return_counts=True)
    return colors[np.argmax(counts)].astype(np.int16) + 2


def bands(prof, gap, mins):
    idx = np.where(prof)[0]
    if len(idx) == 0:
        return []
    segs = []
    s = p = int(idx[0])
    for i in idx[1:]:
        i = int(i)
        if i - p > gap:
            if p - s + 1 >= mins:
                segs.append((s, p + 1))
            s = i
        p = i
    if p - s + 1 >= mins:
        segs.append((s, p + 1))
    return segs


def measure_cells(a, bg, tol, row_gap, col_gap, merge_radius, min_size, region=None):
    """返回 region 内内容单元格 bbox 列表（合并半径内列段并帧）。"""
    m = (np.abs(a - bg).sum(axis=2) > tol)
    x0 = y0 = 0
    if region:
        x0, y0, x1, y1 = region
        m = m[y0:y1, x0:x1]
    out = []
    for (ry0, ry1) in bands(m.any(axis=1), row_gap, min_size):
        cols = bands(m[ry0:ry1, :].any(axis=0), col_gap, min_size)
        frames = []
        for (cx0, cx1) in cols:
            if frames and cx0 - frames[-1][1] < merge_radius:
                frames[-1][1] = cx1
            else:
                frames.append([cx0, cx1])
        for (cx0, cx1) in frames:
            sub = m[ry0:ry1, cx0:cx1]
            ys = np.where(sub.any(axis=1))[0]
            out.append((x0 + cx0, y0 + int(ry0 + ys[0]), x0 + cx1, y0 + int(ry0 + ys[-1] + 1)))
    return out


def largest_cc_pivot(mask):
    """最大连通域的脚底中心 (cx, bottom)。mask: HxW bool。"""
    h, w = mask.shape
    seen = np.zeros_like(mask, dtype=bool)
    best = None  # (area, cx_sum, bottom)
    for sy in range(h):
        for sx in range(w):
            if mask[sy, sx] and not seen[sy, sx]:
                stack = [(sy, sx)]
                seen[sy, sx] = True
                area = 0
                sx_sum = 0
                bot = sy
                while stack:
                    y, x = stack.pop()
                    area += 1
                    sx_sum += x
                    bot = max(bot, y)
                    for ny, nx in ((y+1,x),(y-1,x),(y,x+1),(y,x-1)):
                        if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not seen[ny, nx]:
                            seen[ny, nx] = True
                            stack.append((ny, nx))
                if best is None or area > best[0]:
                    best = (area, sx_sum / area, bot)
    if best is None or best[0] < 0.02 * mask.size:
        return w / 2.0, float(h)
    return best[1], float(best[2] + 1)


# ---------- 抠底 / 缩放 ----------
def key_out_bg(rgb, bg, inner=18.0, outer=70.0):
    d = np.abs(rgb.astype(np.int16) - bg).sum(axis=2).astype(np.float32)
    alpha = np.clip((d - inner) / (outer - inner), 0.0, 1.0)
    rgba = np.dstack([rgb, (alpha * 255).astype(np.uint8)]).astype(np.uint8)
    return Image.fromarray(rgba, 'RGBA')


def white_flash(img):
    a = np.asarray(img).copy()
    a[:, :, 0] = 242
    a[:, :, 1] = 246
    a[:, :, 2] = 255
    return Image.fromarray(a, 'RGBA')


def bake_scale(img, scale):
    if abs(scale - 1.0) < 1e-3:
        return img
    w = max(1, round(img.width * scale))
    h = max(1, round(img.height * scale))
    return img.resize((w, h), Image.BOX)


# ---------- 装箱 ----------
def shelf_pack(items, page_max=PAGE_MAX, pad=PAD):
    """items: [(name, w, h)] → pages: [ {name:(x,y)} ]。按高降序 shelf。"""
    order = sorted(items, key=lambda t: (-t[2], -t[1], t[0]))
    pages = []
    cur, cx, cy, row_h = {}, 0, 0, 0
    for name, w, h in order:
        if w > page_max or h > page_max:
            raise SystemExit(f'frame {name} too big {w}x{h}')
        if cx + w + pad > page_max and cx > 0:
            cy += row_h + pad
            cx, row_h = 0, 0
        if cy + h > page_max and (cx > 0 or cy > 0):
            pages.append(cur)
            cur, cx, cy, row_h = {}, 0, 0, 0
        cur[name] = (cx, cy)
        cx += w + pad
        row_h = max(row_h, h)
    if cur:
        pages.append(cur)
    return pages


def median_cut_quantize(page_rgba, max_colors=PALETTE_MAX):
    """对整页做快速中位切分量化（FASTOCTREE 近似），保留 alpha（PNG8+tRNS）。"""
    return page_rgba.quantize(colors=max_colors, method=Image.FASTOCTREE, dither=Image.Dither.NONE)


# ---------- 主流程 ----------
def build():
    defs = json.load(open(os.path.join(os.path.dirname(__file__), 'sheet_defs.json'), encoding='utf-8'))
    os.makedirs(OUT_ATLAS, exist_ok=True)
    os.makedirs(OUT_PREVIEW, exist_ok=True)

    frames = {}       # name -> dict(page,x,y,w,h,pivot=[px,py])
    previews = {}     # name -> PIL.Image (bake 尺寸)

    for sheet in defs['sheets']:
        path = os.path.join(ASSETS, sheet['file'])
        a = np.asarray(Image.open(path).convert('RGB'), dtype=np.int16)
        bg = bg_mode(a)
        tol, col_gap, row_gap = sheet['tol'], sheet['colGap'], sheet['rowGap']
        merge, min_size = sheet['mergeRadius'], sheet['minSize']
        sid = sheet['id']

        cells = []
        row_defs = []
        if 'fixedGrid' in sheet:
            gx, gy = sheet['fixedGrid']
            H, W = a.shape[:2]
            inset = sheet.get('gridInset', 0)
            for r in range(gy):
                for c in range(gx):
                    region = (c * W // gx + inset, r * H // gy + inset,
                              (c + 1) * W // gx - inset, (r + 1) * H // gy - inset)
                    cells.append(measure_cells(a, bg, tol, row_gap, col_gap, merge, min_size, region))
            for s in sheet.get('subRegions', []):
                panel = cells[s['panel'][1] * gx + s['panel'][0]]
                if s['pick'] >= len(panel):
                    raise SystemExit(f"[{sid}] subRegion {s['name']}: pick {s['pick']} >= {len(panel)} cells in panel")
                row_defs.append({'name': s['name'], 'boxes': [panel[s['pick']]]})
        else:
            flat = measure_cells(a, bg, tol, row_gap, col_gap, merge, min_size)
            expect = sum(sum(len(g) for g in r['cells']) for r in sheet['rows'])
            if len(flat) != expect:
                print(f"[{sid}] WARN measured {len(flat)} cells, defs expect {expect}")
            for rd in sheet['rows']:
                boxes = []
                for g in rd['cells']:
                    box = None
                    for ci in g:
                        b = flat[ci]
                        box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]),
                                                     max(box[2], b[2]), max(box[3], b[3]))
                    boxes.append(box)
                row_defs.append({'name': rd['name'], 'boxes': boxes})

        # 整表统一烘焙比例：锚定行内容高中位数 → worldTargetHeight
        if 'worldScale' in sheet:
            scale = sheet['worldScale']
        else:
            anchor_name = sheet.get('anchorRow', row_defs[0]['name'])
            rd_anchor = next(r for r in row_defs if r['name'] == anchor_name)
            med = float(np.median([b[3] - b[1] for b in rd_anchor['boxes']]))
            scale = sheet['worldTargetHeight'] / med

        for rd in row_defs:
            for i, (x0, y0, x1, y1) in enumerate(rd['boxes']):
                crop = a[y0:y1, x0:x1]
                img = key_out_bg(crop, bg)
                img = bake_scale(img, scale)
                m = np.asarray(img)[:, :, 3] > 96
                px, py = largest_cc_pivot(m)
                name = f"{sid}_{rd['name']}_{i}"
                frames[name] = {'img': img, 'pivot': [round(px, 1), round(py, 1)]}
                previews[name] = img
                if sheet.get('flash'):
                    frames[name + '_flash'] = {'img': white_flash(img), 'pivot': [round(px, 1), round(py, 1)]}
            print(f"[{sid}] row {rd['name']}: {len(rd['boxes'])} frames, scale={scale:.4f}")

    # ---------- 装箱 + 量化 ----------
    names = sorted(frames)
    pages = shelf_pack([(n, frames[n]['img'].width, frames[n]['img'].height) for n in names])
    pack = {'pages': [], 'regions': {}}
    used = Counter()
    for pi, page in enumerate(pages):
        w = max(page[n][0] + frames[n]['img'].width for n in page)
        h = max(page[n][1] + frames[n]['img'].height for n in page)
        canvas = Image.new('RGBA', (PAGE_MAX, PAGE_MAX), (0, 0, 0, 0))
        for n in page:
            x, y = page[n]
            canvas.paste(frames[n]['img'], (x, y))
            pack['regions'][n] = {'page': pi, 'x': x, 'y': y,
                                  'w': frames[n]['img'].width, 'h': frames[n]['img'].height,
                                  'pivot': frames[n]['pivot']}
        canvas = canvas.crop((0, 0, w, h))
        q = median_cut_quantize(canvas)
        fname = f'page-{pi}.png'
        q.save(os.path.join(OUT_ATLAS, fname), optimize=True)
        pack['pages'].append({'file': fname, 'w': w, 'h': h})
        colors = len(q.getcolors(maxcolors=256) or [])
        size = os.path.getsize(os.path.join(OUT_ATLAS, fname))
        print(f"[atlas] {fname}: {w}x{h}, colors={colors}, {size//1024}KB, regions={len(page)}")

    with open(os.path.join(OUT_ATLAS, 'pack.json'), 'w', encoding='utf-8') as f:
        json.dump(pack, f, ensure_ascii=False, separators=(',', ':'))

    # ---------- frames.json ----------
    anims = {}
    for aname, ad in defs['anims'].items():
        if 'sub' in ad:
            src = [f"{ad['sheet']}_{ad['sub']}_0"]
        else:
            row = next(r for r in defs['sheets'] if r['id'] == ad['sheet'] for _ in [0]) if False else None
            sheet = next(s for s in defs['sheets'] if s['id'] == ad['sheet'])
            if 'fixedGrid' in sheet:
                src = [ad['sub']]  # 子区域单帧
            else:
                names_in_row = [f"{ad['sheet']}_{r['name']}_{i}"
                                for r in sheet['rows'] if r['name'] == ad['row']
                                for i in range(len(r['cells']))]
                src = names_in_row
        if ad.get('pick') not in (None, 'all'):
            src = [src[i] for i in ad['pick']]
        pivots = [frames[n]['pivot'] for n in src]
        sizes = [(pack['regions'][n]['w'], pack['regions'][n]['h']) for n in src]
        pivot = [round(float(np.median([p[0] / s[0] for p, s in zip(pivots, sizes)])), 3),
                 round(float(np.median([p[1] / s[1] for p, s in zip(pivots, sizes)])), 3)]
        anims[aname] = {'sheet': ad.get('sheet', 'vfx'), 'frames': src,
                        'fps': ad['fps'], 'loop': ad['loop'], 'pivot': pivot}
    doc = {'meta': {'version': 2, 'virtualResolution': [480, 270],
                    'atlasManifest': 'game/atlas/pack.json',
                    'note': '由 tools/pipeline/sprite_pipeline.py 生成 + sheet_defs.json 人工校准'},
           'anims': anims}
    with open(OUT_FRAMES, 'w', encoding='utf-8') as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
    print(f"[frames.json] anims={len(anims)}")

    # ---------- 预览条 ----------
    def strip(frame_names, out, scale=2):
        imgs = [previews[n] for n in frame_names]
        w = sum(i.width for i in imgs) + 4 * (len(imgs) + 1)
        h = max(i.height for i in imgs) + 8
        canvas = Image.new('RGB', (w, h), (24, 30, 52))
        d = ImageDraw.Draw(canvas)
        for i in range(0, w, 8):
            for j in range(0, h, 8):
                if (i // 8 + j // 8) % 2 == 0:
                    d.point((i, j), fill=(30, 38, 64))
        x = 4
        for im in imgs:
            canvas.paste(im, (x, h - 4 - im.height), im)
            x += im.width + 4
        canvas = canvas.resize((canvas.width * scale, canvas.height * scale), Image.NEAREST)
        canvas.save(os.path.join(OUT_PREVIEW, out))

    for aname, ad in defs['anims'].items():
        ns = anims[aname]['frames']
        if aname.startswith('vfx') or len(ns) > 10:
            continue
        strip(ns, f'anim_{aname}.png', scale=2)
    print(f"[preview] strips -> {OUT_PREVIEW}")


if __name__ == '__main__':
    build()
