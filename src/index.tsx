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
  download,
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
  getFileSize,
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
  download,
};
