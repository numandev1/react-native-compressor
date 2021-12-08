import { NativeModules } from 'react-native';

NativeModules.VideoCompressor = {
  compress: jest.fn(),
};
