import React, { useState } from 'react';
import { View, StyleSheet } from 'react-native';
import Button from '../../Components/Button';
import Row from '../../Components/Row';
import { Audio } from 'react-native-compressor';
import DocumentPicker from 'react-native-document-picker';
const prettyBytes = require('pretty-bytes');

const Index = () => {
  const [fileName, setFileName] = useState('');
  const [mimeType, setMimeType] = useState('');
  const [orignalSize, setOrignalSize] = useState(0);
  const [compressedSize, setCompressedSize] = useState(0);
  const chooseAudioHandler = async () => {
    try {
      const res = await DocumentPicker.pick({
        type: [DocumentPicker.types.allFiles],
      });
      setOrignalSize(prettyBytes(res.size));
      setCompressedSize(prettyBytes(res.size));
      setFileName(res.name);
      setMimeType(res.type);
      Audio.compress(res.uri, { quality: 'medium' })
        .then((result) => {
          console.log(result, 'resultresult');
        })
        .catch((e) => {
          console.log(e, 'error');
        });
    } catch (err) {
      if (DocumentPicker.isCancel(err)) {
      } else {
        throw err;
      }
    }
  };

  return (
    <View style={styles.container}>
      <Row label="File Name" value={fileName} />
      <Row label="Mime Type" value={mimeType} />
      <Row label="Orignal Size" value={orignalSize} />
      <Row label="Compressed Size" value={compressedSize} />
      <Button onPress={chooseAudioHandler} title="Choose Audio" />
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
