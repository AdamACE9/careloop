"""Assembles the full CareLoop demo video with ffmpeg.

intro (30s) -> laptop recording on the browser stage -> phone recording on the
phone stage -> dashboard b-roll -> outro (15s), crossfaded, with a very soft
original music bed under the demo section.
"""
import os
import subprocess
import numpy as np
from PIL import Image, ImageDraw
from scipy.io import wavfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
WORK = os.path.join(HERE, "work")
os.makedirs(WORK, exist_ok=True)

INTRO = os.path.join(REPO, "videos", "careloop-intro", "renders", "careloop-intro-30s-v2.mp4")
OUTRO = os.path.join(REPO, "videos", "careloop-outro", "renders", "careloop-outro-15s-v2.mp4")
LAPTOP = os.path.join(REPO, "Careloop laptop demo.mp4")
PHONE = os.path.join(REPO, "demo recording-phone side.mp4")
STAGE_L = os.path.join(HERE, "stage-laptop", "renders", "stage-laptop.mp4")
STAGE_P = os.path.join(HERE, "stage-phone", "renders", "stage-phone.mp4")
BROLL = os.path.join(HERE, "broll", "renders", "broll.mp4")
OUT = os.path.join(HERE, "renders", "careloop-full-demo.mp4")
os.makedirs(os.path.dirname(OUT), exist_ok=True)

SPEED = 1.1
L_SRC, P_SRC = 95.2, 136.4
L_DUR, P_DUR = round(L_SRC / SPEED, 3), round(P_SRC / SPEED, 3)
I_DUR, B_DUR, O_DUR, T = 30.0, 9.0, 15.0, 0.8
ENC = ["-c:v", "libx264", "-preset", "medium", "-crf", "16", "-pix_fmt", "yuv420p", "-r", "30",
       "-c:a", "aac", "-b:a", "256k", "-ar", "48000", "-ac", "2"]


def run(args):
    print(">", " ".join(a if len(a) < 80 else a[:77] + "..." for a in args[:12]))
    subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y"] + args, check=True)


def mask(path, w, h, radius, top=True):
    im = Image.new("L", (w, h), 0)
    ImageDraw.Draw(im).rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, fill=255)
    if not top:
        ImageDraw.Draw(im).rectangle((0, 0, w, radius), fill=255)
    im.save(path)


# ---- 1. Masks --------------------------------------------------------------
mask(os.path.join(WORK, "mask-laptop.png"), 1600, 858, 22, top=False)
mask(os.path.join(WORK, "mask-phone.png"), 414, 920, 48)

# ---- 2. Very soft music bed ------------------------------------------------
SR = 48000
bed_len = L_DUR + P_DUR + B_DUR - 2 * T + 2.0
t = np.arange(int(SR * bed_len)) / SR
chords = [(59, 62, 66), (55, 59, 62), (50, 54, 57), (57, 61, 64)]  # Bm G D A
bar = 60 / 72 * 4
sig = np.zeros((len(t), 2))
rng = np.random.default_rng(7)
for i in range(int(bed_len / bar) + 1):
    s0 = int(i * bar * SR)
    n = int(bar * 1.35 * SR)
    seg = np.arange(n) / SR
    env = np.minimum(seg / 1.2, 1) * np.exp(-seg / 5.0)
    chord = chords[i % 4]
    for k, midi in enumerate(chord + (chord[0] - 12,)):
        f = 440 * 2 ** ((midi - 69) / 12)
        for det, pan in ((-0.12, 0.3), (0.12, 0.7)):
            ff = f * 2 ** (det / 12 / 8)
            wave = np.sin(2 * np.pi * ff * seg) + 0.18 * np.sin(4 * np.pi * ff * seg)
            amp = (0.9 if k == 3 else 0.5) * env
            end = min(len(t), s0 + n)
            m = end - s0
            if m <= 0:
                continue
            sig[s0:end, 0] += wave[:m] * amp[:m] * (1 - pan)
            sig[s0:end, 1] += wave[:m] * amp[:m] * pan
    # soft plucked arpeggio
    for j in range(8):
        p0 = s0 + int(j * bar / 8 * SR)
        pn = int(0.9 * SR)
        ps = np.arange(pn) / SR
        f = 440 * 2 ** ((chord[j % 3] + 12 - 69) / 12)
        pl = np.sin(2 * np.pi * f * ps) * np.exp(-ps * 4.5) * 0.12
        end = min(len(t), p0 + pn)
        m = end - p0
        if m > 0:
            sig[p0:end, 0] += pl[:m] * (0.4 + 0.2 * (j % 2))
            sig[p0:end, 1] += pl[:m] * (0.6 - 0.2 * (j % 2))
fade = np.minimum(np.minimum(t / 3.0, (bed_len - t) / 3.0), 1.0)
sig *= fade[:, None]
sig /= np.max(np.abs(sig)) + 1e-9
raw = os.path.join(WORK, "bed-raw.wav")
wavfile.write(raw, SR, (sig * 0.8 * 32767).astype(np.int16))
bed = os.path.join(WORK, "bed.wav")
run(["-i", raw, "-af", "lowpass=f=5200,aecho=0.8:0.6:120|240:0.25|0.15,loudnorm=I=-20:TP=-2", "-ar", "48000", bed])

# ---- 3. Normalise each segment to 1920x1080 / 30 fps / 48 kHz stereo ------
voice = "highpass=f=80,loudnorm=I=-16:TP=-1.5:LRA=11"

seg_i = os.path.join(WORK, "s1-intro.mp4")
run(["-i", INTRO, "-vf", "fps=30,format=yuv420p", "-af", "aresample=48000"] + ENC + [seg_i])

seg_l = os.path.join(WORK, "s2-laptop.mp4")
run(["-stream_loop", "-1", "-i", STAGE_L, "-i", LAPTOP, "-loop", "1", "-i", os.path.join(WORK, "mask-laptop.png"),
     "-filter_complex",
     f"[1:v]trim=0:{L_SRC},setpts=(PTS-STARTPTS)/{SPEED},fps=30,scale=1600:858:flags=lanczos,format=rgba[v];"
     f"[2:v]format=gray,fps=30[m];[v][m]alphamerge[vm];"
     f"[0:v]fps=30,trim=0:{L_DUR},setpts=PTS-STARTPTS[bg];[bg][vm]overlay=160:162:shortest=1,format=yuv420p[ov];"
     f"[1:a]atrim=0:{L_SRC},asetpts=PTS-STARTPTS,atempo={SPEED},{voice},aresample=48000[a]",
     "-map", "[ov]", "-map", "[a]", "-t", str(L_DUR)] + ENC + [seg_l])

seg_p = os.path.join(WORK, "s3-phone.mp4")
run(["-stream_loop", "-1", "-i", STAGE_P, "-i", PHONE, "-loop", "1", "-i", os.path.join(WORK, "mask-phone.png"),
     "-filter_complex",
     f"[1:v]trim=0:{P_SRC},setpts=(PTS-STARTPTS)/{SPEED},fps=30,scale=414:920:flags=lanczos,format=rgba[v];"
     f"[2:v]format=gray,fps=30[m];[v][m]alphamerge[vm];"
     f"[0:v]fps=30,trim=0:{P_DUR},setpts=PTS-STARTPTS[bg];[bg][vm]overlay=1196:80:shortest=1,format=yuv420p[ov];"
     f"[1:a]atrim=0:{P_SRC},asetpts=PTS-STARTPTS,atempo={SPEED},{voice},aresample=48000[a]",
     "-map", "[ov]", "-map", "[a]", "-t", str(P_DUR)] + ENC + [seg_p])

seg_b = os.path.join(WORK, "s4-broll.mp4")
run(["-i", BROLL, "-f", "lavfi", "-t", str(B_DUR), "-i", "anullsrc=r=48000:cl=stereo",
     "-filter_complex", "[0:v]fps=30,format=yuv420p[v];[0:a][1:a]amix=inputs=2:normalize=0,atrim=0:9[a]",
     "-map", "[v]", "-map", "[a]", "-t", str(B_DUR)] + ENC + [seg_b])

seg_o = os.path.join(WORK, "s5-outro.mp4")
run(["-i", OUTRO, "-vf", "fps=30,format=yuv420p", "-af", "aresample=48000"] + ENC + [seg_o])

# ---- 4. Join with crossfades, lay the music bed under the demo ------------
x1 = I_DUR - T
x2 = x1 + L_DUR - T
x3 = x2 + P_DUR - T
x4 = x3 + B_DUR - T
total = x4 + O_DUR
music_start = x1
music_end = x4 + T
b_start = x3
run(["-i", seg_i, "-i", seg_l, "-i", seg_p, "-i", seg_b, "-i", seg_o, "-i", bed,
     "-filter_complex",
     f"[0:v][1:v]xfade=transition=fade:duration={T}:offset={x1}[v1];"
     f"[v1][2:v]xfade=transition=smoothleft:duration={T}:offset={x2}[v2];"
     f"[v2][3:v]xfade=transition=fade:duration={T}:offset={x3}[v3];"
     f"[v3][4:v]xfade=transition=fade:duration={T}:offset={x4},format=yuv420p[v];"
     f"[0:a][1:a]acrossfade=d={T}[a1];[a1][2:a]acrossfade=d={T}[a2];"
     f"[a2][3:a]acrossfade=d={T}[a3];[a3][4:a]acrossfade=d={T}[main];"
     f"[main]asplit=2[mix][key];"
     # Very soft under speech, a little fuller during the b-roll where nobody is talking.
     f"[5:a]atrim=0:{music_end - music_start:.3f},asetpts=PTS-STARTPTS,"
     f"volume='if(gte(t,{b_start - music_start:.3f}),0.55,0.22)':eval=frame,"
     f"afade=t=in:d=3,afade=t=out:st={music_end - music_start - 2.5:.3f}:d=2.5,"
     f"adelay={int(music_start * 1000)}|{int(music_start * 1000)},apad[bed];"
     f"[bed][key]sidechaincompress=threshold=0.03:ratio=5:attack=30:release=500[duck];"
     f"[mix][duck]amix=inputs=2:normalize=0:duration=first,alimiter=limit=0.95[a]",
     "-map", "[v]", "-map", "[a]", "-t", f"{total:.3f}", "-movflags", "+faststart"] + ENC + [OUT])
print("done", OUT, f"{total:.1f}s")
