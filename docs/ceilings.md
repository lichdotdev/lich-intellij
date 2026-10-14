# Known Ceilings

Deliberate limits and shortcuts, and the traps they set. A bullet earns its place by naming something that breaks
work when nobody knows it and that the call site never shows. The mechanism and the history stay in the code and
`CHANGELOG.md`; this file is the trap alone.

## lich

- **The status bar polls** (`LichStatusWidget`): lich has no supported way to push a change — its `/events`
  endpoint is internal — so the widget runs `lich sessions --json` every 5 seconds per open project window
  (about 30ms each). A finished turn or a permission prompt is announced up to 5 seconds late, and a state that
  comes and goes between two looks is never announced.
- **The widget finds `lich` once per project window**: a lich installed, or a path set in Settings › Tools › lich,
  after the window opened is seen only once the project is reopened. The send actions look it up every time.
- **`lich focus` is told apart from an old lich by its error text** (`LichClient.focus`): a lich without the
  command answers `unknown command "focus"`, and only that exact answer falls back to raising the window. A lich
  that rewords it turns the fallback into an error balloon.
- **The IDE may carry a lich session's environment**: an IDE started from a lich terminal inherits
  `LICH_SESSION_ID` and `LICH_RESTART_WAIT`. Left in, the first files what the IDE opens as that session's work
  and the second makes a second `lich` launch fight the running window for its port. `LichProcess` drops both;
  any new variable lich adds with the same effect has to be added there.
- **A new session gets 60 seconds to show a prompt** before the paste gives up (`SendFlow`); a provider slower to
  start than that loses the text, although the session stays open.

## Finding the session

- **A worktree is recognised from git's files alone** (`GitWorktree`): a `.git` *file* pointing into the main
  repository's `.git/worktrees/`. A checkout made any other way (a second clone, a copy) is not a worktree to the
  plugin, and a worktree on a detached HEAD cannot have a session opened in it, since lich opens worktrees by
  branch.
- **A project at the home directory never contains a folder** (`SessionMatcher`): a shell session at `~` would
  otherwise match every folder on the machine. Only the home directory itself is excluded: a project at `/` or at
  a broad folder like `~/code` still contains everything under it, and its lone session is still offered.
- **What the IDE has open is `project.basePath`**: a project with several content roots in unrelated directories
  is matched by its base directory only.

## IntelliJ

- **A review comment names its lines as they were when it was written** (`ReviewBatch`): nothing follows them as
  the file is edited, so a comment written before an edit above it points at the wrong lines when the batch is
  sent. For the same reason the batch is kept in memory and lost when the project closes.
- **Two features stand on IntelliJ internals**: "Send problem to lich" reads highlights through
  `DaemonCodeAnalyzerEx`, and "Send Changes to lich" builds its diff with `IdeaTextPatchBuilder` and
  `UnifiedDiffWriter` from `intellij.platform.vcs.impl` (on the classpath through `bundledModule`). Both are
  `impl` APIs that can change in any IDE release; `verifyPlugin` is what notices.
- **A Problems view row carries its severity as a number** (`SendProblemNodeAction`): the name sent is recovered
  by comparing it to the platform's ERROR and WARNING weights, so a custom severity between them is sent as the
  nearest one below it.
- **A failed test's trace is sent whole**: a framework-deep trace (Spring, Gradle workers) can run to hundreds of
  lines at the prompt.
- **`buildSearchableOptions` starts an IDE**: while a `runIde` sandbox is open it fails with "Only one instance of
  IDEA can be run at a time", which fails `buildPlugin`.
- **The sandbox's dynamic reload keeps old menus**: `prepareSandbox` against a running `runIde` reloads the
  plugin's classes, but actions, groups and icons from a changed `plugin.xml` show only after a restart.
- **The plugin compiles against the oldest supported IDE's runtime, not the newest**: the Kotlin stdlib and Gson
  come from the IDE at run time, so `apiVersion` (2.0) and the `compileOnly` Gson (2.11.0) match what 2024.3, the
  `sinceBuild`, bundles. Raising `sinceBuild` is the only way to use newer stdlib or Gson APIs, and lowering it
  needs both moved down — which Kotlin 2.4 no longer allows below API 2.0. Kotlin already warns that API 2.0 is
  deprecated: a Kotlin release that drops it forces `sinceBuild` up with it.
- **`verifyPlugin` checks two IDEs, and its newest is pinned by hand** (`build.gradle.kts`): the oldest supported
  and one recent release, not the seven `recommended()` picks. Each IDE is several gigabytes downloaded and as much
  again extracted under `~/.gradle/caches/<gradle>/transforms`, and the verifier adds gigabytes of plugins in
  `~/.pluginVerifier`; seven filled a 475 GB disk and would fill a CI runner's. A release newer than the pinned
  one goes unverified until someone moves the pin. Neither cache is cleaned by Gradle on its own.
- **Crush and Kiro have no provider mark**: the icons come from lich's own vendored `@lobehub/icons` set, which
  has none for them; their sessions wear lich's icon.
