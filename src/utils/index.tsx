import { Compressor } from '../Main';
import { toNativeOptions } from './helpers';
type qualityType = 'low' | 'medium' | 'high';
const INCORRECT_INPUT_PATH = 'Incorrect input path. Please provide a valid one';

type audioCompresssionType = {
  // bitrate?: number;
  quality?: qualityType;
  bitrate?: number; // 64000-320000
  samplerate?: number; // 44100 - 192000
  channels?: number; // Typically 1 or 2
};

export type defaultResultType = {
  isCorrect: boolean;
  message: string;
};

export const DEFAULT_COMPRESS_AUDIO_OPTIONS: audioCompresssionType = {
  // bitrate: 96,
  quality: 'medium',
};

export type AudioType = {
  compress(value: string, options?: audioCompresssionType): Promise<string>;
};

type createVideoThumbnailType = (
  fileUrl: string,
  options?: {
    headers?: { [key: string]: string };
    quality?: number;
  },
) => Promise<{
  path: string;
  size: number;
  mime: string;
  width: number;
  height: number;
}>;

type clearCacheType = (cacheDir?: string) => Promise<string>;

export type ColorTransfer = 'sdr' | 'hlg' | 'pq' | 'linear';

export type VideoMetaData = {
  extension: string;
  size: number;
  duration: number;
  /** Stored track dimensions, before rotation. */
  width: number;
  height: number;
  /** Dimensions as the video is meant to be shown, with `rotation` applied. */
  displayWidth: number;
  displayHeight: number;
  /** `0`, `90`, `180` or `270`. */
  rotation: number;
  hasVideo: boolean;
  hasAudio: boolean;
  isHDR: boolean;
  colorTransfer: ColorTransfer;
  fileName: string;

  /** Bits per second, video track only. */
  bitrate?: number;
  frameRate?: number;
  frameCount?: number;
  /** Normalized lowercase name, e.g. `"h264"`, `"hevc"`, `"av1"`, `"vp9"`. */
  codec?: string;
  trackCount?: number;
  /** Raw ISO-6709 string. */
  location?: string;
  /** Signed decimal degrees. */
  latitude?: number;
  longitude?: number;
  audioBitrate?: number;
  audioSampleRate?: number;
  audioChannels?: number;

  // Platform extras — one platform only, always optional.
  /** iOS only. `AVAsset` common metadata, merged as-is. */
  albumName?: string;
  artist?: string;
  comment?: string;
  copyrights?: string;
  creationDate?: string;
  date?: string;
  encodedby?: string;
  genre?: string;
  language?: string;
  lastModifiedDate?: string;
  performer?: string;
  publisher?: string;
  title?: string;
};

export type ImageMetaData = {
  /** True pixel dimensions from the decoder, never the EXIF tag. */
  width: number;
  height: number;
  /** Dimensions after `orientation` is applied — swapped when orientation is 5–8. */
  displayWidth: number;
  displayHeight: number;
  /** `0`, `90`, `180` or `270`. */
  rotation: number;
  isMirrored: boolean;
  /** Raw EXIF value 1–8. Missing or `0` is treated as `1`. */
  orientation: number;

  size: number;
  extension: string;
  mimeType: string;
  hasAlpha: boolean;
  fileName: string;

  isAnimated?: boolean;
  frameCount?: number;
  bitDepth?: number;
  colorSpace?: string;
  isHDR?: boolean;
  dpiX?: number;
  dpiY?: number;

  /** Signed decimal degrees. */
  latitude?: number;
  longitude?: number;
  /** Metres, negative below sea level. */
  altitude?: number;

  make?: string;
  model?: string;
  lensModel?: string;
  software?: string;
  iso?: number;
  /** Seconds, as a number (`0.0166`), not the `"1/60"` string. */
  exposureTime?: number;
  focalLength?: number;
  flash?: boolean;
};

type getImageMetaDataType = (filePath: string) => Promise<ImageMetaData>;

type getVideoMetaDataType = (filePath: string) => Promise<VideoMetaData>;
type getRealPathType = (path: string, type: 'video' | 'image') => Promise<string>;

export const generateFilePath: any = (extension: string) => {
  return new Promise((resolve, reject) => {
    Compressor.generateFilePath(extension)
      .then((result: any) => resolve('file://' + result))
      .catch((error: any) => reject(error));
  });
};

export const getRealPath: getRealPathType = (path, type = 'video') => {
  return Compressor.getRealPath(path, type);
};

export const getVideoMetaData: getVideoMetaDataType = (path: string) => {
  return Compressor.getVideoMetaData(path) as ReturnType<getVideoMetaDataType>;
};

export const getImageMetaData: getImageMetaDataType = (path: string) => {
  return Compressor.getImageMetaData(path) as ReturnType<getImageMetaDataType>;
};

export const createVideoThumbnail: createVideoThumbnailType = (fileUrl, options = {}) => {
  return Compressor.createVideoThumbnail(fileUrl, toNativeOptions(options));
};

export const clearCache: clearCacheType = (cacheDir?: string) => {
  return Compressor.clearCache(cacheDir);
};

const isValidUrl = (url: string) => /^(?:\w+:)?\/\/([^\s\.]+\.\S{2}|localhost[\:?\d]*)\S*$/.test(url);

const getFullFilename = (path: string | null) => {
  if (typeof path === 'string') {
    let _path = path;

    // In case of remote media, check if the url would be valid one
    if (path.includes('http') && !isValidUrl(path)) {
      return INCORRECT_INPUT_PATH;
    }

    // In case of url, check if it ends with "/" and do not consider it furthermore
    if (_path[_path.length - 1] === '/') _path = _path.substring(0, path.length - 1);

    const array = _path.split('/');
    return array.length > 1 ? array[array.length - 1] : INCORRECT_INPUT_PATH;
  }
  return INCORRECT_INPUT_PATH;
};

const isFileNameError = (filename: string) => {
  return filename === INCORRECT_INPUT_PATH;
};

const getFilename = (path: string | null) => {
  const fullFilename: string | undefined = getFullFilename(path);
  if (fullFilename && !isFileNameError(fullFilename)) {
    const array = fullFilename.split('.');
    return array.length > 1 ? array.slice(0, -1).join('') : array.join('');
  }
  return fullFilename;
};

const isRemoteMedia = (path: string | null) => {
  return typeof path === 'string' ? path?.split(':/')?.[0]?.includes('http') : null;
};

export const getDetails = (mediaFullPath: string, extesnion: 'mp3' | 'mp4' = 'mp3'): Promise<any | null> => {
  return new Promise(async (resolve, reject) => {
    try {
      // Since we used "-v error", a work around is to call first this command before the following
      const result: any = {};
      if (result !== 0) {
        throw new Error('Failed to execute command');
      }

      // get the output result of the command
      // example of output {"programs": [], "streams": [{"width": 640,"height": 360}], "format": {"size": "15804433"}}
      let mediaInfo: any = await {};
      mediaInfo = JSON.parse(mediaInfo);

      // execute second command
      const mediaInformation: any = await {};

      // treat both results
      mediaInformation.filename = getFilename(mediaFullPath);
      mediaInformation.bitrate = mediaInformation.getMediaProperties().bit_rate;
      mediaInformation.extension = extesnion;
      mediaInformation.isRemoteMedia = isRemoteMedia(mediaFullPath);
      mediaInformation.size = Number(mediaInfo.format.size);

      resolve(mediaInformation);
    } catch (e) {
      reject(e);
    }
  });
};

export const getFileSize = async (filePath: string): Promise<string> => {
  return Compressor.getFileSize(filePath);
};

export * from './Downloader';
export * from './Uploader';
export * from './helpers';
