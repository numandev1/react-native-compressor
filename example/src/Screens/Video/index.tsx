import React, { useState } from 'react';
import { View, StyleSheet, Alert } from 'react-native';
import Button from '../../Components/Button';
import Row from '../../Components/Row';
import * as ImagePicker from 'react-native-image-picker';
const prettyBytes = require('pretty-bytes');
import { getFullFilename } from '../../Utils/index';
import { Video, getMediaInformation } from 'react-native-compressor';

const Index = () => {
  const [fileName, setFileName] = useState('');
  const [mimeType, setMimeType] = useState('');
  const [orignalSize, setOrignalSize] = useState(0);
  const [compressedSize, setCompressedSize] = useState(0);
  const chooseAudioHandler = async () => {
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
            const detail: any = await getMediaInformation(source.uri);
            const videoDetail: any = detail.__private_0_allProperties.format;
            setOrignalSize(prettyBytes(parseInt(videoDetail.size)));
            setCompressedSize(prettyBytes(parseInt(videoDetail.size)));
            setFileName(getFullFilename(source.uri));
            setMimeType('video/mp4');
            Video.compress(source.uri, {})
              .then(() => {})
              .catch((e) => {
                console.log(e, 'error');
              });
          }
        }
      );
    } catch (err) {}
  };

  return (
    <View style={styles.container}>
      <Row label="File Name" value={fileName} />
      <Row label="Mime Type" value={mimeType} />
      <Row label="Orignal Size" value={orignalSize} />
      <Row label="Compressed Size" value={compressedSize} />
      <Button onPress={chooseAudioHandler} title="Choose Video" />
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
});
