import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  image_compress(imagePath: string, optionMap: Object): Promise<string>;
  compress_audio(fileUrl: string, optionMap: Object): Promise<string>;
  generateFilePath(extension: string): Promise<string>;
  getRealPath(path: string, type: string): Promise<string>;
  getVideoMetaData(filePath: string): Promise<string>;
  getFileSize(filePath: string): Promise<string>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('Compressor');
