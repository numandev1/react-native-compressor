import { NativeModules } from 'react-native';

type AudioType = {
  compress(url: string, options?: {}): Promise<number>;
};

const {} = NativeModules;

const Audio = {
  compress: {},
};

export default Audio as AudioType;
