import { NativeModules, NativeEventEmitter, Platform } from 'react-native';
import { v4 as uuidv4 } from 'uuid';
import Upload from 'react-native-background-upload';

export declare enum FileSystemUploadType {
  BINARY_CONTENT = 0,
  MULTIPART = 1,
}

export declare type FileSystemAcceptedUploadHttpMethod =
  | 'POST'
  | 'PUT'
  | 'PATCH';

type videoCompresssionType = { bitrate?: number };

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

const Video: VideoCompressorType = {
  compress: async (
    fileUrl: string,
    options?: { bitrate?: number },
    onProgress?: (progress: number) => void
  ) => {
    const uuid = uuidv4();
    let subscription = null;
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
      const modifiedOptions: { uuid: string; bitrate?: number } = { uuid };
      if (options?.bitrate) modifiedOptions.bitrate = options?.bitrate;
      const result = await NativeVideoCompressor.compress(
        fileUrl,
        modifiedOptions
      );
      return result;
    } finally {
      if (subscription) {
        VideoCompressEventEmitter.removeSubscription(subscription);
      }
    }
  },
  backgroundUpload: async (url, fileUrl, options, onProgress) => {
    if (!NativeVideoCompressor || !NativeVideoCompressor.upload) {
      //check if expo can upload the file
      const scheme = fileUrl.split('://')[0];
      if (['file'].includes(scheme)) {
        return Upload.startUpload({
          url: url,
          path: fileUrl,
          method: options.httpMethod || 'PUT',
          type: 'raw',
        })
          .then((uploadId) => {
            console.log('Upload started');
            Upload.addListener(
              'progress',
              uploadId,
              (data: any) =>
                onProgress && onProgress(parseInt(data.progress), 100)
            );
            Upload.addListener('error', uploadId, (data) => {
              throw data.error;
            });
          })
          .catch((err) => {
            throw err;
          });
        //fallback to use expo upload
        // return FileSystem.uploadAsync(url, fileUrl, options);
      } else {
        // Use poor old fetch
        const fileRes = await fetch(fileUrl);
        const fileBody = await fileRes.blob();

        return fetch(url, {
          method: options.httpMethod,
          body: fileBody,
          headers: options.headers,
        });
      }
    }
    const uuid = uuidv4();
    let subscription = null;
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
      if (subscription) {
        VideoCompressEventEmitter.removeSubscription(subscription);
      }
    }
  },
  activateBackgroundTask(onExpired?) {
    if (onExpired) {
      const subscription = VideoCompressEventEmitter.addListener(
        'backgroundTaskExpired',
        (event: any) => {
          onExpired(event);
          VideoCompressEventEmitter.removeSubscription(subscription);
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
