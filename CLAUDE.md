# CLAUDE.md — react-native-compressor

## Project Overview

`react-native-compressor` is a React Native library that compresses Image, Video, and Audio (WhatsApp-style auto compression, plus manual mode), with background upload/download and video thumbnail helpers. The TypeScript/JS layer is a thin wrapper over a single native module named `Compressor` implemented natively on Android (Kotlin) and iOS (Swift). It is published to npm as a library — the `examples/` apps exist only to develop and test against it.

## Tech Stack & Architecture

**JS layer:** TypeScript · single native module `Compressor` resolved in `src/Main.tsx` (TurboModule on New Arch, `NativeModules` fallback on old arch)
**Android native:** Kotlin · hand-rolled MediaCodec/MediaMuxer video transcoder
**iOS native:** Swift · AVFoundation via vendored `NextLevelSessionExporter.swift`
**Tooling:** Yarn 4 (Berry) workspace (`examples/*`) · Node `>= 22.11` · Jest (native mocked) · react-native-builder-bob · Expo config plugin

### JS wrapper → single native module

All native functionality is exposed through one module called `Compressor`. `src/Main.tsx` resolves it once: it uses the TurboModule spec (`src/Spec/NativeCompressor.ts`) when `global.__turboModuleProxy` exists (New Architecture), otherwise falls back to `NativeModules.Compressor` (old arch), and wraps a `Proxy` that throws a linking error if the module is missing.

The public API is assembled in `src/index.tsx` from four domain modules plus utils:

- `src/Image/index.tsx` — `Image.compress` (strips base64 data-URI headers before calling native)
- `src/Video/index.tsx` — `Video.compress`, `cancelCompression`, `activate/deactivateBackgroundTask`
- `src/Audio/index.tsx` — `Audio.compress`
- `src/utils/` — `Uploader.tsx` (`backgroundUpload`, `cancelUpload`), `Downloader.tsx` (`download`), `helpers.ts`, and metadata/path helpers (`getRealPath`, `getVideoMetaData`, `getImageMetaData`, `generateFilePath`, `createVideoThumbnail`, `clearCache`, `getFileSize`)

### Progress is delivered via events, not the Promise

Each `compress`/`upload`/`download` call generates a `uuid` (`uuidv4`) in JS and passes it to native. Native emits events on a `NativeEventEmitter` (`videoCompressProgress`, `downloadProgress`, `uploadProgress`, `backgroundTaskExpired`); the JS wrapper subscribes, **filters events by matching `event.uuid`**, forwards `event.data.progress` to the user callback, and removes the subscription in a `finally` block when the Promise settles. Cancellation (`cancelCompression`, `cancelUpload`) and `AbortController` signals also key off this uuid. When editing progress/cancellation logic, keep the uuid threading consistent across JS and both native sides.

### Native code organization (mirrors the JS domains)

- **Android** `android/src/main/java/com/reactnativecompressor/` → `Image/`, `Video/`, `Audio/`, `Utils/`. The video transcoder is hand-rolled under `Video/VideoCompressor/` (MediaCodec/MediaMuxer pipeline: `Compressor.kt`, `MP4Builder.kt`, surfaces/renderer, `utils/`). `VideoMain.compress` routes to auto vs manual via `VideoCompressorHelper`. `StreamableVideo.kt` moves the `moov` atom to the front of the output by default — preserve this behavior.
- **iOS** `ios/` → `Image/`, `Audio/` (with `FormatConverter/`), `Video/`, `Utils/`. Video uses `NextLevelSessionExporter.swift` (a vendored AVFoundation exporter) driven by `VideoMain.swift`. Event emission goes through `EventEmitterHandler.swift`.

### Expo support

`src/expo-plugin/compressor.ts` is an Expo config plugin; `app.plugin.js` loads its built output from `lib/commonjs/expo-plugin/compressor` (so the plugin only works after `yarn prepack`).

---

## Commands

Uses **Yarn 4** (Berry) and a Yarn workspace (`examples/*`). Node `>= 22.11`.

```sh
yarn                       # install deps for root + example workspaces
yarn test                  # Jest unit tests (native is mocked — fast, no device)
yarn test path/to/file     # run a single test file
yarn test -t "pattern"     # run tests matching a name pattern
yarn typecheck             # tsc --noEmit
yarn lint                  # eslint over **/*.{js,ts,tsx}
yarn lint --fix            # auto-fix lint/prettier
yarn test:pr               # full PR gate: test --runInBand + typecheck + lint
yarn prepack               # build the publishable lib/ via react-native-builder-bob
yarn clean                 # delete android/ios build dirs (run before switching archs)
```

Example apps (workspace shortcuts):

```sh
yarn example:bare start    # Metro for the bare RN example
yarn example:bare android  # run bare example on Android
yarn example:bare ios      # run bare example on iOS
yarn example:expo start    # Expo example
```

### On-device integration tests (react-native-harness)

The Jest unit tests mock the native module, so **real media decoding is only exercised by the harness tests**, which run inside the bare example app on a booted simulator/emulator:

```sh
yarn test:harness:android  # requires an Android emulator (default Pixel_8_API_35)
yarn test:harness:ios      # requires an iOS simulator (default iPhone 17 Pro, iOS 26.4)
```

Device/version overrides live in `examples/bare/rn-harness.config.mjs` (env vars `RN_HARNESS_ANDROID_DEVICE`, `RN_HARNESS_IOS_DEVICE`, `RN_HARNESS_IOS_VERSION`, etc.). The harness spec is `harness/native-compressor.harness.ts` (the copy under `examples/bare/harness/` re-exports it).

---

## Gotchas
- **Spec in FOUR places — keep in sync.** Adding/renaming/changing a native method must touch all of: (1) `src/Spec/NativeCompressor.ts` (codegen TurboModule `Spec`, New Arch source of truth, library `RNCompressorSpec`); (2) `android/src/oldarch/CompressorSpec.kt` (abstract, old arch) + `android/src/newarch/CompressorSpec.kt` (extends codegen `NativeCompressorSpec`) — selected at build by `newArchEnabled` in `android/build.gradle`; (3) `android/src/main/java/com/reactnativecompressor/CompressorModule.kt` (impl, delegates to per-domain `*Main`; registered via `CompressorPackage.kt`/`TurboReactPackage`); (4) iOS `ios/Compressor.mm` (`RCT_EXTERN_METHOD` + TurboModule binding under `RCT_NEW_ARCH_ENABLED`) + `ios/CompressorManager.swift` (`@objc(Compressor) RCTEventEmitter`).
- **Streamable:** `StreamableVideo.kt` moves `moov` atom to front by default — preserve.
- **uuid threading:** keep `uuid` consistent across JS + both native sides for progress/cancellation.
- **Commits follow Conventional Commits** (`fix:`, `feat:`, `refactor:`, `docs:`, `test:`, `chore:`). `commit-msg` hook runs commitlint; `pre-commit` hook (lefthook) runs eslint + `tsc --noEmit` on staged files. Don't bypass.
- **Build output:** `lib/` and example workspaces excluded from lint/tsc/jest — don't edit `lib/` by hand.
- **Releases:** cut with `yarn release` (release-it + conventional-changelog).

## Coding Guidelines

@.claude/rules/karpathy-guidelines.md
