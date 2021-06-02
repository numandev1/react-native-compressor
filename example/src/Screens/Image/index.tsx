import React, { useState } from 'react';
import { View, StyleSheet, Alert } from 'react-native';
import Button from '../../Components/Button';
import Row from '../../Components/Row';
import * as ImagePicker from 'react-native-image-picker';
const prettyBytes = require('pretty-bytes');
import { Image, getMediaInformation } from 'react-native-compressor';
const Index = () => {
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
            const source: any = result.assets[0];
            if (source) {
              setOrignalSize(prettyBytes(source.fileSize));

              setFileName(source.fileName);
              setMimeType(source.type);
            }

            Image.compress(source.uri, {
              maxWidth: 100,
              input: 'uri',
              output: 'jpg',
              quality: 0.5,
              returnableOutputType: 'uri',
            })
              .then(async (compressedFileUri) => {
                const detail: any = await getMediaInformation(
                  compressedFileUri
                );
                const imageDetail: any =
                  detail.__private_0_allProperties.format;
                setCompressedSize(prettyBytes(parseInt(imageDetail.size)));
                console.log(compressedFileUri, 'compressed');
              })
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
      <Button onPress={chooseAudioHandler} title="Choose Image" />
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
