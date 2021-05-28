import { NativeModules } from 'react-native';

type CompressorType = {
  multiply(a: number, b: number): Promise<number>;
};

const { Compressor } = NativeModules;

export default Compressor as CompressorType;
