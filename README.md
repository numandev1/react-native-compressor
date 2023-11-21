<img src="https://numan.dev/images/child-lives-matter.png" />

<div align="center">
 <img height="150" src="/media/logo.png" />
</div>
 
<br/>

<div align="center">

[![GitHub Repo stars](https://img.shields.io/badge/React_Native-20232A?style=for-the-badge&logo=react&logoColor=61DAFB)](#Installation)
[![GitHub Repo stars](https://img.shields.io/badge/Expo-1B1F23?style=for-the-badge&logo=expo&logoColor=white)](#managed-expo)
[![GitHub Repo stars](https://img.shields.io/static/v1?style=for-the-badge&message=Discord&color=5865F2&logo=Discord&logoColor=FFFFFF&label=)](https://discord.gg/6Wx8Em8KAN)
[![GitHub Repo stars](https://img.shields.io/github/stars/numandev1/react-native-compressor?style=for-the-badge&logo=github)](https://github.com/numandev1/react-native-compressor/stargazers)
![npm](https://img.shields.io/npm/dt/react-native-compressor?style=for-the-badge)

</div>

**REACT-NATIVE-COMPRESSOR** is a react-native package, which helps us to Compress `Image`, `Video`, and `Audio` before uploading, same like **Whatsapp** without knowing the compression `algorithm`

<div align="center">
 <h4>Auto/Manual Compression | Background Upload | Download File | Create Video Thumbnail</h4>
</div>
<br/>

<div align="center">
<pre>
<img height="90" src="/media/whatsapp_logo.png"/>               <img height="90" src="/media/compress_media.png"/>
</pre>
<h2 align="center">🗜️Compress Image, Video, and Audio same like Whatsapp</h2>
</div>

#### Why should we use react-native-compress over [FFmpeg](https://www.ffmpeg.org/)?

We should use **react-native-compressor** instead of **FFmpeg** because **react-native-compressor** gives you same compression of **Whatsapp** (`Image, Video, and Audio`) without knowing the algorithm of compression + it is lightweight only increase **50 KB Size** Size in APK while **FFmpeg** increase ~> **9 MB Size** in **APK**, and we have to give manual image/video/Audo size and quality as well as

**If you find this package useful hit the star** 🌟

### Would you like to support me?

<div align="center">
<a href="https://github.com/numandev1?tab=followers">
    <img src="https://img.shields.io/github/followers/numandev1?label=Follow%20%40numandev1&style=social" height="36" />
</a>
<a href="https://twitter.com/numandev1/">
    <img src="https://img.shields.io/twitter/follow/numandev1?label=Follow%20%40numandev1&style=social" height="36" />
</a>
<a href="https://www.youtube.com/channel/UCYCUspfN7ZevgCj3W5GlFAw"><img src="https://img.shields.io/youtube/channel/subscribers/UCYCUspfN7ZevgCj3W5GlFAw?style=social" height="36" /><a/>
</br>
<a href="https://www.buymeacoffee.com/numan.dev" target="_blank"><img src="https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png" alt="Buy Me A Coffee" style="height: auto !important;width: auto !important;" ></a>
</div>

---

#### See the [Benchmarks](#benchmark)

# Table of Contents

<details>
<summary>Open Table of Contents</summary>

- [Installation](#installation)
  - [For React Native](#Installation)
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
  - [Cancel Background Upload](#cancel-background-upload)
  - [Download File](#download)
  - [Create Video Thumbnail and Clear Cache](#create-video-thumbnail-and-clear-cache)

* [Other Utilities](#api)
  - [Background Upload](#background-upload-1)
  - [Cancel Background Upload](#cancel-background-upload-1)
  - [Get Metadata Of Image](#get-metadata-of-image)
  - [Get Metadata Of Video](#get-metadata-of-video)
  - [Get Real Path](#get-real-path)
  - [Get Temp file Path](#get-temp-file-path)
  </details>

## Installation

```sh
yarn add react-native-compressor
```

### [New Architecture (Turbo Module)](https://reactnative.dev/docs/new-architecture-intro) Supported

you can give feedback on [Discord channel](https://discord.gg/6Wx8Em8KAN)

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

1. In XCode, open Podfile
2. paste this line `pod 'react-native-compressor', :path => '../node_modules/react-native-compressor'` into `Podfile`
3. run this command inside ios folder `pod install`
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

const result = await Image.compress('file://path_of_file/image.jpg');
// OR
const result = await Image.compress('https://path_of_file/image.jpg', {
  progressDivider: 10,
  downloadProgress: (progress) => {
    console.log('downloadProgress: ', progress);
  },
});
```

[Here is this package comparison of images compression with WhatsApp](https://docs.google.com/spreadsheets/d/13TsnC1c7NOC9aCjzN6wkKurJQPeGRNwDhWsQOkXQskU/edit?usp=sharing)

##### Manual Image Compression

```js
import { Image } from 'react-native-compressor';

const result = await Image.compress('file://path_of_file/image.jpg', {
  compressionMethod: 'manual',
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
  {},
  (progress) => {
    console.log('Compression Progress: ', progress);
  }
);

//OR

const result = await Video.compress(
  'https://example.com/video.mp4',
  {
    progressDivider: 10,
    downloadProgress: (progress) => {
      console.log('downloadProgress: ', progress);
    },
  },
  (progress) => {
    console.log('Compression Progress: ', progress);
  }
);
```

[Here is this package comparison of video compression with WhatsApp](https://docs.google.com/spreadsheets/d/13TsnC1c7NOC9aCjzN6wkKurJQPeGRNwDhWsQOkXQskU/edit#gid=1055406534)

##### Manual Video Compression

```js
import { Video } from 'react-native-compressor';

const result = await Video.compress(
  'file://path_of_file/BigBuckBunny.mp4',
  {
    compressionMethod: 'manual',
  },
  (progress) => {
    console.log('Compression Progress: ', progress);
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
  'file://path_of_file/file_example_MP3_2MG.wav', // recommended wav file but can be use mp3 file
  { quality: 'medium' }
);

// OR

const result = await Audio.compress(
  'file://path_of_file/file_example_MP3_2MG.wav', // recommended wav file but can be use mp3 file
  {
    bitrate: 64000,
    samplerate: 44100,
    channels: 1,
  }
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

//OR

const uploadResult = await backgroundUpload(
  url,
  fileUrl,
  { uploadType: UploadType.MULTIPART, httpMethod: 'POST', headers },
  (written, total) => {
    console.log(written, total);
  }
);
```

### Cancel Background Upload
for cancellation Upload, there is two ways
1. by calling, cancelUpload function
2. by calling abort function

##### cancelUpload (support single and all)
```js
import { cancelUpload, backgroundUpload } from 'react-native-compressor';

// if we will call without passing any param then it will remove last pushed uploading
cancelUpload()

// if you pass true as second param then it will cancel all the uploading
cancelUpload("",true)

// if there is multiple files are uploading, and you wanna cancel specific uploading then you pass specific video id like this
let videoId=''
const uploadResult = await backgroundUpload(
  url,
  fileUrl,
  { httpMethod: 'PUT',  getCancellationId: (cancellationId) =>(videoId = cancellationId), },
  (written, total) => {
    console.log(written, total);
  }
);
cancelUpload(videoId)
```

##### cancel by calling abort
```js
import { backgroundUpload } from 'react-native-compressor';

const abortSignalRef = useRef(new AbortController());

const uploadResult = await backgroundUpload(
  url,
  fileUrl,
  { httpMethod: 'PUT' },
  (written, total) => {
    console.log(written, total);
  },
  abortSignalRef.current.signal
);

abortSignalRef.current?.abort(); // this will cancel uploading
```

### Download File

```js
import { download } from 'react-native-compressor';

const downloadFileUrl = await download(url, (progress) => {
  console.log('downloadProgress: ', progress);
});
```

### Video Thumbnail

```js
import { createVideoThumbnail, clearCache } from 'react-native-compressor';

const thumbnail = await createVideoThumbnail(videoUri);

await clearCache(); // this will clear cache of thumbnails cache directory
```

# API

## Image

### ImageCompressor

- ###### `compress(value: string, options?: CompressorOptions): Promise<string>`

  Compresses the input file URI or base-64 string with the specified options. Promise returns a string after compression has completed. Resizing will always keep the original aspect ratio of the image, the `maxWidth` and `maxHeight` are used as a boundary.

### CompressorOptions

- ###### `compressionMethod: compressionMethod` (default: "auto")

  if you want to compress images like **whatsapp** then make this prop `auto`. Can be either `manual` or `auto`, defines the Compression Method.

- ##### `downloadProgress?: (progress: number) => void;`

  it is callback, only trigger when we pass image url from server

- ##### `progressDivider?: number` (default: 0)

  we uses it when we use downloadProgress

- ###### `maxWidth: number` (default: 1280)

  The maximum width boundary used as the main boundary in resizing a landscape image.

- ###### `maxHeight: number` (default: 1280)

  The maximum height boundary used as the main boundary in resizing a portrait image.

- ###### `quality: number` (default: 0.8)

  The quality modifier for the `JPEG` and `PNG` file format, if your input file is `JPEG` and output file is `PNG` then compressed size can be increase

- ###### `input: InputType` (default: uri)

  Can be either `uri` or `base64`, defines the contentents of the `value` parameter.

- ###### `output: OutputType` (default: jpg)

  The quality modifier for the `JPEG` file format, can be specified when output is `PNG` but will be ignored. if you wanna apply quality modifier then you can enable `disablePngTransparency:true`,
  **Note:** if you png image have no transparent background then enable `disablePngTransparency:true` modifier is recommended

- ###### `disablePngTransparency: boolean` (default: false)

  when user add `output:'png'` then by default compressed image will have transparent background, and quality will be ignored, if you wanna apply quality then you have to disablePngTransparency like `disablePngTransparency:true`, it will convert transparent background to white

- ###### `returnableOutputType: ReturnableOutputType` (default: uri)
  Can be either `uri` or `base64`, defines the Returnable output image format.

**if you wanna get image metadata (exif) then [read this](#get-metadata-of-image)**

## Video

- ###### `compress(url: string, options?: videoCompresssionType , onProgress?: (progress: number)): Promise<string>`

- ###### `cancelCompression(cancellationId: string): void`

  we can get cancellationId from `getCancellationId` which is the callback method of compress method options

- ###### `activateBackgroundTask(onExpired?: (data: any) => void): Promise<any>`
  if you wanna compress video while app is in backgroup then you should call this method before compression
- ###### `deactivateBackgroundTask(): Promise<any>`
  if you call `activateBackgroundTask` method, then after video compression, you should call `deactivateBackgroundTask` for disable background task mode.

- ###### `getCancellationId: function`
  `getCancellationId` is a callback function that gives us compress video id, which can be used in `Video.cancelCompression` method to cancel the compression

### videoCompresssionType

- ###### `compressionMethod: compressionMethod` (default: "manual")

  if you want to compress videos like **whatsapp** then make this prop `auto`. Can be either `manual` or `auto`, defines the Compression Method.

- ##### `downloadProgress?: (progress: number) => void;`

  it is callback, only trigger when we pass image url from server

- ##### `progressDivider?: number` (default: 0)

  we uses it when we use downloadProgress/onProgress

- ###### `maxSize: number` (default: 640)

  The maximum size can be height in case of portrait video or can be width in case of landscape video.

- ###### `bitrate: number`

  bitrate of video which reduce or increase video size. if compressionMethod will auto then this prop will not work

- ###### `minimumFileSizeForCompress: number` (default: 0)

  previously default was 16 but now it is 0 by default. 0 mean 0mb. This is an offset, which you can set for minimumFileSizeForCompress will allow this package to dont compress less than or equal to `minimumFileSizeForCompress` ref [#26](https://github.com/numandev1/react-native-compressor/issues/26)

**if you wanna get video metadata then [read this](#get-metadata-of-video)**

## Audio

- ###### `compress(url: string, options?: audioCompresssionType): Promise<string>`
  Android: recommended to use `wav` file as we convert mp3 to wav then apply bitrate

### audioCompresssionType

- ###### `quality?: qualityType` (default: medium)

  we can also control bitrate through quality. qualityType can be `low` | `medium` | `high`

- ###### `bitrate?: number` Range [64000-320000]

  we can control bitrate of audio through bitrate, it should be in the range of `64000-320000`

- ###### `samplerate?: number` Range [44100 - 192000]

  we can control samplerate of audio through samplerate, it should be in the range of `44100 - 192000`

- ###### `channels?: number` Typically 1 or 2
  we can control channels of audio through channels, Typically 1 or 2

## Background Upload

- ###### backgroundUpload: (url: string, fileUrl: string, options: UploaderOptions, onProgress?: ((writtem: number, total: number) => void) | undefined) => Promise< any >

- ###### ` UploaderOptions`

```js
export enum UploadType {
  BINARY_CONTENT = 0,
  MULTIPART = 1,
}

export enum UploaderHttpMethod {
  POST = 'POST',
  PUT = 'PUT',
  PATCH = 'PATCH',
}

export declare type HTTPResponse = {
  status: number;
  headers: Record<string, string>;
  body: string;
};

export declare type HttpMethod = 'POST' | 'PUT' | 'PATCH';

export declare type UploaderOptions = (
  | {
      uploadType?: UploadType.BINARY_CONTENT;
      mimeType?: string;
    }
  | {
      uploadType: UploadType.MULTIPART;
      fieldName?: string;
      mimeType?: string;
      parameters?: Record<string, string>;
    }
) & {
  headers?: Record<string, string>;
  httpMethod?: UploaderHttpMethod;
  getCancellationId?: (cancellationId: string) => void;
};
```

**Note:** some of the uploader code is borrowed from [Expo](https://github.com/expo/expo)
I tested file uploader on this backend [Nodejs-File-Uploader](https://github.com/numandev1/nodejs-file-uploader)

### Cancel Background Upload
for cancellation Upload, there is two ways, you can use one of it
- ##### cancelUpload: ( uuid?: string, shouldCancelAll?: boolean) => void
  1. If we call without passing any param then it will remove the last pushed uploading
  2. If you pass true as the second param then it will cancel all the uploading
  3. if there is multiple files are uploading, and you wanna cancel specific uploading then you pass a specific video ID like this

- ##### we can use [AbortController](https://github.com/facebook/react-native/blob/255fef5263afdf9933ba2f8a3dbcbca39ea9928a/packages/react-native/types/modules/globals.d.ts#L531) in backgroundUpload [Usage](#cancel-background-upload)
  `const abortSignalRef = useRef(new AbortController());`

  `abortSignalRef.current?.abort();`

### Download

- ##### download: ( fileUrl: string, downloadProgress?: (progress: number) => void, progressDivider?: number ) => Promise< string >

### Create Video Thumbnail and Clear Cache

- #### createVideoThumbnail( fileUrl: string, options: {header:Object} ): Promise<{ path: string;size: number; mime: string; width: number; height: number; }>

  it will save the thumbnail of the video into the cache directory and return the thumbnail URI which you can display

- #### clearCache(cacheDir?: string): Promise< string >

  it will clear the cache that was created from createVideoThumbnail, in future this clear cache will be totally customized

### Get Metadata Of Video

if you want to get metadata of video than you can use this function

```js
import { getVideoMetaData } from 'react-native-compressor';

const metaData = await getVideoMetaData(filePath);
```

```
{
	"duration": 20.11,
	"extension": "mp4",
	"height": 1080,
	"size": 16940.0,
	"width": 1920
}
```

- ###### `getVideoMetaData(path: string)`

### Get Metadata Of Image

if you want to get metadata of video than you can use this function

```js
import { getImageMetaData } from 'react-native-compressor';

const metaData = await getImageMetaData(filePath);
```

```
{
  "ImageWidth": 4032,
  "ImageHeight": 3024,
  "Orientation": 3,
  "size": 4127057,
  "extension": "jpg",
  "exif":{...}
}
```

- ###### `getImageMetaData(path: string)`

### Get Real Path

if you want to convert

- `content://` to `file:///` for android
- `ph://` to `file:///` for IOS

then you can call `getRealPath` function like this

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

## Benchmark

[<img height="30" src="/media/whatsapp_logo.png"> Whatsapp:](https://apps.apple.com/us/app/whatsapp-messenger/id310633997) compresses Images,Videos and Audios in every effect way

<p align="center">
  <img height="450" src="/media/branchmark_for_images.png">
   <br /> <br />
  <img height="450" src="/media/benchmark_for_videos.png">
</p>

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT
