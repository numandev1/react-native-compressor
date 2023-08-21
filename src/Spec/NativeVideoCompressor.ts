import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  compress(fileUrl: string, optionMap: Object): Promise<string>;
  cancelCompression(uuid: string): void;
  upload(fileUrl: string, options: Object): Promise<string>;
  activateBackgroundTask(options: Object): Promise<string>;
  deactivateBackgroundTask(options: Object): Promise<string>;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('VideoCompressor');
