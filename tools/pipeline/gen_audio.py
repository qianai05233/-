#!/usr/bin/env python3
"""
《雾境遗章》P1 音频生成：程序化自建 CC0 音效 + BGM（本仓库原创合成，无第三方采样）。
输出：
  android/assets/game/audio/sfx/*.ogg      （18 枚音效，OGG Vorbis）
  android/assets/game/audio/music/fog_*.ogg（5 层 BGM，累计混音，循环无缝）
  core/src/main/resources/audio_manifest.json（AudioManifestTest 校验依据）
  tools/pipeline/preview/audio_report.txt   （时长/码率/体积报告）
合成确定性：rng 固定种子。
"""
import json, os, wave, io, sys
import numpy as np
import soundfile as sf

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SFX_DIR = os.path.join(ROOT, 'android', 'assets', 'game', 'audio', 'sfx')
MUS_DIR = os.path.join(ROOT, 'android', 'assets', 'game', 'audio', 'music')
PREVIEW = os.path.join(ROOT, 'tools', 'pipeline', 'preview')
CORE_RES = os.path.join(ROOT, 'core', 'src', 'main', 'resources')

SR = 44100
RNG = np.random.default_rng(20260919)


# ---------------------------------------------------------------- 基元
def t(n):
    return np.arange(n, dtype=np.float64) / SR


def sec(s):
    return int(s * SR)


def env_ar(n, attack, release, hold=1.0):
    """attack/release 线性包络（秒）。"""
    e = np.ones(n)
    a = min(n, sec(attack))
    r = min(n, sec(release))
    if a > 0:
        e[:a] = np.linspace(0, 1, a)
    if r > 0:
        e[n - r:] = np.linspace(1, 0, r)
    return e * hold


def decay(n, tau):
    return np.exp(-t(n) / tau)


def sine(freq, n, detune_cents=0.0, vib_hz=0.0, vib_amt=0.0):
    f = freq * (2 ** (detune_cents / 1200.0))
    ph = 2 * np.pi * f * t(n)
    if vib_hz > 0:
        ph = ph + vib_amt * np.sin(2 * np.pi * vib_hz * t(n)) * np.cumsum(np.ones(n)) / SR * 2 * np.pi
    return np.sin(ph)


def sweep(f0, f1, n, k=1.0):
    """指数扫频正弦。k>1 曲线更陡。"""
    tt = t(n)
    f = f0 * (f1 / f0) ** (tt / tt[-1]) ** k
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def noise(n, seed=None):
    rng = np.random.default_rng(seed if seed is not None else RNG.integers(1 << 31))
    return rng.standard_normal(n)


def _lp1(x, cutoff):
    """单极低通。"""
    a = 1.0 - np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += a * (x[i] - acc)
        y[i] = acc
    return y


def _hp1(x, cutoff):
    return x - _lp1(x, cutoff)


def lowpass(x, cutoff):
    return _lp1(x, float(cutoff))


def highpass(x, cutoff):
    return _hp1(x, float(cutoff))


def bandpass(x, lo, hi):
    return highpass(lowpass(x, hi), lo)


def delay_echo(x, time_s, fb=0.35, mix=0.4, taps=3):
    d = sec(time_s)
    y = x.copy()
    gain = mix
    for k in range(1, taps + 1):
        if k * d >= len(y):
            break
        y[k * d:] += x[:len(x) - k * d] * gain
        gain *= fb
    return y


def distort(x, drive=2.0):
    return np.tanh(x * drive)


def norm(x, peak=0.9):
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 1e-9 else x


def write_ogg(path, data, stereo=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if stereo:
        sf.write(path, np.stack([data, data], axis=1), SR, format='OGG', subtype='VORBIS')
    else:
        sf.write(path, data, SR, format='OGG', subtype='VORBIS')


# ---------------------------------------------------------------- SFX
def sfx_ui_click():
    n = sec(0.05)
    x = sweep(1900, 1250, n) * decay(n, 0.012)
    return norm(x, 0.5)


def sfx_ui_confirm():
    n1, n2 = sec(0.06), sec(0.09)
    x = np.concatenate([
        sine(660, n1) * decay(n1, 0.03),
        sine(990, n2) * decay(n2, 0.045),
    ])
    return norm(x, 0.55)


def sfx_jump():
    n = sec(0.13)
    x = sweep(240, 540, n) * decay(n, 0.06)
    x += highpass(noise(n, 7), 2500) * decay(n, 0.02) * 0.15
    return norm(x * env_ar(n, 0.004, 0.09), 0.55)


def sfx_roll():
    n = sec(0.17)
    body = bandpass(noise(n, 11), 700, 2600)
    mod = sweep(1.0, 3.0, n)[:n]  # 频率感
    x = body * (0.4 + 0.6 * np.abs(sweep(1200, 400, n))) * env_ar(n, 0.02, 0.1)
    x += lowpass(noise(n, 12), 400) * decay(n, 0.05) * 0.4
    return norm(x, 0.5)


def _slash(n_dur, f_start, f_end, ping, level=0.75):
    n = sec(n_dur)
    x = bandpass(noise(n, 21), f_start * 0.5, f_end) * decay(n, n_dur * 0.35)
    x += bandpass(noise(n, 22), f_start, f_end * 1.2) * decay(n, n_dur * 0.18) * 0.7
    x += sine(ping, n) * decay(n, 0.03) * 0.35
    return norm(x * env_ar(n, 0.002, n_dur * 0.5), level)


def sfx_slash_1():
    return _slash(0.09, 3000, 6500, 4300)


def sfx_slash_2():
    return _slash(0.09, 2600, 6000, 3600)


def sfx_slash_3():
    n = sec(0.13)
    x = _slash(0.13, 2000, 7000, 2800, 0.8)
    x[:n] += sine(150, n) * decay(n, 0.045) * 0.5
    return norm(x, 0.85)


def sfx_charge_ready():
    n = sec(0.22)
    trem = 0.6 + 0.4 * np.sin(2 * np.pi * 12 * t(n))
    x = (sine(1174, n) * 0.6 + sine(1760, n) * 0.4) * trem * decay(n, 0.12)
    return norm(x * env_ar(n, 0.01, 0.1), 0.45)


def sfx_charge_release():
    n = sec(0.19)
    x = bandpass(noise(n, 31), 900, 9000) * decay(n, 0.06)
    x += sweep(500, 9000, n) * decay(n, 0.07) * 0.5
    x += sweep(1600, 300, n) * decay(n, 0.1) * 0.4
    return norm(x * env_ar(n, 0.002, 0.12), 0.85)


def _hit(n_dur, thump, nseed, cut, level=0.8):
    n = sec(n_dur)
    x = sine(thump, n) * decay(n, 0.035) * 1.0
    x += sweep(thump * 1.4, thump * 0.7, n) * decay(n, 0.03) * 0.6
    x += bandpass(noise(n, nseed), cut * 0.5, cut) * decay(n, 0.02) * 0.7
    return norm(distort(x, 1.6) * env_ar(n, 0.001, n_dur * 0.6), level)


def sfx_hit_1():
    return _hit(0.08, 190, 41, 2600)


def sfx_hit_2():
    return _hit(0.09, 155, 42, 2100)


def sfx_hit_crit():
    n = sec(0.16)
    x = _hit(0.16, 120, 43, 3000, 0.9)
    metal = (sine(740, n) * 0.5 + sine(1122, n) * 0.4 + sine(1866, n) * 0.25) * decay(n, 0.07)
    return norm(x + metal * 0.7, 0.9)


def sfx_enemy_hit():
    n = sec(0.07)
    x = sweep(250, 150, n) * decay(n, 0.025)
    x += lowpass(noise(n, 51), 1200) * decay(n, 0.02) * 0.8
    return norm(distort(x, 1.4), 0.6)


def sfx_hurt():
    n = sec(0.15)
    f = 130
    x = (sine(f, n) + 0.4 * sine(3 * f, n) + 0.2 * sine(5 * f, n)) * decay(n, 0.06)
    x += sweep(620, 210, n) * decay(n, 0.05) * 0.6
    return norm(distort(x, 2.2) * env_ar(n, 0.002, 0.1), 0.75)


def sfx_die_hero():
    n = sec(0.7)
    vib = np.sin(2 * np.pi * 6 * t(n)) * 8
    x = sweep(300, 52, n) * decay(n, 0.28)
    x += (sweep(450, 78, n) * 0.4) * decay(n, 0.22)
    x += lowpass(noise(n, 61), 900) * decay(n, 0.2) * 0.35
    ph = 2 * np.pi * np.cumsum(sweep_f(300, 52, n)) / SR + vib * 0.001 * np.arange(n)
    return norm(x * env_ar(n, 0.005, 0.35), 0.8)


def sweep_f(f0, f1, n, k=1.0):
    tt = t(n)
    return f0 * (f1 / f0) ** ((tt / tt[-1]) ** k)


def sfx_enemy_die():
    n = sec(0.35)
    x = sweep(520, 110, n) * decay(n, 0.1)
    x += highpass(noise(n, 71), 1800) * decay(n, 0.09) * 0.6
    crackle = (noise(n, 72) > 2.2).astype(np.float64) * decay(n, 0.12) * 0.5
    x += crackle
    return norm(x * env_ar(n, 0.002, 0.2), 0.65)


def sfx_pickup():
    seq = [(659, 0.06), (880, 0.06), (1318, 0.09)]
    parts = []
    for f, d in seq:
        nn = sec(d)
        parts.append((sine(f, nn) + 0.3 * sine(2 * f, nn)) * decay(nn, d * 0.6))
    x = np.concatenate(parts)
    return norm(x, 0.5)


def sfx_portal():
    n = sec(0.9)
    x = sweep(220, 880, n) * 0.5 + sweep(880, 440, n) * 0.3
    vib = 0.15 * np.sin(2 * np.pi * 5 * t(n))
    x *= (0.7 + vib)
    x += lowpass(noise(n, 81), 700) * 0.3
    return norm(x * env_ar(n, 0.25, 0.4), 0.5)


SFX = {
    'ui_click': sfx_ui_click,
    'ui_confirm': sfx_ui_confirm,
    'jump': sfx_jump,
    'roll': sfx_roll,
    'slash_1': sfx_slash_1,
    'slash_2': sfx_slash_2,
    'slash_3': sfx_slash_3,
    'charge_ready': sfx_charge_ready,
    'charge_release': sfx_charge_release,
    'hit_1': sfx_hit_1,
    'hit_2': sfx_hit_2,
    'hit_crit': sfx_hit_crit,
    'enemy_hit': sfx_enemy_hit,
    'hurt': sfx_hurt,
    'die_hero': sfx_die_hero,
    'enemy_die': sfx_enemy_die,
    'pickup': sfx_pickup,
    'portal': sfx_portal,
}


# ---------------------------------------------------------------- BGM（5 层，累计混音）
BPM = 110
BEAT = 60.0 / BPM            # 0.545s
BAR = BEAT * 4
BARS = 4
LOOP_N = sec(BAR * BARS)     # 8.727s

NOTE = {
    'D2': 73.42, 'F2': 87.31, 'G2': 98.0, 'A2': 110.0, 'Bb2': 116.54, 'C3': 130.81,
    'D3': 146.83, 'E3': 164.81, 'F3': 174.61, 'G3': 196.0, 'A3': 220.0, 'Bb3': 233.08,
    'C4': 261.63, 'D4': 293.66, 'E4': 329.63, 'F4': 349.23, 'G4': 392.0, 'A4': 440.0,
    'Bb4': 466.16, 'C5': 523.25, 'D5': 587.33, 'F5': 698.46, 'A5': 880.0,
}

CHORDS = [  # 每小节和弦（根音/三音/五音）
    ['D3', 'F3', 'A3'],   # Dm
    ['Bb2', 'D3', 'F3'],  # Bb
    ['C3', 'E3', 'G3'],   # C
    ['D3', 'F3', 'A3'],   # Dm
]
ROOTS = ['D2', 'Bb2', 'C3', 'D2']
ARP = [['D4', 'F4', 'A4', 'D5'], ['Bb3', 'D4', 'F4', 'Bb4'], ['C4', 'E4', 'G4', 'C5'], ['D4', 'F4', 'A4', 'D5']]


def place(buf, start_s, sig):
    i = sec(start_s)
    j = min(len(buf), i + len(sig))
    if i < len(buf):
        buf[i:j] += sig[:j - i]


def render_pad(n):
    x = np.zeros(n)
    for b, ch in enumerate(CHORDS):
        dur = BAR
        seg = np.zeros(sec(dur))
        for nm in ch:
            f = NOTE[nm]
            seg += sine(f, len(seg), detune_cents=4) + sine(f, len(seg), detune_cents=-5)
        seg *= env_ar(len(seg), 0.45, 0.6, 1.0) / 6.0
        place(x, b * BAR, seg)
    return norm(x, 0.30)


def render_bass(n):
    x = np.zeros(n)
    for b, root in enumerate(ROOTS):
        f = NOTE[root]
        for e in range(8):  # 八分音符
            if e % 4 == 0:
                dur, amp = BEAT * 0.9, 1.0
            elif e % 2 == 0:
                dur, amp = BEAT * 0.5, 0.7
            else:
                dur, amp = BEAT * 0.42, 0.5
            m = sec(dur)
            seg = (sine(f, m) + 0.35 * sine(3 * f, m) + 0.12 * sine(5 * f, m)) * decay(m, dur * 0.4) * amp
            place(x, b * BAR + e * BEAT / 2, seg * 0.5)
    return norm(x, 0.5)


def render_arp(n):
    x = np.zeros(n)
    step = BEAT / 4
    k = 0
    for b in range(BARS):
        for s in range(16):
            nm = ARP[b][k % 4]
            k += 1
            f = NOTE[nm] * (2 if s % 8 in (3, 7) else 1)
            m = sec(step * 1.7)
            seg = (sine(f, m) + 0.33 * sine(3 * f, m) + 0.2 * sine(5 * f, m) + 0.12 * sine(7 * f, m))
            seg *= decay(m, step * 1.1) * (0.9 if s % 4 == 0 else 0.6)
            place(x, b * BAR + s * step, seg * 0.3)
    return norm(delay_echo(x, BEAT * 3 / 4, fb=0.35, mix=0.35), 0.34)


def render_perc(n):
    x = np.zeros(n)
    for b in range(BARS):
        for s in range(16):
            tt = b * BAR + s * BEAT / 4
            m = sec(0.03)
            hat = highpass(noise(m, 100 + s), 6000) * decay(m, 0.008) * (0.5 if s % 2 == 0 else 0.9)
            place(x, tt, hat * 0.35)
            if s in (4, 12):  # 军鼓 2/4 拍
                m = sec(0.09)
                sn = bandpass(noise(m, 200 + s), 1500, 3200) * decay(m, 0.04)
                sn += sine(190, m) * decay(m, 0.03) * 0.5
                place(x, tt, sn * 0.8)
    return norm(x, 0.4)


def render_kick(n):
    x = np.zeros(n)
    for b in range(BARS):
        hits = [0, 2] + ([3.5] if b == BARS - 1 else [])
        for s in hits:
            tt = b * BAR + s * BEAT
            m = sec(0.1)
            f = sweep_f(58, 38, m)
            seg = np.sin(2 * np.pi * np.cumsum(f) / SR) * decay(m, 0.045)
            seg += highpass(noise(m, 300), 1500) * decay(m, 0.006) * 0.4
            place(x, tt, seg * 1.1)
    return norm(x, 0.75)


def loop_crossfade(x):
    """循环无缝：渲染 5 小节裁掉第 1 小节，并对首尾 5ms 微淡入出。"""
    body = x[sec(BAR):sec(BAR) + LOOP_N]
    f = sec(0.005)
    body[:f] *= np.linspace(0.3, 1, f)
    body[-f:] *= np.linspace(1, 0.3, f)
    return body


def build_music():
    n = sec(BAR * (BARS + 1))
    stems = [render_pad(n), render_bass(n), render_arp(n), render_perc(n), render_kick(n)]
    files = []
    acc = np.zeros(n)
    for i, s in enumerate(stems):
        acc = acc + s
        mix = loop_crossfade(norm(acc, 0.85))
        path = os.path.join(MUS_DIR, f'fog_{i}.ogg')
        write_ogg(path, mix, stereo=True)
        files.append(path)
    return files


# ---------------------------------------------------------------- 主流程
def main():
    os.makedirs(SFX_DIR, exist_ok=True)
    os.makedirs(MUS_DIR, exist_ok=True)
    os.makedirs(PREVIEW, exist_ok=True)
    report = []
    manifest = {'sfx': [], 'music': []}

    for name, fn in SFX.items():
        x = fn()
        path = os.path.join(SFX_DIR, f'{name}.ogg')
        write_ogg(path, x)
        info = sf.info(path)
        manifest['sfx'].append({'file': f'{name}.ogg', 'duration': round(info.duration, 3),
                                'bytes': os.path.getsize(path), 'sr': info.samplerate})
        report.append(f"sfx/{name:16s} {info.duration:5.2f}s {os.path.getsize(path)//1024:4d}KB "
                      f"{os.path.getsize(path)*8/info.duration/1000:5.0f}kbps")

    for path in build_music():
        info = sf.info(path)
        manifest['music'].append({'file': os.path.basename(path), 'duration': round(info.duration, 3),
                                  'bytes': os.path.getsize(path), 'sr': info.samplerate})
        report.append(f"music/{os.path.basename(path):12s} {info.duration:5.2f}s {os.path.getsize(path)//1024:4d}KB "
                      f"{os.path.getsize(path)*8/info.duration/1000:5.0f}kbps")

    with open(os.path.join(CORE_RES, 'audio_manifest.json'), 'w', encoding='utf-8') as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
    with open(os.path.join(PREVIEW, 'audio_report.txt'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(report) + '\n')
    print('\n'.join(report))
    print(f"manifest: {len(manifest['sfx'])} sfx, {len(manifest['music'])} music")


if __name__ == '__main__':
    main()
