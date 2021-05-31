import { NativeModules } from 'react-native';

type ImageType = {
  compress(url: string, options?: {}): Promise<number>;
};

const {} = NativeModules;

const Image = {
  compress: {},
};

export default Image as ImageType;
