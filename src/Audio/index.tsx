import { Compressor } from '../Main';

import { DEFAULT_COMPRESS_AUDIO_OPTIONS } from '../utils';
import type { AudioType } from '../utils';
const NativeAudio = Compressor;

const Audio: AudioType = {
  compress: async (url, options = DEFAULT_COMPRESS_AUDIO_OPTIONS) => {
    try {
      return NativeAudio.compress_audio(url, options);
    } catch (error: any) {
      throw error.message;
    }
  },
};

export default Audio;
