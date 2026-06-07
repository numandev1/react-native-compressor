# CLAUDE.md

Guidance for AI agents (Claude, Copilot, etc.) and new contributors working in
this repository. Keep this file up to date when project structure, conventions,
or workflows change.

## What this project is

`react-native-compressor` is a React Native library that compresses **video**,
**image**, and **audio** files (and provides background upload/download helpers)
with results comparable to WhatsApp-style compression. It ships native code for
both **iOS** (Swift/Obj-C) and **Android** (Kotlin), exposed to JavaScript through
a TurboModule-capable spec, and supports both the old and new React Native
architectures plus an Expo config plugin.

- Package name: `react-native-compressor`
- Package manager: **Yarn 4** (`packageManager: yarn@4.14.1`); Yarn workspaces with `examples/*`.
- Upstream: https://github.com/numandev1/react-native-compressor — this is a fork
  (`XChikuX/react-native-compressor`) that triages and fixes upstream issues.

## Repository layout

```
src/                       JavaScript/TypeScript public API (the npm entry point)
  index.tsx                Re-exports the public surface
  Main.tsx                 Aggregates the default export object
  Spec/NativeCompressor.ts TurboModule spec (single source of truth for native methods)
  Video/ Image/ Audio/     Per-domain JS wrappers (compress(), options, events)
  utils/                   Uploader/Downloader/helpers (uuid, path normalization)
  expo-plugin/             Expo config plugin
ios/                       Native iOS implementation (Swift + Obj-C bridge)
  Video/VideoMain.swift            Video compression entry (auto/manual helpers)
  Video/NextLevelSessionExporter.swift  AVAssetReader/Writer export engine
  Image/ Audio/ Utils/             Image, audio, upload/download, thumbnails
  Compressor.mm / Compressor.h     Obj-C bridge to the Swift module
android/                   Native Android implementation (Kotlin)
  src/main/java/com/reactnativecompressor/
    Video/                 Video compression (MediaCodec transcode pipeline)
      VideoCompressor/compressor/Compressor.kt  Core encode/decode loop
      VideoCompressor/utils/CompressorUtils.kt  Format/codec helpers
    Image/ Audio/ Utils/   Image, audio, upload/download, helpers
  src/oldarch / src/newarch  Architecture-specific TurboModule specs
__tests__/                 Jest unit tests for the JS wrapper (native is mocked)
harness/                   react-native-harness on-device smoke test definitions
examples/bare              Bare React Native example app (build + harness target)
examples/expo              Expo example app
TRIAGE.md                  Running triage of upstream issues and fixes in this fork
```

## Public API surface

The default export aggregates these modules/functions (see
`__tests__/compressor.test.ts` for the authoritative list):
`Audio`, `Image`, `Video`, `UploadType`, `UploaderHttpMethod`, `backgroundUpload`,
`cancelUpload`, `clearCache`, `createVideoThumbnail`, `download`,
`generateFilePath`, `getDetails`, `getFileSize`, `getImageMetaData`,
`getRealPath`, `getVideoMetaData`, `uuidv4`.

Video compression supports `compressionMethod: 'auto' | 'manual'`, `maxSize`,
`bitrate`, `progressDivider`, `minimumFileSizeForCompress`, and `stripAudio`.

## Build, test, and validate

Run JS-level checks from the repo root:

| Command | Purpose |
| --- | --- |
| `yarn install` | Install dependencies (Yarn 4) |
| `yarn jest` / `yarn test` | Run the JS wrapper unit tests |
| `yarn typecheck` | `tsc --noEmit` |
| `yarn lint` | ESLint over `**/*.{js,ts,tsx}` |
| `yarn test:pr` | `test --runInBand && typecheck && lint` (run before opening a PR) |
| `yarn build:android` | Assemble the bare example (`arm64-v8a`) |
| `yarn build:ios` | Build the bare example for the iOS simulator |
| `yarn test:harness:android` / `yarn test:harness:ios` | On-device/simulator smoke tests |

**Important:** The Jest tests mock the native module, so they validate only the
JS contract. Real media decoding/encoding **cannot** be verified by unit tests —
it must be smoke-tested in the example app on a simulator or device. When you
change native Swift/Kotlin code, state clearly that it was not runtime-verified
in CI and, where possible, validate via the example app or harness.

## Native video pipeline notes (high-signal, easy to get wrong)

### iOS (`ios/Video/`)
- `VideoMain.swift` builds the `videoOutputConfiguration` / `compressionDict`
  and drives `NextLevelSessionExporter`.
- `NextLevelSessionExporter.setupVideoOutput` only creates the video writer input
  when `writer.canApply(outputSettings:forMediaType:) == true`; otherwise it logs
  `"Unsupported output configuration"` and writes **audio only**, yet still ends
  as `.completed`. That means a bad `videoOutputConfiguration` can silently yield
  an **audio-only** MP4 reported as success.
- Do **not** add undocumented H.264 (`avc1`) compression properties such as
  `AVVideoExpectedSourceFrameRateKey` or `AVVideoAverageNonDroppableFrameRateKey`:
  `canApply(...)` accepts them but the iOS encoder drops the video track
  (regression in #392, fixed for #400). After export, verify the output asset
  actually contains a video track before resolving success.

### Android (`android/.../Video/VideoCompressor/`)
- `Compressor.kt` runs an `MediaExtractor` → decoder (Surface) → encoder
  (`video/avc`) → `MP4Builder` transcode loop.
- The decoder is created from the **input** track's MIME. Some containers (notably
  iPhone `.MOV`) report `video/dolby-vision`, which fails with `NAME_NOT_FOUND`
  on devices lacking a Dolby Vision decoder. `CompressorUtils.ensureDecodableVideoFormat`
  remaps such inputs to their backward-compatible HEVC/AVC base layer (profiles 8/4
  → HEVC, profile 9 → AVC) or throws a clear error for non-compatible profiles
  (5/7). See #398.
- The encoder is intentionally `c2.android.avc.encoder` (when QTI codecs exist) or
  `MediaCodec.createEncoderByType("video/avc")`; QTI AVC encoders can produce MP4s
  that do not play on Mac/iPhone, so avoid switching this without testing.

## Conventions

- Keep changes surgical and aligned with surrounding style. Native helper objects
  (e.g. `CompressorUtils`) use member imports and unqualified calls in
  `Compressor.kt` — match that.
- When fixing an upstream issue, record it in `TRIAGE.md` (triage row + the
  "Minor fixes made in this branch" list) referencing the issue number.
- Prefer graceful, descriptive failures over cryptic native crashes for
  unsupported media (clear error messages that tell the user what happened).

## Merging back upstream

This fork accumulates many incremental commits. When contributing back to
`numandev1/react-native-compressor`, use a **squash merge** so the history lands
as a single, well-described commit rather than the full incremental series.
