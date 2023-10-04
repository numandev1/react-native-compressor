import Video from './Video';
import type { VideoCompressorType } from './Video';
import Audio from './Audio';
import Image from './Image';
import {
  getDetails,
  uuidv4,
  generateFilePath,
  getRealPath,
  getVideoMetaData,
  getFileSize,
  backgroundUpload,
  createVideoThumbnail,
  download,
  clearCache,
  UploadType,
  UploaderHttpMethod,
} from './utils';

export {
  Video,
  Audio,
  Image,
  backgroundUpload,
  download,
  //type
  getDetails,
  uuidv4,
  generateFilePath,
  getRealPath,
  getVideoMetaData,
  createVideoThumbnail,
  clearCache,
  getFileSize,
  UploadType,
  UploaderHttpMethod,
};
export type { VideoCompressorType };
export default {
  Video,
  Audio,
  Image,
  getDetails,
  uuidv4,
  generateFilePath,
  getRealPath,
  getVideoMetaData,
  getFileSize,
  backgroundUpload,
  createVideoThumbnail,
  clearCache,
  download,
  UploadType,
  UploaderHttpMethod,
};
