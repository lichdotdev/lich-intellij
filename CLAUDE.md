# lich for JetBrains IDEs

An IntelliJ Platform plugin that sends what the developer is looking at — a selection, a file, a failed test, a
problem, a diff — to a [lich](https://github.com/omartelo/lich) session, and shows the project's sessions in the
status bar. lich is an ADE that runs AI coding agents (Claude Code, Codex and the rest) side by side; its source
lives in `~/try/skipo` on this machine, and its CLI contract in that repo's `docs/cli.md` and `docs/stability.md`.
The plugin is built for other developers to use, not only its author: docs, errors and defaults answer to a
stranger.

**This file is instruction, not documentation.** Rules, gates and the workflow live here. What the plugin *is*
lives in the code, `README.md`, `docs/` and `CHANGELOG.md` — never restate any of it here.

## Where things are written down

- **Deliberate limits and the traps they set**: `docs/ceilings.md`. Read it before changing how a session is
  found, how lich is run, or anything that touches IntelliJ internals. A change that sets a new trap or closes an
  old one moves that file in the same change.
- **What a user sees and how to build**: `README.md`.
- **User-facing feature history**: `CHANGELOG.md`.
- **Toolchain**: `mise.toml` pins Java 21 and Gradle; run Gradle through the wrapper under mise
  (`mise exec -- ./gradlew …`). The system Java may be newer than the wrapper's Gradle accepts.

## Rules of the codebase

- `core/` decides everything — what text a gesture becomes, which session it goes to, how `lich` is found and
  run — and has **no IntelliJ dependency**: it builds and tests on a plain JDK. `src/` only gathers what the IDE
  shows and draws the result. Logic that can live in `core/` lives there, with its test.
- lich is reached **only through the `lich` CLI**, the part covered by lich's stability promise. Its HTTP
  endpoints (`/rpc`, `/ws`, `/events`), `runtime.json` and database are internal to lich and off limits, however
  convenient. A capability the CLI lacks is added to lich first.
- Every `lich` child process starts from `LichProcess.builder`, never a bare `ProcessBuilder`.
- The IDE ships the Kotlin runtime and Gson; both stay `compileOnly` in `core/`. Bundling either breaks class
  loading against the platform's copy.
- The plugin depends on `com.intellij.modules.platform` and `com.intellij.modules.vcs` only, so it runs in every
  JetBrains IDE. A dependency on a language plugin (Java, Kotlin, …) is a design change, not a convenience.

## Local gate (before every commit)

- `mise exec -- ./gradlew test buildPlugin` green. `buildPlugin` runs `buildSearchableOptions`, which starts an
  IDE and fails while a `runIde` sandbox is open: close it, never skip the task.
- `mise exec -- ./gradlew verifyPlugin` before a release, and after touching any IntelliJ API — it is what tells
  an internal or removed API apart from a stable one.
- A change to `plugin.xml` (actions, groups, icons, extensions) is checked in a restarted `runIde`: the dynamic
  reload swaps the classes but keeps menus built before it.
- Shipped anything a user can see? Its `CHANGELOG.md` `[Unreleased]` entry lands in the same commit, and
  `README.md` moves with it when the feature is listed there.

## Hard invariants

Non-negotiable. A violation means the work is not done.

1. **Nothing is sent for the user.** The plugin pastes at a prompt (`lich insert`) and never submits (`lich send`,
   `lich control … prompt`). What to ask about the context is the user's.
2. **Never paste into a session the user did not mean.** A session is used without asking only when it runs in
   the open folder or the folder is its project's root. Another checkout, a project that merely contains the
   folder, or a remembered choice for either is always asked about. A project at the home directory contains
   nothing.
3. **Tests answer to the contract, never the other way round.** Never weaken, skip or rewrite a test to get a
   green run. A test changes for exactly two reasons — the contract changed, or it asserted something the contract
   never promised — and the change says which. A bug fix starts with a test that fails on the bug.
4. **Failures reach the user worded for them.** A `LichException` carries a sentence a person can act on; a
   failure is never swallowed into a log line. The one deliberate fallback — `lich focus` on a lich that predates
   it — is matched on lich's exact answer, not on any error.

## Changelog

`CHANGELOG.md` follows [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/).

- Every user-visible change adds a bullet under `## [Unreleased]`, in the same commit, in one of the canonical
  sections in this order: Added, Changed, Deprecated, Removed, Fixed, Security. Create a section only when it has
  a bullet.
- A bullet is written for the user, not the developer: what they can now do or what stopped going wrong, opening
  with a bold sentence. Internals (refactors, build, CI, tests) do not get a bullet.

## Versioning

[Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html). The version lives in `gradle.properties`
(`pluginVersion`) and nowhere else; the release tag is `v` + that version.

- **Major**: a minimum IDE build or minimum lich release goes up, or a gesture or setting the user relied on is
  removed.
- **Minor**: a new gesture, setting or capability.
- **Patch**: fixes only.
- While on `0.x`, a breaking change bumps the minor.

## Releases

Publishing goes to the JetBrains Marketplace. The very first upload is manual (plugins.jetbrains.com › Upload
plugin, then JetBrains' review); every release after it is a tag. The `publish` skill
(`.claude/skills/publish/`) walks this list when the user asks for a release:

- [ ] Local gate green, `verifyPlugin` included.
- [ ] Bump `pluginVersion` in `gradle.properties`.
- [ ] Cut `CHANGELOG.md` with `mise exec -- ./gradlew patchChangelog`: it moves `[Unreleased]`'s entries under
      `## [X.Y.Z] - <today>` for the `pluginVersion` above, leaves `[Unreleased]` empty at the top and rewrites the
      compare links. Read the diff: the heading must be bracketed, ISO-dated and without `v`, because the release
      job greps `^## \[X.Y.Z\]` and fails without it.
- [ ] Commit, then push the `vX.Y.Z` tag. `.github/workflows/release.yml` checks the tag against
      `pluginVersion`, signs and publishes to the Marketplace — whose "What's new" is that changelog section,
      rendered into `plugin.xml` by the `org.jetbrains.changelog` Gradle plugin — and creates the GitHub release
      from the same section. It reads `PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, `PRIVATE_KEY` and `PRIVATE_KEY_PASSWORD` from the
      repository secrets.
