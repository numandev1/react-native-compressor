import Video, { backgroundUpload } from './Video';
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
} from './utils';

export {
  Video,
  Audio,
  Image,
  backgroundUpload,
  //type

  getDetails,
  uuidv4,
  generateFilePath,
  getRealPath,
  getVideoMetaData,
  getFileSize,
};
export type { VideoCompressorType };
export default {
  Video,
  Audio,
  Image,
  backgroundUpload,
  getDetails,
  uuidv4,
  generateFilePath,
  getRealPath,
  getVideoMetaData,
  getFileSize,
};
