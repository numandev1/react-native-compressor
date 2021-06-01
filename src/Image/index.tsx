import { NativeModules } from 'react-native';
const base64UrlRegex = /^data:image\/.*;(?:charset=.{3,5};)?base64,/;

export type InputType = 'base64' | 'uri';

export type OutputType = 'jpeg' | 'png';

export type CompressorOptions = {
  /***
   * The maximum width boundary used when compressing a landscape image.
   */
  maxWidth?: number;
  /***
   * The maximum height boundary used when compressing a portrait image.
   */
  maxHeight?: number;
  /***
   * The compression factor used when compressing JPEG images. Won't be used in PNG.
   */
  quality?: number;
  /***
   * The type of data the input value contains.
   */
  input?: InputType;
  /***
   * The output image type.
   */
  output?: OutputType;
};

const NativeImage = NativeModules.Compressor;

type ImageType = {
  compress(value: string, options?: CompressorOptions): Promise<string>;
};

const Image: ImageType = {
  compress: (value, options) => {
    if (!value) {
      throw new Error(
        'Compression value is empty, please provide a value for compression.'
      );
    }
    const cleanData = value.replace(base64UrlRegex, '');
    return NativeImage.image_compress(cleanData, options);
  },
};

export default Image;