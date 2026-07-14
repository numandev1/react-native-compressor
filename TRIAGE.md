# Upstream issue triage

Audited against `numandev1/react-native-compressor` open issues on 2026-07-14 and compared with the current tree in this fork.

Legend:
- `real` = credible library issue
- `fixed here` = addressed in this branch
- `duplicate` = same root cause as another issue
- `stale` = issue targets code that is no longer present in the current tree
- `needs info` = not enough detail to prove a library defect
- `feature` = request, not a bug
- `not a bug` = current expectation does not match exposed API

| Issue | Triage | Notes |
| --- | --- | --- |
| #390 | not a bug | Reports `start` / `end` time behavior for video compression, but the current public video API does not expose trim parameters. |
| #387 | needs info | Gradle binary store corruption looks environment-specific; report does not isolate a library code change. |
| #384 | needs info | Performance question, not a reproducible defect report. |
| #383 | real, fixed here | Android transcode pipeline can blow up on pathological audio metadata (`uint32 overflow`). This branch skips unsupported copied audio metadata instead of crashing. |
| #382 | needs info | “Works in dev, fails in prod” has no logs or repro app. |
| #381 | feature | Nitro Modules migration request. |
| #380 | real, fixed here | Android manual compression could produce invalid tiny files when `maxSize` generated odd dimensions or invalid output. This branch normalizes dimensions and rejects invalid output files. |
| #377 | real, fixed here | Android auto compression was overly aggressive. This branch switches to adaptive bitrate + frame-rate caps for high-res sources. |
| #376 | duplicate | Same symptom family as #380 / #369: invalid tiny Android outputs on specific devices. |
| #375 | real, fixed here | Quality complaint is consistent with the old hard bitrate cap. Adaptive bitrate selection in this branch directly targets it. |
| #371 | duplicate | Likely another Android video transcode failure in the same cluster as #343 / #380 / #376. |
| #370 | stale | Current tree no longer imports `AssetsLibrary`; this is already gone. |
| #369 | real, fixed here | “Playable only in VLC” is credible output-container compatibility fallout. This branch fast-starts Android MP4 outputs and skips unsupported audio sample metadata that can produce incompatible containers. |
| #367 | stale | Same `AssetsLibrary` removal request as #370 / #362; already addressed in current sources. |
| #366 | real, fixed here | `libandroidlame.so` 16 KB page-size warning is addressed by the current `TAndroidLame` fork dependency already present in this tree. |
| #365 | real, fixed here | Android parsed bitrate metadata as `Int` and could overflow on bogus sentinel values. This branch now clamps metadata safely. |
| #364 | real, fixed here | Manual compression crash report is credible; manual-path sizing, metadata, output validation, and audio-container hardening in this branch address the likely causes. |
| #363 | real, fixed here | iOS assumed a video track existed and could crash on audio-only MP4 files. This branch now guards that path. |
| #362 | stale | Another `AssetsLibrary` build failure that no longer matches the current tree. |
| #358 | feature | Live photo optimization request. |
| #356 | real, fixed here | Android AGP 8+ `BuildConfig` generation issue. This branch enables `buildConfig` in the library Gradle file. |
| #354 | stale | Old Android build failure references the previous `AndroidLame-kotlin` dependency coordinates, which are no longer in this tree. |
| #353 | feature | Audio speed-up request. |
| #352 | real, fixed here | Thumbnail generation now retries with tolerant frame extraction and reports a deterministic error when no frame can be decoded. |
| #348 | stale | Report targets `1.11.0` Gradle sync behavior with minimal details; no matching current-tree defect was found. |
| #347 | real, fixed here | Image quality is now clamped consistently before JPEG encoding on Android and iOS. |
| #345 | stale | Current tree has only one TurboModule spec (`src/Spec/NativeCompressor.ts`); the duplicate-spec issue no longer matches HEAD. |
| #343 | real, fixed here | Repeated 4k Android compression failures line up with old manual sizing/bitrate behavior. This branch reworks the compression profile for high-res inputs. |
| #318 | stale | Old dependency-resolution issue references outdated dependency coordinates and repository/network failures. |
| #308 | duplicate | Broad “sometimes compresses, sometimes not” report fits the Android video-quality/output cluster but lacks a repro sample. |
| #302 | needs info | Slow compression is a product concern, but the report is only a timing complaint with no reproducible defect. |
| #263 | real, fixed here | iOS background upload now accumulates response data and returns the response body string like Android. |

## Main clusters

### Android video compression cluster

These are all likely manifestations of the same area and should be tracked together:

- #343
- #375
- #376
- #377
- #380
- #369
- #371
- #308

This branch addresses the most obvious causes in that cluster:

- odd output dimensions
- brittle bitrate heuristics
- no frame-rate cap for high-resolution sources
- success being reported for invalid output files
- overflow-prone metadata parsing

### Already obsolete issues

These should be closed upstream unless a current repro still exists on the latest code:

- #345
- #354
- #362
- #367
- #370
- #318

## Recent PRs on the 1.19.3 branch

Since the last audit, the branch has integrated or backported work reflected in the following areas:

| PR | What changed |
| --- | --- |
| #408 | Fixed Expo plugin/release build references so managed Expo apps can build against the current module structure. |
| #407 | Corrected Nitro module imports/paths to keep `main` working after the 2.0.0 Nitro migration. |
| #411 | iOS image compression: replaced `NSException.raise()` with Swift `throw` so errors properly reject the promise instead of aborting the whole app. |
| #403 / #400 | iOS export session now fails explicitly instead of silently returning an audio-only MP4 when the video track is missing or cannot be written. |
| #402 | Dolby Vision compatibility improvements on iOS. |
| #399 | Dolby Vision crash fix, up to ~50% faster iOS transcode, and corrected fps/bitrate/GPS metadata handling. |
| #397 | Cross-platform compressor hardening and review-feedback fixes. |
| #396 | Additional test coverage for compression paths. |
| #395 | Refined video compression profiles after upstream issue triage. |
| #393 | New `stripAudio` option on video compression to drop the audio track from the output. |
| #392 | Upstream issue triage and high-resolution video compression hardening. |
| #388 | iOS background upload now resolves with the server response body, matching Android behavior. |
| #391 | Tooling, CI, and example-app modernization (Yarn 4, React Native 0.85, updated harness). |
| #386 | Android 14+ orientation correction for images. |
| #385 | Out-of-memory crash mitigation during large-file processing. |
| #374 | Base64 image compression handling on iOS. |
| #372 | 16 KB page-size support on Android. |
| #368 | Removed deprecated iOS `AssetsLibrary` API and fixed an iOS runtime crash. |
| #351 | Replaced `toLowerCase()` with Kotlin `lowercase()`. |
| #342 | Moved `uuidv4` to a dedicated helper to avoid circular dependencies with React Compiler. |
| #341 | Refactored `createVideoThumbnail` internals. |
| #339 | Preserved original audio channel count to fix iOS playback for certain Android-compressed videos. |
| #334 | Fixed iOS crash when processing file size of an unparsed URL. |
| #328 | Documentation repetition fix. |
| #325 / #324 | Build fixes for missing `kAudioFormatAPAC` symbol on some Xcode versions. |
| #321 | iPhone 16 / Pro Max compression fix. |
| #320 | Yarn upgrade and workspace tooling refresh. |
| #311 | `isoparser` 1.9.x migration and old-code cleanup. |
| #305 | Same `isoparser` modernization. |
| #295 | iOS manual image compression source fix. |
| #290 | mp4parser compatibility fix. |
| #284 / #281 | Android upload and speed fixes. |

## Minor fixes made in this branch

- Android: enable `buildConfig` generation for AGP 8+ builds
- Android: clamp metadata parsing and reject invalid transcode output
- Android: adaptive video compression profile for high-resolution inputs
- Android: fast-start compressed MP4 outputs and skip unsupported copied audio sample metadata
- Android/iOS: clamp image and thumbnail JPEG quality values
- Android/iOS: harden thumbnail frame extraction for difficult source videos
- iOS: guard missing video tracks and use the same adaptive sizing/bitrate strategy
- iOS: return background-upload response bodies consistently
