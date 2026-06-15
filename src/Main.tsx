import { Platform } from 'react-native';
import { NitroModules } from 'react-native-nitro-modules';

import type { Compressor as CompressorSpec } from './specs/Compressor.nitro';

const LINKING_ERROR =
  `The package 'react-native-compressor' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n' +
  '- You have installed `react-native-nitro-modules`\n';

const createCompressor = (): CompressorSpec => {
  try {
    return NitroModules.createHybridObject<CompressorSpec>('Compressor');
  } catch {
    throw new Error(LINKING_ERROR);
  }
};

const Compressor = createCompressor();

export { Compressor };
