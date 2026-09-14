# XENOWAVE Implementation Inventory

## Protected baseline

- Branch: `feat/xenowave-brand`
- Base commit: `cf602708dbaeab2b7c87fde2217bae05f8c263da`
- The hash supplied as `cf602708dbaeab2b7c87fde221074988d4d9e03a3` did not match HEAD. Per approval, implementation used the audited HEAD hash recorded above.
- `Codex_GLM.bat` remains untracked and is excluded only through `.git/info/exclude`.
- Project `.gitignore` was not modified.

## Baseline validation

The commands below were run before implementation. Each used the local JDK 17 at `C:\Users\rafael.de.souza\Documents\Codex\tools\jdk\jdk-17.0.20.1+1` because `JAVA_HOME` was not configured globally.

| Command | Result | Duration | Pre-existing issue |
|---|---|---:|---|
| `.:app:assembleCoreDebug` | Failed during Gradle configuration | 69 s | Dependencies could not be downloaded from Google/Maven due SSL `certificate_unknown` / PKIX path building failure |
| `.:app:testCoreDebugUnitTest` | Failed during Gradle configuration | 6 s | Same dependency/TLS failure |
| `.:app:lintCoreDebug` | Failed during Gradle configuration | 12 s | Same dependency/TLS failure |

The initial invocation without a configured JDK also failed in 0 s with `JAVA_HOME is not set`. The failures did not reach Android compilation, so they were not caused by uninitialized `ffMetadataEx` or `taglib` submodules. No submodule update was performed and no submodule pointer was changed.

## Implemented

- Canonical brand documentation.
- Official Master Visual Reference verification and registration.
- Centralized XML palette tokens, including secondary surface and primary light text.
- Centralized Compose semantic tokens, metrics, and shapes.
- XENOWAVE Material 3 light/dark schemes.
- XENOWAVE as the default app theme.
- Dynamic wallpaper/artwork coloring disabled by default and retained as an optional user setting.
- Visible app name and debug launcher label.
- Careful visible string migration, preserving technical references and old URLs where required.
 - Fastlane store-description brand names, preserving old OuterTune URLs.
- Removal of the old OuterTune logo from the About and OOBE screens; only the approved text name is shown while assets are pending.
- Approved typography and verbal-identity decisions registered; font embedding remains pending.

## Pending official assets

- Adaptive launcher foreground.
- Launcher background decision based on the master artwork.
- Monochrome/themed launcher asset.
- Splash artwork.
- Play Store icon.
- Fastlane icon and store screenshots.
- Isolated official files for the approved dark-background, light-background, monochrome, and isolated-symbol variations.
- Orbitron and Inter font integration.

No derivative has been generated. The existing OuterTune launcher is only a temporary non-substitute state while the approved derivatives remain pending.

## Final validation

The final commands used the same local JDK 17 and a temporary corporate truststore outside the repository. The truststore resolved the initial Java certificate mismatch, but the network proxy then returned HTTP `503 Service Unavailable` for Google Maven and Maven Central artifacts. All three commands therefore failed during Gradle configuration before Android/Kotlin compilation.

| Command | Result | Duration | Blocking issue |
|---|---|---:|---|
| `.:app:assembleCoreDebug` | Failed during Gradle configuration | 34 s | Proxy HTTP 503 while downloading build-classpath artifacts |
| `.:app:testCoreDebugUnitTest` | Failed during Gradle configuration | 66 s | Proxy HTTP 503 while downloading build-classpath artifacts |
| `.:app:lintCoreDebug` | Failed during Gradle configuration | 35 s | Proxy HTTP 503 while downloading build-classpath artifacts |

Static validation completed successfully:

- 121 Android XML files parsed successfully.
- `git diff --check` passed.
- No Android `<string>` node still contains `OuterTune` in any letter case.
- The Master Visual Reference retains its approved dimensions and SHA-256.
- The application ID and namespace remain `com.dd3boh.outertune`.
- No diff exists in InnerTubeX/streaming, download, or database implementation paths.

The Gradle failures did not identify `ffMetadataEx` or `taglib` as their cause. Because configuration never reached compilation, submodule initialization was neither required by the observed failure nor performed.

## Preserved technical areas

- InnerTubeX engine and resolver.
- `ResolvedStream`, headers, and `RangePolicy`.
- `StreamDataSpec` and Media3 integration.
- Download rules and download manager behavior.
- Database schema and migrations.
- Login, library, navigation, playback, and persistence behavior.
- Application ID, namespace, packages, actions, routes, preference keys, and persisted identifiers.
