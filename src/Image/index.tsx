import { Compressor } from '../Main';
import { uuidv4 } from '../utils';
const base64UrlRegex = /^data:image\/.*;(?:charset=.{3,5};)?base64,/;
import type { NativeEventSubscription } from 'react-native';
import { NativeEventEmitter } from 'react-native';

export type InputType = 'base64' | 'uri';

export type OutputType = 'jpg' | 'png';

export type ReturnableOutputType = 'uri' | 'base64';

export type compressionMethod = 'auto' | 'manual';

export type CompressorOptions = {
  /***
   * The compression method to use.
   */
  compressionMethod?: compressionMethod;
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
  /***
   * when user add `output:'png'` then by default compressed image will have transparent background, and quality will be ignored, if you wanna apply quality then you have to disablePngTransparency like `disablePngTransparency:true`, it will convert transparent background to white
   */
  disablePngTransparency?: boolean;
  /***
   * The output that will return to user.
   */
  returnableOutputType?: ReturnableOutputType;
  /***
   * it is callback, only trigger when we pass image url from server
   */
  downloadProgress?: (progress: number) => void;
  /***
   * Default:0, we uses when we use downloadProgress
   */
  progressDivider?: number;
};

const ImageCompressEventEmitter = new NativeEventEmitter(Compressor);

const NativeImage = Compressor;

type ImageType = {
  compress(value: string, options?: CompressorOptions): Promise<string>;
};

const Image: ImageType = {
  compress: async (value, options = {}) => {
    if (!value) {
      throw new Error(
        'Compression value is empty, please provide a value for compression.'
      );
    }

    let subscription: NativeEventSubscription;
    try {
      if (options?.downloadProgress) {
        const uuid = uuidv4();
        //@ts-ignore
        options.uuid = uuid;
        subscription = ImageCompressEventEmitter.addListener(
          'downloadProgress',
          (event: any) => {
            if (event.uuid === uuid) {
              options.downloadProgress &&
                options.downloadProgress(event.data.progress);
            }
          }
        );
      }

      const cleanData = value.replace(base64UrlRegex, '');
      return await NativeImage.image_compress(cleanData, options);
    } finally {
      // @ts-ignore
      if (subscription) {
        subscription.remove();
      }
    }
  },
};

export default Image;
