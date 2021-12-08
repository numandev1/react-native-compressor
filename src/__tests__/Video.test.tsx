import Video from '../Video/index';
import { NativeModules } from 'react-native';

jest.mock(
  '../../node_modules/react-native/Libraries/EventEmitter/NativeEventEmitter'
);

jest.mock('uuid', () => ({
  v4: () => 'foouuid',
}));

describe('Video Compression', () => {
  it('Includes the minimumFileSizeForCompress option even if it is 0', () => {
    Video.compress('fooUrl.mp4', { minimumFileSizeForCompress: 0 });

    expect(NativeModules.VideoCompressor.compress).toHaveBeenCalledWith(
      'fooUrl.mp4',
      {
        compressionMethod: 'manual',
        minimumFileSizeForCompress: 0,
        maxSize: 640,
        uuid: 'foouuid',
      }
    );
  });
});
