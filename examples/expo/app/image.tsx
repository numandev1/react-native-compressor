import { useState, useRef } from 'react';
import { View, StyleSheet, Alert, useWindowDimensions, Image as RNImage, Platform } from 'react-native';
import Button from './_components/Button';
import Row from './_components/Row';
import ProgressBar from './_components/ProgressBar';
import type { ProgressBarRafType } from './_components/ProgressBar';
import * as ImagePicker from 'expo-image-picker';
import { CameraRoll } from '@react-native-camera-roll/camera-roll';
import prettyBytes from 'pretty-bytes';
import { Image, getFileSize, getImageMetaData } from 'react-native-compressor';
import { getFileInfo } from './_utils';

export default function ImageScreen() {
  const progressRef = useRef<ProgressBarRafType>(null);
  const dimension = useWindowDimensions();
  const [orignalUri, setOrignalUri] = useState<string>();
  const [commpressedUri, setCommpressedUri] = useState<string>();
  const [fileName, setFileName] = useState<any>('');
  const [mimeType, setMimeType] = useState<any>('');
  const [orignalSize, setOrignalSize] = useState<string>('');
  const [compressedSize, setCompressedSize] = useState<string>('0');

  const compressHandler = async (result: ImagePicker.ImagePickerResult) => {
    if (result.canceled) {
      Alert.alert('Failed selecting Image');
      return;
    }
    const source = result.assets[0];
    if (source) {
      const detail: any = await getFileInfo(source.uri);
      setOrignalSize(prettyBytes(parseInt(detail.size || 0, 10)));
      setFileName(source.fileName || 'image');
      setMimeType(source.mimeType || 'image/jpeg');
      setOrignalUri(source.uri);

      const metadata = await getImageMetaData(source.uri);
      console.log(JSON.stringify(metadata, null, 4), 'metadata');
      Image.compress(source.uri)
        .then(async (compressedFileUri) => {
          console.log(compressedFileUri, 'compressedFileUri');
          setCommpressedUri(compressedFileUri);
          const compressedDetail: any = await getFileInfo(compressedFileUri);
          setCompressedSize(prettyBytes(parseInt(compressedDetail.size || 0, 10)));
        })
        .catch((e) => {
          console.log(e, 'error');
        });
    }
  };

  const chooseCameraImageHandler = async () => {
    try {
      const result = await ImagePicker.launchCameraAsync({
        mediaTypes: ['images'],
      });
      compressHandler(result);
    } catch (err) {
      console.log(err, 'error');
    }
  };

  const chooseGalleryImageHandler = async () => {
    try {
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ['images'],
      });
      compressHandler(result);
    } catch (err) {
      console.log(err, 'error');
    }
  };

  const onPressRemoteImage = async () => {
    const url =
      'https://svs.gsfc.nasa.gov/vis/a030000/a030800/a030877/frames/5760x3240_16x9_01p/BlackMarble_2016_1200m_africa_s_labeled.png';
    setFileName('test.jpg');
    const size = await getFileSize(url);
    setOrignalSize(prettyBytes(parseInt(size, 10)));
    setMimeType('image/jpeg');
    Image.compress(url, {
      progressDivider: 10,
      downloadProgress: (progress) => {
        console.log('downloadProgress: ', progress);
        progressRef.current?.setProgress(progress);
      },
    })
      .then(async (compressedFileUri) => {
        console.log('test', commpressedUri);
        setCommpressedUri(compressedFileUri);
        const detail: any = await getFileInfo(compressedFileUri);
        setCompressedSize(prettyBytes(parseInt(detail.size || 0, 10)));
      })
      .catch((e) => {
        console.log(e, 'error1');
      });
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
        setCompressedSize(prettyBytes(parseInt(detail.size, 10)));
      })
      .catch((e) => {
        console.log(e, 'error');
      });
  };

  return (
    <View style={styles.container}>
      <ProgressBar ref={progressRef} />
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
        <Button onPress={chooseGalleryImageHandler} title="Choose Image (Gallery)" />
        <Button onPress={chooseCameraImageHandler} title="Choose Image (Camera)" />
        <Button onPress={onPressRemoteImage} title="Remote Image (http://)" />
        {Platform.OS === 'ios' && <Button title={'compress image from camera roll (ph://)'} onPress={onCompressImagefromCameraoll} />}
      </View>
    </View>
  );
}

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
