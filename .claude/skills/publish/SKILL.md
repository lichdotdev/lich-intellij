---
name: publish
description: Release a new version of the lich IntelliJ plugin to the JetBrains Marketplace and GitHub — pick the version, cut CHANGELOG.md, run the gates, tag and watch the release workflow. Use only when the user asks to publish, release or ship a version of the plugin (e.g. "publica", "faz a release", "/publish 0.2.0"); never on your own initiative.
---

# Publish a release

The release itself is done by `.github/workflows/release.yml` when a `vX.Y.Z` tag is pushed. This skill gets the
repository into the state that tag expects, and stops for the user's yes before anything leaves the machine. The
rules it applies — versioning, changelog format, the release checklist — are the `CLAUDE.md` sections of those
names; read them first, and when they disagree with this file, `CLAUDE.md` wins.

Run Gradle as `mise exec -- ./gradlew …` throughout.

## 1. Preconditions — stop on any failure

- On `main`, working tree clean (`git status --short` empty), and in sync with `origin/main` after `git fetch`.
- The last CI run on `main` is green: `gh run list --branch main --workflow CI --limit 1`.
- `## [Unreleased]` in `CHANGELOG.md` has at least one bullet. Nothing unreleased is nothing to publish: say so and
  stop.
- **First release?** `git tag --list 'v*'` is empty and the user has not said the plugin is already on the
  Marketplace: `publishPlugin` cannot create a Marketplace listing. Walk the user through the manual first upload
  instead (plugins.jetbrains.com › Upload plugin with the zip from step 4, license AGPL-3.0, then JetBrains'
  review), and resume from step 5 once it is approved.
- The repository secrets exist: `gh secret list` shows `PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, `PRIVATE_KEY` and
  `PRIVATE_KEY_PASSWORD`. A missing one fails the workflow after the tag is public — name it and stop.

## 2. Choose the version

Read `[Unreleased]` and the current `pluginVersion` in `gradle.properties`, and propose the next version by the
`CLAUDE.md` › Versioning rules, quoting the bullet that decides it (a Removed bullet or a raised minimum IDE/lich →
major, or minor while on `0.x`; an Added bullet → minor; Fixed alone → patch). A version the user already named is
used as given, after checking it is greater than the current one.

**Ask the user to confirm the version.** Do not continue on an assumed one.

## 3. Bump and cut

1. Set `pluginVersion=X.Y.Z` in `gradle.properties` — the only place the version lives.
2. `mise exec -- ./gradlew patchChangelog`, then read `git diff CHANGELOG.md`:
   - a `## [X.Y.Z] - YYYY-MM-DD` heading (today, bracketed, no `v`) holds every former `[Unreleased]` entry;
   - `## [Unreleased]` is still at the top, empty;
   - the links at the bottom read `[Unreleased]: …/compare/vX.Y.Z...HEAD` and `[X.Y.Z]: …`.
   Fix anything off by hand; the release job greps `^## \[X.Y.Z\]` and fails without it.

## 4. Gates

All must pass; a failure is reported with its output and the release stops there. Never skip a task to get green.

- `ps -eo args | grep "[i]dea-sandbox"` prints nothing — a running `runIde` makes `buildSearchableOptions` fail. Ask the user to close
  it rather than killing it: it may hold unsaved work.
- `df -h ~` shows at least 20 GB free: the verification downloads and extracts IDEs (`docs/ceilings.md` ›
  IntelliJ). Less than that, stop and tell the user what is using it rather than filling the disk.
- `mise exec -- ./gradlew printProductsReleases` lists the current IDE releases. When one is newer than the
  newest pinned in `build.gradle.kts` › `pluginVerification`, ask the user whether to move the pin before
  verifying.
- `mise exec -- ./gradlew test buildPlugin verifyPlugin`. `verifyPlugin` downloads the recommended IDEs and takes
  minutes; its report is what tells whether an internal IntelliJ API the plugin uses (`docs/ceilings.md` ›
  IntelliJ) has moved. A compatibility problem blocks the release; a deprecation warning is reported to the user,
  who decides.
- `mise exec -- ./gradlew getChangelog --no-header` prints the section the Marketplace will show — show it to the
  user.

## 5. Commit, confirm, tag

1. Commit `gradle.properties` and `CHANGELOG.md` as `Release vX.Y.Z`.
2. **Ask the user before pushing**, showing the version, the changelog section and the gate results. Pushing the
   tag publishes to the Marketplace, which cannot be taken back — a published version can only be hidden.
3. On yes: `git tag vX.Y.Z`, `git push origin main`, `git push origin vX.Y.Z`.

## 6. Watch the release

- `gh run watch` on the Release run the tag started (`gh run list --workflow Release --limit 1`).
- On failure, read the log (`gh run view --log-failed`), report the cause and stop. Do not delete or move the tag
  without the user's say-so: whether a version reached the Marketplace decides whether it may be reused.
- On success, report the GitHub release URL (`gh release view vX.Y.Z --json url`) and that the Marketplace shows
  the version after its automatic review, usually within hours.
