import { NativeEventEmitter } from 'react-native';
import type { NativeEventSubscription } from 'react-native';
import { Compressor } from '../Main';
import { uuidv4 } from '../utils';

export type compressionMethod = 'auto' | 'manual';
type videoCompresssionType = {
  bitrate?: number;
  maxSize?: number;
  compressionMethod?: compressionMethod;
  minimumFileSizeForCompress?: number;
  getCancellationId?: (cancellationId: string) => void;
  downloadProgress?: (progress: number) => void;
  /***
   * Default:0, we uses it when we use downloadProgress/onProgress
   */
  progressDivider?: number;
};

export type VideoCompressorType = {
  compress(
    fileUrl: string,
    options?: videoCompresssionType,
    onProgress?: (progress: number) => void
  ): Promise<string>;
  cancelCompression(cancellationId: string): void;
  activateBackgroundTask(onExpired?: (data: any) => void): Promise<any>;
  deactivateBackgroundTask(): Promise<any>;
};

const VideoCompressEventEmitter = new NativeEventEmitter(Compressor);

const NativeVideoCompressor = Compressor;

export const cancelCompression = (cancellationId: string) => {
  return NativeVideoCompressor.cancelCompression(cancellationId);
};

const Video: VideoCompressorType = {
  compress: async (
    fileUrl: string,
    options?: videoCompresssionType,
    onProgress?: (progress: number) => void
  ) => {
    const uuid = uuidv4();
    let subscription: NativeEventSubscription;
    let subscription2: NativeEventSubscription;

    try {
      if (onProgress) {
        subscription = VideoCompressEventEmitter.addListener(
          'videoCompressProgress',
          (event: any) => {
            if (event.uuid === uuid) {
              onProgress(event.data.progress);
            }
          }
        );
      }

      if (options?.downloadProgress) {
        //@ts-ignore
        subscription2 = VideoCompressEventEmitter.addListener(
          'downloadProgress',
          (event: any) => {
            if (event.uuid === uuid) {
              options.downloadProgress &&
                options.downloadProgress(event.data.progress);
            }
          }
        );
      }

      const modifiedOptions: {
        uuid: string;
        bitrate?: number;
        compressionMethod?: compressionMethod;
        maxSize?: number;
        minimumFileSizeForCompress?: number;
        progressDivider?: number;
      } = { uuid };
      if (options?.progressDivider)
        modifiedOptions.progressDivider = options?.progressDivider;
      if (options?.bitrate) modifiedOptions.bitrate = options?.bitrate;
      if (options?.compressionMethod) {
        modifiedOptions.compressionMethod = options?.compressionMethod;
      } else {
        modifiedOptions.compressionMethod = 'auto';
      }
      if (options?.maxSize) {
        modifiedOptions.maxSize = options?.maxSize;
      } else {
        modifiedOptions.maxSize = 640;
      }
      if (options?.minimumFileSizeForCompress !== undefined) {
        modifiedOptions.minimumFileSizeForCompress =
          options?.minimumFileSizeForCompress;
      }
      if (options?.getCancellationId) {
        options?.getCancellationId(uuid);
      }

      const result = await NativeVideoCompressor.compress(
        fileUrl,
        modifiedOptions
      );
      return result;
    } finally {
      // @ts-ignore
      if (subscription) {
        subscription.remove();
      }
      //@ts-ignore
      if (subscription2) {
        subscription2.remove();
      }
    }
  },
  cancelCompression,
  activateBackgroundTask(onExpired?) {
    if (onExpired) {
      const subscription: NativeEventSubscription =
        VideoCompressEventEmitter.addListener(
          'backgroundTaskExpired',
          (event: any) => {
            onExpired(event);
            if (subscription) {
              subscription.remove();
            }
          }
        );
    }
    return NativeVideoCompressor.activateBackgroundTask({});
  },
  deactivateBackgroundTask() {
    VideoCompressEventEmitter.removeAllListeners('backgroundTaskExpired');
    return NativeVideoCompressor.deactivateBackgroundTask({});
  },
} as VideoCompressorType;

export default Video;
