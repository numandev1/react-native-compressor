### Would you like to support me?

<a href="https://www.buymeacoffee.com/numan.dev" target="_blank"><img src="https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png" alt="Buy Me A Coffee" style="height: auto !important;width: auto !important;" ></a>

---

# react-native-compressor

<!-- Title -->
<p align="center">
<img src="/media/icon.png" alt="alt text" width="150"/>
</p>

<!-- Header -->

<p align="center">
  <b>Compress videos, images and audio before upload</b>
  <br />
</p>

<p align="center">
  <img height="450" src="/media/cover.png">
</p>

**react-native-compressor** package is a set of functions that allow you compress `Image`,`Audio` and `Video`

If you find this package useful hit the star 🌟

## Installation

### React Native

#### For React Native<0.65

```sh
yarn add react-native-compressor@0.5.9
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

##### For Like Whatsapp Image Compression

```js
import { Image } from 'react-native-compressor';

const result = await Image.compress('file://path_of_file/image.jpg', {
  compressionMethod: 'auto',
});
```

[Here is this package comparison of images compression with WhatsApp](https://docs.google.com/spreadsheets/d/13TsnC1c7NOC9aCjzN6wkKurJQPeGRNwDhWsQOkXQskU/edit?usp=sharing)

##### For manual Compression

```js
import { Image } from 'react-native-compressor';

const result = await Image.compress('file://path_of_file/image.jpg', {
  maxWidth: 1000,
  quality: 0.8,
});
```

### Video

##### For Like Whatsapp Video Compression

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

##### For manual Compression

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

### videoCompresssionType

- ###### `compressionMethod: compressionMethod` (default: "manual")

  if you want to compress videos like **whatsapp** then make this prop `auto`. Can be either `manual` or `auto`, defines the Compression Method.

- ###### `maxSize: number` (default: 640)

  The maximum size can be height in case of portrait video or can be width in case of landscape video.

- ###### `bitrate: string`

  bitrate of video which reduce or increase video size. if compressionMethod will auto then this prop will not work

- ###### `minimumFileSizeForCompress: number` (default: 16)
  16 means 16mb. default our package do not compress under 16mb video file. minimumFileSizeForCompress will allow us to change this 16mb offset. fixed [#26](https://github.com/Shobbak/react-native-compressor/issues/26)

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

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT
