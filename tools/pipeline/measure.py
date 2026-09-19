#!/usr/bin/env python3
"""帧表网格测量：按背景色投影分割行列，输出每帧 bbox（frames.json 校准依据）。"""
import json, sys
import numpy as np
from PIL import Image

BG = np.array([13, 18, 32], dtype=np.int16)  # #0D1220

def load(path):
    im = Image.open(path).convert('RGB')
    return np.asarray(im, dtype=np.int16)

def content_mask(a, tol=14):
    return (np.abs(a - BG).sum(axis=2) > tol)

def bands(profile, min_gap, min_size):
    """profile: 1D bool。返回 [(start,end)] 连续内容区段，gap<min_gap 合并。"""
    idx = np.where(profile)[0]
    if len(idx) == 0:
        return []
    segs = []
    s = p = int(idx[0])
    for i in idx[1:]:
        i = int(i)
        if i - p > min_gap:
            if p - s + 1 >= min_size:
                segs.append((s, p + 1))
            s = i
        p = i
    if p - s + 1 >= min_size:
        segs.append((s, p + 1))
    return segs

def measure(path, row_gap=10, col_gap=10, merge_radius=26, min_size=6, exclude_rects=()):
    a = load(path)
    m = content_mask(a)
    for (x, y, w, h) in exclude_rects:
        m[y:y+h, x:x+w] = False
    rows = bands(m.any(axis=1), row_gap, min_size)
    out = []
    for (ry0, ry1) in rows:
        band = m[ry0:ry1, :]
        cols = bands(band.any(axis=0), col_gap, min_size)
        # 用二维 flood 分组：把相邻 <merge_radius 的列段并成帧
        frames = []
        for (cx0, cx1) in cols:
            if frames and cx0 - frames[-1][1] < merge_radius:
                frames[-1][1] = cx1
            else:
                frames.append([cx0, cx1])
        for (cx0, cx1) in frames:
            sub = m[ry0:ry1, cx0:cx1]
            ys = np.where(sub.any(axis=1))[0]
            out.append({'x': int(cx0), 'y': int(ry0 + ys[0]), 'w': int(cx1 - cx0), 'h': int(ys[-1] - ys[0] + 1)})
    return out, a.shape

if __name__ == '__main__':
    path = sys.argv[1]
    kw = json.loads(sys.argv[2]) if len(sys.argv) > 2 else {}
    cells, shape = measure(path, **kw)
    print(f"# {path} shape={shape} cells={len(cells)}")
    for i, c in enumerate(cells):
        print(i, c)
