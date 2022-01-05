import { NativeModules } from 'react-native';
import {
  AUDIO_BITRATE,
  AudioType,
  DEFAULT_COMPRESS_AUDIO_OPTIONS,
  defaultResultType,
  checkUrlAndOptions,
} from '../utils';
const NativeAudio = NativeModules.Compressor;

const Audio: AudioType = {
  compress: async (url, options = DEFAULT_COMPRESS_AUDIO_OPTIONS) => {
    try {
      const checkUrlAndOptionsResult: defaultResultType =
        await checkUrlAndOptions(url, options);
      if (!checkUrlAndOptionsResult.isCorrect) {
        throw checkUrlAndOptionsResult.message;
      } else {
        // Get resulting output file path

        // Get media details
        // const mediaDetails: any = await getDetails(url).catch(() => null);
        const mediaDetails: any = {
          bitrate: 0,
        };

        // Initialize bitrate
        let bitrate: any = DEFAULT_COMPRESS_AUDIO_OPTIONS.bitrate;

        if (mediaDetails && mediaDetails.bitrate) {
          // Check and return the appropriate bitrate according to quality expected
          for (let i = 0; i < AUDIO_BITRATE.length; i++) {
            // Check a particular bitrate to return its nearest lower according to quality
            if (mediaDetails.bitrate > AUDIO_BITRATE[i]) {
              if (i + 2 < AUDIO_BITRATE.length) {
                if (options.quality === 'low') bitrate = AUDIO_BITRATE[i + 2];
                else if (options.quality === 'medium')
                  bitrate = AUDIO_BITRATE[i + 1];
                else bitrate = AUDIO_BITRATE[i];
              } else if (i + 1 < AUDIO_BITRATE.length) {
                if (options.quality === 'low') bitrate = AUDIO_BITRATE[i + 1];
                else bitrate = AUDIO_BITRATE[i];
              } else bitrate = AUDIO_BITRATE[i];
              break;
            }

            // Check if the matching bitrate is the last in the array
            if (
              mediaDetails.bitrate <= AUDIO_BITRATE[AUDIO_BITRATE.length - 1]
            ) {
              bitrate = AUDIO_BITRATE[AUDIO_BITRATE.length - 1];
              break;
            }
          }
        }

        return NativeAudio.compress_audio(url, {
          bitrate,
          quality: options.quality,
        });
      }
    } catch (error: any) {
      throw error.message;
    }
  },
};

export default Audio;
