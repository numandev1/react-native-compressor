# CLAUDE.md — react-native-compressor

This file is a project guide for Claude Code / AI agents working on `react-native-compressor`. It describes the repository layout, how the library is structured, how to build and test, common pitfalls, and the conventions used when making changes.

## 1. Project overview

`react-native-compressor` is a React Native module that compresses images, videos, and audio, creates video thumbnails, downloads remote media, and performs background file uploads.

- **Language stack:** TypeScript (JS wrapper), Kotlin (Android), Swift/Objective-C++ (iOS).
- **Module systems:** Supports both the legacy Native Modules bridge and the New Architecture TurboModules spec. The current `1.19.3` branch remains on TurboModules; `main` (2.x) has migrated to Nitro Modules (see §8).
- **Package manager:** Yarn 4 with workspaces. The repository contains two example apps under `examples/`.
- **Node engine:** >= 22.11.0.

## 2. Repository layout

```text
react-native-compressor/
├── android/              # Android library (Kotlin, Gradle)
├── ios/                  # iOS library (Swift, Objective-C++)
├── src/                  # TypeScript source and TurboModule spec
│   ├── Audio/
│   ├── Image/
│   ├── Video/
│   ├── Spec/             # TurboModule spec
│   ├── expo-plugin/
│   ├── utils/            # Upload, download, helpers
│   ├── Main.tsx          # Module resolution (Turbo / legacy)
│   ├── index.tsx         # Public exports
│   └── global.d.ts
├── __tests__/            # Jest unit tests
├── examples/
│   ├── bare/             # Bare React Native example
│   └── expo/             # Expo example
├── harness/              # react-native-harness configuration
├── media/                # Test assets
├── package.json
├── CONTRIBUTING.md
├── TRIAGE.md             # Upstream issue audit
└── README.md
```

## 3. Public API

The default export and named exports live in `src/index.tsx`:

```ts
import {
  Image,
  Video,
  Audio,
  backgroundUpload,
  cancelUpload,
  download,
  getDetails,
  uuidv4,
  generateFilePath,
  getRealPath,
  getVideoMetaData,
  getImageMetaData,
  createVideoThumbnail,
  clearCache,
  getFileSize,
  UploadType,
  UploaderHttpMethod,
} from 'react-native-compressor';
```

Key behavioral rules:

- `Image.compress(value, options?)` rejects empty `value` before calling native code and strips `data:image/...;base64,` headers.
- `Video.compress(fileUrl, options?, onProgress?)` always generates an internal UUID, defaults `compressionMethod` to `'auto'` and `maxSize` to `640`, and supports `stripAudio`.
- `Audio.compress(url, options?)` defaults to `{ quality: 'medium' }`.
- `backgroundUpload(url, fileUrl, options, onProgress?, abortSignal?)` removes the `file://` prefix on Android and wires an `AbortSignal` to `cancelUpload`.
- `download(fileUrl, progress?, progressDivider?)` also strips `file://` on Android.

## 4. Native module resolution

`src/Main.tsx` resolves the native module:

```ts
const isTurboModuleEnabled = global.__turboModuleProxy != null;
const CompressorModule = isTurboModuleEnabled
  ? require('./Spec/NativeCompressor').default
  : NativeModules.Compressor;
```

The TurboModule spec is in `src/Spec/NativeCompressor.ts`. Do **not** rename these methods without updating all native call sites.

## 5. Development workflow

Install dependencies:

```sh
yarn
```

Run the examples:

```sh
yarn example:bare start
yarn example:bare android
yarn example:bare ios
yarn example:expo start
```

Code quality gates:

```sh
yarn typecheck   # tsc --noEmit
yarn lint        # eslint "**/*.{js,ts,tsx}"
yarn lint --fix  # auto-fix formatting / lint issues
yarn test        # Jest unit tests
yarn test:pr     # test + typecheck + lint (CI gate)
```

Clean build folders:

```sh
yarn clean
```

Release (maintainers only):

```sh
yarn release
```

## 6. Testing

### Unit tests

`__tests__/compressor.test.ts` mocks the native module and validates the JS wrapper contract. When you change option forwarding, event-emitter wiring, or the export surface, update this test.

Run:

```sh
yarn test
```

### Native harness tests

For changes that affect compression/upload/download native behavior, run the harness tests on a real device or simulator:

```sh
yarn test:harness:android
yarn test:harness:ios
```

Harness config lives in `examples/bare/jest.harness.config.mjs`. The harness tests exercise actual media decoding and are the source of truth for native behavior.

### Example apps

Always test manually in `examples/bare` and `examples/expo` when touching:

- Native Android/iOS code
- The Expo plugin (`app.plugin.js`, `src/expo-plugin/`)
- Upload/download progress wiring

## 7. Git conventions

- **Commit format:** Conventional Commits.
  - `fix:` bug fixes
  - `feat:` new features
  - `refactor:` code refactoring
  - `docs:` documentation changes
  - `test:` test changes
  - `chore:` tooling / CI
- **Pre-commit hooks** (`lefthook.yml`) run ESLint and `tsc --noEmit` on staged files; `commit-msg` runs `commitlint`.
- **PR template:** `.github/PULL_REQUEST_TEMPLATE.md` requires a Summary, Changelog entry, and Test Plan.

Always run the PR gate before pushing:

```sh
yarn test:pr
```

## 8. Branch strategy and the Nitro migration

- `main` is on 2.x and has migrated to **Nitro Modules** (`feat: migrate react-native-compressor to Nitro Modules #401`).
- The current working branch is `1.19.3`, which keeps the TurboModule / legacy bridge structure.
- Fixes that land on `1.19.3` may need to be forward-ported to `main`/Nitro, and vice versa. Check both branches when resolving regressions.

## 9. Platform-specific notes

### Android

- Source lives in `android/src/main/java/com/reactnativecompressor/`.
- Uses Kotlin coroutines (`kotlinx-coroutines-core/android 1.6.4`).
- Uses `org.mp4parser:isoparser:1.9.56` for MP4 container manipulation.
- Uses `com.github.kaushik-naik:TAndroidLame` for audio encoding.
- `android/build.gradle` enables `buildConfig` for AGP 8+ compatibility.
- `file://` prefixes are stripped before passing paths to native methods (done in JS for download/upload).

### iOS

- Source lives in `ios/` and is organized into `Audio/`, `Image/`, `Video/`, and `Utils/`.
- Entry point: `ios/Compressor.mm` with `CompressorManager.swift` coordinating work.
- `AssetsLibrary` has been removed (iOS 26 / modern SDK support).
- Guard against audio-only / missing video tracks; exports must fail explicitly rather than silently producing audio-only MP4s.

## 10. Common pitfalls

1. **Native method names.** The spec in `src/Spec/NativeCompressor.ts` must match the Android/iOS method names. Renames require three-way updates.
2. **UUID wiring.** Progress events are keyed by UUID. If a native call does not receive the UUID, progress callbacks will not fire.
3. **File URI normalization.** Android native APIs expect plain filesystem paths; `file://` stripping happens in `src/utils/Uploader.tsx` and `Downloader.tsx`. iOS generally accepts `file://` URIs.
4. **Event listener cleanup.** Wrappers add `NativeEventEmitter` listeners in `try` and remove them in `finally`. Always preserve this pattern to avoid leaks.
5. **Base64 images.** `Image.compress` strips the base64 data URL header before calling `image_compress`. Do not double-strip in native code.
6. **Expo plugin changes.** If you change config-plugin behavior, test in `examples/expo` and verify `app.plugin.js` still resolves correctly.
7. **New Architecture.** Builds with `newArchEnabled=true` generate code into `android/build/generated/source/codegen/java`. Clean builds are required when the spec changes.

## 11. What to do when touching specific areas

| Area | Files to read / update | Validation |
| --- | --- | --- |
| Image compression | `src/Image/index.tsx`, `ios/Image/`, `android/src/.../Image/` | Unit test + bare example |
| Video compression | `src/Video/index.tsx`, `ios/Video/`, `android/src/.../Video/` | Harness test + bare example |
| Audio compression | `src/Audio/index.tsx`, `ios/Audio/`, `android/src/.../Audio/` | Bare example |
| Upload / download | `src/utils/Uploader.tsx`, `src/utils/Downloader.tsx` | Unit test + harness test |
| Thumbnails / metadata | `src/utils/index.tsx`, native utils | Unit test + bare example |
| Expo plugin | `app.plugin.js`, `src/expo-plugin/` | `examples/expo` build |
| TurboModule spec | `src/Spec/NativeCompressor.ts` | `yarn typecheck`, rebuild native apps |
| CI / tooling | `.github/workflows/`, `package.json`, `lefthook.yml` | Push to a PR and inspect Actions |

## 12. Useful commands

```sh
# Full PR gate
yarn test:pr

# Android build (debug)
yarn build:android

# iOS build (debug simulator)
yarn build:ios

# Native harness
yarn test:harness:android
yarn test:harness:ios

# Clean everything
yarn clean
```

## 13. Release notes

Published versions use `release-it` with the conventional-changelog plugin. The changelog is generated from commit messages, so write clear, scoped commits.

---

When in doubt, keep changes minimal, add or update unit tests, and run `yarn test:pr` before asking for review.
