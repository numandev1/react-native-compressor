import React, { useEffect } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import compressor, { Video } from 'react-native-compressor';
const Index = () => {
  useEffect(() => {
    Video.compress(
      'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
      {}
    )
      .then(() => {})
      .catch((e) => {
        console.log(e, 'error');
      });
    console.log(compressor, 'compressor', Video);
  }, []);
  return (
    <View style={styles.container}>
      <Text>Video Screen</Text>
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
