import { androidEmulator, androidPlatform } from '@react-native-harness/platform-android';
import { applePlatform, appleSimulator } from '@react-native-harness/platform-apple';

const androidDevice = process.env.RN_HARNESS_ANDROID_DEVICE ?? 'Pixel_9a';
const iosDevice = process.env.RN_HARNESS_IOS_DEVICE ?? 'iPhone 17 Pro';
const iosVersion = process.env.RN_HARNESS_IOS_VERSION ?? '26.4';

const config = {
  entryPoint: './index.js',
  appRegistryComponentName: 'BareExample',
  defaultRunner: 'android',
  metroPort: Number(process.env.RN_HARNESS_METRO_PORT ?? 8082),
  bridgeTimeout: Number(process.env.RN_HARNESS_BRIDGE_TIMEOUT ?? 30000),
  bundleStartTimeout: Number(process.env.RN_HARNESS_BUNDLE_START_TIMEOUT ?? 30000),
  forwardClientLogs: true,
  runners: [
    androidPlatform({
      name: 'android',
      device: androidEmulator(androidDevice),
      bundleId: 'com.bareexample',
    }),
    applePlatform({
      name: 'ios',
      device: appleSimulator(iosDevice, iosVersion),
      bundleId: 'org.reactjs.native.example.BareExample',
    }),
  ],
};

export default config;
