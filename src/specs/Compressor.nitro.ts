import type { AnyMap, HybridObject } from 'react-native-nitro-modules';

export interface VideoThumbnailResult {
  path: string;
  size: number;
  mime: string;
  width: number;
  height: number;
}

/**
 * Nitro HybridObject spec for the single native `Compressor` module.
 *
 * Notes on the migration from the old TurboModule spec:
 * - Progress is delivered through callback parameters (`onProgress` / `onDownloadProgress` /
 *   `onExpired`) instead of `NativeEventEmitter` events. Callbacks can't live inside an `AnyMap`,
 *   so any progress callback that used to be nested in the options object is now a top-level param.
 * - `optionMap`/`options` stay untyped (`AnyMap`) and are parsed natively, exactly as before.
 * - `getImageMetaData` / `getVideoMetaData` / `upload` resolve objects at runtime (EXIF map /
 *   metadata map / `{status,headers,body}`), so they return `AnyMap` — the old `Promise<string>`
 *   typing was lossy.
 * - `uuid` is still threaded inside the options map for cancellation (`cancelCompression` /
 *   `cancelUpload`) and for routing native progress emissions to the registered callback.
 */
export interface Compressor extends HybridObject<{ ios: 'swift'; android: 'kotlin' }> {
  // Image
  image_compress(imagePath: string, optionMap: AnyMap, onDownloadProgress?: (progress: number) => void): Promise<string>;
  getImageMetaData(filePath: string): Promise<AnyMap>;

  // Video
  compress(
    fileUrl: string,
    optionMap: AnyMap,
    onProgress?: (progress: number) => void,
    onDownloadProgress?: (progress: number) => void,
  ): Promise<string>;
  cancelCompression(uuid: string): void;
  getVideoMetaData(filePath: string): Promise<AnyMap>;
  activateBackgroundTask(options: AnyMap, onExpired?: () => void): Promise<string>;
  deactivateBackgroundTask(options: AnyMap): Promise<string>;

  // Audio
  compress_audio(fileUrl: string, optionMap: AnyMap): Promise<string>;

  // Upload / Download
  upload(fileUrl: string, options: AnyMap, onProgress?: (written: number, total: number) => void): Promise<AnyMap>;
  cancelUpload(uuid: string, shouldCancelAll: boolean): void;
  download(fileUrl: string, options: AnyMap, onProgress?: (progress: number) => void): Promise<string>;

  // Others
  generateFilePath(fileExtension: string): Promise<string>;
  getRealPath(path: string, type: string): Promise<string>;
  getFileSize(filePath: string): Promise<string>;
  createVideoThumbnail(fileUrl: string, options: AnyMap): Promise<VideoThumbnailResult>;
  clearCache(cacheDir?: string): Promise<string>;
}
