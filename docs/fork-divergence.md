# Fork divergence

This repository is a fork of [home-assistant/android](https://github.com/home-assistant/android).

**Fork point:** `cc8fdb027` — *Update github/codeql-action action to v4.32.0 (#6349)*, 2026-01-29.

Upstream commits are picked manually rather than merged, so every difference from upstream has to
be deliberate and recorded here. A difference that is not on this list is either a mistake or an
undocumented decision — both are worth investigating rather than preserving.

## How to check divergence for a file

```bash
# one-time: a local clone of upstream
git clone https://github.com/home-assistant/android.git ~/android

cd ~/android && git fetch origin
diff <(git show origin/main:<path>) <path-in-this-repo>
```

## Intentional divergence

| File / area | Difference | Why | Upstream status |
|---|---|---|---|
| `build-logic/.../AndroidApplicationConventionPlugin.kt` | `applicationId = "com.woowtech.home"` | Company fork identity | Permanent — will never be upstreamed |
| `.github/mock-google-services.json` | Still carries upstream package names, so it does not satisfy this fork's build | Not yet updated after the applicationId change | Should be fixed here; not an upstream concern |
| `app/.../util/CrashSaving.kt` | Adds `CrashSavingFailFastHandler`, latch, and diagnostic context | `FailFast` terminates via `exitProcess` without throwing, so its failures never reached the uncaught exception handler and existed only in logcat | **Upstream has the identical gap** — verified byte-identical to `origin/main` before this change. Candidate for an upstream PR |
| `app/.../util/StrictModeDiagnostics.kt` | New file | Build/device context for the above | Same as above |
| `app/.../HomeAssistantApplication.kt` | Registers the FailFast handler before StrictMode | Ordering matters: the first violation happens while the first activity attaches | Same as above |
| `app/src/test/.../IgnoreViolationRulesTest.kt` | New file | Upstream has no tests for the ignore rules; they rely on emulator.wtf instrumentation runs that this fork does not have | Candidate for an upstream PR |
| `app/src/test/.../CrashSavingFailFastHandlerTest.kt` | New file | Covers the divergence above | Same as above |
| `tools/repro-locale-strictmode.sh` | New file | Manual reproduction for an API 31/32-only crash | Fork-local tooling |

The diagnostics work above is deliberately confined to `:app`. It was originally written by
extending `FailFast.failWith()` and `HAStrictMode.enable()` in `:common`, and was reworked so that
those two shared files stay byte-identical to upstream — `:common` has the highest upstream churn
and is the most expensive place to diverge.

## Deliberately NOT diverged

- **`autoStoreLocales=true`** in the app and automotive manifests. Removing it would eliminate real
  main-thread disk I/O on every cold start below API 33, but the manifest is a merge-conflict
  hotspot and the gain is a few milliseconds on a shrinking device population. Upstream chose to
  suppress the resulting StrictMode violation instead; this fork follows that decision.
- **`app/.../util/IgnoreViolationRules.kt`** is kept byte-identical to upstream `origin/main`.
  Do not add fork-local rules to it — if one is ever needed, put it in a separate file so this one
  can keep being replaced wholesale from upstream.

## Known staleness (not divergence — we are simply behind)

| File | What upstream has that we lack |
|---|---|
| `common/.../FailFast.kt` | `failWhen()` returns `Boolean` so it can be used as an inline guard |

## Known pre-existing problems in this fork

These are not caused by any single change and each deserves its own issue:

- `:app:compileMinimalDebugUnitTestKotlin` fails with 64 compile errors — the entire `minimal`
  flavor unit test suite does not compile, so it never runs. Put new tests in a source set that
  actually executes and verify they ran.
- `ConnectionViewModelTest > ...onReceivedError...` fails on a Turbine timeout.
- `ktlintCheck` reports violations in ~10 files unrelated to any recent change.
- `google-services.json` is absent for `:app`, `:automotive` and `:wear`; nothing builds without it.

## Edition dimension (cloud/on-premise in one repo, 2026-08-24)

Implemented per `docs/plans/2026-08-23-prd-cloud-edition-single-repo.md`. The former
`WOOWTECH/woow_ha_app_cloud_vesion` repo is superseded by the `cloud` edition here.

| File / area | Difference | Why | Upstream status |
|---|---|---|---|
| `AndroidEditionFlavorConventionPlugin.kt`, `app/src/cloud|onprem/`, `:cloud-data`, `app/src/testCloud|testOnprem/` | New files/modules | Cloud edition | Fork-only, never upstreamed |
| `OnboardingNavigation.kt` | ~5 lines: edition hooks | Start destination + screens per edition | Re-apply on rebase |
| `NameYourDeviceViewModel.kt` | ~5 lines: `ServerRegisteredListener` set | Cloud session cleanup seam | Re-apply on rebase |
| 18 `BuildConfig.FLAVOR == "full"` sites | 1 line each -> `BuildConfig.IS_FULL` | Two dimensions make FLAVOR the combined name; string compares silently fail | If upstream adds a new comparison, rewrite it to `IS_FULL` during rebase |
| `AndroidFullMinimalFlavorConventionPlugin.kt` | explicit `dimension` + `IS_FULL` fields | Same | Re-apply on rebase |
| `automotive/build.gradle.kts` | +1 srcDir (`../app/src/onprem/kotlin`) | Edition hooks for automotive | Re-apply on rebase |
| `settings.gradle.kts` | `:cloud-data` include | D6 | Re-apply on rebase |
| `app/build.gradle.kts` | edition plugin alias + `cloudImplementation` | — | Re-apply on rebase |
| `.editorconfig` | test glob covers `testOnprem`/`testCloud` | Flavored test source sets keep the line-length exemption | Re-apply on rebase |
| `app/src/screenshotTestFullDebug/` -> `app/src/screenshotTestFullOnpremDebug/` | Directory renamed | Variant rename moved the reference lookup path | **Every upstream golden addition/update lands in the old path and must be re-routed on rebase** (PRD risk #2 fallback; AGP offers no stable remap for screenshot reference dirs) |
| `.github/workflows/{pr,onPush,updateScreenshot}.yml` | Variant task renames; release aggregates replaced with explicit onprem lists | Aggregates would silently sign cloud releases / double R8; unqualified screenshot task names get shadowed by `:automotive` and hollow out the gate | Compare against upstream on every workflow rebase |
| 8 shared onboarding nav tests | +1 line each: `assumeOnboardingStartsAtWelcome()` | Those specs pin the Welcome entry; cloud starts at the chooser and skips them visibly | Re-apply on rebase |

Cloud screenshot tests live in `app/src/screenshotTestCloud/` (flavor-scoped source set,
verified supported). Their reference images are deliberately not generated yet: goldens must be
produced by `updateScreenshot.yml` on ubuntu-latest (platform discipline; macOS renders diverge),
and cloud validation is not part of the PR gate until then. `:app:updateFullCloudDebugScreenshotTest`
is the task to wire in when that day comes.
