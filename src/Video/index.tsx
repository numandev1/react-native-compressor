import { NativeModules } from 'react-native';

type VideoType = {
  compress(url: string, options?: {}): Promise<number>;
};

const {} = NativeModules;

const Video = {
  compress: {},
};

export default Video as VideoType;
