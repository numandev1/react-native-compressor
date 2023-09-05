import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-compressor' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

// @ts-expect-error
const isTurboModuleEnabled = global.__turboModuleProxy != null;

const CompressorModule = isTurboModuleEnabled
  ? require('./Spec/NativeCompressor').default
  : NativeModules.Compressor;

const Compressor = CompressorModule
  ? CompressorModule
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export { Compressor };
