# react-native-compressor

react-native-compressor package is a set of functions that allow you compress `Image`,`Audio` and `Video`

## Installation

Using Yarn

```sh
yarn add react-native-compressor
```

Using Npm

```sh
npm install react-native-compressor --save
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

```js
import { Image } from 'react-native-compressor';

const result = await Image.compress('file://path_of_file/image.jpg', {
  maxWidth: 1000,
  quality: 0.8,
});
```

### Audio

```js
import { Audio } from 'react-native-compressor';

const result = await Audio.compress(
  'file://path_of_file/file_example_MP3_2MG.mp3',
  { quality: 'medium' }
);
```

### Video

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

# API

## Image

### ImageCompressor

- ###### `compress(value: string, options?: CompressorOptions): Promise<string>`

  Compresses the input file URI or base-64 string with the specified options. Promise returns a string after compression has completed. Resizing will always keep the original aspect ratio of the image, the `maxWidth` and `maxHeight` are used as a boundary.

### CompressorOptions

- ###### `maxWidth: number` (default: 1024)

  The maximum width boundary used as the main boundary in resizing a landscape image.

- ###### `maxHeight: number` (default: 1024)

  The maximum height boundary used as the main boundary in resizing a portrait image.

- ###### `quality: number` (default: 1.0)

  The quality modifier for the `JPEG` file format, can be specified when output is `PNG` but will be ignored.

- ###### `input: InputType` (default: uri)

  Can be either `uri` or `base64`, defines the contentents of the `value` parameter.

- ###### `output: OutputType` (default: jpg)

  Can be either `jpg` or `png`, defines the output image format.

- ###### `returnableOutputType: ReturnableOutputType` (default: uri)
  Can be either `uri` or `base64`, defines the Returnable output image format.

## Audio

- ###### `compress(url: string, options?: audioCompresssionType): Promise<string>`

### audioCompresssionType

- ###### `quality: qualityType` (default: medium)
  we can also control bitrate through quality. qualityType can be `low` | `medium` | `high`

## Video

- ###### `compress(url: string, options?: videoCompresssionType , onProgress?: (progress: number)): Promise<string>`

### videoCompresssionType

- ###### `bitrate: string`
  bitrate of video which reduce or increase video size.

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT
