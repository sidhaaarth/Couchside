"""Convert YouTube-style auto-caption VTT (rolling lines + <c> word tags) to a clean SRT.

Each new caption line appears once, in the cue that carries its word-timing tags.
Usage: python vtt_to_srt.py Charade_1963.en.vtt ../tv/src/main/assets/charade-en.srt 223
The optional last argument drops lines before that many seconds (the archive.org copy opens
with a presenter's intro; the film starts at 3:43).
"""
import re
import sys

TS = r"(\d+):(\d\d):(\d\d)\.(\d{3})"


def ms(h, m, s, f):
    return ((int(h) * 60 + int(m)) * 60 + int(s)) * 1000 + int(f)


def srt_time(t):
    return "%02d:%02d:%02d,%03d" % (t // 3_600_000, t // 60_000 % 60, t // 1000 % 60, t % 1000)


def convert(vtt, skip_ms=0):
    lines = []  # (start_ms, text)
    for block in vtt.replace("\r", "").split("\n\n"):
        rows = block.strip().split("\n")
        timing = next((r for r in rows if "-->" in r), None)
        new = [r for r in rows if "<c>" in r or re.search(r"<\d", r)]
        if not timing or not new:
            continue
        text = re.sub(r"<[^>]+>", "", new[-1]).strip()
        start = ms(*re.match(TS, timing).groups())
        if text and start >= skip_ms and (not lines or lines[-1][1] != text):
            lines.append((start, text))
    out = []
    for i, (start, text) in enumerate(lines):
        end = min(lines[i + 1][0], start + 4000) if i + 1 < len(lines) else start + 3000
        out.append(f"{i + 1}\n{srt_time(start)} --> {srt_time(end)}\n{text}\n")
    return "\n".join(out)


if __name__ == "__main__":
    src, dst = sys.argv[1:3]
    skip = int(sys.argv[3]) * 1000 if len(sys.argv) > 3 else 0
    with open(src, encoding="utf-8") as f, open(dst, "w", encoding="utf-8") as g:
        g.write(convert(f.read(), skip))
