import { RNFFmpeg } from 'react-native-ffmpeg';

import {
  AUDIO_BITRATE,
  AudioType,
  DEFAULT_COMPRESS_AUDIO_OPTIONS,
  defaultResultType,
  checkUrlAndOptions,
  getDetails,
} from './utils';

const Audio: AudioType = {
  compress: (url, options = DEFAULT_COMPRESS_AUDIO_OPTIONS) => {
    return new Promise(async (resolve: any, reject) => {
      try {
        const checkUrlAndOptionsResult: defaultResultType =
          await checkUrlAndOptions(url, options);
        if (!checkUrlAndOptionsResult.isCorrect) {
          reject(checkUrlAndOptionsResult.message);
          return;
        } else {
          // Get resulting output file path
          const { outputFilePath } = checkUrlAndOptionsResult;

          // Get media details
          const mediaDetails = await getDetails(url).catch(() => null);

          // Initialize bitrate
          let bitrate = DEFAULT_COMPRESS_AUDIO_OPTIONS.bitrate;

          if (mediaDetails && mediaDetails.bitrate) {
            // Check and return the appropriate bitrate according to quality expected
            for (let i = 0; i < AUDIO_BITRATE.length; i++) {
              // Check a particular bitrate to return its nearest lower according to quality
              if (mediaDetails.bitrate > AUDIO_BITRATE[i]) {
                if (i + 2 < AUDIO_BITRATE.length) {
                  if (options.quality === 'low')
                    bitrate = `${AUDIO_BITRATE[i + 2]}k`;
                  else if (options.quality === 'medium')
                    bitrate = `${AUDIO_BITRATE[i + 1]}k`;
                  else bitrate = `${AUDIO_BITRATE[i]}k`;
                } else if (i + 1 < AUDIO_BITRATE.length) {
                  if (options.quality === 'low')
                    bitrate = `${AUDIO_BITRATE[i + 1]}k`;
                  else bitrate = `${AUDIO_BITRATE[i]}k`;
                } else bitrate = `${AUDIO_BITRATE[i]}k`;
                break;
              }

              // Check if the matching bitrate is the last in the array
              if (
                mediaDetails.bitrate <= AUDIO_BITRATE[AUDIO_BITRATE.length - 1]
              ) {
                bitrate = `${AUDIO_BITRATE[AUDIO_BITRATE.length - 1]}k`;
                break;
              }
            }
          }

          // group command from calculated values
          const cmd = [
            '-i',
            `"${url}"`,
            '-b:a',
            options.bitrate ? options.bitrate : bitrate,
            '-map',
            'a',
            `"${outputFilePath}"`,
          ];

          // Execute command
          RNFFmpeg.execute(cmd.join(' '))
            .then((result) => resolve({ outputFilePath, rc: result }))
            .catch((error) => reject(error));
        }
      } catch (error) {
        reject(error.message);
      }
    });
  },
};

export default Audio;
