import { NativeEventEmitter, Platform } from 'react-native';
import type { NativeEventSubscription } from 'react-native';
import { Compressor } from '../Main';
const CompressEventEmitter = new NativeEventEmitter(Compressor);
import { uuidv4 } from '.';
export declare enum FileSystemUploadType {
  BINARY_CONTENT = 0,
  MULTIPART = 1,
}

export declare enum FileSystemSessionType {
  BACKGROUND = 0,
  FOREGROUND = 1,
}

export declare type HTTPResponse = {
  status: number;
  headers: Record<string, string>;
  body: string;
};

export declare type FileSystemAcceptedUploadHttpMethod =
  | 'POST'
  | 'PUT'
  | 'PATCH';

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
      subscription = CompressEventEmitter.addListener(
        'uploadProgress',
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
    const result = await Compressor.upload(fileUrl, {
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
