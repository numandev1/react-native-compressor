import {
  NativeModules,
  NativeEventEmitter,
  Platform,
  NativeEventSubscription,
} from 'react-native';
import { uuidv4 } from '../utils';

export declare enum FileSystemUploadType {
  BINARY_CONTENT = 0,
  MULTIPART = 1,
}

export declare type FileSystemAcceptedUploadHttpMethod =
  | 'POST'
  | 'PUT'
  | 'PATCH';
export type compressionMethod = 'auto' | 'manual';
type videoCompresssionType = {
  bitrate?: number;
  maxSize?: number;
  compressionMethod?: compressionMethod;
  minimumFileSizeForCompress?: number;
  getCancellationId?: (cancellationId: string) => void;
};

export declare enum FileSystemSessionType {
  BACKGROUND = 0,
  FOREGROUND = 1,
}

export declare type HTTPResponse = {
  status: number;
  headers: Record<string, string>;
  body: string;
};

export declare type FileSystemUploadOptions = (
  | {
      uploadType?: FileSystemUploadType.BINARY_CONTENT;
    }
  | {
      uploadType: FileSystemUploadType.MULTIPART;
      fieldName?: string;
      mimeType?: string;
      parameters?: Record<string, string>;
    }
) & {
  headers?: Record<string, string>;
  httpMethod?: FileSystemAcceptedUploadHttpMethod;
  sessionType?: FileSystemSessionType;
};

export type VideoCompressorType = {
  compress(
    fileUrl: string,
    options?: videoCompresssionType,
    onProgress?: (progress: number) => void
  ): Promise<string>;
  cancelCompression(cancellationId: string): void;
  backgroundUpload(
    url: string,
    fileUrl: string,
    options: FileSystemUploadOptions,
    onProgress?: (writtem: number, total: number) => void
  ): Promise<any>;
  activateBackgroundTask(onExpired?: (data: any) => void): Promise<any>;
  deactivateBackgroundTask(): Promise<any>;
};

const VideoCompressEventEmitter = new NativeEventEmitter(
  NativeModules.VideoCompressor
);

const NativeVideoCompressor = NativeModules.VideoCompressor;

export const backgroundUpload = async (
  url: string,
  fileUrl: string,
  options: FileSystemUploadOptions,
  onProgress?: (writtem: number, total: number) => void
): Promise<any> => {
  const uuid = uuidv4();
  let subscription: NativeEventSubscription;
  try {
    if (onProgress) {
      subscription = VideoCompressEventEmitter.addListener(
        'VideoCompressorProgress',
        (event: any) => {
          if (event.uuid === uuid) {
            onProgress(event.data.written, event.data.total);
          }
        }
      );
    }
    if (Platform.OS === 'android' && fileUrl.includes('file://')) {
      fileUrl = fileUrl.replace('file://', '');
    }
    const result = await NativeVideoCompressor.upload(fileUrl, {
      uuid,
      method: options.httpMethod,
      headers: options.headers,
      url,
    });
    return result;
  } finally {
    // @ts-ignore
    if (subscription) {
      subscription.remove();
    }
  }
};

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
      const modifiedOptions: {
        uuid: string;
        bitrate?: number;
        compressionMethod?: compressionMethod;
        maxSize?: number;
        minimumFileSizeForCompress?: number;
      } = { uuid };
      if (options?.bitrate) modifiedOptions.bitrate = options?.bitrate;
      if (options?.compressionMethod) {
        modifiedOptions.compressionMethod = options?.compressionMethod;
      } else {
        modifiedOptions.compressionMethod = 'manual';
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
    }
  },
  backgroundUpload,
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
