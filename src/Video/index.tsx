import { NativeModules, NativeEventEmitter } from 'react-native';
import { v4 as uuidv4 } from 'uuid';
import * as FileSystem from 'expo-file-system';

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

export type VideoUploadType = {
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
  NativeModules.Compressor
);

const NativeVideoUpload = NativeModules.Compressor;

const Video: VideoUploadType = {
  compress: async (
    fileUrl: string,
    options?: { bitrate?: number },
    onProgress?: (progress: number) => void
  ) => {
    if (!NativeVideoUpload || !NativeVideoUpload.video_compress) {
      return fileUrl; //For android passthrough
    }
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
      const result = await NativeVideoUpload.video_compress(
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
    if (!NativeVideoUpload || !NativeVideoUpload.video_upload) {
      //check if expo can upload the file
      const scheme = fileUrl.split('://')[0];
      if (['file'].includes(scheme)) {
        //fallback to use expo upload
        return FileSystem.uploadAsync(url, fileUrl, options);
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
          'videoUploadProgress',
          (event: any) => {
            if (event.uuid === uuid) {
              onProgress(event.data.written, event.data.total);
            }
          }
        );
      }
      const result = await NativeVideoUpload.video_upload(fileUrl, {
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
    if (!NativeVideoUpload || !NativeVideoUpload.video_activateBackgroundTask) {
      return Promise.resolve('1'); //For android passthrough
    }
    if (onExpired) {
      const subscription = VideoCompressEventEmitter.addListener(
        'backgroundTaskExpired',
        (event: any) => {
          onExpired(event);
          VideoCompressEventEmitter.removeSubscription(subscription);
        }
      );
    }
    return NativeVideoUpload.activateBackgroundTask({});
  },
  deactivateBackgroundTask() {
    if (
      !NativeVideoUpload ||
      !NativeVideoUpload.video_deactivateBackgroundTask
    ) {
      return Promise.resolve('1'); //For android passthrough
    }
    VideoCompressEventEmitter.removeAllListeners('backgroundTaskExpired');
    return NativeVideoUpload.deactivateBackgroundTask({});
  },
} as VideoUploadType;

export default Video;
