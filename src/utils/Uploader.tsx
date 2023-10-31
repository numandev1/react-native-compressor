import { NativeEventEmitter, Platform } from 'react-native';
import type { NativeEventSubscription } from 'react-native';
import { Compressor } from '../Main';
const CompressEventEmitter = new NativeEventEmitter(Compressor);
import { uuidv4 } from './index';
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
  httpMethod?: UploaderHttpMethod | HttpMethod;
};

export const backgroundUpload = async (
  url: string,
  fileUrl: string,
  options: UploaderOptions,
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
      uploadType: options.uploadType,
      ...options,
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
