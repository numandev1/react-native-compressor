// These unit tests validate the JavaScript wrapper contract. The native
// Compressor module is mocked, so real media decoding must be smoke-tested
// in an example app on a simulator or device.
const mockCompressor = {
  image_compress: jest.fn(),
  compress: jest.fn(),
  cancelCompression: jest.fn(),
  activateBackgroundTask: jest.fn(),
  deactivateBackgroundTask: jest.fn(),
  compress_audio: jest.fn(),
  upload: jest.fn(),
  cancelUpload: jest.fn(),
  download: jest.fn(),
  generateFilePath: jest.fn(),
  getRealPath: jest.fn(),
  getVideoMetaData: jest.fn(),
  getImageMetaData: jest.fn(),
  createVideoThumbnail: jest.fn(),
  clearCache: jest.fn(),
  getFileSize: jest.fn(),
};

const localVideoUri = 'file:///tmp/react-native-compressor/input-video.mp4';
const localImageUri = 'file:///tmp/react-native-compressor/input-image.jpg';
const localAudioUri = 'file:///tmp/react-native-compressor/input-audio.wav';

// The wrapper resolves the native module through Nitro instead of NativeModules.
jest.mock('react-native-nitro-modules', () => ({
  NitroModules: {
    createHybridObject: jest.fn(() => mockCompressor),
  },
}));

jest.mock('react-native', () => ({
  Platform: {
    OS: 'ios',
    select: jest.fn((options: Record<string, string>) => options.ios ?? options.default),
  },
}));

describe('react-native-compressor JS wrapper API', () => {
  let api: typeof import('../src');
  let reactNative: typeof import('react-native');

  beforeEach(() => {
    jest.clearAllMocks();
    reactNative = require('react-native');
    reactNative.Platform.OS = 'ios';
    api = require('../src');
  });

  it('exports all public modules and helper functions', () => {
    const publicRuntimeExports = [
      'Audio',
      'Image',
      'UploadType',
      'UploaderHttpMethod',
      'Video',
      'backgroundUpload',
      'cancelUpload',
      'clearCache',
      'createVideoThumbnail',
      'download',
      'generateFilePath',
      'getDetails',
      'getFileSize',
      'getImageMetaData',
      'getRealPath',
      'getVideoMetaData',
      'uuidv4',
    ];

    expect(Object.keys(api.default).sort()).toEqual(publicRuntimeExports.sort());
    publicRuntimeExports.forEach((exportName) => {
      expect(api.default[exportName as keyof typeof api.default]).toBe(api[exportName as keyof typeof api]);
    });
    expect(api.UploadType.MULTIPART).toBe(1);
    expect(api.UploaderHttpMethod.PATCH).toBe('PATCH');
  });

  it('compresses images, strips base64 headers, and forwards download progress', async () => {
    mockCompressor.image_compress.mockImplementation(async (_value, _options, onDownloadProgress) => {
      onDownloadProgress?.(55);
      return 'file://compressed-image.jpg';
    });
    const downloadProgress = jest.fn();

    await expect(
      api.Image.compress('data:image/png;charset=utf-8;base64,abc123', {
        quality: 0.7,
        downloadProgress,
      }),
    ).resolves.toBe('file://compressed-image.jpg');

    expect(mockCompressor.image_compress).toHaveBeenCalledWith('abc123', expect.objectContaining({ quality: 0.7 }), expect.any(Function));
    expect(downloadProgress).toHaveBeenCalledWith(55);
    expect(downloadProgress).toHaveBeenCalledTimes(1);
  });

  it('rejects empty image compression input before calling native code', async () => {
    await expect(api.Image.compress('')).rejects.toThrow('Compression value is empty');
    expect(mockCompressor.image_compress).not.toHaveBeenCalled();
  });

  it('compresses videos with defaults, cancellation id, and progress callbacks', async () => {
    mockCompressor.compress.mockImplementation(async (_fileUrl, _options, onProgress, onDownloadProgress) => {
      onProgress?.(22);
      onDownloadProgress?.(33);
      return 'file://compressed-video.mp4';
    });
    const onProgress = jest.fn();
    const downloadProgress = jest.fn();
    const getCancellationId = jest.fn();

    await expect(
      api.Video.compress(
        localVideoUri,
        {
          bitrate: 1000,
          getCancellationId,
          downloadProgress,
          stripAudio: true,
        },
        onProgress,
      ),
    ).resolves.toBe('file://compressed-video.mp4');

    expect(mockCompressor.compress).toHaveBeenCalledWith(
      localVideoUri,
      expect.objectContaining({
        uuid: expect.any(String),
        bitrate: 1000,
        compressionMethod: 'auto',
        maxSize: 640,
        stripAudio: true,
      }),
      expect.any(Function),
      expect.any(Function),
    );
    expect(getCancellationId).toHaveBeenCalledWith(expect.any(String));
    expect(onProgress).toHaveBeenCalledWith(22);
    expect(downloadProgress).toHaveBeenCalledWith(33);
  });

  it('forwards manual video compression options and minimum file size', async () => {
    mockCompressor.compress.mockResolvedValue('file://manual.mp4');

    await api.Video.compress(localVideoUri, {
      compressionMethod: 'manual',
      maxSize: 720,
      minimumFileSizeForCompress: 10,
      progressDivider: 5,
    });

    expect(mockCompressor.compress).toHaveBeenCalledWith(
      localVideoUri,
      expect.objectContaining({
        compressionMethod: 'manual',
        maxSize: 720,
        minimumFileSizeForCompress: 10,
        progressDivider: 5,
      }),
      undefined,
      undefined,
    );
  });

  it('proxies video cancellation and background task lifecycle', async () => {
    mockCompressor.activateBackgroundTask.mockImplementation(async (_options, onExpired) => {
      onExpired?.();
      return 'activated';
    });
    mockCompressor.deactivateBackgroundTask.mockResolvedValue('deactivated');
    const onExpired = jest.fn();

    api.Video.cancelCompression('video-id');
    await expect(api.Video.activateBackgroundTask(onExpired)).resolves.toBe('activated');
    await expect(api.Video.deactivateBackgroundTask()).resolves.toBe('deactivated');

    expect(mockCompressor.cancelCompression).toHaveBeenCalledWith('video-id');
    expect(mockCompressor.activateBackgroundTask).toHaveBeenCalledWith({}, expect.any(Function));
    expect(mockCompressor.deactivateBackgroundTask).toHaveBeenCalledWith({});
    expect(onExpired).toHaveBeenCalledWith(undefined);
  });

  it('compresses audio with defaults and custom options', async () => {
    mockCompressor.compress_audio.mockResolvedValueOnce('file://audio.aac').mockResolvedValueOnce('file://audio-high.aac');

    await expect(api.Audio.compress(localAudioUri)).resolves.toBe('file://audio.aac');
    await expect(api.Audio.compress(localAudioUri, { quality: 'high', bitrate: 128000 })).resolves.toBe('file://audio-high.aac');

    expect(mockCompressor.compress_audio).toHaveBeenNthCalledWith(1, localAudioUri, { quality: 'medium' });
    expect(mockCompressor.compress_audio).toHaveBeenNthCalledWith(2, localAudioUri, {
      quality: 'high',
      bitrate: 128000,
    });
  });

  it('wraps native utility functions and normalizes image metadata', async () => {
    reactNative.Platform.OS = 'android';
    mockCompressor.generateFilePath.mockResolvedValue('/tmp/output.jpg');
    mockCompressor.getRealPath.mockResolvedValue('/real/video.mp4');
    mockCompressor.getVideoMetaData.mockResolvedValue({ width: 1920, height: 1080 });
    mockCompressor.getImageMetaData.mockResolvedValue({
      ImageWidth: '640',
      ImageLength: '480',
      Orientation: '1',
      size: 123,
      extension: 'jpg',
    });
    mockCompressor.createVideoThumbnail.mockResolvedValue({ path: 'thumb.jpg' });
    mockCompressor.clearCache.mockResolvedValue('cleared');
    mockCompressor.getFileSize.mockResolvedValue('10');

    await expect(api.generateFilePath('jpg')).resolves.toBe('file:///tmp/output.jpg');
    await expect(api.getRealPath('content://video', 'video')).resolves.toBe('/real/video.mp4');
    await expect(api.getVideoMetaData('file://video.mp4')).resolves.toEqual({ width: 1920, height: 1080 });
    await expect(api.getImageMetaData(localImageUri)).resolves.toEqual({
      ImageWidth: 640,
      ImageHeight: 480,
      Orientation: 1,
      size: 123,
      extension: 'jpg',
      exif: {
        ImageWidth: '640',
        ImageLength: '480',
        Orientation: '1',
        size: 123,
        extension: 'jpg',
      },
    });
    await expect(api.createVideoThumbnail(localVideoUri, { headers: { Authorization: 'token' } })).resolves.toEqual({
      path: 'thumb.jpg',
    });
    await expect(api.clearCache('/tmp/cache')).resolves.toBe('cleared');
    await expect(api.getFileSize(localImageUri)).resolves.toBe('10');
  });

  it('uses iOS image metadata keys when running on iOS', async () => {
    mockCompressor.getImageMetaData.mockResolvedValue({
      PixelWidth: 1024,
      PixelHeight: 768,
      Orientation: 6,
      size: 456,
      extension: 'heic',
    });

    await expect(api.getImageMetaData('file:///tmp/react-native-compressor/input-image.heic')).resolves.toMatchObject({
      ImageWidth: 1024,
      ImageHeight: 768,
      Orientation: 6,
      size: 456,
      extension: 'heic',
    });
  });

  it('downloads files, strips Android file prefixes, and reports progress', async () => {
    reactNative.Platform.OS = 'android';
    mockCompressor.download.mockImplementation(async (_fileUrl, _options, onProgress) => {
      onProgress?.(88);
      return '/downloads/file.mp4';
    });
    const downloadProgress = jest.fn();

    await expect(api.download('file:///storage/input.mp4', downloadProgress, 10)).resolves.toBe('/downloads/file.mp4');

    expect(mockCompressor.download).toHaveBeenCalledWith(
      '/storage/input.mp4',
      {
        uuid: expect.any(String),
        progressDivider: 10,
      },
      expect.any(Function),
    );
    expect(downloadProgress).toHaveBeenCalledWith(88);
  });

  it('uploads files with options, progress, cancellation id, abort handling, and Android path normalization', async () => {
    reactNative.Platform.OS = 'android';
    mockCompressor.upload.mockImplementation(async (_fileUrl, _options, onProgress) => {
      onProgress?.(4, 10);
      return { status: 200 };
    });
    const onProgress = jest.fn();
    const getCancellationId = jest.fn();
    const abortController = new AbortController();

    await expect(
      api.backgroundUpload(
        'https://example.com/upload',
        'file:///storage/input.mp4',
        {
          uploadType: api.UploadType.MULTIPART,
          fieldName: 'file',
          httpMethod: api.UploaderHttpMethod.PATCH,
          headers: { Authorization: 'token' },
          parameters: { album: 'demo' },
          getCancellationId,
        },
        onProgress,
        abortController.signal,
      ),
    ).resolves.toEqual({ status: 200 });

    const uploadOptions = mockCompressor.upload.mock.calls[0][1];
    abortController.abort();

    expect(mockCompressor.upload).toHaveBeenCalledWith(
      '/storage/input.mp4',
      expect.objectContaining({
        uuid: expect.any(String),
        method: 'PATCH',
        url: 'https://example.com/upload',
        uploadType: api.UploadType.MULTIPART,
        fieldName: 'file',
        headers: { Authorization: 'token' },
        parameters: { album: 'demo' },
      }),
      expect.any(Function),
    );
    expect(getCancellationId).toHaveBeenCalledWith(uploadOptions.uuid);
    expect(onProgress).toHaveBeenCalledWith(4, 10);
    expect(mockCompressor.cancelUpload).toHaveBeenCalledWith(uploadOptions.uuid, false);
  });

  it('cancels one upload or all uploads through the native module', () => {
    api.cancelUpload('upload-id');
    api.cancelUpload(undefined, true);

    expect(mockCompressor.cancelUpload).toHaveBeenNthCalledWith(1, 'upload-id', false);
    expect(mockCompressor.cancelUpload).toHaveBeenNthCalledWith(2, '', true);
  });

  it('generates uuid-like ids', () => {
    expect(api.uuidv4()).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });
});
