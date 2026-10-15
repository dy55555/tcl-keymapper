# -*- coding: utf-8 -*-
"""按键改键器图标生成器 —— 画出 ic_launcher 的五档 mipmap。

设计思路：一颗"键帽"上刻一个大写 A，右下角压一个白色的「循环」符号
（两段弧 + 两个三角箭头），表示"把按键指向换成另一个应用"。

    · 键帽：琥珀色渐变顶面 + 底部深色侧壁（3D 唇边）
    · A：阴刻三层（左上暗 / 右下亮 / 中间槽底），居中对齐键帽
    · 循环圈：外径 172 设计单位、线宽 22，右下角探出键帽 8

★ 2026-10-12 修：转换圈的两个三角箭头原来和圆弧错位 ——
  弧带半径区间是 [ar-aw, ar]、中线 rm = ar - aw/2，但旧版把三角形
  底边的**中点**放在了外沿 ar 上，底边整体向外偏了 aw/2 = 11，
  于是箭头一半探出弧外、看起来和圆环对不上。
  现在底边中点落在中线 rm 上、半宽 0.60*ah 向两侧对称张开，
  箭头与弧同轴（见 make-icon 运行时打印的自检数据）。

用法：
    python make-icon.py                 # 直接写进 ../res/mipmap-*/
    python make-icon.py --out ./preview # 只出预览
    python make-icon.py --preview       # 额外存一张 512 大图到临时目录

依赖：pillow, numpy
"""
import argparse
import math
import os
import sys
import tempfile

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit('缺少 pillow：pip install pillow')
try:
    import numpy as np
except ImportError:
    sys.exit('缺少 numpy：pip install numpy')

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

K = 512                     # 设计画布边长
SS = 4                      # 超采样倍数
FONT = 'C:/Windows/Fonts/arialbd.ttf'

CAP = np.array([255, 194, 77], np.float32)
CAP_HI = np.array([255, 224, 150], np.float32)
WALL = (176, 121, 16)
ES = (168, 112, 8)          # 阴刻左上暗
EF = (205, 146, 28)         # 槽底
EL = (255, 236, 186)        # 阴刻右下亮
RING = (255, 255, 255)

# B=背景边框(=键帽内缩量，同时用作白圈线宽)  wall=侧壁  R=键帽圆角
# ts=A字号  a_cy=A墨迹中心 y  ar/aw=白圈外半径/线宽  ah=箭头大小  ring_out=白圈探出量
DEFAULTS = dict(B=22, wall=26, R=64, ts=262, a_cx=None, a_cy=244,
                ar=86, aw=None, ah=28, ring_out=8)


def _s(v):
    return int(round(v * SS))


def _ss(box):
    return [_s(v) for v in box]


def _font(ts):
    if not os.path.exists(FONT):
        sys.exit('缺少字体：%s' % FONT)
    return ImageFont.truetype(FONT, _s(ts))


def face_gradient(base, hi, h, w, peak=96, ramp_at=0.60):
    y = np.arange(h, dtype=np.float32)[:, None]
    ramp = np.clip(1.0 - y / (h * ramp_at), 0.0, 1.0) ** 1.25
    arr = base[None, None, :] + (hi - base)[None, None, :] * ramp[:, :, None] * (peak / 255.0)
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8).repeat(w, axis=1), 'RGB')


# ---------------------------------------------------------------- 几何
def geom(B, ar, ring_out, aw):
    kx0 = ky0 = B
    kx1 = ky1 = K - B
    rcx = (K - B) + ring_out - ar
    rcy = rcx
    return dict(kx0=kx0, ky0=ky0, kx1=kx1, ky1=ky1,
                kcx=(kx0 + kx1) / 2.0, kcy=(ky0 + ky1) / 2.0,
                rcx=rcx, rcy=rcy, ar=ar, aw=aw, ring_out=ring_out)


# ---------------------------------------------------------------- 圆环
def draw_ring(d, cx, cy, ar, aw, ah, color=RING, s1=188, e1=336, s2=8, e2=156):
    """两段弧 + 两个箭头 = 「循环」符号

    ★ ImageDraw.arc 的 width 是**向内**扩展的：外沿永远 = ar。
      外径 = 2*ar，内径 = 2*(ar-aw)。

    ★★ 箭头必须「骑在圆弧的中线上」：
      弧带占的半径区间是 [ar-aw, ar]，中线 rm = ar-aw/2。
      三角形底边以 rm 为**中点**向两侧对称张开（半宽 0.60*ah=16.8），
      两侧各探出弧带约 5.8 —— 弧到端点后对称展成箭头再收成尖，
      指向沿切线（顺时针方向）。
    """
    d.arc(_ss([cx - ar, cy - ar, cx + ar, cy + ar]), s1, e1, fill=color, width=_s(aw))
    d.arc(_ss([cx - ar, cy - ar, cx + ar, cy + ar]), s2, e2, fill=color, width=_s(aw))
    rm = ar - aw / 2.0            # 弧带中线半径
    for ang in (e1, e2):
        a = math.radians(ang)
        tx, ty = -math.sin(a), math.cos(a)      # 切线（旋转前进方向）
        rux, ruy = math.cos(a), math.sin(a)     # 半径向外方向
        back = aw * 0.12
        mx = cx + rm * rux - tx * back          # 底边中点：在弧带中线上，再沿切线往回搭一点
        my = cy + rm * ruy - ty * back
        hw = ah * 0.60                          # 半宽：相对中线对称张开
        tip = (mx + tx * ah * 1.15, my + ty * ah * 1.15)
        d.polygon([(_s(tip[0]), _s(tip[1])),
                   (_s(mx + rux * hw), _s(my + ruy * hw)),
                   (_s(mx - rux * hw), _s(my - ruy * hw))], fill=color)


# ---------------------------------------------------------------- A 字
def _a_layer(ts, tx, ty):
    im = Image.new('L', (K * SS, K * SS), 0)
    ImageDraw.Draw(im).text((_s(tx), _s(ty)), 'A', font=_font(ts), fill=255, anchor='mm')
    return np.asarray(im) > 24


def a_union_mask(ts, cx, cy):
    """阴刻三层（左上暗、右下亮、中间槽底）各偏 dep，取并集 = 实际可见的 A"""
    dep = max(3, round(ts * 0.021))
    m = np.zeros((K * SS, K * SS), bool)
    for dx, dy in ((-dep, -dep), (dep, dep), (0, 0)):
        m |= _a_layer(ts, cx + dx, cy + dy)
    return m


def fit_a(ts, cx, cy):
    """把 A 的墨迹中心对准 (cx, cy)，返回修正后的锚点"""
    m = a_union_mask(ts, cx, cy)
    ys, xs = np.nonzero(m)
    mx, my = (xs.min() + xs.max()) / 2.0 / SS, (ys.min() + ys.max()) / 2.0 / SS
    return cx + (cx - mx), cy + (cy - my)


def draw_A(d, ts, cx, cy):
    dep = max(3, round(ts * 0.021))
    ax, ay = fit_a(ts, cx, cy)
    d.text((_s(ax - dep), _s(ay - dep)), 'A', font=_font(ts), fill=ES, anchor='mm')
    d.text((_s(ax + dep), _s(ay + dep)), 'A', font=_font(ts), fill=EL, anchor='mm')
    d.text((_s(ax), _s(ay)), 'A', font=_font(ts), fill=EF, anchor='mm')


# ---------------------------------------------------------------- 渲染
def render(**kw):
    p = dict(DEFAULTS)
    p.update(kw)
    B = p['B']
    aw = p['aw'] if p['aw'] is not None else B
    g = geom(B, p['ar'], p['ring_out'], aw)
    if p['a_cx'] is None:
        p['a_cx'] = g['kcx']

    im = Image.new('RGBA', (K * SS, K * SS), (0, 0, 0, 0))

    # 侧壁
    ImageDraw.Draw(im).rounded_rectangle(
        _ss([g['kx0'], g['ky0'] + p['wall'], g['kx1'], g['ky1']]),
        radius=_s(p['R']), fill=WALL + (255,))

    # 顶面
    face_box = [g['kx0'], g['ky0'], g['kx1'], g['ky1'] - p['wall']]
    fw, fh = _s(face_box[2] - face_box[0]), _s(face_box[3] - face_box[1])
    grad = face_gradient(CAP, CAP_HI, fh, fw)
    mask = Image.new('L', (K * SS, K * SS), 0)
    ImageDraw.Draw(mask).rounded_rectangle(_ss(face_box), radius=_s(p['R']), fill=255)
    canvas = Image.new('RGB', (K * SS, K * SS), (0, 0, 0))
    canvas.paste(grad, (_s(face_box[0]), _s(face_box[1])))
    im.paste(canvas, (0, 0), mask)

    d = ImageDraw.Draw(im)
    draw_A(d, p['ts'], p['a_cx'], p['a_cy'])
    draw_ring(d, g['rcx'], g['rcy'], g['ar'], g['aw'], p['ah'])
    return im, g, p


def corner_check(img):
    al = np.asarray(img)[:, :, 3]
    c = (int(al[0, 0]), int(al[0, -1]), int(al[-1, 0]), int(al[-1, -1]))
    ok = 'OK' if max(c) == 0 else '!! 四角不透明，上电视会糊成黑方块'
    print(f'  四角 alpha={c}  {ok}')
    return max(c) == 0


def ring_audit(g, p):
    """箭头自检：打印底边中点半径 vs 弧带中线，两者相等才算对齐"""
    ar, aw, ah = g['ar'], g['aw'], p['ah']
    rm = ar - aw / 2.0
    print('  转换圈: 外径 %d  内径 %d  中线 %.1f' % (2 * ar, 2 * (ar - aw), rm))
    print('          箭头底边中点 %.1f（应与中线相等）  底边半宽 %.1f  '
          '弧带半宽 %.1f' % (rm, ah * 0.60, aw / 2.0))
    side = ah * 0.60 - aw / 2.0
    print('          → 箭头两侧各探出弧带 %.1f（对称 = 对齐）' % side)


# ★ 尺寸用 108dp 规格（跟 tcl-numberpad 一致），不是 Android 标准的 48dp：
#   这台电视 1920x1080 / densityDpi=240，240dpi 在 Android 里正好是 hdpi，
#   桌面图标槽位实测 160px，所以 hdpi 必须给 160px 才能 1:1、不出现最近邻放大的锯齿。
SIZES = [('mdpi', 108), ('hdpi', 160), ('xhdpi', 216),
         ('xxhdpi', 324), ('xxxhdpi', 432)]


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--out', default=os.path.join(ROOT, 'res'),
                    help='输出目录，默认写进项目的 res/mipmap-*')
    ap.add_argument('--preview', action='store_true',
                    help='额外存一张 512 大图到临时目录')
    args = ap.parse_args()

    im, g, p = render()
    print('参数: %s' % p)
    ring_audit(g, p)
    if not corner_check(im):
        sys.exit('图标四角必须透明，否则电视桌面会显示成黑方块')

    if args.preview:
        pth = os.path.join(tempfile.gettempdir(), 'ic_launcher_keymapper_512.png')
        im.resize((K, K), Image.LANCZOS).save(pth)
        print('  预览 -> %s' % pth)

    for bucket, side in SIZES:
        d = os.path.join(args.out, f'mipmap-{bucket}')
        os.makedirs(d, exist_ok=True)
        out = im.resize((side, side), Image.LANCZOS)
        dst = os.path.join(d, 'ic_launcher.png')
        out.save(dst)
        al = np.asarray(out)[:, :, 3]
        corners = (int(al[0, 0]), int(al[0, -1]), int(al[-1, 0]), int(al[-1, -1]))
        flag = 'OK' if max(corners) == 0 else '!! 四角不透明'
        print(f'  {bucket:8s} {side:3d}px  {os.path.getsize(dst):6d}B  '
              f'corners={corners}  {flag}')

    print('\n完成。重新打包：./build.sh')


if __name__ == '__main__':
    main()
