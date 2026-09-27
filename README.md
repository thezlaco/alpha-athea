# Athea

A terminal that reads like a messenger.

Instead of a character grid, Athea turns a shell session into a conversation:
your commands appear as bubbles on the right, their output as blocks on the
left. It is a real shell underneath — a PTY, a shell, your real `$PATH` — with a
chat-shaped reading layer on top.

The problem it solves is a phone one. A soft keyboard has no Ctrl, Esc, Tab or
arrow keys, and a raw terminal is miserable on a small screen. Athea adds a key
row for exactly those keys, turns scrollback into collapsible messages, and
replaces line editing with a composer.

## How a shell becomes a chat

The trick is shell integration. The app ships an rc file that makes the shell
emit **OSC 133** markers into the output stream, and `StreamParser` recognises
them:

| Marker | Meaning | Athea does |
| --- | --- | --- |
| `OSC 133;A` | prompt about to be drawn | ignored |
| `OSC 133;B` | prompt written | ignored |
| `OSC 133;C` | command started | opens a running output block |
| `OSC 133;D;<code>` | command finished | closes it and records the exit code |

Everything between `C` and `D` becomes the body of that command's answer. A
submission is a `CommandBlock`; the output that follows is an `OutputBlock`. No
markers means no block boundary, which is why behaviour degrades gracefully on
shells that do not load the rc file.

Each session journals its events to disk, and a restart replays the journal
through the current parser — so parser fixes apply retroactively to old
sessions.

## Layers

```
engine/          NativeShellEngine, SshShellEngine, PtyBridge (JNI → pty.c)
      │  EngineEvent.Output / EngineEvent.Exited
core/terminal/   TerminalEngine — the only interface the UI knows
      ▼
parse/           StreamParser — pure Kotlin. UTF-8 across chunk boundaries,
                 ANSI SGR → spans, CR semantics, OSC 133 recognition
      │  StreamEvent.Text / OutputBegin / CommandEnd
transcript/      TranscriptBuilder — pure Kotlin. Events → blocks, collapse
                 rules, raw projection. Deterministic: same events, same
                 transcript
      ▼
ui/              Compose. Reads an immutable UiState, sends intents.
```

`parse/` and `transcript/` have no Android dependencies and carry the unit
tests. That is deliberate: they are where correctness lives, and they are
testable without a device.

## Known limits

- **Full-screen TUI programs are not supported.** `vim`, `top`, `less`, `fzf`
  redraw the screen with cursor positioning and the alternate screen buffer.
  The parser strips every escape sequence except SGR, so these render as
  garbage. The `RAW` display mode is *not* a terminal emulator — it is the same
  parsed stream without bubbles, so it does not help. A real grid renderer for
  intercepted blocks is the missing piece.
- **Interactive programs without a completion marker** (REPLs such as `python`
  or `psql`, `tail -f`) never emit `OSC 133;D`. Their output block stays open,
  which is reported honestly as "running".
- **Submitting while a command runs** does not cancel it. The bytes are
  buffered by the tty and run afterwards; the pending bubble shows immediately
  and the running block keeps growing above it, so the transcript order still
  matches the real stream order.
- **`\r` overwrites are approximated.** Carriage return is treated as "discard
  the line" rather than "overwrite from column 0", so progress bars can lose
  their prefix.

## Build

Requires JDK 21, Android SDK 35, NDK 26.1.10909125. There is no Gradle wrapper
checked in; CI installs Gradle 8.9.

```bash
gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

`ci/emulator_run.sh` drives the debug APK on an emulator: installs, launches,
types a command, then reads back the session journal and watches for crashes.
