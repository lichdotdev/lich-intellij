# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Review comments, handed to the session as one paste.** Right-click a line or a selection, in the editor or on the
  file's side of a diff, and *Add Comment to lich Review*: the comment waits in the **lich Review** tool window, where
  a double-click rewords it and the remove button drops it, beside a note about the change as a whole. *Send to lich*
  pastes them all as one list, each anchored to its file and lines, the way lich's own review panel sends them. Only
  a paste that landed clears them.

## [0.1.1] - 2026-10-11

### Fixed

- **The status bar tooltip names each session's agent** — Claude Code, Codex, a shell — beside its state and
  folder; in 0.1.0 that column was left empty.

## [0.1.0] - 2026-10-10

### Added

- **Send Selection to lich** pastes the selected code, headed by its file and lines, at the prompt of the lich
  session running on your project. Nothing is sent: the text waits for you to write the question around it.
- **Send File to lich** pastes the path of the current file, or of every file selected in the Project view.
- **Send Failure to lich** pastes a failed test, or every failed test of a class or suite, with its file, line,
  message and stack trace.
- **Send problem to lich** pastes an error or warning with the lines it is about, from `Alt+Enter` in the editor
  or a right-click in the Problems view.
- **Send Changes to lich** pastes the diff of the files selected in the Commit view, for a review or a commit
  message.
- **The session is found for you.** The one running on your project is used without asking; when several are,
  you pick one and it is remembered. A worktree opened in the IDE reaches its project's sessions.
- **Open a new session here** ends the session list: lich opens a session on your project — and a worktree
  opened in the IDE becomes a session in that worktree — and the text lands at its prompt once the agent is up.
  Settings › Tools › lich chooses what it runs.
- **The status bar shows your project's lich sessions** — working, done, waiting on a permission — with a
  balloon when one finishes its turn or stops on a permission prompt. Hover for each session's provider, state
  and folder; click to list them.
- **Show in lich** opens the session's card in lich, on a lich with `lich focus`; on an older one it brings the
  lich window forward.
- **Sessions wear their provider's mark** — Claude Code, Codex, Antigravity, opencode, oh-my-pi, Cursor — in
  every list, and a terminal for a shell session.

[Unreleased]: https://github.com/lichdotdev/lich-intellij/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/lichdotdev/lich-intellij/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/lichdotdev/lich-intellij/commits/v0.1.0
