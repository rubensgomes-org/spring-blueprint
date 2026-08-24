# Dependency Locking

## Goal

Resolve the build scan finding: *"Dependency versions are not predictable if the
lock file (gradle.lockfile or gradle/verification-metadata.xml) is missing."*

`settings-gradle.lockfile` is already committed and is already enforced, but it
covers only the version-catalog resolution. The application's own dependency
graph remains unlocked. This plan closes that gap.

## Current state

Verified against the working tree on 2026-08-22 (Gradle 9.7.0):

- `settings-gradle.lockfile` locks exactly one configuration,
  `incomingCatalogForLibs0` — the resolution of
  `com.rubensgomes:gradle-catalog:0.2.1` declared in `settings.gradle.kts`.
- That lockfile **is** enforced with no `dependencyLocking { }` block present.
  Gradle activates locking for version-catalog configurations automatically.
  Confirmed by temporarily poisoning the pinned version and observing
  `Did not resolve 'com.rubensgomes:gradle-catalog:9.9.9' which is part of the
  dependency lock state`.
- `app/gradle.lockfile` does **not** exist.
- No `dependencyLocking` block exists in `settings.gradle.kts`,
  `app/build.gradle.kts`, or either `gradle.properties`.
- The Spring Boot BOM imported at `app/build.gradle.kts` pins the *direct*
  dependency versions, so the exposure is the **transitive** closure, not the
  declared coordinates.
- There is no CI configuration in the repository (`.github/` absent), so no
  pipeline currently needs updating.

Resolvable configurations on `:app` that carry real dependency graphs:

| Configuration | Affects |
| --- | --- |
| `compileClasspath` | shipped code |
| `runtimeClasspath` | shipped artifact |
| `testCompileClasspath` | tests |
| `testRuntimeClasspath` | tests |
| `annotationProcessor` | Lombok codegen |
| `testAnnotationProcessor` | Lombok codegen in tests |
| `developmentOnly` | `bootRun` / devtools |
| `jacocoAgent`, `jacocoAnt` | coverage tooling only |

## Design decisions

**1. Scope the locking; do not use `lockAllConfigurations()`.**

The one-line `dependencyLocking { lockAllConfigurations() }` also locks the
jacoco, spotless, sonarqube and release-plugin configurations. Those do not
affect the shipped artifact, but they do make the lockfile churn on every
tooling bump and turn routine plugin upgrades into merge conflicts. Lock the
seven configurations that affect compiled output and tests; leave
`jacocoAgent` / `jacocoAnt` out.

**2. Use `LockMode.STRICT`.**

`DEFAULT` silently treats a missing lockfile as "nothing to verify", which is
exactly the failure the scan flagged. `STRICT` fails the build when a
configuration has locking activated but no lock state present, so a deleted or
unmerged lockfile becomes a build error rather than a silent fallback.

**3. Buildscript classpath locking is a separate, lower-value item.**

Plugin versions already come from the pinned catalog, so the marginal gain is
the plugins' own transitive closure. Gradle's documented
`buildscript { configurations.classpath { ... } }` approach does not reliably
capture plugins applied through the `plugins { }` DSL, so this needs an
empirical check before committing to it. Sequenced last and allowed to fail.

**4. `gradle/verification-metadata.xml` is out of scope.**

Checksum/signature verification solves a different problem (artifact integrity)
than locking (version predictability). The scan message accepts either. Revisit
separately if supply-chain verification is wanted.

## Tasks

### Phase 1 — Lock the app dependency graph

- [x] Add a `dependencyLocking` section to `app/build.gradle.kts`, placed after
      the `dependencies { }` block, following the file's existing banner-comment
      style. Declare the locked configuration names as a named constant rather
      than repeating string literals:
      - [x] Set `dependencyLocking { lockMode.set(LockMode.STRICT) }`
      - [x] Activate locking on the seven configurations listed above via
            `configurations.matching { ... }.configureEach { resolutionStrategy.activateDependencyLocking() }`
      - [x] Document *why* jacoco configurations are excluded, in the same
            explanatory-comment style used elsewhere in the file
- [x] Export `GITHUB_USER` and `GITHUB_TOKEN` — lock generation must hit
      GitHub Packages for the catalog and cannot run `--offline`
      (already present in the shell environment; nothing to change)
- [x] Generate the lockfile: `./gradlew :app:dependencies --write-locks`
- [x] Confirm `app/gradle.lockfile` was created and contains entries for all
      seven configurations
- [x] Confirm the lockfile is not ignored: `git check-ignore -v app/gradle.lockfile`
      must report no match

### Phase 2 — Verify enforcement

- [x] Run `./gradlew :app:build` clean — must pass with the lockfile in place
- [x] ~~Negative test: temporarily alter a version inside `app/gradle.lockfile`,
      confirm the build fails~~ **Premise was wrong; test replaced.** Altering a
      version to another version that *exists* does not fail: the lock is
      authoritative and simply forces it. `dependencyInsight` showed the BOM's
      `spring-core:7.0.9 -> 7.0.1` with reason *"By constraint: Dependency
      version enforced by Dependency Locking"*. Useful result — it confirms the
      lockfile overrides the BOM rather than merely agreeing with it.
- [x] Replacement negative test: remove an entry from `app/gradle.lockfile`,
      confirm the build fails, then restore. Observed:
      `Resolved 'org.springframework:spring-core:7.0.9' which is not part of
      the dependency lock state`
- [x] Negative test: temporarily move `app/gradle.lockfile` aside, confirm
      `STRICT` mode fails the build rather than resolving freely, then restore.
      Observed: `Locking strict mode: Configuration ':app:compileClasspath' is
      locked but does not have lock state.` This is the concrete payoff of
      choosing STRICT over DEFAULT.
- [x] Confirm `./gradlew spotlessCheck` still passes on the edited build script
- [x] Confirm `app/gradle.lockfile` is byte-identical to the generated original
      after all negative tests

### Phase 3 — Regeneration workflow

- [x] Add a "Dependency locking" section to `BUILD.md`, near the existing
      "The `libs` version catalog — dependency versions" section, covering:
      - [x] Which lockfiles exist and what each one covers
      - [x] That both must be regenerated whenever the `gradle-catalog` version
            in `settings.gradle.kts` changes, since the Spring Boot BOM version
            flows from the catalog
      - [x] The regeneration command and its GitHub credential requirement
      - [x] That lockfiles are committed, and that `--write-locks` must never be
            passed in an automated build
      - [x] Added beyond the original plan: the Phase 2 finding that a lock file
            is a *forcing constraint*, not a checksum, so hand-editing a version
            silently changes the build instead of failing it
- [x] Added a `--write-locks` row to the `BUILD.md` "Quick reference" table
- [x] Fixed stale versions in `BUILD.md`: the catalog was documented as `0.2.0`
      and Spring Boot as `4.1.0`; the tree actually resolves `0.2.1` and
      `4.1.1`. Corrected so the document does not contradict the new section.
      (Not in the original plan — see Review.)
- [x] Decide whether to add a `resolveAndLockAll` helper task (Gradle's
      documented pattern) or rely on `:app:dependencies --write-locks`.
      **Decision: no extra task.** That pattern exists to fan out across many
      subprojects; this build has exactly one (`app`), and
      `:app:dependencies --write-locks` was confirmed in Phase 1 to write lock
      state for all seven activated configurations. An extra task would be
      dead weight.

### Phase 4 — Buildscript classpath

Kept, not abandoned. The concern recorded in the plan turned out to be half
right: it holds for `settings.gradle.kts` and not for `app/build.gradle.kts`.

- [x] Prototype `buildscript { configurations.classpath { resolutionStrategy.activateDependencyLocking() } }`
      in `settings.gradle.kts` — **reverted.** It wrote no lock state at all.
      Plugins requested through the settings `plugins { }` block do not pass
      through the settings buildscript classpath, so the block locked an empty
      configuration. `settings-gradle.lockfile` was byte-identical before and
      after. Removed rather than left in place as dead configuration.
- [x] Prototype the same block in `app/build.gradle.kts` — **kept.** The
      `plugins { }` DSL entries *are* captured here: 65 entries in
      `app/buildscript-gradle.lockfile`, covering Spotless, SonarQube,
      task-tree, `net.researchgate.release` and the Spring Boot plugin, plus
      their transitives.
- [x] Verify enforcement: removing `net.researchgate:gradle-release:3.1.0` from
      the lock file fails with `Resolved 'net.researchgate:gradle-release:3.1.0'
      which is not part of the dependency lock state`
- [x] Discovered and fixed: `LockMode.STRICT` on the project extension does
      **not** reach the buildscript classpath. With only the project-level mode
      set, deleting `app/buildscript-gradle.lockfile` left the build passing
      silently — the same blind spot the original scan flagged. Fixed with a
      second `dependencyLocking { lockMode.set(LockMode.STRICT) }` inside the
      `buildscript { }` block; absence now fails with
      `Locking strict mode: Configuration 'classpath' is locked but does not
      have lock state.`
- [x] Replace the prototype marker comment with a documented block in the
      file's established banner style, recording why the settings-level
      equivalent was removed and why STRICT appears twice
- [x] Regenerate all lock state and re-verify: clean `:app:build` passes, all
      four jars produced, `spotlessKotlinGradleCheck` passes, none of the three
      lock files is gitignored

## Risks

- Regenerating locks requires network access and valid GitHub Packages
  credentials. A developer without them can still build from a warm Gradle
  cache, but cannot refresh lock state.
- `net.researchgate.release` is already incompatible with the configuration
  cache (`org.gradle.configuration-cache=false`), so every lock regeneration is
  a cold configure. Slow, not blocking.
- `STRICT` mode makes a missing lockfile a hard failure. That is the intent, but
  it means a fresh clone with a corrupted or partially merged lockfile fails
  loudly instead of degrading.

## Review

All four phases complete; 29 of 29 items checked.

### What shipped

| Change | File |
|---|---|
| Dependency Locking section (7 configurations, `LockMode.STRICT`) | `app/build.gradle.kts` |
| Buildscript Classpath Locking section (STRICT, plugin classpath) | `app/build.gradle.kts` |
| Application dependency lock state, 97 entries | `app/gradle.lockfile` (new) |
| Plugin classpath lock state, 65 entries | `app/buildscript-gradle.lockfile` (new) |
| "Dependency locking" documentation section | `BUILD.md` |
| `--write-locks` row in Quick reference | `BUILD.md` |

The originating scan finding is resolved: dependency versions are now fully
predictable, and an absent lock file is a build failure rather than a silent
fall back.

### Where the plan was wrong

Three assumptions in the approved plan did not survive contact:

1. **"Alter a version in the lock file and the build fails."** It does not. A
   lock file is a forcing constraint, so a version that exists is simply used,
   overriding the Spring Boot BOM (`spring-core:7.0.9 -> 7.0.1`, *"By
   constraint: Dependency version enforced by Dependency Locking"*). The real
   failure paths are a *missing entry* and a *missing file*. Test replaced;
   the behaviour is now documented in `BUILD.md`, since hand-editing a version
   is the most plausible way to quietly break this setup.

2. **"Buildscript locking probably will not capture the `plugins { }` DSL."**
   True for `settings.gradle.kts`, false for `app/build.gradle.kts`. Phase 4
   was sequenced last and expected to be abandoned; half of it shipped.

3. **"One `LockMode.STRICT` covers the build."** It does not span the
   buildscript classpath. Found only because Phase 4 re-ran the missing-file
   test on the new lock file rather than assuming Phase 2's result carried over.

### Out of scope, deliberately

- `gradle/verification-metadata.xml` — artifact integrity is a different problem
  from version predictability, and the scan accepts either. Locking alone should
  clear the finding.
- `resolveAndLockAll` helper task — that pattern fans out across subprojects;
  this build has one.
- CI enforcement — no `.github/` exists in this repository yet. When CI is
  added, it must never pass `--write-locks`.
  **Update (2026-08-23):** CI now exists —
  `.github/workflows/build-verify.yml`. It does not pass `--write-locks`
  anywhere, and the workflow carries a comment saying it never should.

### Follow-ups not actioned

- `BUILD.md` had drifted from the tree independently of this work: it documented
  the catalog as `0.2.0` and Spring Boot as `4.1.0` against an actual `0.2.1`
  and `4.1.1`. Corrected, since the new section would otherwise contradict it.
  Only the sections touched here were audited — **other parts of `BUILD.md` may
  still be stale.**
- Nothing has been committed. `app/build.gradle.kts` and `BUILD.md` are
  modified; `app/gradle.lockfile`, `app/buildscript-gradle.lockfile` and `misc/`
  are untracked. The pre-existing modifications to `.gitignore`,
  `gradle.properties` and `settings.gradle.kts` are unrelated to this work.
