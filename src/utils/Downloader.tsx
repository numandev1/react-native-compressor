import { Platform } from 'react-native';
import { Compressor } from '../Main';
import { toNativeOptions, uuidv4 } from './helpers';

export const download = async (fileUrl: string, downloadProgress?: (progress: number) => void, progressDivider?: number): Promise<any> => {
  const uuid = uuidv4();
  if (Platform.OS === 'android' && fileUrl.includes('file://')) {
    fileUrl = fileUrl.replace('file://', '');
  }
  const result = await Compressor.download(fileUrl, toNativeOptions({ uuid, progressDivider }), downloadProgress);
  return result;
};
