# Upstream issue triage

Audited against `numandev1/react-native-compressor` open issues on 2026-04-27 and compared with the current tree in this fork.

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
| #400 | real, fixed here | iOS regression from #392: H.264 `videoOutputConfiguration` added `AVVideoExpectedSourceFrameRateKey` / `AVVideoAverageNonDroppableFrameRateKey`, which `canApply(...)` accepts but the iOS encoder silently drops the video track for, yielding an audio-only MP4 reported as success. This branch removes those keys and verifies the exported file actually contains a video track. |
| #398 | real, fixed here | Android could not compress Dolby Vision `.MOV` inputs (iPhone HDR): `MediaCodec.createDecoderByType("video/dolby-vision")` fails with `NAME_NOT_FOUND` on devices without a Dolby Vision decoder. This branch remaps the input to its backward-compatible HEVC/AVC base layer when possible, and otherwise fails with a clear, actionable error. |
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

## Minor fixes made in this branch

- Android: enable `buildConfig` generation for AGP 8+ builds
- Android: clamp metadata parsing and reject invalid transcode output
- Android: adaptive video compression profile for high-resolution inputs
- Android: fast-start compressed MP4 outputs and skip unsupported copied audio sample metadata
- Android: decode the backward-compatible base layer of Dolby Vision `.MOV` inputs, or fail with a clear error (#398)
- Android/iOS: clamp image and thumbnail JPEG quality values
- Android/iOS: harden thumbnail frame extraction for difficult source videos
- iOS: guard missing video tracks and use the same adaptive sizing/bitrate strategy
- iOS: drop unsupported H.264 frame-rate keys and verify the output has a video track to prevent silent audio-only results (#400)
- iOS: return background-upload response bodies consistently
