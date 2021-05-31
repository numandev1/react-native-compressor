import { NativeModules } from 'react-native';

type CompressorType = {
  compress(fileUrl: string, options: any): Promise<number>;
};

const { Compressor } = NativeModules;

export default Compressor as CompressorType;
