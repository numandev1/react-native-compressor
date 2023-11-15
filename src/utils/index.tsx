/* eslint-disable no-bitwise */
import { Compressor } from '../Main';
import { Platform } from 'react-native';
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
  }
) => Promise<{
  path: string;
  size: number;
  mime: string;
  width: number;
  height: number;
}>;

type clearCacheType = (cacheDir?: string) => Promise<string>;

type getImageMetaDataType = (filePath: string) => Promise<{
  ImageWidth: number;
  ImageHeight: number;
  Orientation: number;
  size: number;
  extension: string;
  exif: { [key: string]: string };
}>;

type getVideoMetaDataType = (filePath: string) => Promise<{
  extension: string;
  size: number;
  duration: number;
  width: number;
  height: number;
}>;
type getRealPathType = (
  path: string,
  type: 'video' | 'image'
) => Promise<string>;

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
  return Compressor.getVideoMetaData(path);
};

const unifyMetaData = (exifResult: any) => {
  const output: any = {};
  const isIos = Platform.OS === 'ios';
  output.ImageWidth = isIos
    ? exifResult?.PixelWidth
    : parseInt(exifResult.ImageWidth);

  output.ImageHeight = isIos
    ? exifResult?.PixelHeight
    : parseInt(exifResult.ImageLength);

  output.Orientation = isIos
    ? exifResult.Orientation
    : parseInt(exifResult.Orientation);

  output.size = exifResult.size;
  output.extension = exifResult.extension;
  output.exif = exifResult;
  return output;
};

export const getImageMetaData: getImageMetaDataType = async (path: string) => {
  const result = await Compressor.getImageMetaData(path);
  return unifyMetaData(result);
};

export const createVideoThumbnail: createVideoThumbnailType = (
  fileUrl,
  options = {}
) => {
  return Compressor.createVideoThumbnail(fileUrl, options);
};

export const clearCache: clearCacheType = (cacheDir?: string) => {
  return Compressor.clearCache(cacheDir);
};

const isValidUrl = (url: string) =>
  /^(?:\w+:)?\/\/([^\s\.]+\.\S{2}|localhost[\:?\d]*)\S*$/.test(url);

const getFullFilename = (path: string | null) => {
  if (typeof path === 'string') {
    let _path = path;

    // In case of remote media, check if the url would be valid one
    if (path.includes('http') && !isValidUrl(path)) {
      return INCORRECT_INPUT_PATH;
    }

    // In case of url, check if it ends with "/" and do not consider it furthermore
    if (_path[_path.length - 1] === '/')
      _path = _path.substring(0, path.length - 1);

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
  return typeof path === 'string'
    ? path?.split(':/')?.[0]?.includes('http')
    : null;
};

export const getDetails = (
  mediaFullPath: string,
  extesnion: 'mp3' | 'mp4' = 'mp3'
): Promise<any | null> => {
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

export const uuidv4 = () => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r =
        (parseFloat(
          '0.' +
            Math.random().toString().replace('0.', '') +
            new Date().getTime()
        ) *
          16) |
        0,
      // eslint-disable-next-line eqeqeq
      v = c == 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};
export * from './Downloader';
export * from './Uploader';
