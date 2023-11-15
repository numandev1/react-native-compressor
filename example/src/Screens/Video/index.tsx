import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  Button,
  Image,
  Alert,
  Platform,
  BackHandler,
} from 'react-native';
import {
  Video,
  getRealPath,
  backgroundUpload,
  cancelUpload,
  UploadType,
  UploaderHttpMethod,
  createVideoThumbnail,
  clearCache,
} from 'react-native-compressor';

import * as ImagePicker from 'react-native-image-picker';
import CameraRoll from '@react-native-camera-roll/camera-roll';
import prettyBytes from 'pretty-bytes';
import { getFileInfo } from '../../Utils';
import ProgressBar from '../../Components/ProgressBar';
import type { ProgressBarRafType } from '../../Components/ProgressBar';
// const DOMAIN = 'http://localhost:8080';
const DOMAIN = 'http://192.168.1.5:8080';
const uploadPutRequest = `${DOMAIN}/upload/putRequestFile.mov`;
const uploadPutRequest1 = `${DOMAIN}/upload/putRequestFile1.mov`;
const uploadPostRequest = `${DOMAIN}/upload`;
// const uploadPostRequestFail = `${DOMAIN}/uploadFail`;
let counter1 = 0;
export default function App() {
  const cancellationIdForUploadRef = useRef<string>('');
  const progressRef = useRef<ProgressBarRafType>();
  const abortSignalRef = useRef(new AbortController());
  const cancellationIdRef = useRef<string>('');
  const [sourceVideo, setSourceVideo] = useState<string>();
  const [sourceSize, setSourceSize] = useState<number>();
  const [sourceVideoThumbnail, setSourceVideoThumbnail] = useState<string>();
  const [compressedVideo, setCompressedVideo] = useState<string>();
  const [compressedSize, setCompressedSize] = useState<number>();
  const [compressedVideoThumbnail, setcompressedVideoThumbnail] =
    useState<string>();

  const [doingSomething, setDoingSomething] = useState<boolean>(false);
  const [backgroundMode, setBackgroundMode] = useState<boolean>(false);

  useEffect(() => {
    counter1 = -1;
    if (!sourceVideo) return;
    createVideoThumbnail(sourceVideo, {})
      .then((response) => setSourceVideoThumbnail(response.path))
      .catch((error) => console.log({ error }));
    (async () => {
      const detail: any = await getFileInfo(sourceVideo);
      setSourceSize(prettyBytes(parseInt(detail.size)));
    })();
  }, [sourceVideo]);

  useEffect(() => {
    if (!compressedVideo) return;
    setcompressedVideoThumbnail(sourceVideoThumbnail);
    createVideoThumbnail(compressedVideo)
      .then((response) => setcompressedVideoThumbnail(response.path))
      .catch((error) => {
        console.log({ errorThumnail: error });
        setcompressedVideoThumbnail(sourceVideoThumbnail);
      });

    (async () => {
      const detail: any = await getFileInfo(compressedVideo);
      setCompressedSize(prettyBytes(parseInt(detail.size)));
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [compressedVideo]);

  useEffect(() => {
    if (doingSomething) {
      let counter = 1;
      const timer = setInterval(() => {
        console.log(counter, ' Doing Something', new Date());
        counter += 1;
      }, 500);
      return () => {
        clearInterval(timer);
      };
    }
    return undefined;
  }, [doingSomething]);

  useEffect(() => {
    const handler = () => {
      abortSignalRef.current?.abort();
      return true;
    };
    BackHandler.addEventListener('hardwareBackPress', handler);

    return () => BackHandler.removeEventListener('hardwareBackPress', handler);
  }, []);

  const selectVideo = async () => {
    try {
      ImagePicker.launchImageLibrary(
        {
          mediaType: 'video',
        },
        async (result: ImagePicker.ImagePickerResponse) => {
          if (result.didCancel) {
          } else if (result.errorCode) {
            Alert.alert('Failed selecting video');
          } else {
            if (result.assets) {
              const source: any = result.assets[0];
              let uri = source.uri;
              if (Platform.OS === 'android' && uri.includes('content://')) {
                const realPath = await getRealPath(uri, 'video');
                console.log('old path==>', uri, 'realPath ==>', realPath);
              }
              setSourceVideo(uri);
            }
          }
        }
      );
    } catch (error) {
      console.log('Failed to select video', error);
    }
  };

  const testCompress = async () => {
    if (!sourceVideo) return;
    try {
      const dstUrl = await Video.compress(
        sourceVideo,
        {
          progressDivider: 10,
          getCancellationId: (cancellationId) =>
            (cancellationIdRef.current = cancellationId),
        },
        (progress) => {
          console.log('Compression Progress: ', progress);
          progressRef.current?.setProgress(progress);
          if (backgroundMode) {
          } else {
          }
        }
      );
      console.log({ dstUrl }, 'compression result');
      setCompressedVideo(dstUrl);
      progressRef.current?.setProgress(0);
    } catch (error) {
      console.log({ error }, 'compression error');
      setCompressedVideo(sourceVideo);
      progressRef.current?.setProgress(0);
    }
  };

  const onPressRemoteVideo = async () => {
    // const url =
    // 'https://filesamples.com/samples/video/mp4/sample_960x400_ocean_with_audio.mp4';

    const url =
      'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WhatCarCanYouGetForAGrand.mp4';

    setSourceVideoThumbnail(
      'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQvFTncZP4wzhJ9qH0dR1ZCCde_riH3aOoaVQZnOeVDnA&s'
    );
    try {
      const dstUrl = await Video.compress(
        url,
        {
          progressDivider: 10,
          minimumFileSizeForCompress: 0,
          getCancellationId: (cancellationId) =>
            (cancellationIdRef.current = cancellationId),
          downloadProgress: (progress) => {
            console.log('downloadProgress: ', progress);
            progressRef.current?.setProgress(progress);
          },
        },
        (progress) => {
          console.log('Compression Progress: ', progress);
          progressRef.current?.setProgress(progress);
          if (backgroundMode) {
          } else {
          }
        }
      );
      console.log({ dstUrl }, 'compression result');
      setCompressedVideo(dstUrl);
      progressRef.current?.setProgress(0);
    } catch (error) {
      console.log({ error }, 'compression error');
      setCompressedVideo(sourceVideo);
      progressRef.current?.setProgress(0);
    }
  };

  const cancelCompression = () => {
    Video.cancelCompression(cancellationIdRef.current);
  };

  const uploadByPostRequest = async (
    type: 'actual' | 'compressed' = 'actual'
  ) => {
    console.log('uploadByPostRequest');
    const localFileUrl = type === 'actual' ? sourceVideo : compressedVideo;
    if (!localFileUrl) return;
    try {
      console.log('upload start', localFileUrl);
      const headers = {
        Authorization: `Bearer ABCABC`,
      };
      const result = await backgroundUpload(
        uploadPostRequest,
        localFileUrl,
        {
          uploadType: UploadType.MULTIPART,
          httpMethod: UploaderHttpMethod.POST,
          // fieldName: 'file',
          // mimeType: 'video/quicktime',
          parameters: { message: 'this is test message' },
          headers,
        },
        (written, total) => {
          progressRef.current?.setProgress(written / total);
          console.log(written, total);
        },
        abortSignalRef.current.signal
      );

      console.log(result, 'result');
    } catch (error) {
      console.log('error=>', error);
    } finally {
      progressRef.current?.setProgress(0);
    }
  };

  const uploadByPutRequest = async (type: 'actual' | 'compressed') => {
    const localFileUrl = type === 'actual' ? sourceVideo : compressedVideo;
    if (!localFileUrl) return;
    try {
      console.log('upload start', sourceVideo);
      const headers = {
        Authorization: `Bearer ABCABC`,
      };
      counter1++;
      const result = await backgroundUpload(
        counter1 % 2 == 0 ? uploadPutRequest : uploadPutRequest1,
        localFileUrl,
        {
          uploadType: UploadType.BINARY_CONTENT,
          httpMethod: UploaderHttpMethod.PUT,
          headers,
          getCancellationId: (cancellationId) =>
            (cancellationIdForUploadRef.current = cancellationId),
        },
        (written, total) => {
          progressRef.current?.setProgress(written / total);
          console.log(written, total);
        },
        abortSignalRef.current.signal
      );

      console.log(result, 'result');
    } catch (error) {
      console.log('error=>', error);
    } finally {
      progressRef.current?.setProgress(0);
    }
  };

  const cancelUploader = () => {
    console.log('cancelUploader', cancellationIdForUploadRef.current);
    cancelUpload(cancellationIdForUploadRef.current);
  };

  const onCompressVideofromCameraoll = async () => {
    const photos = await CameraRoll.getPhotos({
      first: 1,
      assetType: 'Videos',
    });
    const phUrl = photos.page_info.end_cursor;
    setSourceVideo(phUrl);
    if (phUrl?.includes('ph://')) {
      const realPath = await getRealPath(phUrl, 'video');
      console.log('old path==>', phUrl, 'realPath ==>', realPath);
    }
  };

  const clearThumbnailCache = () => {
    clearCache()
      .then(() => {
        console.log('done');
      })
      .catch((error: any) => console.log(error));
  };

  return (
    <View style={{ flex: 1 }}>
      <ProgressBar ref={progressRef} />
      <View
        style={{
          flex: 1,
          flexDirection: 'row',
          justifyContent: 'space-around',
        }}
      >
        <View style={{ width: 200, backgroundColor: '#f00' }}>
          {sourceVideoThumbnail && (
            <View>
              <Text>Source</Text>
              <Image
                style={{ width: 200, height: 200 }}
                source={{ uri: sourceVideoThumbnail }}
                resizeMode="contain"
              />
              {sourceSize && <Text>Size: {sourceSize}</Text>}
              <Button
                title="Upload(Post)"
                onPress={() => uploadByPostRequest('actual')}
              />
              <Button
                title="Upload(Put)"
                onPress={() => uploadByPutRequest('actual')}
              />

              <Button title="Cancel Upload" onPress={() => cancelUploader()} />
            </View>
          )}
        </View>
        <View style={{ width: 200, backgroundColor: '#ff0' }}>
          {compressedVideoThumbnail && (
            <View>
              <Text>Compressed</Text>
              <Image
                style={{ width: 200, height: 200 }}
                source={{ uri: compressedVideoThumbnail }}
                resizeMode="contain"
              />
              {compressedSize && <Text>Size: {compressedSize}</Text>}
              <Button
                title="Upload(Post)"
                onPress={() => uploadByPostRequest('compressed')}
              />
              <Button
                title="Upload(Put)"
                onPress={() => uploadByPutRequest('compressed')}
              />
            </View>
          )}
        </View>
      </View>
      <View
        style={{
          height: 50,
          flexDirection: 'row',
          justifyContent: 'space-around',
          backgroundColor: '#0f0',
        }}
      >
        <Button title="Select Video" onPress={selectVideo} />

        <Button
          title="Compress"
          disabled={!sourceVideo}
          onPress={testCompress}
        />
      </View>
      <View style={{ height: 200 }}>
        <Button
          title="Remote Video (http://) and Compress"
          onPress={onPressRemoteVideo}
        />
        <Button title="Cancel Compression" onPress={cancelCompression} />
        <Button title="clear thumbnail cache" onPress={clearThumbnailCache} />
        <Text>Put app in background and check console output</Text>
        <View
          style={{
            backgroundColor: '#707',
            flex: 1,
            justifyContent: 'space-around',
          }}
        >
          {Platform.OS === 'ios' && (
            <Button
              title={'Compress video from camera roll (ph://)'}
              onPress={onCompressVideofromCameraoll}
            />
          )}
          <Button
            title={doingSomething ? 'Stop Work' : 'Start Work'}
            onPress={() => {
              setDoingSomething(!doingSomething);
            }}
          />

          <Button
            title={
              backgroundMode
                ? 'Deactivate Background Mode'
                : 'Activate Background Mode'
            }
            onPress={() => {
              if (backgroundMode) {
                setBackgroundMode(false);
                Video.deactivateBackgroundTask()
                  .then((id: any) =>
                    console.log('Background Mode Deactivated', id)
                  )
                  .catch((error) =>
                    console.log('Failed to deactivate background task', error)
                  );
              } else {
                setBackgroundMode(true);
                Video.activateBackgroundTask((data) => {
                  console.log('Background Mode Expired', data);
                  setBackgroundMode(false);
                })
                  .then((id: any) =>
                    console.log('Background Mode Activated', id)
                  )
                  .catch((error) =>
                    console.log('Failed to activate background task', error)
                  );
              }
            }}
          />
        </View>
      </View>
    </View>
  );
}
