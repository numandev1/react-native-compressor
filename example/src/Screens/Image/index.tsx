import React, { useState } from 'react';
import {
  View,
  StyleSheet,
  Alert,
  useWindowDimensions,
  Image as RNImage,
  Platform,
} from 'react-native';
import Button from '../../Components/Button';
import Row from '../../Components/Row';
import * as ImagePicker from 'react-native-image-picker';
import CameraRoll from '@react-native-community/cameraroll';
const prettyBytes = require('pretty-bytes');
import { Image } from 'react-native-compressor';
import { getFileInfo } from '../../Utils';
const Index = () => {
  const dimension = useWindowDimensions();
  const [orignalUri, setOrignalUri] = useState<string>();
  const [commpressedUri, setCommpressedUri] = useState<string>();
  const [fileName, setFileName] = useState<any>('');
  const [mimeType, setMimeType] = useState<any>('');
  const [orignalSize, setOrignalSize] = useState(0);
  const [compressedSize, setCompressedSize] = useState(0);
  const chooseAudioHandler = async () => {
    try {
      ImagePicker.launchImageLibrary(
        {
          mediaType: 'photo',
        },
        (result: ImagePicker.ImagePickerResponse) => {
          if (result.didCancel) {
          } else if (result.errorCode) {
            Alert.alert('Failed selecting video');
          } else {
            if (result.assets) {
              const source: any = result.assets[0];
              if (source) {
                setOrignalSize(prettyBytes(source.fileSize));

                setFileName(source.fileName);
                setMimeType(source.type);
                setOrignalUri(source.uri);
              }

              Image.compress(source.uri, {
                compressionMethod: 'auto',
              })
                .then(async (compressedFileUri) => {
                  setCommpressedUri(compressedFileUri);
                  const detail: any = await getFileInfo(compressedFileUri);
                  setCompressedSize(prettyBytes(parseInt(detail.size)));
                })
                .catch((e) => {
                  console.log(e, 'error');
                });
            }
          }
        }
      );
    } catch (err) {}
  };

  const onCompressImagefromCameraoll = async () => {
    const photos = await CameraRoll.getPhotos({
      first: 1,
      assetType: 'Photos',
    });
    const phUrl: any = photos.page_info.end_cursor;
    Image.compress(phUrl, {
      compressionMethod: 'auto',
    })
      .then(async (compressedFileUri) => {
        setCommpressedUri(compressedFileUri);
        const detail: any = await getFileInfo(compressedFileUri);
        setCompressedSize(prettyBytes(parseInt(detail.size)));
      })
      .catch((e) => {
        console.log(e, 'error');
      });
  };

  return (
    <View style={styles.container}>
      <View style={styles.imageContainer}>
        {orignalUri && (
          <RNImage
            resizeMode="contain"
            source={{ uri: orignalUri }}
            style={{
              width: dimension.width / 3,
            }}
          />
        )}
        {commpressedUri && (
          <RNImage
            resizeMode="contain"
            source={{ uri: commpressedUri }}
            style={{
              width: dimension.width / 3,
            }}
          />
        )}
      </View>
      <View style={styles.container}>
        <Row label="File Name" value={fileName} />
        <Row label="Mime Type" value={mimeType} />
        <Row label="Orignal Size" value={orignalSize} />
        <Row label="Compressed Size" value={compressedSize} />
        <Button onPress={chooseAudioHandler} title="Choose Image" />
        {Platform.OS === 'ios' && (
          <Button
            title={'compress image from camera roll (ph://)'}
            onPress={onCompressImagefromCameraoll}
          />
        )}
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
});
