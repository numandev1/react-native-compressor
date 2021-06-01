import React, { useEffect } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import compressor, { Audio } from 'react-native-compressor';
const Index = () => {
  useEffect(() => {
    Audio.compress(
      'https://file-examples-com.github.io/uploads/2017/11/file_example_MP3_2MG.mp3',
      { quality: 'medium' }
    )
      .then((result) => {
        console.log(result, 'resultresult');
      })
      .catch((e) => {
        console.log(e, 'error');
      });
    console.log(compressor, 'compressor', Audio);
  }, []);
  return (
    <View style={styles.container}>
      <Text>Audio Screen</Text>
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
