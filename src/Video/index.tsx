import { Compressor } from '../Main';
import { toNativeOptions, uuidv4 } from '../utils';

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
  /***
   * When true, the audio track is removed entirely from the output video.
   * Default: false
   */
  stripAudio?: boolean;
};

export type VideoCompressorType = {
  compress(fileUrl: string, options?: videoCompresssionType, onProgress?: (progress: number) => void): Promise<string>;
  cancelCompression(cancellationId: string): void;
  activateBackgroundTask(onExpired?: (data: any) => void): Promise<any>;
  deactivateBackgroundTask(): Promise<any>;
};

const NativeVideoCompressor = Compressor;

export const cancelCompression = (cancellationId: string) => {
  return NativeVideoCompressor.cancelCompression(cancellationId);
};

const Video: VideoCompressorType = {
  compress: async (fileUrl: string, options?: videoCompresssionType, onProgress?: (progress: number) => void) => {
    const uuid = uuidv4();

    const modifiedOptions: Record<string, unknown> = { uuid };
    if (options?.progressDivider) modifiedOptions.progressDivider = options?.progressDivider;
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
      modifiedOptions.minimumFileSizeForCompress = options?.minimumFileSizeForCompress;
    }
    if (options?.stripAudio) {
      modifiedOptions.stripAudio = options.stripAudio;
    }
    if (options?.getCancellationId) {
      options?.getCancellationId(uuid);
    }

    return NativeVideoCompressor.compress(fileUrl, toNativeOptions(modifiedOptions), onProgress, options?.downloadProgress);
  },
  cancelCompression,
  activateBackgroundTask(onExpired?) {
    return NativeVideoCompressor.activateBackgroundTask({}, onExpired ? () => onExpired(undefined) : undefined);
  },
  deactivateBackgroundTask() {
    return NativeVideoCompressor.deactivateBackgroundTask({});
  },
} as VideoCompressorType;

export default Video;
