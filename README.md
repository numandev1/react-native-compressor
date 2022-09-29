### Would you like to support me?

<div align="center">
<a href="https://github.com/numandev1?tab=followers">
    <img src="https://img.shields.io/github/followers/numandev1?label=Follow%20%40numandev1&style=social" height="36" />
</a>
<a href="https://www.youtube.com/channel/UCYCUspfN7ZevgCj3W5GlFAw"><img src="https://img.shields.io/youtube/channel/subscribers/UCYCUspfN7ZevgCj3W5GlFAw?style=social" height="36" /><a/>
</br>
<a href="https://www.buymeacoffee.com/numan.dev" target="_blank"><img src="https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png" alt="Buy Me A Coffee" style="height: auto !important;width: auto !important;" ></a>
</div>

---

# react-native-compressor

<!-- Header -->

<p align="center">
  <b>Compress videos, images and audio before upload</b>
  <br />
</p>

<p align="center">
  <img height="450" src="/media/cover.png">
</p>

**react-native-compressor** package is a set of functions that allow you compress `Image`,`Audio` and `Video`

**If you find this package useful hit the star** 🌟

# Table of Contents

<details>
<summary>Open Table of Contents</summary>

- [Installation](#installation)
  - [For React Native](#react-native)
    - [For React Native<0.65](#for-react-native065)
    - [For React Native 0.65 or greater](#for-react-native-065-or-greater)
  - [Managed Expo](#managed-expo)
- [Usage](#usage)
  - [Image](#image)
    - [Automatic Image Compression Like Whatsapp](#automatic-image-compression-like-whatsapp)
    - [Manual Image Compression](#manual-image-compression)
    - [ImageCompressor API Docs](#imagecompressor)
  - [Video](#video)
    - [Automatic Video Compression Like Whatsapp](#automatic-video-compression-like-whatsapp)
    - [Manual Video Compression](#manual-video-compression)
    - [Cancel Video Compression](#cancel-video-compression)
    - [Video Api Docs](#video-1)
  - [Audio](#audio)
  - [Background Upload](#background-upload)

* [Other Utilities](#api)
  - [Background Upload](#background-upload-1)
  - [Get Metadata Of Video](#get-metadata-of-video)
  - [Get Real Path](#get-real-path)
  - [Get Temp file Path](#get-temp-file-path)
  </details>

## Installation

### React Native

#### For React Native<0.65

```sh
yarn add react-native-compressor@rnlessthan65
```

#### For React Native 0.65 or greater

```sh
yarn add react-native-compressor
```

### Managed Expo

```
expo install react-native-compressor
```

Add the Compressor plugin to your Expo config (`app.json`, `app.config.json` or `app.config.js`):

```json
{
  "name": "my app",
  "plugins": ["react-native-compressor"]
}
```

Finally, compile the mods:

```
expo prebuild
```

To apply the changes, build a new binary with EAS:

```
eas build
```

### Automatic linking (for React Native >= 0.60 only)

Automatic linking is supported for both `Android` and `IOS`

### Linking (for React Native <= 0.59 only)

Note: If you are using react-native version 0.60 or higher you don't need to link this package.

```sh
react-native link react-native-compressor
```

### Manual installation

#### iOS

1. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
2. Go to `node_modules` ➜ `react-native-compressor` and add `Compressor.xcodeproj`
3. In XCode, in the project navigator, select your project. Add `libCompressor.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
4. Run your project (`Cmd+R`)<

#### Android

1. Open up `android/app/src/main/java/[...]/MainActivity.java`

- Add `import com.reactnativecompressor.CompressorPackage;` to the imports at the top of the file
- Add `new CompressorPackage()` to the list returned by the `getPackages()` method

2. Append the following lines to `android/settings.gradle`:
   ```
   include ':react-native-compressor'
   project(':react-native-compressor').projectDir = new File(rootProject.projectDir,'../node_modules/react-native-compressor/android')
   ```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
   ```
     compile project(':react-native-compressor')
   ```

## Usage

### Image

##### Automatic Image Compression Like Whatsapp

```js
import { Image } from 'react-native-compressor';

const result = await Image.compress('file://path_of_file/image.jpg', {
  compressionMethod: 'auto',
});
```

[Here is this package comparison of images compression with WhatsApp](https://docs.google.com/spreadsheets/d/13TsnC1c7NOC9aCjzN6wkKurJQPeGRNwDhWsQOkXQskU/edit?usp=sharing)

##### Manual Image Compression

```js
import { Image } from 'react-native-compressor';

const result = await Image.compress('file://path_of_file/image.jpg', {
  maxWidth: 1000,
  quality: 0.8,
});
```

### Video

##### Automatic Video Compression Like Whatsapp

```js
import { Video } from 'react-native-compressor';

const result = await Video.compress(
  'file://path_of_file/BigBuckBunny.mp4',
  {
    compressionMethod: 'auto',
  },
  (progress) => {
    if (backgroundMode) {
      console.log('Compression Progress: ', progress);
    } else {
      setCompressingProgress(progress);
    }
  }
);
```

[Here is this package comparison of video compression with WhatsApp](https://docs.google.com/spreadsheets/d/13TsnC1c7NOC9aCjzN6wkKurJQPeGRNwDhWsQOkXQskU/edit#gid=1055406534)

##### Manual Video Compression

```js
import { Video } from 'react-native-compressor';

const result = await Video.compress(
  'file://path_of_file/BigBuckBunny.mp4',
  {},
  (progress) => {
    if (backgroundMode) {
      console.log('Compression Progress: ', progress);
    } else {
      setCompressingProgress(progress);
    }
  }
);
```

##### Cancel Video Compression

```js
import { Video } from 'react-native-compressor';

let cancellationVideoId = '';

const result = await Video.compress(
  'file://path_of_file/BigBuckBunny.mp4',
  {
    compressionMethod: 'auto',
    // getCancellationId for get video id which we can use for cancel compression
    getCancellationId: (cancellationId) =>
      (cancellationVideoId = cancellationId),
  },
  (progress) => {
    if (backgroundMode) {
      console.log('Compression Progress: ', progress);
    } else {
      setCompressingProgress(progress);
    }
  }
);

// we can cancel video compression by calling cancelCompression with cancel video id which we can get from getCancellationId function while compression
Video.cancelCompression(cancellationVideoId);
```

### Audio

```js
import { Audio } from 'react-native-compressor';

const result = await Audio.compress(
  'file://path_of_file/file_example_MP3_2MG.mp3',
  { quality: 'medium' }
);
```

### Background Upload

```js
import { backgroundUpload } from 'react-native-compressor';

const headers = {};

const uploadResult = await backgroundUpload(
  url,
  fileUrl,
  { httpMethod: 'PUT', headers },
  (written, total) => {
    console.log(written, total);
  }
);
```

# API

## Image

### ImageCompressor

- ###### `compress(value: string, options?: CompressorOptions): Promise<string>`

  Compresses the input file URI or base-64 string with the specified options. Promise returns a string after compression has completed. Resizing will always keep the original aspect ratio of the image, the `maxWidth` and `maxHeight` are used as a boundary.

### CompressorOptions

- ###### `compressionMethod: compressionMethod` (default: "manual")

  if you want to compress images like **whatsapp** then make this prop `auto`. Can be either `manual` or `auto`, defines the Compression Method.

- ###### `maxWidth: number` (default: 1280)

  The maximum width boundary used as the main boundary in resizing a landscape image.

- ###### `maxHeight: number` (default: 1280)

  The maximum height boundary used as the main boundary in resizing a portrait image.

- ###### `quality: number` (default: 0.8)

  The quality modifier for the `JPEG` file format, can be specified when output is `PNG` but will be ignored.

- ###### `input: InputType` (default: uri)

  Can be either `uri` or `base64`, defines the contentents of the `value` parameter.

- ###### `output: OutputType` (default: jpg)

  Can be either `jpg` or `png`, defines the output image format.

- ###### `returnableOutputType: ReturnableOutputType` (default: uri)
  Can be either `uri` or `base64`, defines the Returnable output image format.

## Video

- ###### `compress(url: string, options?: videoCompresssionType , onProgress?: (progress: number)): Promise<string>`

- ###### `cancelCompression(cancellationId: string): void`
  we can get cancellationId from `getCancellationId` which is the callback method of compress method options

### videoCompresssionType

- ###### `compressionMethod: compressionMethod` (default: "manual")

  if you want to compress videos like **whatsapp** then make this prop `auto`. Can be either `manual` or `auto`, defines the Compression Method.

- ###### `maxSize: number` (default: 640)

  The maximum size can be height in case of portrait video or can be width in case of landscape video.

- ###### `bitrate: string`

  bitrate of video which reduce or increase video size. if compressionMethod will auto then this prop will not work

- ###### `minimumFileSizeForCompress: number` (default: 16)

  16 means 16mb. default our package do not compress under 16mb video file. minimumFileSizeForCompress will allow us to change this 16mb offset. fixed [#26](https://github.com/Shobbak/react-native-compressor/issues/26)

- ###### `getCancellationId: function`
  `getCancellationId` is a callback function that gives us compress video id, which can be used in `Video.cancelCompression` method to cancel the compression

## Audio

- ###### `compress(url: string, options?: audioCompresssionType): Promise<string>`

### audioCompresssionType

- ###### `quality: qualityType` (default: medium)
  we can also control bitrate through quality. qualityType can be `low` | `medium` | `high`

**Note: Audio compression will be add soon**

## Background Upload

- ###### `backgroundUpload: (url: string, fileUrl: string, options: FileSystemUploadOptions, onProgress?: ((writtem: number, total: number) => void) | undefined) => Promise<any>

- ###### ` FileSystemUploadOptions`

```js
type FileSystemUploadOptions = (
  | {
      uploadType?: FileSystemUploadType.BINARY_CONTENT,
    }
  | {
      uploadType: FileSystemUploadType.MULTIPART,
      fieldName?: string,
      mimeType?: string,
      parameters?: Record<string, string>,
    }
) & {
  headers?: Record<string, string>,
  httpMethod?: FileSystemAcceptedUploadHttpMethod,
  sessionType?: FileSystemSessionType,
};
```

### Get Metadata Of Video

if you want to get metadata of video than you can use this function

```js
import { getVideoMetaData } from 'react-native-compressor';

const metaData = await getVideoMetaData(filePath);
```

```
{
	"duration": "6",
	"extension": "mp4",
	"height": "1080",
	"size": "16940.0",
	"width": "1920"
}
```

- ###### `getVideoMetaData(path: string)`

### Get Real Path

if you want to convert

- `content://` to `file:///` for android
- `ph://` to `file:///` for IOS

the you can you `getRealPath` function like this

```js
import { getRealPath } from 'react-native-compressor';

const realPath = await getRealPath(fileUri, 'video'); //   file://file_path.extension
```

- ###### `getRealPath(path: string, type: string = 'video'|'image')`

### Get Temp file Path

if you wanna make random file path in cache folder then you can use this method like this

```js
import { generateFilePath } from 'react-native-compressor';

const randomFilePathForSaveFile = await generateFilePath('mp4'); //   file://file_path.mp4
```

- ##### `generateFilePath(fileextension: string)`

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT
