import React, { useState, useEffect, useRef } from 'react';
import { View, Text, Button, Image, Alert, Platform } from 'react-native';
import { Video, getRealPath } from 'react-native-compressor';
import * as ImagePicker from 'react-native-image-picker';
import { createThumbnail } from 'react-native-create-thumbnail';
import * as Progress from 'react-native-progress';
import CameraRoll from '@react-native-community/cameraroll';
const prettyBytes = require('pretty-bytes');
import { getFileInfo } from '../../Utils';

export default function App() {
  const cancellationIdRef = useRef<string>('');
  const [sourceVideo, setSourceVideo] = useState<string>();
  const [sourceSize, setSourceSize] = useState<number>();
  const [sourceVideoThumbnail, setSourceVideoThumbnail] = useState<string>();
  const [compressedVideo, setCompressedVideo] = useState<string>();
  const [compressedSize, setCompressedSize] = useState<number>();
  const [compressedVideoThumbnail, setcompressedVideoThumbnail] =
    useState<string>();

  const [compressingProgress, setCompressingProgress] = useState<number>(0);
  const [sourceUploadProgress, setSourceUploadProgress] = useState<number>(0);
  const [compressedUploadProgress, setCompressedUploadProgress] =
    useState<number>(0);

  const [doingSomething, setDoingSomething] = useState<boolean>(false);
  const [backgroundMode, setBackgroundMode] = useState<boolean>(false);

  useEffect(() => {
    if (!sourceVideo) return;
    createThumbnail({
      url: sourceVideo,
    })
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
    createThumbnail({
      url: compressedVideo,
    })
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
        console.log(counter, ' Doing Simething', new Date());
        counter += 1;
      }, 500);
      return () => {
        clearInterval(timer);
      };
    }
    return undefined;
  }, [doingSomething]);

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
          compressionMethod: 'auto',
          minimumFileSizeForCompress: 0,
          getCancellationId: (cancellationId) =>
            (cancellationIdRef.current = cancellationId),
        },
        (progress) => {
          if (backgroundMode) {
            console.log('Compression Progress: ', progress);
          } else {
            setCompressingProgress(progress);
          }
        }
      );
      console.log({ dstUrl }, 'compression result');
      setCompressedVideo(dstUrl);
      setCompressingProgress(0);
    } catch (error) {
      console.log({ error }, 'compression error');
      setCompressedVideo(sourceVideo);
      setCompressingProgress(0);
    }
  };

  const cancelCompression = () => {
    Video.cancelCompression(cancellationIdRef.current);
  };

  const uploadSource = async () => {
    if (!sourceVideo) return;
    try {
      const result = await Video.backgroundUpload(
        'http://w.hbu50.com:8080/hello.mp4',
        sourceVideo,
        { httpMethod: 'PUT' },
        (written, total) => {
          setSourceUploadProgress(written / total);
          console.log(written, total);
        }
      );
      console.log(result);
    } catch (error) {
      console.log(error);
    } finally {
      setSourceUploadProgress(0);
    }
  };

  const uploadCompressed = async () => {
    if (!compressedVideo) return;
    try {
      setCompressedUploadProgress(1);
      const result = await Video.backgroundUpload(
        'http://w.hbu50.com:8080/hello.mp4',
        compressedVideo,
        { httpMethod: 'PUT' },
        (written, total) => {
          setCompressedUploadProgress(written / total);
          console.log(written, total);
        }
      );
      console.log(result);
    } catch (error) {
      console.log(error);
    } finally {
      setCompressedUploadProgress(0);
    }
  };

  const onCompressVideofromCameraoll = async () => {
    const photos = await CameraRoll.getPhotos({
      first: 1,
      assetType: 'Videos',
    });
    const phUrl = photos.page_info.end_cursor;
    setSourceVideo(phUrl);
    console.log('nomi', phUrl);
    if (phUrl?.includes('ph://')) {
      const realPath = await getRealPath(phUrl, 'video');
      console.log('old path==>', phUrl, 'realPath ==>', realPath);
    }
  };
  return (
    <View style={{ flex: 1 }}>
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
              <Button title="Upload" onPress={uploadSource} />
              {sourceUploadProgress > 0 && (
                <Progress.Bar progress={sourceUploadProgress} width={200} />
              )}
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
              <Button title="Upload" onPress={uploadCompressed} />
              {compressedUploadProgress > 0 && (
                <View>
                  <Progress.Bar
                    progress={compressedUploadProgress}
                    width={200}
                  />
                </View>
              )}
            </View>
          )}
        </View>
      </View>
      {compressingProgress > 0 && (
        <Progress.Bar progress={compressingProgress} width={400} />
      )}
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
        <Button title="Cancel Compression" onPress={cancelCompression} />
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
