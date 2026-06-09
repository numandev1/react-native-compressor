# CLAUDE.md — react-native-compressor

## Project Overview

`react-native-compressor` is a React Native library that compresses Image, Video, and Audio (WhatsApp-style auto compression, plus manual mode), with background upload/download and video thumbnail helpers. The TypeScript/JS layer is a thin wrapper over a single native module named `Compressor` implemented natively on Android (Kotlin) and iOS (Swift). It is published to npm as a library — the `examples/` apps exist only to develop and test against it.

## Tech Stack & Architecture

**JS layer:** TypeScript · single native module `Compressor` exposed as a **Nitro HybridObject** (`react-native-nitro-modules`), resolved in `src/Main.tsx` via `NitroModules.createHybridObject`
**Android native:** Kotlin · hand-rolled MediaCodec/MediaMuxer video transcoder
**iOS native:** Swift (C++/Swift interop) · AVFoundation via vendored `NextLevelSessionExporter.swift`
**Codegen:** Nitrogen (`yarn nitrogen`) generates the native bindings from the `*.nitro.ts` spec into `nitrogen/generated/` (committed to git)
**Tooling:** Yarn 4 (Berry) workspace (`examples/*`) · Node `>= 22.11` · Jest (native mocked) · react-native-builder-bob · Expo config plugin
**Requirements (Nitro):** RN ≥ 0.75 · iOS ≥ 13.4 / Xcode ≥ 16.4 · Android compileSdk ≥ 34 · C++20. Works on both old & new architecture (Nitro handles its own linking)

### JS wrapper → single native module

All native functionality is exposed through one Nitro HybridObject named `Compressor`. `src/Main.tsx` resolves it once via `NitroModules.createHybridObject<Compressor>('Compressor')`, typed by the spec `src/specs/Compressor.nitro.ts`, and re-throws a friendly linking error if Nitro can't find it. Options are passed as Nitro `AnyMap` (untyped maps), parsed natively as before.

The public API is assembled in `src/index.tsx` from four domain modules plus utils:

- `src/Image/index.tsx` — `Image.compress` (strips base64 data-URI headers before calling native)
- `src/Video/index.tsx` — `Video.compress`, `cancelCompression`, `activate/deactivateBackgroundTask`
- `src/Audio/index.tsx` — `Audio.compress`
- `src/utils/` — `Uploader.tsx` (`backgroundUpload`, `cancelUpload`), `Downloader.tsx` (`download`), `helpers.ts`, and metadata/path helpers (`getRealPath`, `getVideoMetaData`, `getImageMetaData`, `generateFilePath`, `createVideoThumbnail`, `clearCache`, `getFileSize`)

### Progress is delivered via callbacks, not events

Nitro has no `NativeEventEmitter`. Progress is delivered through **callback functions passed as method parameters** (`onProgress`, `onDownloadProgress`, `onExpired`) — first-class, reference-counted, auto-scheduled onto the JS thread. Callbacks can't live inside an `AnyMap`, so any callback that used to be nested in the options object is lifted to a top-level method parameter (the JS layer strips functions/`undefined` from option maps via `toNativeOptions` in `src/utils/helpers.ts`, since Nitro's AnyMap throws on those).

A `uuid` (`uuidv4`) is still generated in JS and threaded inside the options map, but now only for (a) cancellation (`cancelCompression`, `cancelUpload`, `AbortController`) and (b) routing native progress emissions to the correct callback. Natively, the per-domain code still calls `EventEmitterHandler.emit*`, but that class is now a **uuid → callback registry** (not a bridge emitter): the binding registers the JS callback under the uuid before invoking the domain method and unregisters when the Promise settles. Keep the uuid threading consistent across JS and both native sides.

### Native code organization (mirrors the JS domains)

The thin Nitro binding lives separately from the heavy domain logic:

- **Android binding** `android/src/main/java/com/margelo/nitro/compressor/HybridCompressor.kt` (extends the generated `HybridCompressorSpec`) converts `AnyMap` → `ReadableMap`, bridges the Nitro `Promise` to the domain layer's `com.facebook.react.bridge.Promise` via `NitroPromiseAdapter.kt`, and runs domain work on a background executor. `NitroCompressorPackage.kt` (a `BaseReactPackage`) exists only so RN autolinking registers the Gradle project and its `companion init` loads `libNitroCompressor.so`.
- **iOS binding** `ios/HybridCompressor.swift` (implements `HybridCompressorSpec`) converts `AnyMap` → `NSDictionary`, synthesizes `RCTPromiseResolveBlock`/`RejectBlock` to drive the Nitro `Promise`.

Heavy domain logic (unchanged, mirrors the JS domains):

- **Android** `android/src/main/java/com/reactnativecompressor/` → `Image/`, `Video/`, `Audio/`, `Utils/`. The video transcoder is hand-rolled under `Video/VideoCompressor/` (MediaCodec/MediaMuxer pipeline: `Compressor.kt`, `MP4Builder.kt`, surfaces/renderer, `utils/`). `VideoMain.compress` routes to auto vs manual via `VideoCompressorHelper`. `StreamableVideo.kt` moves the `moov` atom to the front of the output by default — preserve this behavior.
- **iOS** `ios/` → `Image/`, `Audio/` (with `FormatConverter/`), `Video/`, `Utils/`. Video uses `NextLevelSessionExporter.swift` (a vendored AVFoundation exporter) driven by `VideoMain.swift`. Domain Swift files `import React` for `RCTPromise*` (bridging headers are unsupported under RN 0.85 framework linkage), and `FormatConverter`/`AudioFileFormat` are `internal` to keep them out of the Swift↔C++ interop surface.
- On both platforms `EventEmitterHandler` is the uuid→callback registry described above.

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
yarn nitrogen              # regenerate Nitro native bindings into nitrogen/generated/ (run after editing the *.nitro.ts spec)
yarn prepack               # nitrogen + build the publishable lib/ via react-native-builder-bob
yarn clean                 # delete android/ios build dirs
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
- **One Nitro spec — regenerate after editing.** The spec lives in ONE place: `src/specs/Compressor.nitro.ts` (config in `nitro.json`). After adding/renaming/changing a method, run `yarn nitrogen` and commit the updated `nitrogen/generated/`, then update the two implementations: `ios/HybridCompressor.swift` and `android/.../com/margelo/nitro/compressor/HybridCompressor.kt`. (No more old/new-arch specs, `Compressor.mm`, or `RCTEventEmitter` — those were deleted.)
- **Nitrogen Swift keyword gotcha:** don't name a spec parameter after a Swift keyword (e.g. `extension`) — nitrogen emits it unescaped and the generated Swift won't compile. `generateFilePath` uses `fileExtension` for this reason.
- **iOS framework linkage:** RN 0.85 builds pods as frameworks, where bridging headers are unsupported. Swift files needing React types must `import React` (and `import UIKit` for UIKit types). Public Swift value types with nested types (e.g. `FormatConverter`) must stay `internal` or they break the Swift↔C++ interop link.
- **Android autolink/.so:** `NitroCompressorPackage` must exist (RN CLI keys autolinking off a `ReactPackage`, and its `init` loads `libNitroCompressor.so`). After changing it, clear `examples/bare/android/build/generated/autolinking` if the project stops being found.
- **AnyMap is strict:** option maps must contain only JSON-like values (no functions, no `undefined`) — use `toNativeOptions`. Numbers arrive natively as `Double`; the binding round-trips through `ReadableMap`/`NSNumber` so the domain parsers' `getInt`/`as? Int` keep working.
- **Streamable:** `StreamableVideo.kt` moves `moov` atom to front by default — preserve.
- **uuid threading:** keep `uuid` consistent across JS + both native sides for cancellation + progress-callback routing.
- **Commits follow Conventional Commits** (`fix:`, `feat:`, `refactor:`, `docs:`, `test:`, `chore:`). `commit-msg` hook runs commitlint; `pre-commit` hook (lefthook) runs eslint + `tsc --noEmit` on staged files. Don't bypass.
- **Build output:** `lib/` and example workspaces excluded from lint/tsc/jest — don't edit `lib/` by hand.
- **Releases:** cut with `yarn release` (release-it + conventional-changelog).

## Coding Guidelines

@.claude/rules/karpathy-guidelines.md
