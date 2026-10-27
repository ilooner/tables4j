#!/usr/bin/env python3
"""Record the real example CLIs in a PTY and render their ANSI output as GIFs.

Requires macOS/Linux, Bash, Java 21, and recording-requirements.txt.
The raw recordings use https://docs.asciinema.org/manual/asciicast/v2/.
Terminal state is interpreted by pyte, including color and cursor movement.
"""

import argparse
import codecs
import errno
import fcntl
import json
import math
import os
from pathlib import Path
import pty
import select
import signal
import struct
import subprocess
import termios
import time

from PIL import Image, ImageDraw, ImageFont
import pyte


ROOT = Path(__file__).resolve().parents[1]
DEMOS = (
    ("inventory", "demoInventory", "Inventory report", 80, 18),
    ("streaming", "demoStreaming", "Streaming deployment events", 80, 20),
    ("wrapping", "demoWrapping", "Two layouts / 44 columns", 64, 32),
)
PROMPT = "tables4j $ "
BACKGROUND = "#0d1117"
FOREGROUND = "#e6edf3"
COLORS = dict(zip(
    ("black", "red", "green", "brown", "blue", "magenta", "cyan", "white",
     "brightblack", "brightred", "brightgreen", "brightbrown", "brightblue",
     "brightmagenta", "brightcyan", "brightwhite"),
    ("#161b22", "#ff7b72", "#7ee787", "#e3b341", "#79c0ff", "#d2a8ff",
     "#76e3ea", "#e6edf3", "#8b949e", "#ffa198", "#aff5b4", "#f8e3a1",
     "#a5d6ff", "#e2c5ff", "#b3f0ff", "#ffffff"),
))


def record(task, columns, rows):
    """Capture an interactive shell, including its real command echo and exit status."""
    command = f"./gradlew -q --console=plain {task}"
    pid, master = pty.fork()
    if pid == 0:
        os.chdir(ROOT)
        env = dict(os.environ, TERM="xterm-256color", COLUMNS=str(columns), LINES=str(rows),
                   PS1="\\[\\e[36m\\]" + PROMPT + "\\[\\e[0m\\]",
                   HISTFILE="/dev/null", INPUTRC="/dev/null",
                   BASH_SILENCE_DEPRECATION_WARNING="1")
        env.pop("PROMPT_COMMAND", None)
        os.execvpe("bash", ["bash", "--noprofile", "--norc", "-i"], env)

    fcntl.ioctl(master, termios.TIOCSWINSZ, struct.pack("HHHH", rows, columns, 0, 0))
    start = time.monotonic()
    decoder = codecs.getincrementaldecoder("utf-8")()
    events = []

    def read_for(seconds):
        output = ""
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            ready, _, _ = select.select([master], [], [], max(0, deadline - time.monotonic()))
            if not ready:
                break
            try:
                data = os.read(master, 65536)
            except OSError as error:
                if error.errno == errno.EIO:
                    break
                raise
            if not data:
                break
            text = decoder.decode(data)
            if text:
                events.append([round(time.monotonic() - start, 4), "o", text])
                output += text
        return output

    def await_prompt():
        output = ""
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            output += read_for(0.1)
            if PROMPT in output:
                return
        raise TimeoutError(f"No shell prompt after running {task}")

    exited = False
    try:
        await_prompt()
        read_for(0.4)
        for character in command:
            os.write(master, character.encode())
            read_for(0.025)
        read_for(0.25)
        os.write(master, b"\n")
        await_prompt()
        read_for(2.5)
        events.append([round(time.monotonic() - start, 4), "o", ""])
        recording_end = len(events)
        print(f"Captured {task}; closing the shell.", flush=True)
        os.write(master, b"exit\n")
        # Drain the shell's final echo before closing the terminal, including on macOS.
        read_for(0.2)
        del events[recording_end:]
        os.close(master)
        master = None
        _, status = os.waitpid(pid, 0)
        exited = True
        if os.waitstatus_to_exitcode(status) != 0:
            raise RuntimeError(f"{task} failed; terminal output: {events!r}")
    finally:
        if master is not None:
            os.close(master)
        if not exited:
            os.kill(pid, signal.SIGKILL)
            os.waitpid(pid, 0)

    return {
        "version": 2, "width": columns, "height": rows,
        "duration": events[-1][0], "command": command,
        "env": {"TERM": "xterm-256color"},
    }, events


def color(value, default):
    if value == "default":
        return default
    return COLORS.get(value, "#" + value)


def draw_screen(screen, title, font):
    cell_width = math.ceil(font.getlength("M"))
    cell_height = sum(font.getmetrics())
    inset, top = 24, 64
    width = screen.columns * cell_width + inset * 2
    height = screen.lines * cell_height + top + inset
    frame = Image.new("RGB", (width, height), BACKGROUND)
    draw = ImageDraw.Draw(frame)
    draw.rectangle((0, 0, width, 42), fill="#161b22")
    for x, fill in zip((24, 44, 64), ("#ff5f57", "#febc2e", "#28c840")):
        draw.ellipse((x - 5, 16, x + 5, 26), fill=fill)
    draw.text((width // 2, 21), title, font=font, fill="#8b949e", anchor="mm")
    for row in range(screen.lines):
        for column in range(screen.columns):
            char = screen.buffer[row][column]
            fg = color(char.fg, FOREGROUND)
            bg = color(char.bg, BACKGROUND)
            if char.reverse:
                fg, bg = bg, fg
            x, y = inset + column * cell_width, top + row * cell_height
            if bg != BACKGROUND:
                draw.rectangle((x, y, x + cell_width - 1, y + cell_height - 1), fill=bg)
            if char.data.strip():
                draw.text((x, y), char.data, font=font, fill=fg)
    if not screen.cursor.hidden:
        x = inset + min(screen.cursor.x, screen.columns - 1) * cell_width
        y = top + screen.cursor.y * cell_height
        draw.rectangle((x, y + cell_height - 3, x + cell_width - 1, y + cell_height - 1),
                       fill="#76e3ea")
    return frame


def render(header, events, title, font, destination):
    screen = pyte.Screen(header["width"], header["height"])
    stream = pyte.Stream(screen)
    frames, durations = [], []
    index = 0
    for tick in range(math.ceil(header["duration"] * 10) + 1):
        changed = False
        while index < len(events) and events[index][0] <= tick / 10:
            stream.feed(events[index][2])
            changed = changed or bool(events[index][2])
            index += 1
        if changed or not frames:
            frames.append(draw_screen(screen, title, font))
            durations.append(100)
        else:
            durations[-1] += 100

    # A shared palette keeps text and ANSI colors stable throughout the animation.
    palette = frames[-1].quantize(colors=128)
    encoded = [frame.quantize(palette=palette, dither=Image.Dither.NONE) for frame in frames]
    # Start the loop on the completed table so non-animated previews are useful too.
    encoded = encoded[-1:] + encoded[:-1]
    durations = durations[-1:] + durations[:-1]
    encoded[0].save(destination, save_all=True, append_images=encoded[1:],
                    duration=durations, loop=0, optimize=True)
    with Image.open(destination) as gif:
        if gif.n_frames < 2:
            raise RuntimeError(f"Recording is not animated: {destination}")
    print(f"{destination.relative_to(ROOT)}: {len(frames)} frames, "
          f"{sum(durations) / 1000:.1f}s, {destination.stat().st_size // 1024} KiB")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--font", type=Path, help="Path to a monospace TTF/OTF/TTC font")
    args = parser.parse_args()
    font_path = args.font or next((path for path in (
        Path("/System/Library/Fonts/Menlo.ttc"),
        Path("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"),
        Path("/usr/share/fonts/truetype/liberation2/LiberationMono-Regular.ttf"),
    ) if path.exists()), None)
    if font_path is None:
        parser.error("No monospace font found; supply --font /path/to/font.ttf")
    font = ImageFont.truetype(str(font_path), 18)

    # Keep compilation out of the recordings, while recording the documented Gradle commands.
    subprocess.run(["./gradlew", "-q", "--console=plain", "examplesClasses"], cwd=ROOT, check=True)
    media = ROOT / "docs" / "media"
    recordings = ROOT / "docs" / "recordings"
    media.mkdir(parents=True, exist_ok=True)
    recordings.mkdir(parents=True, exist_ok=True)
    for name, task, title, columns, rows in DEMOS:
        print(f"Recording {task}...", flush=True)
        header, events = record(task, columns, rows)
        header["title"] = title
        with (recordings / f"{name}.cast").open("w", encoding="utf-8") as output:
            for entry in [header, *events]:
                output.write(json.dumps(entry, ensure_ascii=False) + "\n")
        render(header, events, title, font, media / f"{name}.gif")


if __name__ == "__main__":
    main()
