import React, { useState } from 'react';
import { View, StyleSheet, Dimensions, Text } from 'react-native';
import Button from '../../Components/Button';
import {
  generateFilePath,
  getRealPath,
  getVideoMetaData,
} from 'react-native-compressor';
import CameraRoll from '@react-native-community/cameraroll';
import * as ImagePicker from 'react-native-image-picker';

const { width, height } = Dimensions.get('screen');
const Index = () => {
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
      console.log(metaData, 'metaData');
      makeLog(metaData);
    });
  };
  return (
    <View style={styles.container}>
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
