import React, { useState, useRef } from 'react';
import { View, StyleSheet, Dimensions, Text } from 'react-native';
import Button from '../../Components/Button';
import {
  generateFilePath,
  getRealPath,
  getVideoMetaData,
  getFileSize,
  download,
} from 'react-native-compressor';
import CameraRoll from '@react-native-camera-roll/camera-roll';
import * as ImagePicker from 'react-native-image-picker';
import ProgressBar from '../../Components/ProgressBar';
import type { ProgressBarRafType } from '../../Components/ProgressBar';
const { width, height } = Dimensions.get('screen');
const Index = () => {
  const progressRef = useRef<ProgressBarRafType>();
  const [log, setLog] = useState('');
  const makeLog = (obj: any) => {
    let logStr = '';
    for (let [key, value] of Object.entries(obj)) {
      logStr += `\n\n${key} : ${value}`;
    }
    setLog(logStr);
  };

  const generateTempFilePath = async () => {
    const randomFilePathForSaveFile = await generateFilePath('mp4');
    makeLog({ path: randomFilePathForSaveFile });
  };

  const onGetRealPathIOS = async () => {
    const photos = await CameraRoll.getPhotos({
      first: 1,
      assetType: 'Videos',
    });
    const filePath = photos.page_info.end_cursor;
    const realPath = await getRealPath(filePath, 'video');
    makeLog({ oldPath: filePath, newPath: realPath });
  };

  const getImageUri = () => {
    return new Promise((resolve, reject) => {
      ImagePicker.launchImageLibrary(
        {
          mediaType: 'video',
        },
        async (result: ImagePicker.ImagePickerResponse) => {
          if (result.didCancel) {
            reject('');
          } else if (result.errorCode) {
            reject(result.errorCode);
          } else {
            if (result.assets) {
              const source: any = result.assets[0];
              let filePath = source.uri;
              resolve(filePath);
            }
          }
        }
      );
    });
  };

  const onGetRealPathAndroid = async () => {
    getImageUri().then(async (filePath) => {
      const realPath = await getRealPath(filePath, 'video');
      makeLog({ oldPath: filePath, newPath: realPath });
    });
  };

  const onGetMetaInfoOfVideo = () => {
    getImageUri().then(async (filePath) => {
      const metaData = await getVideoMetaData(filePath);
      makeLog(metaData);
    });
  };

  const onGetFileSize = () => {
    getImageUri().then(async (filePath: any) => {
      const size = await getFileSize(filePath);
      makeLog({ fileSize: size });
    });
  };

  const downloadFile = () => {
    const url =
      'https://svs.gsfc.nasa.gov/vis/a030000/a030800/a030877/frames/5760x3240_16x9_01p/BlackMarble_2016_1200m_africa_s_labeled.png';
    download(url, (progress) => {
      console.log('downloadProgress: ', progress);
      progressRef.current?.setProgress(progress);
    })
      .then(async (downloadedFileUri) => {
        console.log('test', downloadedFileUri);
        makeLog({ downloadedFileUri: downloadedFileUri });
      })
      .catch((e) => {
        console.log(e, 'error1');
      });
  };

  return (
    <View style={styles.container}>
      <ProgressBar ref={progressRef} />
      <Text style={styles.textInput}>{log}</Text>
      <View style={styles.container}>
        <Button
          onPress={generateTempFilePath}
          title="Generate Temp File Path"
        />
        <Button onPress={onGetRealPathIOS} title="Get Real Path (IOS) ph://" />
        <Button
          onPress={onGetRealPathAndroid}
          title="Get Real Path (Android) content://"
        />
        <Button onPress={onGetMetaInfoOfVideo} title="Get Meta Info Of Video" />
        <Button onPress={onGetFileSize} title="Get File Size" />
        <Button onPress={downloadFile} title="Download File" />
      </View>
    </View>
  );
};

export default Index;

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  imageContainer: {
    flex: 1,
    width: '100%',
    flexDirection: 'row',
    justifyContent: 'space-around',
  },
  textInput: {
    height: height * 0.4,
    width: width,
    borderColor: 'gray',
    borderWidth: 5,
    fontSize: 20,
    padding: 5,
  },
});
