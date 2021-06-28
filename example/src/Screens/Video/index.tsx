import * as React from 'react';
import { View, Text, Button, Image, Alert, Platform } from 'react-native';
import { Video } from 'react-native-compressor';
import * as ImagePicker from 'react-native-image-picker';
import { createThumbnail } from 'react-native-create-thumbnail';
import * as Progress from 'react-native-progress';
const prettyBytes = require('pretty-bytes');
import { v4 as uuidv4 } from 'uuid';
const RNFS = require('react-native-fs');
import { getFileInfo } from '../../Utils';

export default function App() {
  const [sourceVideo, setSourceVideo] = React.useState<string>();
  const [sourceSize, setSourceSize] = React.useState<number>();
  const [sourceVideoThumbnail, setSourceVideoThumbnail] =
    React.useState<string>();
  const [compressedVideo, setCompressedVideo] = React.useState<string>();
  const [compressedSize, setCompressedSize] = React.useState<number>();
  const [compressedVideoThumbnail, setcompressedVideoThumbnail] =
    React.useState<string>();

  const [compressingProgress, setCompressingProgress] =
    React.useState<number>(0);
  const [sourceUploadProgress, setSourceUploadProgress] =
    React.useState<number>(0);
  const [compressedUploadProgress, setCompressedUploadProgress] =
    React.useState<number>(0);

  const [doingSomething, setDoingSomething] = React.useState<boolean>(false);
  const [backgroundMode, setBackgroundMode] = React.useState<boolean>(false);

  React.useEffect(() => {
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

  React.useEffect(() => {
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

  React.useEffect(() => {
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

  const getAbsolutePath = async (uri: string, extension: string) => {
    const destPath = `${RNFS.TemporaryDirectoryPath}/${uuidv4()}.${extension}`;
    await RNFS.copyFile(uri, destPath);
    return `file://${destPath}`;
  };

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
            const source: any = result.assets[0];
            let uri = source.uri;
            if (Platform.OS === 'android' && uri.includes('content://')) {
              uri = await getAbsolutePath(uri, 'mp4');
            }
            setSourceVideo(uri);
          }
        }
      );
    } catch (error) {
      console.log('Failed to select video', error);
    }
  };

  const testCompress = async () => {
    if (!sourceVideo) return;
    const dstUrl = await Video.compress(
      sourceVideo,
      {
        compressionMethod: 'auto',
      },
      (progress) => {
        if (backgroundMode) {
          console.log('Compression Progress: ', progress);
        } else {
          setCompressingProgress(progress);
        }
      }
    );
    setCompressedVideo(dstUrl);
    setCompressingProgress(0);
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
              <Button
                title="Upload"
                onPress={() => {
                  uploadSource();
                }}
              />
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
              <Button
                title="Upload"
                onPress={() => {
                  uploadCompressed();
                }}
              />
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
        <Button
          title="Select Video"
          onPress={() => {
            selectVideo();
          }}
        />

        <Button
          title="Compress"
          disabled={!sourceVideo}
          onPress={() => {
            testCompress();
          }}
        />
      </View>
      <View style={{ height: 200 }}>
        <Text>Put app in background and check console output</Text>
        <View
          style={{
            backgroundColor: '#707',
            flex: 1,
            justifyContent: 'space-around',
          }}
        >
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
