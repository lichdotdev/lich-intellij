# lich for JetBrains IDEs

Send what you are looking at in IntelliJ IDEA, PyCharm, WebStorm, GoLand, CLion, Rider, RubyMine, PhpStorm
and the rest to a [lich](https://github.com/omartelo/lich) session.

- **Send Selection to lich** (`Ctrl+Alt+Shift+L`, or the editor's right-click menu) pastes the selected code at the
  prompt of the session running on your project, headed by its file and lines:

  ````
  src/main/kotlin/App.kt:12-18
  ```kt
  fun main() { ... }
  ```
  ````

- **Send File to lich** (editor, editor tab or Project view right-click menu) pastes the path of the current file,
  or of every file you selected in the Project view.

- **Send problem to lich** (`Alt+Enter` on an error or warning, or right-click it in the Problems view) pastes what the IDE says about it, over the lines
  it is about.

- **Send Changes to lich** (right-click changed files in the Commit view) pastes their diff, for a review or a
  commit message.

- **Send Failure to lich** (right-click a failed test, class or suite in the test runner) pastes each failed test
  with its file and line, and its message and stack trace.

- **The status bar** shows the lich sessions on your project (`lich: 1 waiting · 2 working`), and a balloon tells you
  when one finishes its turn or stops on a permission prompt. Click it to list them; picking one brings lich forward.
  lich has no supported way to push changes, so the plugin asks `lich sessions` every 5 seconds.

**Nothing is sent for you.** The text lands at the prompt, after whatever you had already typed there, and waits:
you write the question around it and press Enter. That is deliberate — a selection is context, and what to ask
about it is yours.

## Which session

The plugin asks lich for its live sessions and picks the one running in the folder your IDE has open:

1. A session running in that very directory (an IDE opened on a worktree reaches the session in the worktree).
2. Otherwise the project's sessions — the folder is the root of the project they belong to.
3. Otherwise sessions of a project that contains the folder, for a module opened on its own.

A git worktree with no session of its own offers its project's sessions, which work in another checkout. One match of
the first two kinds is used without asking; a session of another checkout, or of a project that only contains the
folder, is always asked about. A project at your home directory never counts as containing a folder. Several are
listed, and the one you pick is remembered for that project. If none is near, every session is offered. **Settings › Tools › lich** turns on asking every time.

The list always ends with **Open a new session here**: lich opens a session on the folder — opening the folder as a
project first, if it does not have it open — and the text lands at its prompt once the agent is up. What it runs
(Claude Code by default) is set in **Settings › Tools › lich**.

A session waiting on a permission prompt is shown greyed and refused by lich: text pasted there would go to the
dialog.

## Requirements

- lich 0.65.0 or newer, the first release with `lich insert`. An older lich is recognised and the plugin says so.
- lich must be running; the plugin talks to it through the `lich` command, the part of lich that is covered by its
  [stability promise](https://github.com/omartelo/lich/blob/main/docs/stability.md).
- An IDE of build 242 (2024.2) or newer.

The `lich` command is searched on `PATH`, then where lich installs it (Homebrew, `/usr/local/bin`, `~/.local/bin`,
Scoop's shims), then through your login shell — an IDE started from a launcher does not inherit the PATH of your
terminal. Set the path by hand in **Settings › Tools › lich** if none of those finds it.

## Build

```sh
./gradlew :core:test      # the logic, on a plain JDK 21, no IDE needed
./gradlew test            # everything, downloads the IDE
./gradlew buildPlugin     # build/distributions/lich-intellij-<version>.zip
./gradlew runIde          # a sandbox IDE with the plugin loaded
```

Install the zip from **Settings › Plugins › ⚙ › Install Plugin from Disk…**.

`core/` holds everything that decides what is sent where — snippet formatting, session matching, finding and
running `lich` — with no IntelliJ dependency. The plugin proper (`src/`) only gathers a selection from the editor
and shows the chooser and the result.

## How it talks to lich

Two commands, both documented in lich's [`docs/cli.md`](https://github.com/omartelo/lich/blob/main/docs/cli.md):

```sh
lich sessions --json                                # who is running, and where (path, projectPath)
lich insert --session <id> --json --timeout 10 -    # the text arrives on stdin
```

The text goes on stdin and never in an argument, so no quoting, no argument-length limit and no Windows command
line to get wrong.

## Known limits

- On Windows the terminal drops the markers that tell an agent a paste is one paste, so a multi-line selection can
  reach some agents line by line. lich documents it as a ceiling (`lich insert` in `docs/ceilings.md`).
- Over 64 KiB lich refuses the text; send the file instead.
- A project reached through a path lich does not know as the project (another mount, WSL from a Windows IDE) has no
  session "beside" it; the plugin offers every session.

## License

AGPL-3.0, like lich.
